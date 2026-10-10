package com.focustrace.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.coroutineContext

private val Context.focusAudioStore by preferencesDataStore(name = "focus_audio")

data class FocusAudioTrack(val id: String, val title: String, val durationSeconds: Long)
data class FocusAudioLibrary(val tracks: List<FocusAudioTrack> = emptyList(), val selectedId: String? = null) {
    val selected: FocusAudioTrack? get() = tracks.firstOrNull { it.id == selectedId }
}

class FocusAudioRepository(context: Context) {
    private val context = context.applicationContext
    private val store = this.context.focusAudioStore
    private val lock = Mutex()
    private val tracksKey = stringPreferencesKey("tracks")
    private val selectedKey = stringPreferencesKey("selected")
    val library = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences ->
        FocusAudioLibrary(decode(preferences[tracksKey]), preferences[selectedKey])
    }

    suspend fun importAudio(uri: Uri): FocusAudioTrack = withContext(Dispatchers.IO) {
        lock.withLock {
            val title = if (uri.scheme == "content") {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            } else uri.lastPathSegment
            val id = UUID.randomUUID().toString()
            val file = ownedFile(id)
            try {
                val input = context.contentResolver.openInputStream(uri) ?: error("无法读取音频文件")
                input.use { source ->
                    file.outputStream().use { destination ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_AUDIO_BYTES) { "音频过大，请选择不超过 100 MB 的文件" }
                            destination.write(buffer, 0, count)
                        }
                        require(total > 0) { "音频文件为空" }
                    }
                }
                val retriever = MediaMetadataRetriever()
                val duration = try {
                    retriever.setDataSource(file.absolutePath)
                    require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes" &&
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") {
                        "请选择手机支持的音频文件"
                    }
                    val millis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                    require(millis > 0) { "音频格式不受支持或文件已损坏" }
                    (millis + 999) / 1000
                } finally { retriever.release() }
                val track = FocusAudioTrack(id, title?.take(200)?.takeIf { it.isNotBlank() } ?: "未命名音频", duration)
                // Commit the library entry as one step; cancellation after the file is copied
                // must not leave a persisted entry pointing at a deleted file.
                withContext(NonCancellable) {
                    store.edit { preferences ->
                        preferences[tracksKey] = encode(decode(preferences[tracksKey]) + track)
                        preferences[selectedKey] = id
                    }
                }
                track
            } catch (error: Exception) {
                // Only this import's explicitly owned file is removed; no directory cleanup.
                file.delete()
                throw error
            }
        }
    }

    suspend fun select(id: String) = store.edit { preferences ->
        require(decode(preferences[tracksKey]).any { it.id == id }) { "音频已被移除，请重新选择" }
        preferences[selectedKey] = id
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val file = ownedFile(id)
            store.edit { preferences ->
                val tracks = decode(preferences[tracksKey]).filterNot { it.id == id }
                preferences[tracksKey] = encode(tracks)
                if (preferences[selectedKey] == id) {
                    if (tracks.isEmpty()) preferences.remove(selectedKey)
                    else preferences[selectedKey] = tracks.first().id
                }
            }
            check(!file.exists() || file.delete()) { "音频文件未能移除，请重试" }
        }
    }

    suspend fun open(track: FocusAudioTrack): ParcelFileDescriptor = withContext(Dispatchers.IO) {
        val file = ownedFile(track.id)
        require(file.isFile) { "音频文件不存在，请重新导入" }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun ownedFile(id: String): File {
        require(UUID.fromString(id).toString() == id) { "音频标识无效" }
        val file = File(context.filesDir, "focus_audio_$id")
        require(file.canonicalFile.parentFile == context.filesDir.canonicalFile) { "音频路径无效" }
        return file
    }

    private fun decode(value: String?): List<FocusAudioTrack> {
        val array = JSONArray(value ?: "[]")
        return List(array.length()) { index ->
            val row = array.getJSONObject(index)
            FocusAudioTrack(row.getString("id"), row.getString("title"), row.getLong("seconds"))
        }
    }

    private fun encode(tracks: List<FocusAudioTrack>): String = JSONArray().apply {
        tracks.forEach { track -> put(JSONObject().put("id", track.id).put("title", track.title).put("seconds", track.durationSeconds)) }
    }.toString()

    companion object { private const val MAX_AUDIO_BYTES = 100L * 1024 * 1024 }
}
