package com.focustrace.lifecycle

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.hardware.display.DisplayManager
import android.view.Display

/** App-owned receiver combines process visibility with screen/keyguard state. */
class ScreenFocusMonitor(private val context: Context, private val transition: (Boolean, Boolean) -> Unit) {
    private var foreground = true
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val displays = context.getSystemService(DisplayManager::class.java)
    private var lastState: Pair<Boolean, Boolean>? = null
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) checkState()
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            publish(intent.action == Intent.ACTION_SCREEN_OFF)
        }
    }
    fun register() {
        // These protected system broadcasts require runtime registration only.
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        // Some devices delay screen broadcasts in background. Display callbacks
        // independently detect OFF/DOZE, without needing another Activity callback.
        displays.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    }
    fun onBackground() { foreground = false; publish() }
    fun onForeground() { foreground = true; publish() }
    /** Called by the existing foreground-service tick as a broadcast fallback. */
    fun checkState() = publish()
    private fun publish(screenOff: Boolean = false) {
        val displayState = displays.getDisplay(Display.DEFAULT_DISPLAY)?.state
        val displayAsleep = displayState == Display.STATE_OFF || displayState == Display.STATE_DOZE ||
            displayState == Display.STATE_DOZE_SUSPEND
        val state = foreground to (screenOff || displayAsleep || !power.isInteractive || keyguard.isKeyguardLocked)
        // Repeated service ticks/display notifications must not enqueue DB work.
        if (state != lastState) {
            lastState = state
            transition(state.first, state.second)
        }
    }
}
