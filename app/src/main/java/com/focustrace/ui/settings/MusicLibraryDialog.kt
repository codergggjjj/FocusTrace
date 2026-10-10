package com.focustrace.ui.settings

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.data.repository.FocusAudioTrack

@Composable
internal fun MusicLibraryDialog(viewModel: MusicLibraryViewModel, onDismiss: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importAudio(uris)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("专注音乐") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("支持多选，单首最多 100 MB。按列表顺序循环，选中项作为起始曲目。", style = MaterialTheme.typography.bodySmall)
                if (state.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                (state.message ?: state.playback.error)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (state.library.tracks.isEmpty()) Text("还没有导入音乐", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(state.library.tracks, key = { it.id }) { track ->
                        AudioTrackRow(track, track.id == state.library.selectedId,
                            onSelect = { viewModel.selectAudio(track.id) },
                            onRemove = { removeId = track.id })
                    }
                }
            }
        }, confirmButton = {
            TextButton(onClick = { picker.launch(arrayOf("audio/*")) }, enabled = !state.importing,
                modifier = Modifier.testTag("focus-audio-import")) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("批量导入")
            }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
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
