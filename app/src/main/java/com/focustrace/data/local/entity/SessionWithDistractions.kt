package com.focustrace.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class SessionWithDistractions(
    @Embedded val session: FocusSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId") val events: List<DistractionEventEntity>
)
