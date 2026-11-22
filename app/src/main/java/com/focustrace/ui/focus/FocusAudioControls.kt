package com.focustrace.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.data.repository.FocusAudioLibrary
import com.focustrace.focus.FocusAudioPlayback
import com.focustrace.focus.MusicSource

data class FocusAudioUiState(val library: FocusAudioLibrary = FocusAudioLibrary(),
    val playback: FocusAudioPlayback = FocusAudioPlayback(), val importing: Boolean = false,
    val message: String? = null)

@Composable
internal fun FocusAudioControls(viewModel: FocusViewModel, immersive: Boolean, focusing: Boolean) {
    val state by viewModel.audioState.collectAsStateWithLifecycle()
    val external by viewModel.externalAudioState.collectAsStateWithLifecycle()
    val useLocal = external.source == MusicSource.LOCAL
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshExternalAudio() }
    val title = if (useLocal) state.playback.track?.title ?: "专注音乐"
        else external.title?.takeIf { it.isNotBlank() } ?: external.source.label
    val subtitle = if (useLocal) when {
        state.playback.switching || state.playback.preparing -> "正在加载…"
        state.library.tracks.isEmpty() -> "在设置中批量导入音乐"
        state.playback.playing -> "列表循环 · 共 ${state.library.tracks.size} 首"
        else -> "已暂停 · 共 ${state.library.tracks.size} 首"
    } else when {
        !external.installed -> "尚未安装${external.source.label}"
        !external.granted -> "在专注音乐设置中开启通知访问"
        !external.connected -> "请先在${external.source.label}中播放歌曲"
        else -> listOfNotNull(external.artist?.takeIf { it.isNotBlank() }, external.source.label).joinToString(" · ")
    }
    val playing = if (useLocal) state.playback.foregroundActive else external.playing
    val localCanPlay = state.playback.canPlay && !state.playback.switching
    val localCanSwitch = localCanPlay && state.library.tracks.size > 1
    val canPrevious = focusing && if (useLocal) localCanSwitch else external.canPrevious
    val canNext = focusing && if (useLocal) localCanSwitch else external.canNext
    val canToggle = focusing && if (useLocal) localCanPlay && state.playback.track != null else external.canToggle
    val foreground = if (immersive) Color.White else MaterialTheme.colorScheme.onSurface
    val secondary = if (immersive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = MaterialTheme.shapes.large,
        color = if (immersive) Color.Black.copy(alpha = 0.42f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = foreground)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = foreground,
                        style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = secondary, style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previousAudio, enabled = canPrevious,
                    modifier = Modifier.size(48.dp).testTag("focus-audio-previous")) {
                    Icon(Icons.Outlined.SkipPrevious, contentDescription = "上一首",
                        tint = if (canPrevious) foreground else secondary.copy(alpha = 0.38f))
                }
                FilledTonalIconButton(onClick = viewModel::toggleAudio,
                    enabled = canToggle,
                    modifier = Modifier.size(48.dp).testTag("focus-audio-toggle")) {
                    Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (playing) "暂停音乐" else "播放音乐")
                }
                IconButton(onClick = viewModel::nextAudio, enabled = canNext,
                    modifier = Modifier.size(48.dp).testTag("focus-audio-next")) {
                    Icon(Icons.Outlined.SkipNext, contentDescription = "下一首",
                        tint = if (canNext) foreground else secondary.copy(alpha = 0.38f))
                }
            }
            if (useLocal) (state.message ?: state.playback.error)?.let { Text(it, color = secondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
