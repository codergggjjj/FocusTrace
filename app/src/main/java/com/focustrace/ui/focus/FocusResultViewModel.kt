package com.focustrace.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustrace.data.repository.FocusRepository
import com.focustrace.ui.components.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class FocusResultViewModel(repository: FocusRepository, sessionId: Long) : ViewModel() {
    private val reload = MutableStateFlow(0)
    val uiState = reload.flatMapLatest { repository.reportFor(sessionId).asLoadState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadState.Loading)
    fun retry() { reload.value += 1 }
}
