package com.focustrace.data.repository
import com.focustrace.data.datastore.SettingsDataStore
import com.focustrace.data.datastore.UserSettings
class SettingsRepository(private val store: SettingsDataStore) {
    val settings = store.settings
    suspend fun update(settings: UserSettings) = store.update(settings)
}
