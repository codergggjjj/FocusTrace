package com.focustrace.data
import android.content.Context
import androidx.room.Room
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.data.datastore.SettingsDataStore
import com.focustrace.data.repository.*
class AppContainer(context: Context) {
    val database = Room.databaseBuilder(context.applicationContext, FocusTraceDatabase::class.java, "focustrace.db")
        .addMigrations(FocusTraceDatabase.Migration1To2, FocusTraceDatabase.Migration2To3, FocusTraceDatabase.Migration3To4, FocusTraceDatabase.Migration4To5).addCallback(FocusTraceDatabase.SeedCategories).build()
    private val clock = com.focustrace.focus.AndroidTimerClock(context)
    val pomodoro = com.focustrace.focus.PomodoroEngine(database, clock)
    val taskRepository = TaskRepository(database.taskDao(), database.categoryDao())
    val focusRepository = FocusRepository(database.focusSessionDao(), database.distractionDao())
    val statisticsRepository = StatisticsRepository(database.focusSessionDao())
    val settingsRepository = SettingsRepository(SettingsDataStore(context))
    val lifecycle = com.focustrace.lifecycle.FocusLifecycleCoordinator(pomodoro, settingsRepository, clock)
}
