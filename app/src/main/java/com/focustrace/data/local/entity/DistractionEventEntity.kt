package com.focustrace.data.local.entity
import androidx.room.*
@Entity(tableName = "distraction_events", foreignKeys = [ForeignKey(entity = FocusSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("sessionId"), Index("backgroundTime")],)
data class DistractionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val backgroundTime: Long,
    val foregroundTime: Long,
    val durationSeconds: Long
)
