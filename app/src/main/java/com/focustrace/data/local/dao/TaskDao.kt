package com.focustrace.data.local.dao
import androidx.room.*
import com.focustrace.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC, id DESC")
    fun getAll(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: Long): TaskEntity?
    @Insert suspend fun insert(value: TaskEntity): Long
    @Update suspend fun update(value: TaskEntity)
    @Delete suspend fun delete(value: TaskEntity)
}
