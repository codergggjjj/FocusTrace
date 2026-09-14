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
