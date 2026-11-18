package com.focustrace.studyroom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.focustrace.FocusTraceApplication
import com.focustrace.MainActivity
import com.focustrace.R
import kotlinx.coroutines.*

internal class StudyRoomService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val room get() = (application as FocusTraceApplication).container.studyRoom
    private var running: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            if (running?.isActive == true) room.requestStop() else stopSelf()
            return START_NOT_STICKY
        }
        if (running?.isActive == true) return START_NOT_STICKY
        val request = room.state.value as? StudyRoomState.Connecting
        if (request == null) { stopSelf(); return START_NOT_STICKY }
        val power = getSystemService(PowerManager::class.java)
        val wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:study-room").apply {
            setReferenceCounted(false)
        }
        try {
            val notifications = getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, "自习室连接", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, ID, Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
            val disconnect = PendingIntent.getService(this, ID, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_focus)
                .setContentTitle(if (request.hosting) "正在主持自习室" else "正在连接自习室")
                .setContentText("退出自习室不会中断个人计时")
                .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
                .addAction(0, if (request.hosting) "关闭自习室" else "离开自习室", disconnect)
                .build()
            ServiceCompat.startForeground(this, ID, notification,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
            running = scope.launch {
                val keepAwake = launch {
                    while (isActive) {
                        wakeLock.acquire(10 * 60 * 1000L)
                        delay(8 * 60 * 1000L)
                    }
                }
                try {
                    room.runForeground {
                        ServiceCompat.stopForeground(this@StudyRoomService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                } finally {
                    keepAwake.cancel()
                    if (wakeLock.isHeld) wakeLock.release()
                }
            }
        } catch (_: RuntimeException) {
            room.serviceUnavailable()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running?.isActive == true) room.abortTransport()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "study_room_connection"
        const val ID = 4301
        const val STOP = "com.focustrace.studyroom.STOP"
    }
}
