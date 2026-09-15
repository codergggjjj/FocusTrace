package com.focustrace.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustrace.data.repository.SettingsRepository
import com.focustrace.data.datastore.UserSettings
import com.focustrace.ui.components.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val uiState = repository.settings.asLoadState().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun save(value: UserSettings, done: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try { repository.update(value); done() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = "保存失败，请重试。" }
            finally { _busy.value = false }
        }
    }
}
