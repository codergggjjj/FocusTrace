package com.focustrace.ui.todo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustrace.data.repository.TaskRepository
import com.focustrace.ui.components.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
class TodoViewModel(repository: TaskRepository) : ViewModel() {
    val uiState = repository.allTasks.asLoadState().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
}
