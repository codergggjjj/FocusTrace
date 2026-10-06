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
    @Query("SELECT * FROM tasks WHERE repeatType != 'NONE' AND repeatParentId IS NULL")
    suspend fun repeatingTasks(): List<TaskEntity>
    @Transaction
    suspend fun refreshRepeats(date: java.time.LocalDate, now: Long) {
        for (task in repeatingTasks()) {
            val last = task.repeatLastDate?.let(java.time.LocalDate::parse)
                ?: java.time.Instant.ofEpochMilli(task.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            if (date > last && com.focustrace.data.repository.TaskRepeat.due(task.repeatType, date)) {
                insert(task.copy(id = 0, completed = false, repeatType = "NONE", reminderTime = null,
                    repeatLastDate = null, repeatParentId = task.id, repeatDate = date.toString(), createdAt = now, updatedAt = now))
                update(task.copy(repeatLastDate = date.toString()))
            }
        }
    }
    @Transaction
    suspend fun updateKeepingRepeatCursor(value: TaskEntity) {
        val current = getTask(value.id) ?: return
        update(value.copy(repeatLastDate = if (current.repeatType == value.repeatType) current.repeatLastDate else java.time.LocalDate.now().toString(),
            repeatParentId = current.repeatParentId, repeatDate = current.repeatDate))
    }
    @Insert suspend fun insert(value: TaskEntity): Long
    @Update suspend fun update(value: TaskEntity)
    @Delete suspend fun delete(value: TaskEntity)
}
