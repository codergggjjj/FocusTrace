package com.focustrace.data.repository
import com.focustrace.data.local.dao.TaskDao
import com.focustrace.data.local.entity.TaskEntity
enum class TaskMove { UP, DOWN, TOP }
class TaskRepository(private val tasks: TaskDao) {
    val allTasks = tasks.getAll()
    suspend fun insert(task: TaskEntity): Long {
        require(TaskRepeat.valid(task.repeatType))
        return tasks.insertAtTop(task.copy(repeatLastDate = java.time.LocalDate.now().toString()))
    }
    suspend fun refreshRepeats() = tasks.refreshRepeats(java.time.LocalDate.now(), System.currentTimeMillis())
    suspend fun update(task: TaskEntity) {
        require(TaskRepeat.valid(task.repeatType))
        tasks.updateKeepingRepeatCursor(task)
    }
    suspend fun delete(task: TaskEntity) = tasks.delete(task)
    suspend fun movePending(id: Long, move: TaskMove) = tasks.movePending(id, move)
}
