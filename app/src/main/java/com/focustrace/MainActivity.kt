package com.focustrace
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focustrace.data.datastore.ThemeMode
import com.focustrace.ui.components.LoadState
import com.focustrace.ui.settings.SettingsViewModel
import com.focustrace.navigation.AppNavigation
import com.focustrace.ui.theme.FocusTraceTheme
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
            FocusTraceTheme(mode) { AppNavigation(container) }
        }
    }
}
