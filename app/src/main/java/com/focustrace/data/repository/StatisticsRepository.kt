package com.focustrace.data.repository

import com.focustrace.data.local.dao.FocusSessionDao
import com.focustrace.statistics.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.ZoneId

class StatisticsRepository(private val sessions: FocusSessionDao) {
    fun learningGoals(today: LocalDate, zone: ZoneId, dailyMinutes: Int, weeklyMinutes: Int): kotlinx.coroutines.flow.Flow<LearningGoalsSummary> {
        val week = statisticsRange(today, StatisticsPeriod.WEEK, zone)
        if (dailyMinutes == 0 && weeklyMinutes == 0) return flowOf(LearningGoalsSummary(today, week.start,
            LearningGoalProgress(0, 0), LearningGoalProgress(0, 0)))
        return statistics(week).map { summary ->
            val dailySeconds = summary.days.firstOrNull { it.date == today }?.totals?.focusSeconds ?: 0
            LearningGoalsSummary(today, week.start, LearningGoalProgress(dailyMinutes, dailySeconds),
                LearningGoalProgress(weeklyMinutes, summary.totals.focusSeconds))
        }
    }
    fun taskStudy(taskId: Long, range: StatisticsRange) = sessions.observeTaskStatistics(taskId)
        .distinctUntilChanged()
        .map { rows ->
            val records = rows.map { it.session }
            val byDay = records.groupBy { java.time.Instant.ofEpochMilli(it.startTime).atZone(range.zone).toLocalDate() }
            TaskStudySummary(records.sumOf { it.focusSeconds }, records, byDay,
                summarizeStatistics(rows, range, taskId))
        }.flowOn(Dispatchers.Default)
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
