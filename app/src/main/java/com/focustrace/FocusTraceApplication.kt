package com.focustrace
import android.app.Application
import com.focustrace.data.AppContainer
class FocusTraceApplication : Application() {
    val container by lazy { AppContainer(this) }
}
