package com.focustrace.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.app.KeyguardManager
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.focustrace.FocusTraceApplication
import com.focustrace.MainActivity
import com.focustrace.R
import com.focustrace.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FocusTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operationLock = Mutex()
    private val container by lazy { (application as FocusTraceApplication).container }
    private var loop: Job? = null
    private var lastSession: FocusSessionEntity? = null
    private var lastNotificationKey: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startAsForeground(placeholderNotification())
        loop = scope.launch { monitorTimer() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> performAction {
                container.lifecycle.awaitEvents()
                container.pomodoro.pause()
                refreshAndPublish(allowCompletion = false)
            }
            ACTION_RESUME -> performAction {
                container.lifecycle.awaitEvents()
                container.pomodoro.resume()
                if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    val power = getSystemService(PowerManager::class.java)
                    val keyguard = getSystemService(KeyguardManager::class.java)
                    container.lifecycle.transition(false, !power.isInteractive || keyguard.isKeyguardLocked)
                    container.lifecycle.awaitEvents()
                }
                refreshAndPublish(allowCompletion = false)
            }
            ACTION_FINISH -> performAction {
                container.lifecycle.awaitEvents()
                container.pomodoro.finish()
                stopTimerService()
            }
            ACTION_SYNC, null -> Unit
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun performAction(block: suspend () -> Unit) {
        scope.launch {
            operationLock.withLock {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    refreshAndPublish(allowCompletion = false)
                }
            }
        }
    }

    private suspend fun monitorTimer() {
        while (currentCoroutineContext().isActive) {
            val active = operationLock.withLock { refreshAndPublish(allowCompletion = true) }
            if (!active) break
            delay(1_000)
        }
    }

    private suspend fun refreshAndPublish(allowCompletion: Boolean): Boolean {
        container.lifecycle.awaitEvents()
        val beforeRefresh = container.database.focusSessionDao().latest()
        val current = container.pomodoro.refresh()
        val previous = lastSession ?: beforeRefresh
        if (allowCompletion) notifyTransition(previous, current)
        lastSession = current
        val activeSession = current?.takeIf { it.status in ACTIVE_STATUSES }
        if (activeSession == null) {
            stopTimerService()
            return false
        }
        val key = listOf(activeSession.id, activeSession.status, activeSession.backgroundWall != null,
            activeSession.anchorWall, activeSession.elapsedMillis).joinToString(":")
        if (key != lastNotificationKey) {
            startAsForeground(ongoingNotification(activeSession))
            lastNotificationKey = key
        }
        return true
    }

    private suspend fun notifyTransition(previous: FocusSessionEntity?, current: FocusSessionEntity?) {
        if (previous == null || current == null) return
        val focusFinished = previous.id == current.id && previous.status == 1 && current.status in listOf(3, 4) &&
            current.type == 0 && current.plannedSeconds > 0 && current.focusSeconds >= current.plannedSeconds
        if (focusFinished) {
            postCompletion("专注完成", "${current.taskTitleSnapshot ?: "自由专注"} · ${formatNotificationDuration(current.focusSeconds)}")
        }
        val restFinished = previous.status == 3 &&
            (current.id != previous.id || current.status == 4)
        if (restFinished) {
            postCompletion("休息结束", if (current.id != previous.id) "下一轮专注已经开始" else "可以开始下一轮专注了")
        }
    }

    private suspend fun postCompletion(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val soundEnabled = container.settingsRepository.settings.first().soundEnabled
        val channel = if (soundEnabled) COMPLETION_SOUND_CHANNEL else COMPLETION_SILENT_CHANNEL
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .apply {
                if (!soundEnabled) setSilent(true)
            }
            .build()
        NotificationManagerCompat.from(this).notify(COMPLETION_ID, notification)
    }

    private fun ongoingNotification(session: FocusSessionEntity): Notification {
        val elapsed = container.pomodoro.elapsed(session).coerceAtLeast(0) / 1_000
        val title = session.taskTitleSnapshot ?: "自由专注"
        val builder = NotificationCompat.Builder(this, ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_focus)
            .setContentTitle(title)
            .setContentIntent(openAppIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        when (session.status) {
            1 -> if (session.backgroundWall != null) {
                builder.setContentText("已离开应用 · 返回后继续计时")
            } else if (session.type == 1) {
                builder.setContentText("正向专注进行中")
                    .setWhen(System.currentTimeMillis() - elapsed * 1_000)
                    .setUsesChronometer(true)
            } else {
                val remaining = (session.plannedSeconds - elapsed).coerceAtLeast(0)
                builder.setContentText("专注中 · 剩余 ${formatNotificationDuration(remaining)}")
                    .setWhen(System.currentTimeMillis() + remaining * 1_000)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
            }
            2 -> builder.setContentText("已暂停 · 已专注 ${formatNotificationDuration(elapsed)}")
            3 -> {
                val remaining = (session.restSeconds - elapsed).coerceAtLeast(0)
                builder.setContentText("休息中 · 剩余 ${formatNotificationDuration(remaining)}")
                    .setWhen(System.currentTimeMillis() + remaining * 1_000)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
            }
        }

        when (session.status) {
            1 -> builder.addAction(R.drawable.ic_notification_pause, "暂停", serviceIntent(ACTION_PAUSE, 1))
            2 -> builder.addAction(R.drawable.ic_notification_play, "继续", serviceIntent(ACTION_RESUME, 2))
        }
        builder.addAction(R.drawable.ic_notification_stop, if (session.status == 3) "结束休息" else "结束", serviceIntent(ACTION_FINISH, 3))
        return builder.build()
    }

    private fun placeholderNotification() = NotificationCompat.Builder(this, ONGOING_CHANNEL)
        .setSmallIcon(R.drawable.ic_notification_focus)
        .setContentTitle("FocusTrace")
        .setContentText("正在恢复专注计时…")
        .setContentIntent(openAppIntent())
        .setOngoing(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(this, requestCode,
        Intent(this, FocusTimerService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun startAsForeground(notification: Notification) {
        ServiceCompat.startForeground(this, ONGOING_ID, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
    }

    private fun stopTimerService() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(ONGOING_CHANNEL, "专注计时", NotificationManager.IMPORTANCE_LOW).apply {
            description = "显示当前专注或休息状态"
            setSound(null, null)
            enableVibration(false)
        })
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build()
        manager.createNotificationChannel(NotificationChannel(COMPLETION_SOUND_CHANNEL, "完成提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "专注完成或休息结束提醒"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), attributes)
        })
        manager.createNotificationChannel(NotificationChannel(COMPLETION_SILENT_CHANNEL, "静音完成提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "无提示音的专注完成或休息结束提醒"
            setSound(null, null)
            enableVibration(false)
        })
    }

    companion object {
        private const val ONGOING_CHANNEL = "focus_timer_ongoing"
        private const val COMPLETION_SOUND_CHANNEL = "focus_completion_sound"
        private const val COMPLETION_SILENT_CHANNEL = "focus_completion_silent"
        private const val ONGOING_ID = 1001
        private const val COMPLETION_ID = 1002
        private const val ACTION_SYNC = "com.focustrace.notification.SYNC"
        private const val ACTION_PAUSE = "com.focustrace.notification.PAUSE"
        private const val ACTION_RESUME = "com.focustrace.notification.RESUME"
        private const val ACTION_FINISH = "com.focustrace.notification.FINISH"
        private val ACTIVE_STATUSES = listOf(1, 2, 3)

        fun start(context: Context) {
            ContextCompat.startForegroundService(context,
                Intent(context, FocusTimerService::class.java).setAction(ACTION_SYNC))
        }
    }
}

internal fun formatNotificationDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return if (safe >= 3_600) "%d:%02d:%02d".format(safe / 3_600, safe % 3_600 / 60, safe % 60)
    else "%02d:%02d".format(safe / 60, safe % 60)
}
