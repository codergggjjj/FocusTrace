package com.focustrace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.data.local.entity.*
import com.focustrace.statistics.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class StatisticsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2024, 2, 29)
    private fun row(id: Long, focus: Long, duration: Long = 0): StatisticsRecord {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        return StatisticsRecord(FocusSessionEntity(id = id, type = 0, startTime = start,
            endTime = start + focus.coerceAtLeast(1) * 1000, plannedSeconds = 100, focusSeconds = focus, status = 4),
            if (duration == 0L) emptyList() else listOf(DistractionEventEntity(id, id, start + 10000, start + 10000 + duration * 1000, duration)))
    }

    @Test fun hourlyChartSplitsTimerSessionByActualHourOverlap() {
        val source = row(1, 6600, 20)
        val start = date.atTime(13, 0).atZone(zone).toInstant().toEpochMilli()
        val session = source.copy(session = source.session.copy(startTime = start,
            endTime = date.atTime(14, 50).atZone(zone).toInstant().toEpochMilli()))
        val result = summarizeStatistics(listOf(session), statisticsRange(date, StatisticsPeriod.DAY, zone))
        assertEquals(24, result.hours.size)
        assertEquals(3600L, result.hours[13].focusSeconds)
        assertEquals(3000L, result.hours[14].focusSeconds)
        assertEquals(20L, result.hours[13].distractionSeconds)
        assertEquals(result.totals.focusSeconds, result.hours.sumOf { it.focusSeconds })
    }

    @Test fun hourlyChartSplitsManualSessionAcrossEveryCoveredHour() {
        val start = date.atTime(13, 30).atZone(zone).toInstant().toEpochMilli()
        val manual = row(2, 6000).copy(session = row(2, 6000).session.copy(
            startTime = start,
            endTime = date.atTime(15, 10).atZone(zone).toInstant().toEpochMilli(),
            type = 1,
            source = "MANUAL"
        ))
        val result = summarizeStatistics(listOf(manual), statisticsRange(date, StatisticsPeriod.DAY, zone))
        assertEquals(1800L, result.hours[13].focusSeconds)
        assertEquals(3600L, result.hours[14].focusSeconds)
        assertEquals(600L, result.hours[15].focusSeconds)
        assertEquals(6000L, result.hours.sumOf { it.focusSeconds })
    }

    @Test fun hourlyChartKeepsEffectiveTotalWhenWallTimeIncludesPauses() {
        val start = date.atTime(13, 0).atZone(zone).toInstant().toEpochMilli()
        val timer = row(3, 3600).copy(session = row(3, 3600).session.copy(
            startTime = start,
            endTime = date.atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        ))
        val result = summarizeStatistics(listOf(timer), statisticsRange(date, StatisticsPeriod.DAY, zone))
        assertEquals(1800L, result.hours[13].focusSeconds)
        assertEquals(1800L, result.hours[14].focusSeconds)
        assertEquals(3600L, result.hours.sumOf { it.focusSeconds })
    }

    @Test fun heatmapThresholdsAreStable() {
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4),
            listOf(0L, 1L, 1799L, 1800L, 3599L, 3600L, 7199L, 7200L).map { com.focustrace.ui.statistics.heatLevel(it) })
    }
    @Test fun calendarRangesUseMondayLeapMonthAndDst() {
        val week = statisticsRange(LocalDate.of(2023, 1, 1), StatisticsPeriod.WEEK, zone)
        assertEquals(LocalDate.of(2022, 12, 26), week.start)
        assertEquals(LocalDate.of(2023, 1, 2), week.endExclusive)
        val month = statisticsRange(date, StatisticsPeriod.MONTH, zone)
        assertEquals(29, summarizeStatistics(emptyList(), month).days.size)
        val year = statisticsRange(date, StatisticsPeriod.YEAR, zone)
        assertEquals(LocalDate.of(2024, 1, 1), year.start)
        assertEquals(LocalDate.of(2025, 1, 1), year.endExclusive)
        assertEquals(LocalDate.of(2023, 1, 1), previousStatisticsRange(year, StatisticsPeriod.YEAR).start)
        val ny = ZoneId.of("America/New_York")
        val spring = statisticsRange(LocalDate.of(2024, 3, 10), StatisticsPeriod.DAY, ny)
        val fall = statisticsRange(LocalDate.of(2024, 11, 3), StatisticsPeriod.DAY, ny)
        assertEquals(23 * 3600000L, spring.endMillis - spring.startMillis)
        assertEquals(25 * 3600000L, fall.endMillis - fall.startMillis)
    }

    @Test fun weightedTotalsExcludeUnfinishedAndKeepCrossMidnightAtStart() {
        val a = row(1, 301, 100)
        val b = row(2, 900)
        val active = row(3, 800).let { it.copy(session = it.session.copy(status = 1, endTime = null)) }
        val resting = row(4, 0).let { it.copy(session = it.session.copy(status = 3)) }
        val fiveMinutes = row(5, 300, 50)
        val range = statisticsRange(date, StatisticsPeriod.MONTH, zone)
        val result = summarizeStatistics(listOf(a, b, active, resting, fiveMinutes), range)
        assertEquals(1201L, result.totals.focusSeconds)
        assertEquals(2, result.totals.sessions)
        assertEquals(2, result.totals.pomodoros)
        assertEquals(92, result.totals.focusPercent)
        assertEquals(100L, result.totals.averageDistractionSeconds)
        assertEquals(10L, result.totals.averageFirstDistractionSeconds)
        assertEquals(0L, result.days.first().totals.focusSeconds)
        assertEquals(1201L, result.days.last().totals.focusSeconds)
        assertFalse(result.sessions.any { it.id == fiveMinutes.session.id })
        assertEquals(0, summarizeStatistics(listOf(a), statisticsRange(date.plusDays(1), StatisticsPeriod.DAY, zone)).totals.sessions)
        assertNull(summarizeStatistics(emptyList(), range).totals.focusPercent)
    }

    @Test fun taskDistributionHoursAndHabitsComeFromValidSessions() {
        val firstStart = date.minusDays(1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val first = row(1, 1800).copy(session = row(1, 1800).session.copy(taskId = 7, taskTitleSnapshot = "算法",
            startTime = firstStart, endTime = firstStart + 1800000))
        val secondDay = date
        val secondStart = secondDay.atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        val second = row(2, 3600).copy(session = row(2, 3600).session.copy(taskId = 7, taskTitleSnapshot = "算法",
            startTime = secondStart, endTime = secondStart + 3600000))
        val short = row(3, 300).copy(session = row(3, 300).session.copy(taskTitleSnapshot = "不应计入"))
        val range = statisticsRange(date, StatisticsPeriod.MONTH, zone)
        val result = summarizeStatistics(listOf(first, second, short), range)
        assertEquals(1, result.taskDistribution.size)
        assertEquals("算法", result.taskDistribution.single().title)
        assertEquals(5400L, result.taskDistribution.single().focusSeconds)
        assertEquals(3600L, result.hours[20].focusSeconds)
        val habits = summarizeHabits(listOf(first.session, second.session, short.session), secondDay, zone)
        assertEquals(2, habits.currentStreak)
        assertEquals(2, habits.longestStreak)
        assertEquals(2, habits.focusedDaysInMonth)
    }

    @Test fun taskFilterScopesTotalsChartsRecordsAndHabitsTogether() {
        val firstStart = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val first = row(10, 1800).copy(session = row(10, 1800).session.copy(taskId = 7,
            taskTitleSnapshot = "算法", startTime = firstStart, endTime = firstStart + 1_800_000))
        val secondStart = date.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val second = row(11, 3600).copy(session = row(11, 3600).session.copy(taskId = 8,
            taskTitleSnapshot = "英语", startTime = secondStart, endTime = secondStart + 3_600_000))
        val range = statisticsRange(date, StatisticsPeriod.DAY, zone)

        val filtered = summarizeStatistics(listOf(first, second), range, taskId = 7)
        assertEquals(1800L, filtered.totals.focusSeconds)
        assertEquals(1, filtered.totals.sessions)
        assertEquals(listOf("算法"), filtered.taskDistribution.map { it.title })
        assertEquals(listOf(10L), filtered.sessions.map { it.id })
        assertEquals(1800L, filtered.hours[9].focusSeconds)
        assertEquals(0L, filtered.hours[14].focusSeconds)

        val habits = summarizeHabits(listOf(first.session, second.session), date, zone, taskId = 7)
        assertEquals(1, habits.currentStreak)
        assertEquals(1, habits.focusedDaysInMonth)
    }

    @Test fun roomStatisticsObserveUpdatedRecords() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java).build()
        try {
            val taskId = db.taskDao().insert(TaskEntity(title = "测试", targetMinutes = 25, createdAt = 1, updatedAt = 1))
            val sample = row(1, 300, 20)
            db.focusSessionDao().insert(sample.session.copy(taskId = taskId))
            db.distractionDao().insert(sample.events.single())
            val range = statisticsRange(date, StatisticsPeriod.DAY, zone)
            val flow = db.focusSessionDao().observeStatistics(range.startMillis, range.endMillis)
            val result = summarizeStatistics(flow.first(), range)
            assertEquals(0L, result.totals.focusSeconds)
            assertEquals(0L, result.totals.distractionSeconds)
            assertEquals(0L, db.focusSessionDao().observeTaskFocusSeconds(taskId).first())
            db.focusSessionDao().update(sample.session.copy(taskId = taskId, focusSeconds = 301))
            assertEquals(301L, summarizeStatistics(flow.first(), range).totals.focusSeconds)
            assertEquals(301L, db.focusSessionDao().observeTaskFocusSeconds(taskId).first())
        } finally { db.close() }
    }
}
