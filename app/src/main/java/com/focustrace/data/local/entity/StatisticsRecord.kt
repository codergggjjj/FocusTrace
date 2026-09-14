package com.focustrace.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class TaskWithCategory(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id") val category: CategoryEntity?
)
data class StatisticsRecord(
    @Embedded val session: FocusSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId") val events: List<DistractionEventEntity>,
    @Relation(entity = TaskEntity::class, parentColumn = "taskId", entityColumn = "id") val task: TaskWithCategory?
)
