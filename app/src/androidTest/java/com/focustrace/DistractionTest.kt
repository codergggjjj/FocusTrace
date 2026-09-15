package com.focustrace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.testing.MigrationTestHelper
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.focus.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DistractionTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FocusTraceDatabase::class.java, emptyList())
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java).build()

    @Test fun screenOffCountsAsFocusAndUnlockOutsideResumesDistraction() = runBlocking {
        val db = database()
        try {
            val clock = PomodoroTest.Clock()
            val engine = PomodoroEngine(db, clock)
            engine.startStopwatch(null)
            clock.advance(5000)
            engine.onScreenExempt()
            clock.advance(60000)
            engine.onScreenExempt() // Screen on, still locked.
            clock.advance(10000)
            engine.onForeground()
            assertEquals(75000L, engine.elapsed(engine.refresh()!!))
            assertEquals(0, engine.refresh()!!.distractionCount)
            engine.onBackground(3)
            clock.advance(5000)
            engine.onScreenExempt() // Only the preceding unlocked absence counts.
            clock.advance(30000)
            engine.onBackground(3) // Unlocked into another app.
            clock.advance(4000)
            engine.onForeground()
            val session = engine.refresh()!!
            assertEquals(105000L, engine.elapsed(session))
            assertEquals(2, session.distractionCount)
            assertEquals(9L, session.distractionSeconds)
        } finally { db.close() }
    }

    @Test fun pomodoroCompletesDuringScreenOffWithoutStartingNextRound() = runBlocking {
        val db = database()
        try {
            val clock = PomodoroTest.Clock()
            val engine = PomodoroEngine(db, clock)
            engine.start(null, 60, 10, true, true)
            engine.onScreenExempt()
            clock.advance(90000)
            val session = engine.refresh()!!
            assertEquals(3, session.status)
            assertEquals(60L, session.focusSeconds)
            assertEquals(0, session.distractionCount)
            engine.onForeground()
            assertNotEquals(session.id, engine.refresh()!!.id)
        } finally { db.close() }
    }

    @Test fun thresholdBoundariesAndRepeatedCallbacks() = runBlocking {
        val db = database()
        try {
            for ((threshold, duration) in listOf(0 to 500L, 3 to 2999L, 3 to 3000L, 10 to 9999L, 10 to 10000L, 7 to 7000L)) {
                val clock = PomodoroTest.Clock()
                val engine = PomodoroEngine(db, clock)
                engine.startStopwatch(null)
                clock.advance(2000)
                engine.onBackground(threshold)
                clock.advance(duration)
                engine.onBackground(threshold) // Duplicate stop cannot reset the departure anchor.
                assertEquals(2000L, engine.elapsed(engine.refresh()!!))
                engine.onForeground()
                engine.onForeground()
                val s = engine.refresh()!!
                val qualifies = duration >= threshold * 1000L
                assertEquals(if (qualifies) 1 else 0, s.distractionCount)
                assertEquals(if (qualifies) duration / 1000 else 0, s.distractionSeconds)
                assertEquals(2000L + if (qualifies) 0 else duration, engine.elapsed(s))
                assertEquals(s.distractionCount, db.distractionDao().getBySession(s.id).first().size)
                engine.finish()
            }
        } finally { db.close() }
    }

    @Test fun pausedRestingIdleAndFinishedDoNotRecord() = runBlocking {
        val db = database()
        try {
            val clock = PomodoroTest.Clock()
            val engine = PomodoroEngine(db, clock)
            engine.onBackground(0); clock.advance(5000); engine.onForeground()
            engine.start(null, 1, 20, true, false)
            engine.pause()
            engine.onBackground(0); clock.advance(5000); engine.onForeground()
            assertEquals(2, engine.refresh()!!.status)
            engine.resume(); clock.advance(1000)
            assertEquals(3, engine.refresh()!!.status)
            engine.onBackground(0); clock.advance(5000); engine.onForeground()
            assertEquals(3, engine.refresh()!!.status)
            engine.finish()
            engine.onBackground(0); clock.advance(5000); engine.onForeground()
            assertTrue(db.distractionDao().getAll().first().isEmpty())
        } finally { db.close() }
    }

    @Test fun leavingPastDeadlineDoesNotCompleteEffectiveFocus() = runBlocking {
        val db = database()
        try {
            val clock = PomodoroTest.Clock()
            val engine = PomodoroEngine(db, clock)
            engine.start(null, 60, 10, true, false)
            clock.advance(59000)
            engine.onBackground(3)
            clock.advance(120000)
            assertEquals(1, engine.refresh()!!.status)
            engine.onForeground()
            assertEquals(59000L, engine.elapsed(engine.refresh()!!))
            assertEquals(120L, engine.refresh()!!.distractionSeconds)
            clock.advance(1000)
            assertEquals(3, engine.refresh()!!.status)
            assertEquals(60L, engine.refresh()!!.focusSeconds)
        } finally { db.close() }
    }

    @Test fun departureSurvivesDatabaseReopenAndDeviceReboot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "departure-${System.nanoTime()}"
        val clock = PomodoroTest.Clock()
        var db = Room.databaseBuilder(context, FocusTraceDatabase::class.java, name).build()
        try {
            var engine = PomodoroEngine(db, clock)
            engine.startStopwatch(null); clock.advance(4000); engine.onBackground(3)
            db.close()
            clock.advance(15000); clock.bootId++; clock.mono = 100
            db = Room.databaseBuilder(context, FocusTraceDatabase::class.java, name).build()
            engine = PomodoroEngine(db, clock)
            engine.onForeground(); engine.onForeground()
            val s = engine.refresh()!!
            assertEquals(4000L, engine.elapsed(s))
            assertEquals(1, s.distractionCount)
            assertEquals(15L, db.distractionDao().getBySession(s.id).first().single().durationSeconds)
            assertNull(s.backgroundWall)
        } finally { db.close() }
    }

    @Test fun callbackSnapshotsExcludeQueueDelayAndUseMonotonicTime() = runBlocking {
        val db = database()
        try {
            val clock = PomodoroTest.Clock()
            val engine = PomodoroEngine(db, clock)
            engine.startStopwatch(null); clock.advance(1000)
            val departure = clock.snapshot()
            clock.advance(5000)
            val returned = clock.snapshot()
            clock.advance(9000)
            engine.onBackground(3, departure)
            engine.onForeground(returned)
            assertEquals(10000L, engine.elapsed(engine.refresh()!!))
            engine.onBackground(3)
            clock.time -= 3600000; clock.advance(3000)
            engine.onForeground()
            assertEquals(2, engine.refresh()!!.distractionCount)
            assertEquals(8L, engine.refresh()!!.distractionSeconds)
        } finally { db.close() }
    }

    @Test fun eventAndAggregateRollbackTogetherThenRetryOnce() = runBlocking {
        val db = database()
        try {
            val clock = PomodoroTest.Clock()
            val engine = PomodoroEngine(db, clock)
            engine.startStopwatch(null); engine.onBackground(3); clock.advance(3000)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_settlement BEFORE UPDATE ON focus_sessions WHEN NEW.backgroundWall IS NULL BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            try { engine.onForeground(); fail("Expected transaction failure") } catch (_: android.database.sqlite.SQLiteException) { }
            assertTrue(db.distractionDao().getAll().first().isEmpty())
            assertNotNull(engine.refresh()!!.backgroundWall)
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_settlement")
            engine.onForeground(); engine.onForeground()
            assertEquals(1, engine.refresh()!!.distractionCount)
            assertEquals(1, db.distractionDao().getAll().first().size)
        } finally { db.close() }
    }

    @Test fun migrationFromOnePreservesTaskAndAddsPendingColumns() {
        migration.createDatabase("distraction-migration", 1).apply {
            execSQL("INSERT INTO tasks (id, categoryId, title, targetMinutes, completed, repeatType, reminderTime, createdAt, updatedAt) VALUES (1, NULL, 'keep', 25, 0, 'NONE', NULL, 1, 1)")
            close()
        }
        migration.runMigrationsAndValidate("distraction-migration", 3, true, FocusTraceDatabase.Migration1To2, FocusTraceDatabase.Migration2To3).apply {
            query("SELECT title FROM tasks WHERE id = 1").use { assertTrue(it.moveToFirst()); assertEquals("keep", it.getString(0)) }
            close()
        }
    }
}
