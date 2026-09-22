package com.focustrace

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.focustrace.notification.FocusTimerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationTest {
    @Test fun ongoingNotificationSupportsPauseResumeAndFinish() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        val manager = app.getSystemService(NotificationManager::class.java)
        grantNotifications(app)
        app.container.lifecycle.awaitEvents()
        app.container.pomodoro.finish()
        app.container.pomodoro.startStopwatch(null)
        FocusTimerService.start(app)
        try {
            val running = waitForNotification(manager, "暂停")
            assertTrue(running.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
            running.notification.actions.first { it.title.toString() == "暂停" }.actionIntent.send()
            withTimeout(5_000) {
                while (app.container.database.focusSessionDao().latest()?.status != 2) delay(100)
            }
            val paused = waitForNotification(manager, "继续")
            paused.notification.actions.first { it.title.toString() == "继续" }.actionIntent.send()
            withTimeout(5_000) {
                while (app.container.database.focusSessionDao().latest()?.status != 1) delay(100)
            }
            waitForNotification(manager, "结束").notification.actions
                .first { it.title.toString() == "结束" }.actionIntent.send()
            withTimeout(5_000) {
                while (app.container.database.focusSessionDao().latest()?.status != 4) delay(100)
            }
        } finally {
            app.container.pomodoro.finish()
            app.stopService(Intent(app, FocusTimerService::class.java))
        }
    }

    @Test fun naturalPomodoroCompletionPostsCompletionNotification() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        val manager = app.getSystemService(NotificationManager::class.java)
        grantNotifications(app)
        manager.cancelAll()
        app.container.lifecycle.awaitEvents()
        app.container.pomodoro.finish()
        app.container.pomodoro.start(null, seconds = 1, restSeconds = 60, autoBreak = false, autoFocus = false)
        FocusTimerService.start(app)
        try {
            val completed = withTimeout(5_000) {
                var notification: StatusBarNotification? = null
                while (notification == null) {
                    notification = manager.activeNotifications.firstOrNull {
                        it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() == "专注完成"
                    }
                    if (notification == null) delay(100)
                }
                notification
            }
            assertTrue(completed.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)
                ?.contains("自由专注") == true)
        } finally {
            app.container.pomodoro.finish()
            app.stopService(Intent(app, FocusTimerService::class.java))
            manager.cancelAll()
        }
    }

    private suspend fun waitForNotification(manager: NotificationManager, action: String): StatusBarNotification = withTimeout(5_000) {
        var notification: StatusBarNotification? = null
        while (notification == null) {
            notification = manager.activeNotifications.firstOrNull { row ->
                row.notification.actions?.any { it.title.toString() == action } == true
            }
            if (notification == null) delay(100)
        }
        notification
    }

    private fun grantNotifications(app: FocusTraceApplication) {
        if (Build.VERSION.SDK_INT < 33) return
        val command = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${app.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
    }
}
