package com.focustrace.data.local.dao
import androidx.room.*
import com.focustrace.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY completed ASC, CASE WHEN completed = 0 THEN sortOrder ELSE 0 END ASC, createdAt DESC, id DESC")
    fun getAll(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE completed = 0 ORDER BY sortOrder ASC, createdAt DESC, id DESC")
    suspend fun pendingInOrder(): List<TaskEntity>
    @Query("SELECT MIN(sortOrder) FROM tasks WHERE completed = 0")
    suspend fun minimumPendingOrder(): Long?
    @Query("UPDATE tasks SET sortOrder = :position WHERE id = :id AND completed = 0")
    suspend fun setPendingOrder(id: Long, position: Long)
    @Transaction
    suspend fun insertAtTop(value: TaskEntity): Long {
        val position = minimumPendingOrder()?.let { Math.subtractExact(it, 1L) } ?: 0L
        return insert(value.copy(sortOrder = position))
    }
    @Transaction
    suspend fun movePending(id: Long, move: com.focustrace.data.repository.TaskMove) {
        val pending = pendingInOrder()
        val index = pending.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = when (move) {
            com.focustrace.data.repository.TaskMove.UP -> index - 1
            com.focustrace.data.repository.TaskMove.DOWN -> index + 1
            com.focustrace.data.repository.TaskMove.TOP -> 0
        }
        if (target !in pending.indices || target == index) return
        val current = pending[index]
        val neighbour = pending[target]
        val tiedRanks = pending.asSequence().zipWithNext().any { (first, second) -> first.sortOrder == second.sortOrder }
        if (move == com.focustrace.data.repository.TaskMove.TOP || tiedRanks) {
            // Legacy rows share rank zero. Normalize only when necessary; ordinary moves update two rows.
            val ordered = pending.toMutableList().apply { add(target, removeAt(index)) }
            ordered.forEachIndexed { position, task ->
                if (task.sortOrder != position.toLong()) setPendingOrder(task.id, position.toLong())
            }
        } else {
            setPendingOrder(current.id, neighbour.sortOrder)
            setPendingOrder(neighbour.id, current.sortOrder)
        }
    }
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
                insertAtTop(task.copy(id = 0, completed = false, repeatType = "NONE", reminderTime = null,
                    repeatLastDate = null, repeatParentId = task.id, repeatDate = date.toString(), createdAt = now, updatedAt = now))
                update(task.copy(repeatLastDate = date.toString()))
            }
        }
    }
    @Transaction
    suspend fun updateKeepingRepeatCursor(value: TaskEntity) {
        val current = getTask(value.id) ?: return
        update(value.copy(repeatLastDate = if (current.repeatType == value.repeatType) current.repeatLastDate else java.time.LocalDate.now().toString(),
            repeatParentId = current.repeatParentId, repeatDate = current.repeatDate, sortOrder = current.sortOrder))
    }
    @Insert suspend fun insert(value: TaskEntity): Long
    @Update suspend fun update(value: TaskEntity)
    @Delete suspend fun delete(value: TaskEntity)
}
