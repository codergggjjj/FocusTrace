package com.focustrace

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.focustrace.data.datastore.UserSettings
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.data.local.entity.TaskEntity
import com.focustrace.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TaskOrderTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FocusTraceDatabase::class.java, emptyList())

    @Test fun migrationPreservesTasksAndFocusHistory() {
        migration.createDatabase("task-order-migration", 8).apply {
            execSQL("INSERT INTO tasks (id,title,targetMinutes,completed,repeatType,createdAt,updatedAt,timerType) VALUES (1,'旧待办',45,0,'DAILY',1,2,1)")
            execSQL("INSERT INTO tasks (id,title,targetMinutes,completed,repeatType,createdAt,updatedAt,timerType) VALUES (2,'已完成',25,1,'NONE',3,4,0)")
            execSQL("INSERT INTO focus_sessions (id,taskId,type,startTime,endTime,plannedSeconds,focusSeconds,distractionCount,distractionSeconds,status) VALUES (1,1,1,1000,601000,0,600,1,3,4)")
            execSQL("INSERT INTO distraction_events (id,sessionId,backgroundTime,foregroundTime,durationSeconds) VALUES (1,1,2000,5000,3)")
            close()
        }
        migration.runMigrationsAndValidate("task-order-migration", 9, true, FocusTraceDatabase.Migration8To9).apply {
            query("SELECT title,targetMinutes,completed,timerType,repeatType,createdAt,updatedAt,sortOrder FROM tasks WHERE id=1").use {
                assertTrue(it.moveToFirst()); assertEquals("旧待办", it.getString(0)); assertEquals(45, it.getInt(1))
                assertEquals(0, it.getInt(2)); assertEquals(1, it.getInt(3)); assertEquals("DAILY", it.getString(4))
                assertEquals(1L, it.getLong(5)); assertEquals(2L, it.getLong(6)); assertEquals(0L, it.getLong(7))
            }
            query("SELECT completed,sortOrder FROM tasks WHERE id=2").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)); assertEquals(0L, it.getLong(1)) }
            query("SELECT taskId,focusSeconds FROM focus_sessions").use { assertTrue(it.moveToFirst()); assertEquals(1L, it.getLong(0)); assertEquals(600L, it.getLong(1)) }
            query("SELECT sessionId,durationSeconds FROM distraction_events").use { assertTrue(it.moveToFirst()); assertEquals(1L, it.getLong(0)); assertEquals(3L, it.getLong(1)) }
            close()
        }
    }

    @Test fun movesPendingTasksPersistsOrderAndPreservesStaleEdits() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "task-order-test-${System.nanoTime()}"
        var db = Room.databaseBuilder(context, FocusTraceDatabase::class.java, name).build()
        try {
            val dao = db.taskDao()
            val repository = TaskRepository(dao)
            val old = TaskEntity(id=1,title="A",targetMinutes=25,createdAt=1,updatedAt=1)
            listOf(old, old.copy(id=2,title="B",createdAt=2), old.copy(id=3,title="C",createdAt=3),
                old.copy(id=4,title="D",completed=true,createdAt=4), old.copy(id=5,title="E",completed=true,createdAt=5)).forEach { dao.insert(it) }
            suspend fun pending() = dao.getAll().first().filterNot { it.completed }.map { it.id }
            assertEquals(listOf(3L,2L,1L), pending())
            val earlyTimestamp = repository.insert(old.copy(id=0,title="时钟回拨后新建",createdAt=0))
            repository.movePending(earlyTimestamp, TaskMove.DOWN)
            assertEquals(listOf(3L,earlyTimestamp,2L,1L), pending())
            dao.delete(dao.getTask(earlyTimestamp)!!)
            repository.movePending(1, TaskMove.UP)
            assertEquals(listOf(3L,1L,2L), pending())
            repository.update(old.copy(title="改名"))
            assertEquals(listOf(3L,1L,2L), pending())
            repository.movePending(1, TaskMove.TOP)
            assertEquals(listOf(1L,3L,2L), pending())
            repository.movePending(1, TaskMove.UP)
            repository.movePending(2, TaskMove.DOWN)
            repository.movePending(5, TaskMove.TOP)
            assertEquals(listOf(1L,3L,2L), pending())
            val added = repository.insert(old.copy(id=0,title="新建",createdAt=6))
            assertEquals(listOf(added,1L,3L,2L), pending())
            repository.update(dao.getTask(1)!!.copy(completed=true))
            repository.update(dao.getTask(1)!!.copy(completed=false))
            assertEquals(listOf(added,1L,3L,2L), pending())
            assertEquals(listOf(5L,4L), dao.getAll().first().filter { it.completed }.map { it.id })
            db.close()
            db = Room.databaseBuilder(context, FocusTraceDatabase::class.java, name).build()
            assertEquals(listOf(added,1L,3L,2L), db.taskDao().getAll().first().filterNot { it.completed }.map { it.id })
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun backupPreservesOrderAndAcceptsOlderTasks() {
        val task = TaskEntity(id=1,title="任务",targetMinutes=25,createdAt=1,updatedAt=1,sortOrder=-7)
        val data = BackupData(1,emptyList(),listOf(task),emptyList(),emptyList(),UserSettings(),null)
        assertEquals(-7L, BackupFormat.decode(BackupFormat.encode(data),null).tasks.single().sortOrder)
        val old = JSONObject(BackupFormat.encode(data))
        old.getJSONArray("tasks").getJSONObject(0).remove("sortOrder")
        assertEquals(0L, BackupFormat.decode(old.toString(),null).tasks.single().sortOrder)
    }
}
