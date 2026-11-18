package com.focustrace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.data.local.entity.DistractionEventEntity
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.data.local.entity.TaskEntity
import com.focustrace.data.repository.FocusRepository
import com.focustrace.data.repository.StatisticsRepository
import com.focustrace.statistics.StatisticsPeriod
import com.focustrace.statistics.statisticsRange
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FocusDurationRepositoryTest {
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java).build()
    private val date = LocalDate.of(2024, 6, 12)
    private val zone = ZoneId.systemDefault()
    private val start = date.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
    private val record = FocusSessionEntity(type = 0, startTime = start, endTime = start + 1_800_000,
        plannedSeconds = 1500, focusSeconds = 900, elapsedMillis = 900_500, status = 4, taskTitleSnapshot = "原任务")

    @Test fun editsEffectiveDurationAndRefreshesStatisticsWithoutChangingOriginalDetails() = runBlocking {
        val db = database()
        try {
            val dao = db.focusSessionDao()
            val focus = FocusRepository(dao, db.distractionDao())
            val statistics = StatisticsRepository(dao)
            val taskId = db.taskDao().insert(TaskEntity(title = "学习", targetMinutes = 25, createdAt = 1, updatedAt = 1))
            val original = record.copy(taskId = taskId, distractionCount = 1, distractionSeconds = 45, note = "保留备注")
            val id = dao.insert(original)
            val event = DistractionEventEntity(sessionId = id, backgroundTime = start + 60_000,
                foregroundTime = start + 105_000, durationSeconds = 45)
            val eventId = db.distractionDao().insert(event)
            val running = record.copy(status = 1, startTime = start + 1_800_000, endTime = null, focusSeconds = 0, elapsedMillis = 0)
            val runningId = dao.insert(running)

            val day = statisticsRange(date, StatisticsPeriod.DAY, zone)
            val originalObserved = CompletableDeferred<Unit>()
            val updated = async { withTimeout(5000) {
                statistics.statistics(day).first {
                    if (it.totals.focusSeconds == 900L) originalObserved.complete(Unit)
                    it.totals.focusSeconds == 1234L
                }
            } }
            withTimeout(5000) { originalObserved.await() }
            focus.updateTimerDuration(id, 1234)
            assertEquals(1234L, updated.await().hours.sumOf { it.focusSeconds })
            assertEquals(original.copy(id = id, focusSeconds = 1234), dao.getSession(id))
            assertEquals(running.copy(id = runningId), dao.latest())
            assertEquals(listOf(event.copy(id = eventId)), focus.reportFor(id).first()!!.events)
            assertEquals(1234L, focus.taskFocusSeconds(taskId).first())
            assertEquals(1234L, statistics.taskStudy(taskId, day).first().focusSeconds)
            StatisticsPeriod.entries.forEach { period ->
                assertEquals(1234L, statistics.statistics(statisticsRange(date, period, zone)).first().totals.focusSeconds)
            }
        } finally { db.close() }
    }

    @Test fun rejectsShortDurationsAndRecordsThatAreNotFinishedTimers() = runBlocking {
        val db = database()
        try {
            val dao = db.focusSessionDao()
            val focus = FocusRepository(dao, db.distractionDao())
            val id = dao.insert(record)
            for (seconds in listOf(-1L, 0L, 300L)) {
                assertTrue(runCatching { focus.updateTimerDuration(id, seconds) }.isFailure)
                assertEquals(record.copy(id = id), dao.getSession(id))
            }
            val protected = listOf(record.copy(status = 0), record.copy(status = 1, endTime = null),
                record.copy(status = 2, endTime = null), record.copy(status = 3), record.copy(endTime = null),
                record.copy(source = "MANUAL"))
            for (session in protected) {
                val unchanged = session.copy(id = id)
                dao.update(unchanged)
                assertTrue(runCatching { focus.updateTimerDuration(id, 301) }.isFailure)
                assertEquals(unchanged, dao.getSession(id))
            }
            assertTrue(runCatching { focus.updateTimerDuration(-1, 301) }.isFailure)
            dao.update(record.copy(id = id))
            focus.updateTimerDuration(id, 301)
            assertEquals(301L, focus.reportFor(id).first()?.session?.focusSeconds)
        } finally { db.close() }
    }
}
