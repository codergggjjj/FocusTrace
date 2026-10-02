package com.focustrace.statistics

import com.focustrace.data.local.entity.FocusSessionEntity
import java.time.LocalDate

/** Lifetime records and a bounded trend, both derived from the same task query. */
data class TaskStudySummary(
    val focusSeconds: Long,
    val sessions: List<FocusSessionEntity>,
    val recordsByDay: Map<LocalDate, List<FocusSessionEntity>>,
    val trend: StatisticsSummary,
)
