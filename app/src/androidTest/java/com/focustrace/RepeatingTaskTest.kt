package com.focustrace

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.data.local.entity.TaskEntity
import com.focustrace.data.repository.*
import com.focustrace.data.datastore.UserSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class RepeatingTaskTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FocusTraceDatabase::class.java, emptyList())
    @Test fun migrationPreservesOldTasksAndFocusRecords() {
        migration.createDatabase("repeat-migration", 7).apply {
            execSQL("INSERT INTO tasks (id,title,targetMinutes,completed,repeatType,createdAt,updatedAt,timerType) VALUES (1,'旧待办',25,1,'NONE',1,2,1)")
            execSQL("INSERT INTO focus_sessions (id,taskId,type,startTime,endTime,plannedSeconds,focusSeconds,distractionCount,distractionSeconds,status) VALUES (1,1,1,1000,601000,0,600,0,0,4)")
            close()
        }
        migration.runMigrationsAndValidate("repeat-migration", 8, true, FocusTraceDatabase.Migration7To8).apply {
            query("SELECT title,completed,timerType,repeatLastDate,repeatParentId,repeatDate FROM tasks").use {
                assertTrue(it.moveToFirst()); assertEquals("旧待办", it.getString(0)); assertEquals(1,it.getInt(1)); assertEquals(1,it.getInt(2))
                for (i in 3..5) assertTrue(it.isNull(i))
            }
            query("SELECT taskId,focusSeconds FROM focus_sessions").use { assertTrue(it.moveToFirst()); assertEquals(1,it.getLong(0)); assertEquals(600,it.getLong(1)) }
            close()
        }
    }
    @Test fun rulesRespectWeekendsLeapDaysAndValidation() {
        assertTrue(TaskRepeat.due("DAILY", LocalDate.of(2024,2,29)))
        assertFalse(TaskRepeat.due("WEEKDAYS", LocalDate.of(2026,10,4)))
        assertTrue(TaskRepeat.due("WEEKDAYS", LocalDate.of(2026,10,5)))
        assertTrue(TaskRepeat.due("DAYS:17", LocalDate.of(2026,10,4)))
        assertFalse(TaskRepeat.due("DAYS:17", LocalDate.of(2026,10,6)))
        listOf("DAYS:", "DAYS:11", "DAYS:8", "UNKNOWN").forEach { assertFalse(TaskRepeat.valid(it)) }
    }
    @Test fun generationIsAtomicSkipsMissedDaysAndPreservesCompletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java).build()
        try {
            val dao = db.taskDao()
            val original = TaskEntity(title="阅读", targetMinutes=25, completed=true, repeatType="DAILY",
                repeatLastDate="2026-09-30", createdAt=1, updatedAt=1, timerType=1)
            val id = dao.insert(original)
            val day = LocalDate.of(2026,10,4)
            coroutineScope { repeat(4) { launch { dao.refreshRepeats(day, 10) } } }
            val rows = dao.getAll().first()
            assertEquals(2, rows.size)
            val child = rows.single { it.repeatParentId == id }
            assertEquals("2026-10-04",child.repeatDate); assertEquals(1,child.timerType); assertFalse(child.completed)
            assertTrue(dao.getTask(id)!!.completed)
            // A stale edit must not reset the generation cursor.
            dao.updateKeepingRepeatCursor(original.copy(id=id,title="读书"))
            dao.delete(child)
            dao.refreshRepeats(day, 11)
            assertEquals(1,dao.getAll().first().size)
            dao.refreshRepeats(day.minusDays(1), 12)
            assertEquals(1,dao.getAll().first().size)
            dao.refreshRepeats(day.plusDays(1), 13)
            assertEquals("读书",dao.getAll().first().single { it.repeatParentId == id }.title)
            dao.updateKeepingRepeatCursor(dao.getTask(id)!!.copy(repeatType="NONE"))
            dao.refreshRepeats(day.plusDays(2), 14)
            assertEquals(2,dao.getAll().first().size)
            dao.delete(dao.getTask(id)!!)
            dao.refreshRepeats(day.plusDays(3), 15)
            assertEquals(1,dao.getAll().first().size)
        } finally { db.close() }
    }
    @Test fun backupPreservesRepeatCursorAndAcceptsOlderTasks() {
        val task = TaskEntity(id=1,title="阅读",targetMinutes=25,createdAt=1,updatedAt=1,
            repeatType="DAILY",repeatLastDate="2026-10-04")
        val child = task.copy(id=2,repeatType="NONE",repeatLastDate=null,repeatParentId=1,repeatDate="2026-10-04")
        val data = BackupData(1,emptyList(),listOf(task,child),emptyList(),emptyList(),UserSettings(),null)
        assertEquals(data.tasks,BackupFormat.decode(BackupFormat.encode(data),null).tasks)
        val old = org.json.JSONObject(BackupFormat.encode(data.copy(tasks=listOf(task.copy(repeatType="NONE")))))
        old.getJSONArray("tasks").getJSONObject(0).apply { remove("repeatLastDate");remove("repeatParentId");remove("repeatDate") }
        assertNull(BackupFormat.decode(old.toString(),null).tasks.single().repeatLastDate)
    }
}
