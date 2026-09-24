package com.focustrace.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.focustrace.R
import com.focustrace.data.datastore.FocusBackgrounds
import com.focustrace.data.datastore.UserSettings

@Composable
fun FocusBackgroundEditor(
    settings: UserSettings,
    busy: Boolean,
    error: String?,
    onChange: (UserSettings) -> Unit,
    onChooseImage: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("专注背景") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BackgroundSwitch("显示背景图", settings.focusBackgroundEnabled, busy) {
                    onChange(settings.copy(focusBackgroundEnabled = it))
                }
                if (settings.focusBackgroundEnabled) {
                    BackgroundSwitch("每次随机显示", settings.focusBackgroundRandom, busy) {
                        onChange(settings.copy(focusBackgroundRandom = it))
                    }
                    Text(if (settings.focusBackgroundRandom) "随机图片" else "固定图片",
                        style = MaterialTheme.typography.titleSmall)
                    FocusBackgrounds.ids.forEach { id ->
                        val (name, resource) = when (id) {
                            FocusBackgrounds.FOREST -> "林间晨光" to R.drawable.focus_bg_forest
                            FocusBackgrounds.SEASIDE -> "海边阅读" to R.drawable.focus_bg_seaside
                            else -> "晨光湖畔" to R.drawable.focus_bg_lake
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(resource), contentDescription = null,
                                modifier = Modifier.size(52.dp), contentScale = ContentScale.Crop)
                            Text(name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                            if (settings.focusBackgroundRandom) {
                                Checkbox(checked = id in settings.focusBackgroundIds, enabled = !busy,
                                    onCheckedChange = { checked ->
                                        val next = if (checked) settings.focusBackgroundIds + id else settings.focusBackgroundIds - id
                                        if (next.isNotEmpty()) onChange(settings.copy(focusBackgroundIds = next))
                                    })
                            } else RadioButton(selected = settings.focusBackgroundSelectedId == id,
                                enabled = !busy, onClick = { onChange(settings.copy(focusBackgroundSelectedId = id)) })
                        }
                    }
                    HorizontalDivider()
                    Text("自定义图片", style = MaterialTheme.typography.titleSmall)
                    Text(if (settings.focusBackgroundCustomPath == null) "选择后会优先显示在专注页" else "已启用自定义图片",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onChooseImage, enabled = !busy) { Text("从相册选择") }
                        if (settings.focusBackgroundCustomPath != null) TextButton(
                            onClick = { onChange(settings.copy(focusBackgroundCustomPath = null)) }, enabled = !busy
                        ) { Text("移除") }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun BackgroundSwitch(label: String, checked: Boolean, busy: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange, enabled = !busy)
    }
}
