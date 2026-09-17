package com.focustrace.data.local.dao
import androidx.room.*
import com.focustrace.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startTime DESC, id DESC")
    fun getAll(): Flow<List<FocusSessionEntity>>
    @Query("SELECT * FROM focus_sessions WHERE source = 'TIMER' ORDER BY id DESC LIMIT 1")
    suspend fun latest(): FocusSessionEntity?
    @Query("SELECT * FROM focus_sessions WHERE source = 'TIMER' ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<FocusSessionEntity?>
    @Query("SELECT title FROM tasks WHERE id = :id")
    suspend fun taskTitle(id: Long): String?
    @Query("UPDATE focus_sessions SET startTime = :start, endTime = :end, focusSeconds = :seconds, elapsedMillis = :millis, taskId = :taskId, taskTitleSnapshot = :title, note = :note WHERE id = :id AND source = 'MANUAL' AND status = 4")
    suspend fun updateManual(id: Long, start: Long, end: Long, seconds: Long, millis: Long, taskId: Long?, title: String?, note: String?): Int
    @Query("DELETE FROM focus_sessions WHERE id = :id AND source = 'MANUAL' AND status = 4")
    suspend fun deleteManual(id: Long): Int
    @Query("SELECT COALESCE(SUM(focusSeconds), 0) FROM focus_sessions WHERE taskId = :taskId AND endTime IS NOT NULL AND status IN (3, 4) AND focusSeconds > 300")
    fun observeTaskFocusSeconds(taskId: Long): Flow<Long>
    @Transaction
    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    fun observeReport(id: Long): Flow<com.focustrace.data.local.entity.SessionWithDistractions?>
    @Transaction
    @Query("SELECT * FROM focus_sessions WHERE startTime >= :start AND startTime < :end AND endTime IS NOT NULL AND status IN (3, 4) ORDER BY startTime, id")
    fun observeStatistics(start: Long, end: Long): Flow<List<com.focustrace.data.local.entity.StatisticsRecord>>
    @Insert suspend fun insert(value: FocusSessionEntity): Long
    @Update suspend fun update(value: FocusSessionEntity)
    @Delete suspend fun delete(value: FocusSessionEntity)
    @Query("SELECT * FROM focus_sessions WHERE startTime >= :start AND startTime < :end ORDER BY startTime DESC")
    fun getSessionsBetween(start: Long, end: Long): Flow<List<FocusSessionEntity>>
    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun getSession(id: Long): FocusSessionEntity?
}
