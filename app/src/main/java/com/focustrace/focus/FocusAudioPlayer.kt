package com.focustrace.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.data.repository.FocusAudioRepository
import com.focustrace.data.repository.FocusAudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class FocusAudioPlayback(val track: FocusAudioTrack? = null, val playing: Boolean = false,
    val preparing: Boolean = false, val canPlay: Boolean = false, val error: String? = null) {
    val foregroundActive: Boolean get() = playing || preparing
}

/** App-scoped player: Activity/navigation recreation never creates a second audio stream. */
class FocusAudioPlayer(
    private val context: Context,
    private val repository: FocusAudioRepository,
    sessions: Flow<FocusSessionEntity?>,
    private val ensureService: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(FocusAudioPlayback())
    val state = mutableState.asStateFlow()
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change -> if (change < 0) pause() }, Handler(Looper.getMainLooper())).build()
    private var player: MediaPlayer? = null
    private var prepared = false
    private var generation = 0
    private var sessionId: Long? = null
    private var sessionStatus: Int? = null
    private var noisyRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    init {
        scope.launch {
            sessions.map { it?.id to it?.status }.distinctUntilChanged()
                .catch {
                    sessionStatus = null
                    stop()
                    mutableState.update { it.copy(canPlay = false, error = "无法读取专注状态，音乐已停止") }
                }
                .collect { (id, status) ->
                    if (sessionId != id || status !in listOf(1, 2)) stop()
                    else if (status == 2) pause()
                    sessionId = id
                    sessionStatus = status
                    mutableState.update { it.copy(canPlay = status == 1) }
                }
        }
        scope.launch {
            repository.library.catch { stop(); mutableState.update { it.copy(error = "无法读取音乐列表") } }
                .collect { library ->
                    if (mutableState.value.track?.id != library.selected?.id) stop()
                    mutableState.update { it.copy(track = library.selected) }
                }
        }
    }

    fun toggle() {
        if (state.value.foregroundActive) pause() else play()
    }

    fun play() {
        if (sessionStatus != 1) {
            mutableState.update { it.copy(error = "请先继续专注，再播放音乐") }
            return
        }
        val track = state.value.track ?: return
        if (state.value.foregroundActive) return
        try {
            ensureService()
            mutableState.update { it.copy(error = null) }
            if (prepared && player != null) { startPrepared(); return }
        } catch (error: Exception) { fail("无法启动音乐播放"); return }
        val request = ++generation
        mutableState.update { it.copy(preparing = true) }
        scope.launch {
            try {
                repository.open(track).use { descriptor ->
                    if (request != generation || sessionStatus != 1) return@launch
                    val audio = MediaPlayer()
                    player = audio
                    audio.setAudioAttributes(attributes)
                    audio.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                    audio.isLooping = true
                    audio.setOnPreparedListener {
                        if (player === it && request == generation) {
                            prepared = true
                            startPrepared()
                        }
                    }
                    audio.setOnErrorListener { failed, _, _ ->
                        if (player === failed) fail("音频无法播放，请重新导入或选择其他文件")
                        true
                    }
                    audio.setDataSource(descriptor.fileDescriptor)
                    audio.prepareAsync()
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (request == generation) fail("音频无法读取或格式不受支持，请重新导入")
            }
        }
    }

    private fun startPrepared() {
        try {
            if (sessionStatus != 1) { pause(); return }
            if (manager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                fail("声音正在被其他应用占用，请稍后播放")
                return
            }
            if (!noisyRegistered) {
                ContextCompat.registerReceiver(context, noisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
                noisyRegistered = true
            }
            player?.start()
            mutableState.update { it.copy(playing = true, preparing = false, error = null) }
        } catch (error: Exception) { fail("音频无法播放，请选择其他文件") }
    }

    fun pause() {
        if (!prepared) { stop(); return }
        try { player?.pause() }
        catch (error: IllegalStateException) { stop(); return }
        abandonFocus()
        mutableState.update { it.copy(playing = false, preparing = false) }
    }

    fun stop() {
        generation++
        val previous = player
        player = null
        prepared = false
        previous?.release()
        abandonFocus()
        mutableState.update { it.copy(playing = false, preparing = false, error = null) }
    }

    private fun abandonFocus() {
        manager.abandonAudioFocusRequest(focusRequest)
        if (noisyRegistered) {
            context.unregisterReceiver(noisyReceiver)
            noisyRegistered = false
        }
    }

    private fun fail(message: String) {
        stop()
        mutableState.update { it.copy(error = message) }
    }
}
