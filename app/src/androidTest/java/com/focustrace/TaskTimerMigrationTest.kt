package com.focustrace

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.focustrace.data.local.FocusTraceDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskTimerMigrationTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FocusTraceDatabase::class.java, emptyList())
    @Test fun existingTasksKeepPomodoroAndDataAfterUpgrade() {
        migration.createDatabase("task-timer-migration", 4).apply {
            execSQL("INSERT INTO tasks (id, title, targetMinutes, completed, repeatType, createdAt, updatedAt) VALUES (1, 'existing task', 45, 1, 'NONE', 100, 200)")
            close()
        }
        migration.runMigrationsAndValidate("task-timer-migration", 5, true, FocusTraceDatabase.Migration4To5).apply {
            query("SELECT title, targetMinutes, completed, timerType FROM tasks WHERE id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals("existing task", it.getString(0))
                assertEquals(45, it.getInt(1))
                assertEquals(1, it.getInt(2))
                assertEquals(0, it.getInt(3))
            }
            execSQL("UPDATE tasks SET timerType = 1 WHERE id = 1")
            query("SELECT timerType FROM tasks WHERE id = 1").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            close()
        }
    }
}
