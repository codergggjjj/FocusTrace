package com.focustrace

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LifecycleIntegrationTest {
    @Test fun sleepingInAnotherAppCountsOnlyUnlockedAbsenceAsDistraction() {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        val container = app.container
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val original = runBlocking { container.settingsRepository.settings.first() }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
                .use { it.readBytes() }
        }
        fun waitFor(condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 10000
            while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            assertTrue("Expected screen/lifecycle transition within 10 seconds", condition())
        }
        var sessionId: Long? = null
        try {
            runBlocking {
                container.lifecycle.awaitEvents()
                container.pomodoro.finish()
                container.settingsRepository.setDistractionThreshold(3)
                container.pomodoro.startStopwatch(null)
                sessionId = container.pomodoro.refresh()!!.id
                container.startTimerNotification()
            }
            shell("am start -a android.settings.SETTINGS")
            waitFor { runBlocking { container.database.focusSessionDao().latest()?.backgroundWall != null } }
            SystemClock.sleep(3200)
            shell("input keyevent 223")
            waitFor { !app.getSystemService(android.os.PowerManager::class.java).isInteractive }
            waitFor { runBlocking { container.database.focusSessionDao().latest()?.backgroundWall == null } }
            val before = runBlocking { container.pomodoro.refresh()!! }
            assertEquals(1, before.distractionCount) // The departure BEFORE screen-off.
            val elapsedBefore = container.pomodoro.elapsed(before)
            SystemClock.sleep(5500)
            val asleep = runBlocking { container.pomodoro.refresh()!! }
            assertNull(asleep.backgroundWall)
            assertEquals(before.distractionCount, asleep.distractionCount)
            assertEquals(before.distractionSeconds, asleep.distractionSeconds)
            assertTrue(container.pomodoro.elapsed(asleep) - elapsedBefore >= 5000)
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            waitFor { runBlocking { container.database.focusSessionDao().latest()?.backgroundWall != null } }
            SystemClock.sleep(3200)
            app.startActivity(Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            waitFor { runBlocking { container.database.focusSessionDao().latest()?.backgroundWall == null } }
            val returned = runBlocking { container.pomodoro.refresh()!! }
            assertEquals(2, returned.distractionCount) // Only the new unlocked departure.
            val events = runBlocking { container.database.distractionDao().getBySession(returned.id).first() }
            assertEquals(2, events.size)
        } finally {
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            runBlocking {
                container.lifecycle.awaitEvents()
                container.pomodoro.finish()
                container.settingsRepository.update(original)
                sessionId?.let { container.database.focusSessionDao().getSession(it) }
                    ?.let { container.database.focusSessionDao().delete(it) }
            }
            scenario.close()
        }
    }

    @Test fun actualHomeReturnRecordsOnlyWhileFocusing() {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        val container = app.container
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val original = runBlocking { container.settingsRepository.settings.first() }
        fun waitFor(condition: () -> Boolean) {
            val until = SystemClock.elapsedRealtime() + 10000
            while (!condition() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
            assertTrue("Expected lifecycle transition within 10 seconds", condition())
        }
        fun returnToApp() {
            app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            waitFor { runBlocking { container.database.focusSessionDao().latest()?.backgroundWall == null } }
        }
        try {
            runBlocking {
                container.lifecycle.awaitEvents()
                container.pomodoro.finish()
                container.settingsRepository.setDistractionThreshold(3)
                container.pomodoro.startStopwatch(null)
            }
            scenario.recreate()
            SystemClock.sleep(1000)
            assertEquals(0, runBlocking { container.database.focusSessionDao().latest()!!.distractionCount })
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            waitFor { runBlocking { container.database.focusSessionDao().latest()?.backgroundWall != null } }
            SystemClock.sleep(3200)
            returnToApp()
            assertEquals(1, runBlocking { container.database.focusSessionDao().latest()!!.distractionCount })
            assertTrue(runBlocking { container.database.focusSessionDao().latest()!!.distractionSeconds } >= 3)
            runBlocking { container.lifecycle.awaitEvents(); container.pomodoro.pause() }
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            SystemClock.sleep(4000)
            returnToApp()
            SystemClock.sleep(1000)
            assertEquals(1, runBlocking { container.database.focusSessionDao().latest()!!.distractionCount })
        } finally {
            runBlocking {
                container.lifecycle.awaitEvents()
                container.pomodoro.finish()
                container.settingsRepository.update(original)
            }
            scenario.close()
        }
    }
}
