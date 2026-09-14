package com.focustrace.data.local.entity
import androidx.room.*
@Entity(tableName = "tasks", foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)], indices = [Index("categoryId")],)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long? = null,
    val title: String,
    val targetMinutes: Int,
    val completed: Boolean = false,
    val repeatType: String = "NONE",
    val reminderTime: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
