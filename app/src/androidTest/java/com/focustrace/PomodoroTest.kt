package com.focustrace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.focus.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PomodoroTest {
    @get:org.junit.Rule val migration = androidx.room.testing.MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        FocusTraceDatabase::class.java, emptyList())
    @Test fun migrationPreservesExistingTasks() {
        migration.createDatabase("pomodoro-migration", 1).apply {
            execSQL("INSERT INTO tasks (id, categoryId, title, targetMinutes, completed, repeatType, reminderTime, createdAt, updatedAt) VALUES (1, NULL, 'existing', 25, 0, 'NONE', NULL, 1, 1)")
            close()
        }
        migration.runMigrationsAndValidate("pomodoro-migration", 2, true, FocusTraceDatabase.Migration1To2).apply {
            query("SELECT title FROM tasks WHERE id = 1").use { assertTrue(it.moveToFirst()); assertEquals("existing", it.getString(0)) }
            close()
        }
    }

    class Clock : TimerClock {
        var time = 100000L
        var mono = 10000L
        var bootId = 1
        override fun wall() = time
        override fun elapsed() = mono
        override fun boot() = bootId
        fun advance(ms: Long) { time += ms; mono += ms }
    }
    @Test fun pauseRestoreCompleteAndRestAreTimestampBased() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java).build()
        try {
            val clock = Clock()
            var engine = PomodoroEngine(db, clock)
            engine.start(null, 60, 10, true, false)
            clock.advance(12500)
            engine.pause()
            assertEquals(12500L, engine.refresh()!!.elapsedMillis)
            clock.advance(100000)
            engine = PomodoroEngine(db, clock)
            assertEquals(2, engine.refresh()!!.status)
            assertEquals(12500L, engine.elapsed(engine.refresh()!!))
            engine.resume()
            clock.time += 3600000 // Same-boot wall clock changes must not alter the countdown.
            clock.advance(47500)
            val rest = engine.refresh()!!
            assertEquals(3, rest.status)
            assertEquals(60L, rest.focusSeconds)
            clock.advance(10000)
            assertEquals(4, engine.refresh()!!.status)
            assertEquals(60L, engine.refresh()!!.focusSeconds)
        } finally { db.close() }
    }
    @Test fun reopeningDatabaseRestoresRunningTimer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "timer-restore-${System.nanoTime()}"
        val clock = Clock()
        var db = Room.databaseBuilder(context, FocusTraceDatabase::class.java, name).build()
        try {
            PomodoroEngine(db, clock).start(null, 60, 10, false, false)
            db.close()
            clock.advance(15000)
            db = Room.databaseBuilder(context, FocusTraceDatabase::class.java, name).build()
            val restored = PomodoroEngine(db, clock)
            assertEquals(1, restored.refresh()!!.status)
            assertEquals(15000L, restored.elapsed(restored.refresh()!!))
            restored.finish()
            assertEquals(15L, restored.refresh()!!.focusSeconds)
        } finally { db.close() }
    }

    @Test fun stopwatchRunsBeyondOneDayAndRestoresPausedProgress() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java).build()
        try {
            val clock = Clock()
            var engine = PomodoroEngine(db, clock)
            engine.startStopwatch(null)
            assertEquals(0L, engine.elapsed(engine.refresh()!!))
            clock.advance(90000500)
            assertEquals(1, engine.refresh()!!.status)
            engine.pause()
            clock.advance(30000)
            engine = PomodoroEngine(db, clock)
            assertEquals(90000500L, engine.elapsed(engine.refresh()!!))
            engine.resume()
            clock.advance(1500)
            engine.finish()
            assertEquals(90002L, engine.refresh()!!.focusSeconds)
            assertEquals(1, engine.refresh()!!.type)
            assertEquals(0L, engine.refresh()!!.plannedSeconds)
            engine.rest()
            assertEquals(4, engine.refresh()!!.status)
        } finally { db.close() }
    }

    @Test fun delayedRefreshAndRebootDoNotOvercount() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java).build()
        try {
            val clock = Clock()
            val engine = PomodoroEngine(db, clock)
            engine.start(null, 60, 10, true, false)
            clock.advance(80000)
            clock.bootId++
            clock.mono = 100
            val finished = engine.refresh()!!
            assertEquals(4, finished.status)
            assertEquals(60L, finished.focusSeconds)
            assertEquals(160000L, finished.endTime)
            engine.start(null, 60, 10, false, false)
            clock.advance(3000)
            engine.finish()
            engine.finish()
            assertEquals(3L, engine.refresh()!!.focusSeconds)
        } finally { db.close() }
    }
}
