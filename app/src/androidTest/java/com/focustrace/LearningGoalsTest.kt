package com.focustrace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focustrace.data.datastore.SettingsDataStore
import com.focustrace.data.datastore.UserSettings
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.data.repository.FocusRepository
import com.focustrace.data.repository.StatisticsRepository
import com.focustrace.statistics.LearningGoalProgress
import com.focustrace.statistics.StatisticsPeriod
import com.focustrace.statistics.statisticsRange
import com.focustrace.data.local.dao.FocusSessionDao
import com.focustrace.data.local.entity.StatisticsRecord
import com.focustrace.data.repository.TaskRepository
import com.focustrace.ui.components.LoadState
import com.focustrace.ui.statistics.StatisticsViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LearningGoalsTest {
    @Test fun goalLoadingFailureCanBeRetriedWithoutLeavingThePage() = runBlocking {
        val container = ApplicationProvider.getApplicationContext<FocusTraceApplication>().container
        val previous = container.settingsRepository.settings.first()
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java).build()
        val realDao = db.focusSessionDao()
        var fail = true
        val dao = object : FocusSessionDao by realDao {
            override fun observeStatistics(start: Long, end: Long): Flow<List<StatisticsRecord>> {
                if (fail) { fail = false; return flow { throw java.io.IOException("临时读取失败") } }
                return realDao.observeStatistics(start, end)
            }
        }
        var vm: StatisticsViewModel? = null
        var observer: Job? = null
        try {
            container.settingsRepository.setLearningGoals(120, 600)
            val viewModel = StatisticsViewModel(StatisticsRepository(dao), container.focusRepository,
                TaskRepository(db.taskDao()), container.settingsRepository)
            vm = viewModel
            observer = launch { viewModel.learningGoals.collect { } }
            withTimeout(3000) { viewModel.learningGoals.first { it is LoadState.Error } }
            viewModel.retry()
            val restored = withTimeout(3000) { viewModel.learningGoals.first { it is LoadState.Ready } } as LoadState.Ready
            assertEquals(120, restored.value.daily.targetMinutes)
            assertEquals(600, restored.value.weekly.targetMinutes)
        } finally {
            observer?.cancel()
            vm?.viewModelScope?.cancel()
            container.settingsRepository.update(previous)
            db.close()
        }
    }
    @Test fun progressHandlesDisabledZeroExactAndExceededGoals() {
        val disabled = LearningGoalProgress(0, 3600)
        assertFalse(disabled.enabled)
        assertFalse(disabled.reached)
        assertEquals(0f, disabled.fraction, 0f)
        assertEquals(0, disabled.percent)
        assertEquals(0L, disabled.remainingSeconds)
        assertEquals(0, LearningGoalProgress(120, 0).percent)
        assertEquals(50, LearningGoalProgress(120, 3600).percent)
        val almost = LearningGoalProgress(60, 3599)
        assertFalse(almost.reached)
        assertEquals(99, almost.percent)
        assertEquals(1L, almost.remainingSeconds)
        listOf(3600L, 7200L).forEach { seconds ->
            val reached = LearningGoalProgress(60, seconds)
            assertTrue(reached.reached)
            assertEquals(100, reached.percent)
            assertEquals(1f, reached.fraction, 0f)
            assertEquals(0L, reached.remainingSeconds)
        }
        assertEquals(0, UserSettings().dailyGoalMinutes)
        assertEquals(0, UserSettings().weeklyGoalMinutes)
    }

    @Test fun timerAndManualGoalsFollowStatisticsAndCrudAcrossYearBoundary() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java).build()
        try {
            val date = LocalDate.of(2024, 12, 31)
            val zone = ZoneId.systemDefault()
            val dao = db.focusSessionDao()
            suspend fun insert(at: LocalDate, seconds: Long, source: String = "TIMER", status: Int = 4): Long {
                val start = at.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
                return dao.insert(FocusSessionEntity(type = 1, startTime = start, endTime = start + seconds * 1000,
                    plannedSeconds = 0, focusSeconds = seconds, source = source, status = status))
            }
            val timer = insert(date.minusDays(1), 600)
            val manual = insert(date, 1200, "MANUAL")
            insert(date, 900, status = 3) // Completed focus while resting counts, as in the existing statistics.
            insert(date, 300); insert(date, 299); insert(date, 3600, status = 2)
            insert(date.minusDays(2), 7200) // Previous week.
            insert(LocalDate.of(2025, 1, 6), 7200) // Next week.
            val repository = StatisticsRepository(dao)
            val focus = FocusRepository(dao, db.distractionDao())
            suspend fun goals() = repository.learningGoals(date, zone, 60, 100).first()
            val initial = goals()
            assertEquals(LocalDate.of(2024, 12, 30), initial.weekStart)
            assertEquals(2100L, initial.daily.focusSeconds)
            assertEquals(2700L, initial.weekly.focusSeconds)
            assertEquals(45, initial.weekly.percent)
            listOf(StatisticsPeriod.DAY, StatisticsPeriod.WEEK).forEach { period ->
                val statistics = repository.statistics(statisticsRange(date, period, zone)).first()
                assertEquals(statistics.totals.focusSeconds,
                    if (period == StatisticsPeriod.DAY) initial.daily.focusSeconds else initial.weekly.focusSeconds)
            }
            val edited = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5000) { repository.learningGoals(date, zone, 60, 100).first { it.daily.focusSeconds == 2700L } }
            }
            focus.saveManual(manual, date.toString(), "10:00", "10:30", null, "补录调整")
            assertEquals(3300L, edited.await().weekly.focusSeconds)
            focus.saveManual(manual, date.minusDays(1).toString(), "10:00", "10:30", null, "改到昨天")
            assertEquals(900L, goals().daily.focusSeconds)
            assertEquals(3300L, goals().weekly.focusSeconds)
            val deleted = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5000) { repository.learningGoals(date, zone, 60, 100).first { it.weekly.focusSeconds == 2700L } }
            }
            focus.deleteRecord(timer)
            assertEquals(2700L, deleted.await().weekly.focusSeconds)
            focus.deleteRecord(manual)
            assertEquals(900L, goals().weekly.focusSeconds)
            assertFalse(repository.learningGoals(date, zone, 0, 0).first().daily.enabled)
        } finally { db.close() }
    }

    @Test fun goalsUseLocalMidnightAndSundayWeekEndDuringDst() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java).build()
        try {
            val day = LocalDate.of(2024, 3, 10)
            val zone = ZoneId.of("America/New_York")
            suspend fun insert(date: LocalDate, hour: Int) {
                val start = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
                db.focusSessionDao().insert(FocusSessionEntity(type = 1, startTime = start, endTime = start + 600000,
                    plannedSeconds = 0, focusSeconds = 600, status = 4))
            }
            insert(day, 0); insert(day, 23)
            insert(day.minusDays(1), 23); insert(day.plusDays(1), 0)
            val goals = StatisticsRepository(db.focusSessionDao()).learningGoals(day, zone, 60, 120).first()
            assertEquals(day, goals.today)
            assertEquals(LocalDate.of(2024, 3, 4), goals.weekStart)
            assertEquals(1200L, goals.daily.focusSeconds)
            assertEquals(1800L, goals.weekly.focusSeconds)
        } finally { db.close() }
    }

    @Test fun goalPreferencesPersistIndependentlyAndInvalidWritesDoNotChangeSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        val store = SettingsDataStore(context)
        val previous = store.settings.first()
        try {
            store.setLearningGoals(120, 600)
            assertEquals(previous.copy(dailyGoalMinutes = 120, weeklyGoalMinutes = 600), SettingsDataStore(context).settings.first())
            for ((daily, weekly) in listOf(-1 to 600, 1441 to 600, 120 to -1, 120 to 10081)) {
                assertTrue(runCatching { store.setLearningGoals(daily, weekly) }.isFailure)
                assertEquals(120, store.settings.first().dailyGoalMinutes)
                assertEquals(600, store.settings.first().weeklyGoalMinutes)
            }
            store.setLearningGoals(0, 10080)
            assertEquals(0, store.settings.first().dailyGoalMinutes)
            assertEquals(10080, store.settings.first().weeklyGoalMinutes)
            store.setLearningGoals(1440, 10080)
            assertEquals(1440, store.settings.first().dailyGoalMinutes)
        } finally { store.update(previous) }
    }
}
