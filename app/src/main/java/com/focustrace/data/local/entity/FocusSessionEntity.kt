package com.focustrace.data.local.entity
import androidx.room.*
@Entity(tableName = "focus_sessions", foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.SET_NULL)], indices = [Index("taskId"), Index("startTime")],)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val type: Int,
    val startTime: Long,
    val endTime: Long? = null,
    val plannedSeconds: Long,
    val focusSeconds: Long = 0,
    val distractionCount: Int = 0,
    val distractionSeconds: Long = 0,
    val status: Int = 0,
    @ColumnInfo(defaultValue = "0") val elapsedMillis: Long = 0,
    @ColumnInfo(defaultValue = "0") val anchorWall: Long = 0,
    @ColumnInfo(defaultValue = "0") val anchorElapsed: Long = 0,
    @ColumnInfo(defaultValue = "-1") val bootCount: Int = -1,
    @ColumnInfo(defaultValue = "300") val restSeconds: Long = 300,
    @ColumnInfo(defaultValue = "1") val autoBreak: Boolean = true,
    @ColumnInfo(defaultValue = "0") val autoFocus: Boolean = false
)
