package com.focustrace.lifecycle

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager

/** App-owned receiver combines process visibility with screen/keyguard state. */
class ScreenFocusMonitor(private val context: Context, private val transition: (Boolean, Boolean) -> Unit) {
    private var foreground = true
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
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
    }
    fun onBackground() { foreground = false; publish() }
    fun onForeground() { foreground = true; publish() }
    private fun publish(screenOff: Boolean = false) {
        transition(foreground, screenOff || !power.isInteractive || keyguard.isKeyguardLocked)
    }
}
