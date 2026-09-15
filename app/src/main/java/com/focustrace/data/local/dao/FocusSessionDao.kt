package com.focustrace.data.local.dao
import androidx.room.*
import com.focustrace.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startTime DESC, id DESC")
    fun getAll(): Flow<List<FocusSessionEntity>>
    @Query("SELECT * FROM focus_sessions ORDER BY id DESC LIMIT 1")
    suspend fun latest(): FocusSessionEntity?
    @Query("SELECT * FROM focus_sessions ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<FocusSessionEntity?>
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
