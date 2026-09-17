package com.focustrace

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.data.local.entity.TaskEntity
import com.focustrace.data.repository.FocusRepository
import com.focustrace.focus.*
import com.focustrace.statistics.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.*

class ManualRecordTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FocusTraceDatabase::class.java, emptyList())

    @Test fun validatesTimesAndCalculatesTwoHours() {
        assertEquals("16:00", manualEndTime("14:00", "120"))
        listOf("0", "-1", "1440", "99999999999999999999", "abc").forEach {
            assertTrue(runCatching { manualEndTime("14:00", it) }.isFailure)
        }
        assertTrue(runCatching { manualEndTime("23:00", "60") }.isFailure)
        assertEquals("23:59", manualEndTime("23:00", "59"))
        assertEquals(7200L, parseManualFocusInput("2024-06-12", "14:00", "16:00").seconds)
        listOf("14:00" to "14:00", "23:00" to "01:00", "25:00" to "26:00", "" to "16:00").forEach { (start, end) ->
            assertTrue(runCatching { parseManualFocusInput("2024-06-12", start, end) }.isFailure)
        }
        assertTrue(runCatching { parseManualFocusInput("2024-02-30", "14:00", "16:00") }.isFailure)
    }

    @Test fun manualCrudUpdatesAllStatisticsWithoutReplacingActiveTimer() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java).build()
        try {
            val dao = db.focusSessionDao()
            val repository = FocusRepository(dao, db.distractionDao())
            val taskId = db.taskDao().insert(TaskEntity(title = "补录任务", targetMinutes = 25, createdAt = 1, updatedAt = 1))
            val clock = PomodoroTest.Clock()
            val engine = PomodoroEngine(db, clock)
            engine.startStopwatch(null)
            val timerId = dao.latest()!!.id
            repository.saveManual(null, "2024-06-12", "14:00", "16:00", taskId, "复习笔记")
            val manual = dao.getAll().first().single { it.source == "MANUAL" }
            assertEquals("复习笔记", manual.note)
            assertEquals("补录任务", manual.taskTitleSnapshot)
            assertEquals(7200L, repository.taskFocusSeconds(taskId).first())
            StatisticsPeriod.entries.forEach { period ->
                val range = statisticsRange(LocalDate.of(2024, 6, 12), period, ZoneId.systemDefault())
                val summary = summarizeStatistics(dao.observeStatistics(range.startMillis, range.endMillis).first(), range)
                assertEquals(7200L, summary.totals.focusSeconds)
                assertEquals(7200L, summary.days.single { it.date == LocalDate.of(2024, 6, 12) }.totals.focusSeconds)
                assertEquals(0, summary.totals.pomodoros)
            }
            assertEquals(timerId, dao.observeLatest().first()!!.id)
            clock.advance(1000)
            engine.onBackground(3)
            clock.advance(4000)
            engine.onForeground()
            assertEquals(timerId, engine.refresh()!!.id)
            assertEquals(1, engine.refresh()!!.distractionCount)
            assertTrue(runCatching { repository.deleteManual(timerId) }.isFailure)
            assertTrue(runCatching { repository.saveManual(timerId, "2024-06-12", "14:00", "16:00", null, "") }.isFailure)
            val changed = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5000) { repository.taskFocusSeconds(taskId).first { it == 3600L } }
            }
            repository.saveManual(manual.id, "2024-06-13", "14:00", "15:00", taskId, "修改")
            assertEquals(3600L, changed.await())
            val oldDay = statisticsRange(LocalDate.of(2024, 6, 12), StatisticsPeriod.DAY, ZoneId.systemDefault())
            assertTrue(dao.observeStatistics(oldDay.startMillis, oldDay.endMillis).first().isEmpty())
            repository.deleteManual(manual.id)
            assertEquals(0L, repository.taskFocusSeconds(taskId).first())
            assertNull(dao.getSession(manual.id))
            assertEquals(timerId, engine.refresh()!!.id)
        } finally { db.close() }
    }

    @Test fun migrationKeepsTimerHistoryAndAddsSourceDefaults() {
        migration.createDatabase("manual-record-migration", 6).apply {
            execSQL("INSERT INTO focus_sessions (id, taskId, type, startTime, endTime, plannedSeconds, focusSeconds, distractionCount, distractionSeconds, status) VALUES (1, NULL, 0, 1000, 2000, 1, 1, 0, 0, 4)")
            close()
        }
        migration.runMigrationsAndValidate("manual-record-migration", 7, true, FocusTraceDatabase.Migration6To7).use { db ->
            db.query("SELECT source, note, focusSeconds FROM focus_sessions WHERE id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals("TIMER", it.getString(0))
                assertTrue(it.isNull(1))
                assertEquals(1L, it.getLong(2))
            }
        }
    }
}
