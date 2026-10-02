package com.focustrace.data.repository
import android.net.Uri
import com.focustrace.data.datastore.SettingsDataStore
import com.focustrace.data.datastore.UserSettings
import kotlinx.coroutines.flow.first
class SettingsRepository(private val store: SettingsDataStore, private val images: FocusBackgroundImageStore) {
    val settings = store.settings
    suspend fun setDistractionThreshold(seconds: Int) = store.setDistractionThreshold(seconds)
    suspend fun setLearningGoals(daily: Int, weekly: Int) = store.setLearningGoals(daily, weekly)
    suspend fun update(settings: UserSettings) {
        val previous = store.settings.first().focusBackgroundCustomPath
        store.update(settings)
        if (previous != settings.focusBackgroundCustomPath) images.deleteOwned(previous)
    }
    suspend fun imageForSession(sessionId: Long) = store.imageForSession(sessionId)
    suspend fun importBackground(uri: Uri) {
        val path = images.import(uri)
        try {
            update(store.settings.first().copy(focusBackgroundCustomPath = path, focusBackgroundEnabled = true))
        } catch (e: Exception) {
            images.deleteOwned(path)
            throw e
        }
    }
}
