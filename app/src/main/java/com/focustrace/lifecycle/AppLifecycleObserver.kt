package com.focustrace.lifecycle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
/** Reserved for the distraction-detection phase; register with ProcessLifecycleOwner then. */
class AppLifecycleObserver(
    private val onBackground: () -> Unit,
    private val onForeground: () -> Unit
) : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) = onBackground()
    override fun onStart(owner: LifecycleOwner) = onForeground()
}
