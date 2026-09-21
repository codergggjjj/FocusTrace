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
    fun statistics(range: StatisticsRange) = sessions.observeStatistics(range.startMillis, range.endMillis)
        .distinctUntilChanged()
        .map { summarizeStatistics(it, range) }
        .flowOn(Dispatchers.Default)
    fun dashboard(range: StatisticsRange, period: StatisticsPeriod) = combine(
        statistics(range), statistics(previousStatisticsRange(range, period))
    ) { current, previous -> StatisticsDashboard(current, previous) }
        .flowOn(Dispatchers.Default)
    fun habits(today: LocalDate, zone: ZoneId) = sessions.observeValidSessions()
        .distinctUntilChanged()
        .map { summarizeHabits(it, today, zone) }
        .flowOn(Dispatchers.Default)
}
