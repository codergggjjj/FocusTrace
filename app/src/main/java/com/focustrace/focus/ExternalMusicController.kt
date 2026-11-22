package com.focustrace.focus

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.focustrace.FocusTraceApplication
import com.focustrace.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class MusicSource(val label: String, val packageName: String?) {
    LOCAL("本地音乐", null), NETEASE("网易云音乐", "com.netease.cloudmusic"), QQ("QQ音乐", "com.tencent.qqmusic")
}

data class ExternalMusicState(
    val source: MusicSource = MusicSource.LOCAL,
    val installed: Boolean = false, val granted: Boolean = false, val connected: Boolean = false,
    val playing: Boolean = false, val title: String? = null, val artist: String? = null,
    val canToggle: Boolean = false, val canPrevious: Boolean = false, val canNext: Boolean = false
)

class MusicSessionListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        (application as FocusTraceApplication).container.externalMusic.refresh()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        (application as FocusTraceApplication).container.externalMusic.disconnect()
    }
}

internal class MusicSessionTarget<T>(private val packageOf: (T) -> String, private val tokenOf: (T) -> Any) {
    var current: T? = null
        private set
    private var owned: T? = null

    fun bind(sessions: List<T>, source: MusicSource, changed: (T?, T?) -> Unit) {
        val matching = sessions.filter { packageOf(it) == source.packageName }
        val previousToken = current?.let(tokenOf)
        val next = matching.firstOrNull { tokenOf(it) == previousToken } ?: matching.firstOrNull()
        if (next?.let(tokenOf) == current?.let(tokenOf)) return
        val previous = current
        current = next
        owned = null
        changed(previous, next)
    }

    fun command(enabled: Boolean, send: (T) -> Unit) {
        val selected = current ?: return
        if (!enabled) return
        send(selected)
        owned = selected
    }

    fun release(pause: (T) -> Unit) {
        val selected = owned ?: return
        owned = null
        pause(selected)
    }
}

internal fun toggleAction(playing: Boolean, actions: Long): Long {
    val direct = if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
    return when {
        actions and direct != 0L -> direct
        else -> actions and PlaybackState.ACTION_PLAY_PAUSE
    }
}

class ExternalMusicController(
    private val context: Context, sessions: Flow<FocusSessionEntity?>, private val stopLocal: () -> Unit
) {
    private val prefs = context.getSharedPreferences("music_source", Context.MODE_PRIVATE)
    private var source = MusicSource.values().find { it.name == prefs.getString("source", null) } ?: MusicSource.LOCAL
    private val mutableState = MutableStateFlow(ExternalMusicState(source))
    val state = mutableState.asStateFlow()
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MusicSessionListener::class.java)
    private val target = MusicSessionTarget<MediaController>({ it.packageName }, { it.sessionToken })
    private var focusId: Long? = null
    private var observing = false
    private val changes = MediaSessionManager.OnActiveSessionsChangedListener { refresh() }
    private val callback = object : MediaController.Callback() {
        override fun onSessionDestroyed() = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
    }

    init {
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.data?.schemeSpecificPart == source.packageName) refresh()
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }, ContextCompat.RECEIVER_EXPORTED)
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            sessions.map { it?.takeIf { session -> session.status == FocusStatus.FOCUSING.code }?.id }.distinctUntilChanged()
                .catch { emit(null) }.collect { id ->
                    if (focusId != id) release()
                    focusId = id
                    publish()
                }
        }
        refresh()
    }

    fun select(value: MusicSource) {
        if (value == source) return
        release()
        stopLocal()
        source = value
        prefs.edit().putString("source", value.name).apply()
        refresh()
    }

    private fun hasAccess() = if (Build.VERSION.SDK_INT >= 27)
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
    else Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }

    fun refresh() {
        val pkg = source.packageName
        val installed = pkg != null && try {
            context.packageManager.getApplicationInfo(pkg, 0).enabled
        } catch (_: PackageManager.NameNotFoundException) { false }
        val granted = hasAccess()
        mutableState.value = ExternalMusicState(source, installed, granted)
        if (!installed || !granted) { disconnect(); return }
        try {
            if (!observing) {
                manager.addOnActiveSessionsChangedListener(changes, component)
                observing = true
            }
            bind(manager.getActiveSessions(component))
            publish()
        } catch (_: SecurityException) { disconnect() }
    }

    internal fun disconnect() {
        if (observing) {
            try { manager.removeOnActiveSessionsChangedListener(changes) } catch (_: SecurityException) { }
            observing = false
        }
        bind(emptyList())
        mutableState.value = ExternalMusicState(source, state.value.installed, hasAccess())
    }

    private fun bind(sessions: List<MediaController>) {
        target.bind(sessions, source) { previous, next ->
            previous?.unregisterCallback(callback)
            next?.registerCallback(callback)
        }
    }

    private fun publish() {
        val controller = target.current ?: return
        val playback = controller.playbackState
        val actions = playback?.actions ?: 0L
        val playing = playback?.state in setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING, PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING)
        fun supports(action: Long) = focusId != null && actions and action != 0L
        val metadata = controller.metadata
        mutableState.value = state.value.copy(connected = true, playing = playing,
            title = metadata?.description?.title?.toString(), artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            canToggle = focusId != null && toggleAction(playing, actions) != 0L,
            canPrevious = supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS), canNext = supports(PlaybackState.ACTION_SKIP_TO_NEXT))
    }

    fun toggle() = control({ it.canToggle }) { controller ->
        when (toggleAction(state.value.playing, controller.playbackState?.actions ?: 0L)) {
            PlaybackState.ACTION_PLAY -> controller.transportControls.play()
            PlaybackState.ACTION_PAUSE -> controller.transportControls.pause()
            PlaybackState.ACTION_PLAY_PAUSE -> toggleKey(controller)
            else -> Unit
        }
    }
    fun previous() = control({ it.canPrevious }) { it.transportControls.skipToPrevious() }
    fun next() = control({ it.canNext }) { it.transportControls.skipToNext() }

    private fun toggleKey(controller: MediaController) {
        controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    private fun control(enabled: (ExternalMusicState) -> Boolean, command: (MediaController) -> Unit) {
        refresh()
        try {
            target.command(enabled(state.value)) { controller ->
                stopLocal()
                command(controller)
            }
        } catch (_: SecurityException) { disconnect() }
    }

    internal fun release() = target.release pauseOwned@{ controller ->
        try {
            if (hasAccess()) {
                val playback = controller.playbackState ?: return@pauseOwned
                when (toggleAction(true, playback.actions)) {
                    PlaybackState.ACTION_PAUSE -> controller.transportControls.pause()
                    PlaybackState.ACTION_PLAY_PAUSE -> if (playback.state == PlaybackState.STATE_PLAYING) toggleKey(controller)
                    else -> Unit
                }
            }
        } catch (_: SecurityException) { disconnect() }
    }
}
