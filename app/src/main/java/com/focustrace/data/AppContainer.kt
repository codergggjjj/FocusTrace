package com.focustrace.data
import android.content.Context
import androidx.room.Room
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.data.datastore.SettingsDataStore
import com.focustrace.data.repository.*
class AppContainer(context: Context) {
    val database = Room.databaseBuilder(context.applicationContext, FocusTraceDatabase::class.java, "focustrace.db")
        .addCallback(FocusTraceDatabase.SeedCategories).build()
    val taskRepository = TaskRepository(database.taskDao(), database.categoryDao())
    val focusRepository = FocusRepository(database.focusSessionDao(), database.distractionDao())
    val statisticsRepository = StatisticsRepository(database.focusSessionDao())
    val settingsRepository = SettingsRepository(SettingsDataStore(context))
}
