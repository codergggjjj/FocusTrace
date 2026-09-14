package com.focustrace.lifecycle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
/** Process-level callbacks only; persistence and business rules live in the focus engine. */
class AppLifecycleObserver(
    private val onBackground: () -> Unit,
    private val onForeground: () -> Unit
) : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) = onBackground()
    override fun onStart(owner: LifecycleOwner) = onForeground()
}
