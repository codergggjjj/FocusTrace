package com.focustrace

import android.net.Uri
import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.focustrace.data.datastore.SettingsDataStore
import com.focustrace.data.datastore.UserSettings
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.data.local.entity.DistractionEventEntity
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.data.local.entity.TaskEntity
import com.focustrace.data.repository.BackupRepository
import com.focustrace.data.repository.FocusBackgroundImageStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupRepositoryTest {
    @Test fun restoresRelationsAndSettingsAndRefusesActiveTimer() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(target.cacheDir, "backup-repository-test").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(testRoot, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(testRoot, "cache").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(context, FocusTraceDatabase::class.java).build()
        val store = SettingsDataStore(context)
        val previousSettings = store.settings.first()
        val repository = BackupRepository(context, db, store, FocusBackgroundImageStore(context))
        val file = File.createTempFile("focustrace-backup-test-", ".zip", context.cacheDir)
        try {
            store.update(UserSettings(pomodoroMinutes = 40, dailyGoalMinutes = 120, weeklyGoalMinutes = 600))
            db.taskDao().insert(TaskEntity(id = 7, title = "操作系统", targetMinutes = 25,
                createdAt = 1, updatedAt = 1))
            db.focusSessionDao().insert(FocusSessionEntity(id = 9, taskId = 7, type = 0,
                startTime = 1000, endTime = 500000, plannedSeconds = 1500,
                focusSeconds = 499, status = 4, taskTitleSnapshot = "操作系统"))
            db.distractionDao().insert(DistractionEventEntity(id = 11, sessionId = 9,
                backgroundTime = 2000, foregroundTime = 5000, durationSeconds = 3))
            val uri = Uri.fromFile(file)
            val exported = repository.export(uri)
            assertEquals(1, exported.taskCount)
            assertEquals(1, repository.inspect(uri).sessionCount)

            db.focusSessionDao().insert(FocusSessionEntity(id = 20, type = 1, startTime = 600000,
                plannedSeconds = 0, status = 1))
            assertTrue(runCatching { repository.restore(uri) }.isFailure)
            assertEquals(20L, db.focusSessionDao().getSession(20)!!.id)
            db.focusSessionDao().delete(db.focusSessionDao().getSession(20)!!)

            db.distractionDao().delete(db.distractionDao().getAll().first().single())
            db.focusSessionDao().delete(db.focusSessionDao().getSession(9)!!)
            db.taskDao().delete(db.taskDao().getTask(7)!!)
            store.update(UserSettings(pomodoroMinutes = 25))
            repository.restore(uri)

            assertEquals("操作系统", db.taskDao().getTask(7)?.title)
            assertEquals("操作系统", db.focusSessionDao().getSession(9)?.taskTitleSnapshot)
            assertEquals(9L, db.distractionDao().getAll().first().single().sessionId)
            assertEquals(40, store.settings.first().pomodoroMinutes)
            assertEquals(120, store.settings.first().dailyGoalMinutes)
            assertEquals(600, store.settings.first().weeklyGoalMinutes)
        } finally {
            store.update(previousSettings)
            db.close()
            file.delete()
        }
    }
}
