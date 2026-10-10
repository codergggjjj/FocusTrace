package com.focustrace.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustrace.data.repository.FocusAudioRepository
import com.focustrace.focus.FocusAudioPlayer
import com.focustrace.ui.focus.FocusAudioUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MusicLibraryViewModel(private val repository: FocusAudioRepository, private val player: FocusAudioPlayer) : ViewModel() {
    private val importing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    val uiState = combine(repository.library, player.state, importing, message) { library, playback, busy, text ->
        FocusAudioUiState(library, playback, busy, text)
    }.catch { emit(FocusAudioUiState(message = "无法读取音乐列表，请重试")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FocusAudioUiState())

    fun importAudio(uris: List<Uri>) {
        if (importing.value || uris.isEmpty()) return
        val uniqueUris = uris.distinct()
        importing.value = true
        viewModelScope.launch {
            var imported = 0
            var failed = 0
            try {
                for ((index, uri) in uniqueUris.withIndex()) {
                    message.value = "正在导入 ${index + 1} / ${uniqueUris.size}"
                    try { repository.importAudio(uri); imported++ }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { failed++ }
                }
                message.value = if (failed == 0) "已导入 $imported 首" else "已导入 $imported 首，$failed 首失败，请检查格式、大小或可用空间"
            } finally { importing.value = false }
        }
    }
    fun selectAudio(id: String) = action { repository.select(id) }
    fun removeAudio(id: String) = action {
        if (player.state.value.track?.id == id) player.stop()
        repository.remove(id)
    }
    private fun action(block: suspend () -> Unit) {
        if (importing.value) return
        viewModelScope.launch {
            try { block(); message.value = null }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { message.value = "音乐操作失败，请重试" }
        }
    }
}
