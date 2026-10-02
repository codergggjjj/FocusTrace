package com.focustrace.ui.todo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustrace.data.local.entity.TaskEntity
import com.focustrace.data.repository.FocusRepository
import com.focustrace.data.repository.StatisticsRepository
import com.focustrace.data.repository.TaskRepository
import com.focustrace.statistics.StatisticsRange
import com.focustrace.statistics.TaskStudySummary
import com.focustrace.ui.components.LoadState
import com.focustrace.ui.components.asLoadState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId

data class TaskStudyData(val task: TaskEntity?, val tasks: List<TaskEntity>, val summary: TaskStudySummary)

@OptIn(ExperimentalCoroutinesApi::class)
class TaskStudyViewModel(
    val taskId: Long,
    repository: StatisticsRepository,
    taskRepository: TaskRepository,
    private val focusRepository: FocusRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val days = savedState.getStateFlow("studyDays", 7)
    private val reload = MutableStateFlow(0)
    private val calendar = flow {
        while (currentCoroutineContext().isActive) {
            val zone = ZoneId.systemDefault()
            emit(LocalDate.now(zone) to zone)
            delay(60_000)
        }
    }.distinctUntilChanged()
    val uiState = combine(days, calendar, reload) { count, date, _ ->
        StatisticsRange(date.first.minusDays(count - 1L), date.first.plusDays(1), date.second)
    }.flatMapLatest { range ->
        combine(repository.taskStudy(taskId, range), taskRepository.allTasks) { summary, tasks ->
            TaskStudyData(tasks.firstOrNull { it.id == taskId }, tasks, summary)
        }.asLoadState()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadState.Loading)

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun chooseDays(value: Int) { require(value == 7 || value == 30); savedState["studyDays"] = value }
    fun retry() { reload.value += 1 }
    fun clearError() { _error.value = null }
    fun saveManual(id: Long?, date: String, start: String, end: String, task: Long?, note: String, done: () -> Unit) =
        write(done) { focusRepository.saveManual(id, date, start, end, task, note) }
    fun deleteManual(id: Long, done: () -> Unit) = write(done) { focusRepository.deleteManual(id) }
    fun deleteRecord(id: Long, done: () -> Unit) = write(done) { focusRepository.deleteRecord(id) }
    private fun write(done: () -> Unit, action: suspend () -> Unit) {
        if (_saving.value) return
        _saving.value = true
        _error.value = null
        viewModelScope.launch {
            try { action(); done() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = e.message ?: "保存失败，请重试" }
            finally { _saving.value = false }
        }
    }
}
