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
    @Insert suspend fun insert(value: FocusSessionEntity): Long
    @Update suspend fun update(value: FocusSessionEntity)
    @Delete suspend fun delete(value: FocusSessionEntity)
    @Query("SELECT * FROM focus_sessions WHERE startTime >= :start AND startTime < :end ORDER BY startTime DESC")
    fun getSessionsBetween(start: Long, end: Long): Flow<List<FocusSessionEntity>>
    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun getSession(id: Long): FocusSessionEntity?
}
