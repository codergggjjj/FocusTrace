package com.focustrace
import android.app.Application
import com.focustrace.data.AppContainer
class FocusTraceApplication : Application() {
    val container by lazy { AppContainer(this) }
    override fun onCreate() {
        super.onCreate()
        val monitor = com.focustrace.lifecycle.ScreenFocusMonitor(this, container.lifecycle::transition)
        monitor.register()
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
            com.focustrace.lifecycle.AppLifecycleObserver(monitor::onBackground, monitor::onForeground)
        )
    }
}
