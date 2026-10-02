package com.focustrace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.data.local.entity.DistractionEventEntity
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.data.local.entity.TaskEntity
import com.focustrace.data.repository.FocusRepository
import com.focustrace.data.repository.StatisticsRepository
import com.focustrace.focus.PomodoroEngine
import com.focustrace.statistics.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FocusRecordDeletionRepositoryTest {
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java).build()
    private val date = LocalDate.of(2024, 6, 12)
    private val zone = ZoneId.systemDefault()
    private val start = date.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
    private fun record(taskId: Long? = null, source: String = "TIMER") = FocusSessionEntity(taskId = taskId,
        type = 1, startTime = start, endTime = start + 3600000, plannedSeconds = 0, focusSeconds = 3600,
        status = 4, source = source, taskTitleSnapshot = "学习")

    @Test fun deleteCascadesDistractionsAndRefreshesEveryStatisticsRange() = runBlocking {
        val db = database()
        try {
            val dao = db.focusSessionDao()
            val focus = FocusRepository(dao, db.distractionDao())
            val statistics = StatisticsRepository(dao)
            val task = TaskEntity(title = "学习", targetMinutes = 25, createdAt = 1, updatedAt = 1)
            val taskId = db.taskDao().insert(task)
            val timer = dao.insert(record(taskId))
            val manual = dao.insert(record(taskId, "MANUAL"))
            db.distractionDao().insert(DistractionEventEntity(sessionId = timer,
                backgroundTime = start + 600000, foregroundTime = start + 660000, durationSeconds = 60))
            val day = statisticsRange(date, StatisticsPeriod.DAY, zone)
            assertEquals(7200L, statistics.statistics(day).first().totals.focusSeconds)
            assertNotNull(focus.reportFor(timer).first())
            // Observe the same flows the screens use, rather than only querying after the deletion.
            val updatedDay = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5000) { statistics.statistics(day).first { it.totals.focusSeconds == 3600L } }
            }
            val updatedTask = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5000) { statistics.taskStudy(taskId, day).first { it.focusSeconds == 3600L } }
            }
            focus.deleteRecord(timer)
            assertEquals(1, updatedDay.await().totals.sessions)
            assertEquals(manual, updatedTask.await().sessions.single().id)
            assertTrue(focus.distractionsFor(timer).first().isEmpty())
            assertNull(focus.reportFor(timer).first())
            assertEquals(taskId, db.taskDao().getAll().first().single().id)
            StatisticsPeriod.entries.forEach { period ->
                val totals = statistics.statistics(statisticsRange(date, period, zone)).first().totals
                assertEquals(3600L, totals.focusSeconds)
                assertEquals(1, totals.sessions)
                assertEquals(0, totals.distractions)
            }
            focus.deleteRecord(manual)
            assertEquals(0L, focus.taskFocusSeconds(taskId).first())
            val habits = statistics.habits(date, zone).first()
            assertEquals(0, habits.currentStreak)
            assertEquals(0, habits.longestStreak)
            assertEquals(0, habits.focusedDaysInMonth)
            assertTrue(dao.getAll().first().isEmpty())
            val missing = runCatching { focus.deleteRecord(timer) }.exceptionOrNull()
            assertEquals("这条专注记录已不存在", missing?.message)
        } finally { db.close() }
    }

    @Test fun rejectsActivePausedRestingAndUnfinishedRecordsWithoutChangingData() = runBlocking {
        val db = database()
        try {
            val dao = db.focusSessionDao()
            val focus = FocusRepository(dao, db.distractionDao())
            val id = dao.insert(record())
            for (status in listOf(0, 1, 2, 3, 4)) {
                val protected = record().copy(id = id, status = status, endTime = if (status == 3) start + 3600000 else null)
                dao.update(protected)
                assertTrue(runCatching { focus.deleteRecord(id) }.isFailure)
                assertEquals(protected, dao.getSession(id))
            }
            dao.update(record().copy(id = id))
            focus.deleteRecord(id)
            assertNull(dao.getSession(id))
        } finally { db.close() }
    }

    @Test fun deletingOldHistoryPreservesRunningTimerAndDistractionHandling() = runBlocking {
        val db = database()
        try {
            val dao = db.focusSessionDao()
            val old = dao.insert(record())
            val focus = FocusRepository(dao, db.distractionDao())
            val clock = PomodoroTest.Clock()
            val engine = PomodoroEngine(db, clock)
            engine.startStopwatch(null)
            val current = dao.latest()!!
            focus.deleteRecord(old)
            assertEquals(current, dao.latest())
            clock.advance(1000)
            engine.onBackground(3)
            clock.advance(4000)
            engine.onForeground()
            val running = engine.refresh()!!
            assertEquals(current.id, running.id)
            assertEquals(1, running.status)
            assertEquals(1, running.distractionCount)
            assertEquals(4L, running.distractionSeconds)
            assertTrue(runCatching { focus.deleteRecord(running.id) }.isFailure)
            engine.pause()
            assertTrue(runCatching { focus.deleteRecord(running.id) }.isFailure)
            engine.resume()
            assertEquals(1, engine.refresh()!!.status)
        } finally { db.close() }
    }
}
