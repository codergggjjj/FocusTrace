package com.focustrace.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustrace.data.local.entity.*
import com.focustrace.ui.components.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TodoData(val tasks: List<TaskEntity>, val categories: List<CategoryEntity>)
class TodoViewModel(private val container: com.focustrace.data.AppContainer) : ViewModel() {
    private val repository = container.taskRepository
    fun start(taskId: Long, onStarted: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                container.lifecycle.awaitEvents()
                if (container.pomodoro.refresh()?.status in listOf(1, 2, 3)) {
                    _error.value = "已有计时正在进行，请先到专注页结束当前专注或休息。"
                    return@launch
                }
                val task = repository.allTasks.first().firstOrNull { it.id == taskId }
                if (task == null || task.completed) {
                    _error.value = "待办已完成或已被删除，无法开始。"
                    return@launch
                }
                val settings = container.settingsRepository.settings.first()
                if (task.timerType == 1) container.pomodoro.startStopwatch(task.id)
                else container.pomodoro.start(task.id, task.targetMinutes * 60L,
                    settings.breakMinutes * 60L, settings.autoStartBreak, settings.autoStartFocus)
                onStarted()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _error.value = "无法开始专注，请重试；当前计时会保留。" }
            finally { _busy.value = false }
        }
    }

    val uiState = combine(repository.allTasks, repository.allCategories) { tasks, categories -> TodoData(tasks, categories) }
        .asLoadState().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }
    private fun write(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { action() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _error.value = "保存失败，请重试。" }
            finally { _busy.value = false }
        }
    }
    fun save(original: TaskEntity?, title: String, minutes: Int, categoryId: Long?, timerType: Int, done: () -> Unit) {
        require(title.trim().isNotEmpty() && minutes in 1..1440 && timerType in 0..1)
        write {
            val now = System.currentTimeMillis()
            val task = original?.copy(title = title.trim(), targetMinutes = minutes, timerType = timerType, categoryId = categoryId, updatedAt = now)
                ?: TaskEntity(title = title.trim(), targetMinutes = minutes, timerType = timerType, categoryId = categoryId, createdAt = now, updatedAt = now)
            if (original == null) repository.insert(task) else repository.update(task)
            done()
        }
    }
    fun toggle(task: TaskEntity) = write { repository.update(task.copy(completed = !task.completed, updatedAt = System.currentTimeMillis())) }
    fun delete(task: TaskEntity, done: () -> Unit) = write { repository.delete(task); done() }
    fun addCategory(name: String, done: () -> Unit) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val data = (uiState.value as? LoadState.Ready)?.value ?: return
        if (data.categories.any { it.name == clean }) { _error.value = "分类已存在"; return }
        write { repository.addCategory(clean); done() }
    }
}
