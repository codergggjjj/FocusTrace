package com.focustrace.data.repository
import com.focustrace.data.local.dao.CategoryDao
import com.focustrace.data.local.dao.TaskDao
import com.focustrace.data.local.entity.TaskEntity
class TaskRepository(private val tasks: TaskDao, private val categories: CategoryDao) {
    suspend fun addCategory(name: String) = categories.insert(com.focustrace.data.local.entity.CategoryEntity(name = name, icon = "label", sortOrder = 100, createdAt = System.currentTimeMillis()))
    val allTasks = tasks.getAll()
    val allCategories = categories.getAll()
    suspend fun insert(task: TaskEntity) = tasks.insert(task)
    suspend fun update(task: TaskEntity) = tasks.update(task)
    suspend fun delete(task: TaskEntity) = tasks.delete(task)
}
