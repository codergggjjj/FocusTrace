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
            endTime = start + 86400001, plannedSeconds = 100, focusSeconds = focus, status = 4),
            if (duration == 0L) emptyList() else listOf(DistractionEventEntity(id, id, start + 10000, start + 10000 + duration * 1000, duration)), null)
    }

    @Test fun calendarRangesUseMondayLeapMonthAndDst() {
        val week = statisticsRange(LocalDate.of(2023, 1, 1), StatisticsPeriod.WEEK, zone)
        assertEquals(LocalDate.of(2022, 12, 26), week.start)
        assertEquals(LocalDate.of(2023, 1, 2), week.endExclusive)
        val month = statisticsRange(date, StatisticsPeriod.MONTH, zone)
        assertEquals(29, summarizeStatistics(emptyList(), month).days.size)
        val ny = ZoneId.of("America/New_York")
        val spring = statisticsRange(LocalDate.of(2024, 3, 10), StatisticsPeriod.DAY, ny)
        val fall = statisticsRange(LocalDate.of(2024, 11, 3), StatisticsPeriod.DAY, ny)
        assertEquals(23 * 3600000L, spring.endMillis - spring.startMillis)
        assertEquals(25 * 3600000L, fall.endMillis - fall.startMillis)
    }

    @Test fun weightedTotalsExcludeUnfinishedAndKeepCrossMidnightAtStart() {
        val a = row(1, 100, 100)
        val b = row(2, 900)
        val active = row(3, 800).let { it.copy(session = it.session.copy(status = 1, endTime = null)) }
        val resting = row(4, 0).let { it.copy(session = it.session.copy(status = 3)) }
        val range = statisticsRange(date, StatisticsPeriod.MONTH, zone)
        val result = summarizeStatistics(listOf(a, b, active, resting), range)
        assertEquals(1000L, result.totals.focusSeconds)
        assertEquals(3, result.totals.sessions)
        assertEquals(2, result.totals.pomodoros)
        assertEquals(91, result.totals.focusPercent)
        assertEquals(100L, result.totals.averageDistractionSeconds)
        assertEquals(10L, result.totals.averageFirstDistractionSeconds)
        assertEquals(0L, result.days.first().totals.focusSeconds)
        assertEquals(1000L, result.days.last().totals.focusSeconds)
        assertEquals(1000L, result.categories.single().focusSeconds)
        assertEquals(0, summarizeStatistics(listOf(a), statisticsRange(date.plusDays(1), StatisticsPeriod.DAY, zone)).totals.sessions)
        assertNull(summarizeStatistics(emptyList(), range).totals.focusPercent)
    }

    @Test fun roomStatisticsResolveCategoriesAndObserveUpdatedRecords() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java).build()
        try {
            val categoryId = db.categoryDao().insert(CategoryEntity(name = "学习", icon = "book", sortOrder = 0, createdAt = 1))
            val taskId = db.taskDao().insert(TaskEntity(title = "测试", categoryId = categoryId, targetMinutes = 25, createdAt = 1, updatedAt = 1))
            val sample = row(1, 100, 20)
            db.focusSessionDao().insert(sample.session.copy(taskId = taskId))
            db.distractionDao().insert(sample.events.single())
            val range = statisticsRange(date, StatisticsPeriod.DAY, zone)
            val flow = db.focusSessionDao().observeStatistics(range.startMillis, range.endMillis)
            val result = summarizeStatistics(flow.first(), range)
            assertEquals("学习", result.categories.single().name)
            assertEquals(20L, result.totals.distractionSeconds)
            db.focusSessionDao().update(sample.session.copy(taskId = taskId, focusSeconds = 200))
            assertEquals(200L, summarizeStatistics(flow.first(), range).totals.focusSeconds)
        } finally { db.close() }
    }
}
