package com.focustrace.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustrace.data.repository.FocusRepository
import com.focustrace.ui.components.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class FocusResultViewModel(private val repository: FocusRepository, private val sessionId: Long) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()
    val uiState = reload.flatMapLatest { repository.reportFor(sessionId).asLoadState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadState.Loading)
    fun retry() { reload.value += 1 }
    fun clearActionError() { _actionError.value = null }
    fun deleteRecord(onSuccess: () -> Unit) = changeRecord("删除失败，请重试", onSuccess) {
        repository.deleteRecord(sessionId)
    }
    fun updateDuration(seconds: Long, onSuccess: () -> Unit) = changeRecord("保存失败，请重试", onSuccess) {
        repository.updateTimerDuration(sessionId, seconds)
    }
    private fun changeRecord(failureMessage: String, onSuccess: () -> Unit, change: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        clearActionError()
        viewModelScope.launch {
            try { change(); onSuccess() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _actionError.value = when (e) {
                    is IllegalArgumentException, is IllegalStateException -> e.message ?: failureMessage
                    else -> failureMessage
                }
            } finally { _busy.value = false }
        }
    }
}
