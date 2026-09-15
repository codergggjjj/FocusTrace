package com.focustrace.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustrace.data.repository.TaskRepository
import com.focustrace.data.local.entity.*
import com.focustrace.ui.components.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TodoData(val tasks: List<TaskEntity>, val categories: List<CategoryEntity>)
class TodoViewModel(private val repository: TaskRepository) : ViewModel() {
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
