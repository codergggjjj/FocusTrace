package com.focustrace.data.datastore
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.random.Random
private val Context.settingsStore by preferencesDataStore(name = "settings")
enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class UserSettings(
    val pomodoroMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val distractionThreshold: Int = 3,
    val autoStartBreak: Boolean = true,
    val autoStartFocus: Boolean = false,
    val soundEnabled: Boolean = true,
    val darkMode: ThemeMode = ThemeMode.SYSTEM,
    val focusBackgroundEnabled: Boolean = true,
    val focusBackgroundRandom: Boolean = true,
    val focusBackgroundIds: Set<String> = FocusBackgrounds.ids.toSet(),
    val focusBackgroundSelectedId: String = FocusBackgrounds.LAKE,
    val focusBackgroundCustomPath: String? = null
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
        val backgroundEnabled = booleanPreferencesKey("focus_background_enabled")
        val backgroundRandom = booleanPreferencesKey("focus_background_random")
        val backgroundIds = stringSetPreferencesKey("focus_background_ids")
        val backgroundSelectedId = stringPreferencesKey("focus_background_selected_id")
        val backgroundCustomPath = stringPreferencesKey("focus_background_custom_path")
        val backgroundSessionId = longPreferencesKey("focus_background_session_id")
        val backgroundSessionImage = stringPreferencesKey("focus_background_session_image")
    }
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        UserSettings(p[Keys.pomodoro] ?: 25, p[Keys.rest] ?: 5, p[Keys.threshold] ?: 3,
            p[Keys.autoBreak] ?: true, p[Keys.autoFocus] ?: false, p[Keys.sound] ?: true,
            ThemeMode.entries.firstOrNull { it.name == p[Keys.theme] } ?: ThemeMode.SYSTEM,
            p[Keys.backgroundEnabled] ?: true, p[Keys.backgroundRandom] ?: true,
            (p[Keys.backgroundIds] ?: FocusBackgrounds.ids.toSet()).intersect(FocusBackgrounds.ids.toSet()),
            p[Keys.backgroundSelectedId]?.takeIf { it in FocusBackgrounds.ids } ?: FocusBackgrounds.LAKE,
            p[Keys.backgroundCustomPath])
    }
    suspend fun setDistractionThreshold(seconds: Int) {
        require(seconds in 0..86400)
        store.edit { it[Keys.threshold] = seconds }
    }
    suspend fun update(value: UserSettings) {
        require(value.pomodoroMinutes in 1..1440 && value.breakMinutes in 1..1440 && value.distractionThreshold in 0..86400)
        require(value.focusBackgroundSelectedId in FocusBackgrounds.ids)
        require(value.focusBackgroundIds.all { it in FocusBackgrounds.ids })
        store.edit { p ->
            p[Keys.pomodoro] = value.pomodoroMinutes
            p[Keys.rest] = value.breakMinutes
            p[Keys.threshold] = value.distractionThreshold
            p[Keys.autoBreak] = value.autoStartBreak
            p[Keys.autoFocus] = value.autoStartFocus
            p[Keys.sound] = value.soundEnabled
            p[Keys.theme] = value.darkMode.name
            p[Keys.backgroundEnabled] = value.focusBackgroundEnabled
            p[Keys.backgroundRandom] = value.focusBackgroundRandom
            p[Keys.backgroundIds] = value.focusBackgroundIds
            p[Keys.backgroundSelectedId] = value.focusBackgroundSelectedId
            if (value.focusBackgroundCustomPath == null) p.remove(Keys.backgroundCustomPath)
            else p[Keys.backgroundCustomPath] = value.focusBackgroundCustomPath
        }
    }
    suspend fun imageForSession(sessionId: Long): String {
        var image = FocusBackgrounds.LAKE
        store.edit { p ->
            val enabled = (p[Keys.backgroundIds] ?: FocusBackgrounds.ids.toSet()).intersect(FocusBackgrounds.ids.toSet())
            val choices = FocusBackgrounds.ids.filter { it in enabled }.ifEmpty { FocusBackgrounds.ids }
            val stored = p[Keys.backgroundSessionImage]
            image = if (p[Keys.backgroundSessionId] == sessionId && stored in choices) stored!!
                else choices.random(Random.Default)
            p[Keys.backgroundSessionId] = sessionId
            p[Keys.backgroundSessionImage] = image
        }
        return image
    }
}
