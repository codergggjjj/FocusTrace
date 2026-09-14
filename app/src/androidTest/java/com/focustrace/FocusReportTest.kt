package com.focustrace

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.data.local.entity.*
import com.focustrace.data.repository.FocusRepository
import com.focustrace.focus.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusReportTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FocusTraceDatabase::class.java, emptyList())
    @Test fun designExampleProduces88PercentAndFirstDistraction() {
        val session = FocusSessionEntity(id = 1, type = 0, startTime = 1000000, endTime = 4403000,
            plannedSeconds = 3000, focusSeconds = 3000, status = 4, taskTitleSnapshot = "算法训练")
        val events = listOf(
            DistractionEventEntity(3, 1, 3666000, 3703000, 37),
            DistractionEventEntity(1, 1, 1751000, 1822000, 71),
            DistractionEventEntity(2, 1, 2635000, 2930000, 295))
        val report = FocusReport.from(SessionWithDistractions(session, events))
        assertEquals(88, report.focusPercent)
        assertEquals(403L, report.distractionSeconds)
        assertEquals(134L, report.averageDistractionSeconds)
        assertEquals(751L, report.firstDistractionSeconds)
        assertEquals(listOf(1L, 2L, 3L), report.events.map { it.id })
        assertTrue(report.available)
        assertTrue(report.completedPomodoro)
    }
    @Test fun zeroDurationNoDistractionAndClockRollbackHaveExplicitEmptyValues() {
        val s = FocusSessionEntity(type = 1, startTime = 1000, endTime = 1001, plannedSeconds = 0, status = 4)
        val empty = FocusReport.from(SessionWithDistractions(s, emptyList()))
        assertNull(empty.focusPercent)
        assertNull(empty.firstDistractionSeconds)
        assertNull(empty.averageDistractionSeconds)
        assertFalse(empty.completedPomodoro)
        val focused = FocusReport.from(SessionWithDistractions(s.copy(focusSeconds = 60), emptyList()))
        assertEquals(100, focused.focusPercent)
        val backwards = FocusReport.from(SessionWithDistractions(s, listOf(DistractionEventEntity(1, 1, 500, 700, 5))))
        assertNull(backwards.firstDistractionSeconds)
        assertEquals(0, backwards.focusPercent)
        assertEquals("25 时 0 分 2 秒", formatDuration(90002))
        assertFalse(FocusReport.from(SessionWithDistractions(s.copy(endTime = null, status = 1), emptyList())).available)
    }
    @Test fun reportKeepsTaskTitleAfterRenameDeleteAndNextSession() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java).build()
        try {
            val task = TaskEntity(title = "报告标题", targetMinutes = 25, createdAt = 1, updatedAt = 1)
            val id = db.taskDao().insert(task)
            val clock = PomodoroTest.Clock()
            val engine = PomodoroEngine(db, clock)
            val repository = FocusRepository(db.focusSessionDao(), db.distractionDao())
            engine.startStopwatch(id)
            val sessionId = engine.refresh()!!.id
            assertFalse(repository.reportFor(sessionId).first()!!.available)
            clock.advance(10000); engine.onBackground(3); clock.advance(5000); engine.onForeground()
            engine.finish()
            db.taskDao().update(task.copy(id = id, title = "改名后"))
            db.taskDao().delete(task.copy(id = id))
            engine.startStopwatch(null)
            val report = repository.reportFor(sessionId).first()!!
            assertEquals("报告标题", report.title)
            assertNull(report.session.taskId)
            assertEquals(10L, report.session.focusSeconds)
            assertEquals(5L, report.distractionSeconds)
            assertEquals(67, report.focusPercent)
            assertNull(repository.reportFor(Long.MAX_VALUE).first())
        } finally { db.close() }
    }
    @Test fun migrationBackfillsExistingTaskTitle() {
        migration.createDatabase("report-migration", 3).apply {
            execSQL("INSERT INTO tasks (id, categoryId, title, targetMinutes, completed, repeatType, reminderTime, createdAt, updatedAt) VALUES (1, NULL, 'retained title', 25, 0, 'NONE', NULL, 1, 1)")
            execSQL("INSERT INTO focus_sessions (id, taskId, type, startTime, endTime, plannedSeconds, focusSeconds, distractionCount, distractionSeconds, status) VALUES (1, 1, 0, 1000, 2000, 1500, 1, 0, 0, 4)")
            close()
        }
        migration.runMigrationsAndValidate("report-migration", 4, true, FocusTraceDatabase.Migration3To4).apply {
            query("SELECT taskTitleSnapshot, focusSeconds FROM focus_sessions WHERE id = 1").use {
                assertTrue(it.moveToFirst()); assertEquals("retained title", it.getString(0)); assertEquals(1L, it.getLong(1))
            }
            close()
        }
    }
}
