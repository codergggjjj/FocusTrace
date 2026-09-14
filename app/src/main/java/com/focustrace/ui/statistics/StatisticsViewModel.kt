package com.focustrace.ui.statistics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustrace.data.repository.StatisticsRepository
import com.focustrace.ui.components.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
class StatisticsViewModel(repository: StatisticsRepository) : ViewModel() {
    val uiState = repository.sessionsBetween(0, Long.MAX_VALUE).asLoadState().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
}
