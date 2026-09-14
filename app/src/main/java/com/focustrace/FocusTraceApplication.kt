package com.focustrace
import android.app.Application
import com.focustrace.data.AppContainer
class FocusTraceApplication : Application() {
    val container by lazy { AppContainer(this) }
    override fun onCreate() {
        super.onCreate()
        val coordinator = container.lifecycle
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
            com.focustrace.lifecycle.AppLifecycleObserver(coordinator::onBackground, coordinator::onForeground)
        )
    }
}
