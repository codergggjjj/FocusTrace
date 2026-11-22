package com.focustrace.ui.settings

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.net.Uri
import java.io.File
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class DeviceSong(val uri: Uri, val name: String, val folder: String, val artist: String, val seconds: Long = 0)

internal fun selectVisibleSongs(selected: Set<Uri>, ids: Set<Uri>): Set<Uri> {
    return if (selected.containsAll(ids)) selected - ids else selected + ids
}

internal fun selectableSongs(visible: List<DeviceSong>, imported: Set<Uri>) =
    visible.mapTo(mutableSetOf()) { it.uri } - imported

private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "oga", "opus", "amr", "mid", "midi", "mka", "wma", "aiff", "aif", "alac", "ape")

@Suppress("DEPRECATION")
private suspend fun scanDevice(context: Context): List<DeviceSong> = withContext(Dispatchers.IO) {
    val media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val folder = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
    val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, folder, MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.MIME_TYPE)
    val cursor = requireNotNull(context.contentResolver.query(media, columns, null, null, MediaStore.Audio.Media.DATE_ADDED + " DESC, " + MediaStore.Audio.Media._ID + " DESC"))
    cursor.use {
        buildList {
            while (it.moveToNext()) {
                val name = it.getString(1).orEmpty()
                if (!it.getString(5).orEmpty().startsWith("audio/", ignoreCase = true) &&
                    name.substringAfterLast('.', "").lowercase() !in audioExtensions) continue
                val path = it.getString(2).orEmpty()
                val directory = if (Build.VERSION.SDK_INT >= 29) path.trimEnd('/') else File(path).parent.orEmpty()
                add(DeviceSong(ContentUris.withAppendedId(media, it.getLong(0)), name.ifBlank { "未命名音频" },
                    directory.ifEmpty { "根目录" }, it.getString(3).orEmpty(), it.getLong(4).coerceAtLeast(0) / 1000))
            }
        }
    }
}

@Composable
internal fun DeviceMusicDialog(viewModel: MusicLibraryViewModel, onFiles: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var allowed by remember { mutableStateOf(context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    var query by rememberSaveable { mutableStateOf("") }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var foldersOpen by remember { mutableStateOf(false) }
    var songs by remember { mutableStateOf<List<DeviceSong>?>(null) }
    var failure by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val imported = remember(state.library.tracks) { state.library.tracks.mapNotNull { it.sourceUri?.let(Uri::parse) }.toSet() }
    val selected = remember(viewModel.scanSelection, imported) { viewModel.scanSelection - imported }
    LaunchedEffect(allowed, refresh) {
        failure = false
        songs = null
        if (allowed) try {
            songs = scanDevice(context)
            viewModel.scanSelection = viewModel.scanSelection.intersect(songs.orEmpty().map { it.uri }.toSet())
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { failure = true }
    }
    val visible = remember(songs, query, folder) { songs.orEmpty().filter {
        (folder == null || folder == it.folder) && (it.name.contains(query, true) || it.artist.contains(query, true))
    } }
    val available = remember(visible, imported) { selectableSongs(visible, imported) }
    AlertDialog(onDismissRequest = onClose, title = { Text("扫描本机音乐") }, text = {
        LazyColumn {
            if (allowed) item { TextButton(onClick = { refresh++ }) { Text("重新扫描") } }
            if (!allowed) item {
                Text("允许读取音频后，可按歌曲名称、歌手或文件夹选择音乐。")
                TextButton(onClick = { request.launch(permission) }) { Text("允许读取音频") }
            } else if (failure) item { Text("无法读取本机曲库，可从文件导入。") }
            else if (songs == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else {
                item {
                    OutlinedTextField(query, { query = it }, label = { Text("搜索歌曲或歌手") }, singleLine = true)
                    Box {
                        TextButton(onClick = { foldersOpen = true }) { Text(folder ?: "全部文件夹", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        DropdownMenu(foldersOpen, { foldersOpen = false }) {
                            DropdownMenuItem({ Text("全部文件夹") }, { folder = null; foldersOpen = false })
                            remember(songs) { songs.orEmpty().map { it.folder }.distinct().sorted() }.forEach { name ->
                                DropdownMenuItem({ Text(name) }, { folder = name; foldersOpen = false })
                            }
                        }
                    }
                    val all = available.isNotEmpty() && selected.containsAll(available)
                    TextButton(enabled = available.isNotEmpty(), onClick = { viewModel.scanSelection = selectVisibleSongs(selected, available) }) {
                        Text(if (all) "取消全选当前结果" else "全选当前结果")
                    }
                    if (visible.isEmpty()) Text(if (songs.orEmpty().isEmpty()) "没有找到本机音频" else "没有匹配的歌曲")
                }
                items(visible, key = { it.uri.toString() }) { song ->
                    val alreadyImported = song.uri in imported
                    Row(Modifier.fillMaxWidth().clickable(enabled = !alreadyImported) {
                        viewModel.scanSelection = if (song.uri in selected) selected - song.uri else selected + song.uri
                    }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(song.uri in selected, onCheckedChange = null, enabled = !alreadyImported)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(song.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val artist = song.artist.takeUnless { it.isBlank() || it == "<unknown>" } ?: "未知歌手"
                            val duration = if (song.seconds > 0) "%d:%02d".format(song.seconds / 60, song.seconds % 60) else "时长未知"
                            Text(listOfNotNull(artist, duration, "已导入".takeIf { alreadyImported }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.folder, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item { TextButton(onClick = onFiles) { Text("从系统文件选择器导入") } }
        }
    }, confirmButton = {
        TextButton(enabled = allowed && songs != null && selected.isNotEmpty(), onClick = {
            viewModel.importAudio(songs.orEmpty().filter { it.uri in selected }.map { it.uri })
            onClose()
        }) { Text("导入已选（" + selected.size + "）") }
    }, dismissButton = { TextButton(onClick = onClose) { Text("返回") } })
}
