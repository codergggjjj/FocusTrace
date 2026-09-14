package com.focustrace.data.datastore
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
private val Context.settingsStore by preferencesDataStore(name = "settings")
enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class UserSettings(
    val pomodoroMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val distractionThreshold: Int = 3,
    val autoStartBreak: Boolean = true,
    val autoStartFocus: Boolean = false,
    val soundEnabled: Boolean = true,
    val darkMode: ThemeMode = ThemeMode.SYSTEM
)
class SettingsDataStore(context: Context) {
    private val store = context.applicationContext.settingsStore
    private object Keys {
        val pomodoro = intPreferencesKey("pomodoro_minutes")
        val rest = intPreferencesKey("break_minutes")
        val threshold = intPreferencesKey("distraction_threshold")
        val autoBreak = booleanPreferencesKey("auto_start_break")
        val autoFocus = booleanPreferencesKey("auto_start_focus")
        val sound = booleanPreferencesKey("sound_enabled")
        val theme = stringPreferencesKey("dark_mode")
    }
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        UserSettings(p[Keys.pomodoro] ?: 25, p[Keys.rest] ?: 5, p[Keys.threshold] ?: 3,
            p[Keys.autoBreak] ?: true, p[Keys.autoFocus] ?: false, p[Keys.sound] ?: true,
            ThemeMode.entries.firstOrNull { it.name == p[Keys.theme] } ?: ThemeMode.SYSTEM)
    }
    suspend fun update(value: UserSettings) {
        require(value.pomodoroMinutes > 0 && value.breakMinutes > 0 && value.distractionThreshold >= 0)
        store.edit { p ->
            p[Keys.pomodoro] = value.pomodoroMinutes
            p[Keys.rest] = value.breakMinutes
            p[Keys.threshold] = value.distractionThreshold
            p[Keys.autoBreak] = value.autoStartBreak
            p[Keys.autoFocus] = value.autoStartFocus
            p[Keys.sound] = value.soundEnabled
            p[Keys.theme] = value.darkMode.name
        }
    }
}
