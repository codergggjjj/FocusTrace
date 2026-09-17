package com.focustrace.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.data.datastore.ThemeMode
import com.focustrace.ui.components.*

@Composable
fun ProfileScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val loaded = (state as? LoadState.Ready)?.value
    if (editing && loaded != null) SettingsEditor(loaded, busy, error,
        onDismiss = { editing = false }, onSave = { value -> viewModel.save(value) { editing = false } })
    BasePage("我的", "找到适合自己的专注节奏") {
        StateContent(state) { settings ->
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("专注设置", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { editing = true }, enabled = !busy) { Text("编辑设置") }
                }
                SettingRow(Icons.Outlined.Timer, "番茄时长", "${settings.pomodoroMinutes} 分钟", !busy) { editing = true }
                SettingsDivider()
                SettingRow(Icons.Outlined.Coffee, "休息时长", "${settings.breakMinutes} 分钟", !busy) { editing = true }
                SettingsDivider()
                SettingRow(Icons.Outlined.NotificationsNone, "分心判定", "${settings.distractionThreshold} 秒", !busy) { editing = true }
            }
            Column {
                SettingsSection("自动化")
                SettingSwitch(Icons.Outlined.PlayArrow, "自动开始休息", settings.autoStartBreak, !busy) {
                    viewModel.save(settings.copy(autoStartBreak = it)) {}
                }
                SettingsDivider()
                SettingSwitch(Icons.Outlined.Replay, "自动开始下一轮", settings.autoStartFocus, !busy) {
                    viewModel.save(settings.copy(autoStartFocus = it)) {}
                }
            }
            Column {
                SettingsSection("外观")
                SettingRow(Icons.Outlined.Palette, "主题", when (settings.darkMode) {
                    ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色"
                }, !busy) { editing = true }
            }
            if (!editing) error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Text("FocusTrace · 专迹", style = MaterialTheme.typography.titleMedium)
            Text("记录每一次专注，也看见每一次分心。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("数据保存在此设备，无需登录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(title, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(start = 48.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    ListItem(modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(value, style = MaterialTheme.typography.bodySmall) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) })
}

@Composable
private fun SettingSwitch(icon: ImageVector, title: String, value: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = {
            Switch(checked = value, onCheckedChange = onChange, enabled = enabled,
                modifier = Modifier.testTag("profile-$title").semantics { contentDescription = title })
        })
}
