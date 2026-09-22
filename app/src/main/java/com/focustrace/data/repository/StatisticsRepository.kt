package com.focustrace.data.repository

import com.focustrace.data.local.dao.FocusSessionDao
import com.focustrace.statistics.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId

class StatisticsRepository(private val sessions: FocusSessionDao) {
    fun sessionsBetween(start: Long, end: Long) = sessions.getSessionsBetween(start, end)
    fun statistics(range: StatisticsRange) = statistics(range, null)
    fun statistics(range: StatisticsRange, taskId: Long?) = sessions.observeStatistics(range.startMillis, range.endMillis)
        .distinctUntilChanged()
        .map { summarizeStatistics(it, range, taskId) }
        .flowOn(Dispatchers.Default)
    fun dashboard(range: StatisticsRange, period: StatisticsPeriod, taskId: Long? = null) = combine(
        statistics(range, taskId), statistics(previousStatisticsRange(range, period), taskId)
    ) { current, previous -> StatisticsDashboard(current, previous) }
        .flowOn(Dispatchers.Default)
    fun habits(today: LocalDate, zone: ZoneId, taskId: Long? = null) = sessions.observeValidSessions()
        .distinctUntilChanged()
        .map { summarizeHabits(it, today, zone, taskId) }
        .flowOn(Dispatchers.Default)
}
