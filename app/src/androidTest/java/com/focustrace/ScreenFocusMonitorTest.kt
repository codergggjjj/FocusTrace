package com.focustrace

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.focustrace.lifecycle.ScreenFocusMonitor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class ScreenFocusMonitorTest {
    @Test fun screenOffOutsideAppIsDetectedEvenWhenScreenBroadcastIsMissing() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Model delayed/missing screen broadcasts; keep real device power/display services.
        val context = object : ContextWrapper(app) {
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? = null
        }
        val transitions = CopyOnWriteArrayList<Pair<Boolean, Boolean>>()
        val monitor = ScreenFocusMonitor(context) { foreground, exempt ->
            transitions.add(foreground to exempt)
        }
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
                .use { it.readBytes() }
        }
        fun waitFor(condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 5000
            while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            assertTrue("Screen state must update without a screen broadcast", condition())
        }
        shell("input keyevent 224")
        shell("wm dismiss-keyguard")
        try {
            instrumentation.runOnMainSync { monitor.register(); monitor.onBackground() }
            assertEquals(false to false, transitions.last())
            shell("input keyevent 223")
            waitFor { !app.getSystemService(PowerManager::class.java).isInteractive }
            waitFor { transitions.lastOrNull() == (false to true) }
            // A delayed process STOP must not reopen the departure while asleep.
            instrumentation.runOnMainSync { monitor.onBackground() }
            assertEquals(false to true, transitions.last())
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            waitFor { transitions.lastOrNull() == (false to false) }
        } finally {
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
        }
    }
}
