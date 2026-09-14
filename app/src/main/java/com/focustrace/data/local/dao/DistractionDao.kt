package com.focustrace.data.local.dao
import androidx.room.*
import com.focustrace.data.local.entity.DistractionEventEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface DistractionDao {
    @Query("SELECT * FROM distraction_events ORDER BY backgroundTime, id")
    fun getAll(): Flow<List<DistractionEventEntity>>
    @Insert suspend fun insert(value: DistractionEventEntity): Long
    @Update suspend fun update(value: DistractionEventEntity)
    @Delete suspend fun delete(value: DistractionEventEntity)
    @Query("SELECT * FROM distraction_events WHERE sessionId = :sessionId ORDER BY backgroundTime")
    fun getBySession(sessionId: Long): Flow<List<DistractionEventEntity>>
    @Query("SELECT * FROM distraction_events WHERE backgroundTime >= :start AND backgroundTime < :end ORDER BY backgroundTime")
    fun getDistractionsBetween(start: Long, end: Long): Flow<List<DistractionEventEntity>>
}
