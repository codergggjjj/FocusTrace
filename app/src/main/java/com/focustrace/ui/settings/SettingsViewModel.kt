package com.focustrace.ui.settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustrace.data.repository.SettingsRepository
import com.focustrace.ui.components.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
class SettingsViewModel(repository: SettingsRepository) : ViewModel() {
    val uiState = repository.settings.asLoadState().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
}
