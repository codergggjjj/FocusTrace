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
    private val _deleting = MutableStateFlow(false)
    val deleting = _deleting.asStateFlow()
    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError = _deleteError.asStateFlow()
    val uiState = reload.flatMapLatest { repository.reportFor(sessionId).asLoadState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadState.Loading)
    fun retry() { reload.value += 1 }
    fun clearDeleteError() { _deleteError.value = null }
    fun deleteRecord(onSuccess: () -> Unit) {
        if (_deleting.value) return
        _deleting.value = true
        _deleteError.value = null
        viewModelScope.launch {
            try { repository.deleteRecord(sessionId); onSuccess() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _deleteError.value = e.message ?: "删除失败，请重试" }
            finally { _deleting.value = false }
        }
    }
}
