package com.focustrace.data.local.entity
import androidx.room.*
@Entity(tableName = "tasks", foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)], indices = [Index("categoryId"), Index(value = ["repeatParentId", "repeatDate"], unique = true)],)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long? = null,
    val title: String,
    val targetMinutes: Int,
    val completed: Boolean = false,
    val repeatType: String = "NONE",
    val reminderTime: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val timerType: Int = 0, // 0: pomodoro, 1: stopwatch
    @ColumnInfo(defaultValue = "NULL") val repeatLastDate: String? = null,
    @ColumnInfo(defaultValue = "NULL") val repeatParentId: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val repeatDate: String? = null,
    @ColumnInfo(defaultValue = "0") val sortOrder: Long = 0
)
