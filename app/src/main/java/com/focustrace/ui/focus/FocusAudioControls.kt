package com.focustrace.ui.focus

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.data.repository.FocusAudioLibrary
import com.focustrace.data.repository.FocusAudioTrack
import com.focustrace.focus.FocusAudioPlayback

data class FocusAudioUiState(val library: FocusAudioLibrary = FocusAudioLibrary(),
    val playback: FocusAudioPlayback = FocusAudioPlayback(), val importing: Boolean = false,
    val message: String? = null)

@Composable
internal fun FocusAudioControls(viewModel: FocusViewModel, immersive: Boolean, focusing: Boolean) {
    val state by viewModel.audioState.collectAsStateWithLifecycle()
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::importAudio)
    }
    val foreground = if (immersive) Color.White else MaterialTheme.colorScheme.onSurface
    val secondary = if (immersive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = MaterialTheme.shapes.large,
        color = if (immersive) Color.Black.copy(alpha = 0.42f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = foreground)
                Spacer(Modifier.width(8.dp))
                Text("专注音乐", style = MaterialTheme.typography.titleSmall, color = foreground,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { showLibrary = true }, modifier = Modifier.testTag("focus-audio-library")) {
                    Text("选择 / 导入", color = if (immersive) Color.White else MaterialTheme.colorScheme.primary)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.library.selected?.title ?: "还没有选择音乐", color = foreground,
                        style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(when {
                        state.importing -> "正在导入…"
                        state.playback.preparing -> "正在加载…"
                        state.playback.playing -> "循环播放中"
                        state.library.selected != null -> "点击播放 · 单曲循环"
                        else -> "导入手机中的音频"
                    }, color = secondary, style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalIconButton(onClick = viewModel::toggleAudio,
                    enabled = focusing && state.playback.canPlay && state.library.selected != null && !state.importing,
                    modifier = Modifier.size(48.dp).testTag("focus-audio-toggle")) {
                    Icon(if (state.playback.foregroundActive) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (state.playback.foregroundActive) "暂停音乐" else "播放音乐")
                }
            }
            (state.message ?: state.playback.error)?.let { Text(it, color = secondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (showLibrary) AlertDialog(onDismissRequest = { showLibrary = false }, title = { Text("专注音乐") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("选择手机中的音频，单个文件最多 100 MB。", style = MaterialTheme.typography.bodySmall)
                if (state.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                (state.message ?: state.playback.error)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (state.library.tracks.isEmpty()) Text("还没有导入音乐", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(state.library.tracks, key = { it.id }) { track ->
                        AudioTrackRow(track, track.id == state.library.selectedId,
                            onSelect = { viewModel.selectAudio(track.id); showLibrary = false },
                            onRemove = { removeId = track.id })
                    }
                }
            }
        }, confirmButton = {
            TextButton(onClick = { picker.launch(arrayOf("audio/*")) }, enabled = !state.importing,
                modifier = Modifier.testTag("focus-audio-import")) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("导入音频")
            }
        }, dismissButton = { TextButton(onClick = { showLibrary = false }) { Text("关闭") } })
    val removing = state.library.tracks.firstOrNull { it.id == removeId }
    if (removing != null) AlertDialog(onDismissRequest = { removeId = null }, title = { Text("移除音频？") },
        text = { Text(removing.title, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        confirmButton = { TextButton(onClick = { viewModel.removeAudio(removing.id); removeId = null }) { Text("移除") } },
        dismissButton = { TextButton(onClick = { removeId = null }) { Text("取消") } })
}

@Composable
private fun AudioTrackRow(track: FocusAudioTrack, selected: Boolean, onSelect: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        TextButton(onClick = onSelect, modifier = Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Text("${track.durationSeconds / 60} 分 ${track.durationSeconds % 60} 秒",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "移除 ${track.title}") }
    }
}
