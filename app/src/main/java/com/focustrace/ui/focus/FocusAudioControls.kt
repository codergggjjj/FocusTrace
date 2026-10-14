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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.data.repository.FocusAudioLibrary
import com.focustrace.focus.FocusAudioPlayback

data class FocusAudioUiState(val library: FocusAudioLibrary = FocusAudioLibrary(),
    val playback: FocusAudioPlayback = FocusAudioPlayback(), val importing: Boolean = false,
    val message: String? = null)

@Composable
internal fun FocusAudioControls(viewModel: FocusViewModel, immersive: Boolean, focusing: Boolean) {
    val state by viewModel.audioState.collectAsStateWithLifecycle()
    val foreground = if (immersive) Color.White else MaterialTheme.colorScheme.onSurface
    val secondary = if (immersive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = MaterialTheme.shapes.large,
        color = if (immersive) Color.Black.copy(alpha = 0.42f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = foreground)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.playback.track?.title ?: "专注音乐", color = foreground,
                        style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(when {
                        state.playback.switching || state.playback.preparing -> "正在加载…"
                        state.library.tracks.isEmpty() -> "在设置中批量导入音乐"
                        state.playback.playing -> "列表循环 · 共 ${state.library.tracks.size} 首"
                        else -> "已暂停 · 共 ${state.library.tracks.size} 首"
                    }, color = secondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                val canSwitch = focusing && state.playback.canPlay &&
                    state.library.tracks.size > 1 && !state.playback.switching
                IconButton(onClick = viewModel::previousAudio, enabled = canSwitch,
                    modifier = Modifier.size(48.dp).testTag("focus-audio-previous")) {
                    Icon(Icons.Outlined.SkipPrevious, contentDescription = "上一首",
                        tint = if (canSwitch) foreground else secondary.copy(alpha = 0.38f))
                }
                FilledTonalIconButton(onClick = viewModel::toggleAudio,
                    enabled = focusing && state.playback.canPlay && state.playback.track != null && !state.playback.switching,
                    modifier = Modifier.size(48.dp).testTag("focus-audio-toggle")) {
                    Icon(if (state.playback.foregroundActive) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (state.playback.foregroundActive) "暂停音乐" else "播放音乐")
                }
                IconButton(onClick = viewModel::nextAudio, enabled = canSwitch,
                    modifier = Modifier.size(48.dp).testTag("focus-audio-next")) {
                    Icon(Icons.Outlined.SkipNext, contentDescription = "下一首",
                        tint = if (canSwitch) foreground else secondary.copy(alpha = 0.38f))
                }
            }
            (state.message ?: state.playback.error)?.let { Text(it, color = secondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
