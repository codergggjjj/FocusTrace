package com.focustrace.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.focustrace.data.local.entity.CategoryEntity
import com.focustrace.data.local.entity.DistractionEventEntity
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.data.local.entity.TaskEntity

/** Reads and replaces the complete local data set inside a Room transaction. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM categories ORDER BY id") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM tasks ORDER BY id") suspend fun tasks(): List<TaskEntity>
    @Query("SELECT * FROM focus_sessions ORDER BY id") suspend fun sessions(): List<FocusSessionEntity>
    @Query("SELECT * FROM distraction_events ORDER BY id") suspend fun distractions(): List<DistractionEventEntity>
    @Query("SELECT COUNT(*) FROM focus_sessions WHERE source = 'TIMER' AND status IN (1, 2, 3)")
    suspend fun activeTimerCount(): Int

    @Query("DELETE FROM distraction_events") suspend fun clearDistractions()
    @Query("DELETE FROM focus_sessions") suspend fun clearSessions()
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM categories") suspend fun clearCategories()

    @Insert suspend fun insertCategories(values: List<CategoryEntity>)
    @Insert suspend fun insertTasks(values: List<TaskEntity>)
    @Insert suspend fun insertSessions(values: List<FocusSessionEntity>)
    @Insert suspend fun insertDistractions(values: List<DistractionEventEntity>)
}
