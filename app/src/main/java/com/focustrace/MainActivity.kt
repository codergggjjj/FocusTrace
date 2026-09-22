package com.focustrace
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focustrace.data.datastore.ThemeMode
import com.focustrace.ui.components.LoadState
import com.focustrace.ui.settings.SettingsViewModel
import com.focustrace.navigation.AppNavigation
import com.focustrace.ui.theme.FocusTraceTheme
import kotlinx.coroutines.launch
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FocusTraceApplication).container
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory {
                initializer { SettingsViewModel(container.settingsRepository) }
            })
            val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val mode = (settings as? LoadState.Ready)?.value?.darkMode ?: ThemeMode.SYSTEM
            FocusTraceTheme(mode) {
                val lightBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightBars
                        isAppearanceLightNavigationBars = lightBars
                    }
                }
                AppNavigation(container)
            }
        }
        requestNotificationPermission()
        lifecycleScope.launch {
            if (container.database.focusSessionDao().latest()?.status in listOf(1, 2, 3)) {
                container.startTimerNotification()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || isInstrumentationInstalled() ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
    }

    @Suppress("DEPRECATION")
    private fun isInstrumentationInstalled() = runCatching {
        packageManager.getPackageInfo("$packageName.test", 0)
    }.isSuccess

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 2001
    }
}
