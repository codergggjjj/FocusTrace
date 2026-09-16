package com.focustrace.data.repository
import com.focustrace.data.local.dao.TaskDao
import com.focustrace.data.local.entity.TaskEntity
class TaskRepository(private val tasks: TaskDao) {
    val allTasks = tasks.getAll()
    suspend fun insert(task: TaskEntity) = tasks.insert(task)
    suspend fun update(task: TaskEntity) = tasks.update(task)
    suspend fun delete(task: TaskEntity) = tasks.delete(task)
}
