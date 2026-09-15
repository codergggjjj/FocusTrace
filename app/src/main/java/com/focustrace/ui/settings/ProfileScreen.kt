package com.focustrace.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
            TraceCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("专注设置", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { editing = true }) { Text("编辑设置") }
                }
                SettingRow(Icons.Outlined.Timer, "番茄时长", "${settings.pomodoroMinutes} 分钟")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.Coffee, "休息时长", "${settings.breakMinutes} 分钟")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(Icons.Outlined.NotificationsNone, "分心判定", "${settings.distractionThreshold} 秒")
            }
            TraceCard {
                SettingRow(Icons.Outlined.PlayArrow, "自动开始休息", if (settings.autoStartBreak) "开启" else "关闭")
                SettingRow(Icons.Outlined.Replay, "自动开始下一轮", if (settings.autoStartFocus) "开启" else "关闭")
            }
            TraceCard {
                SettingRow(Icons.Outlined.Palette, "外观", when (settings.darkMode) {
                    ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色"
                })
            }
        }
        TraceCard {
            Icon(Icons.Outlined.Spa, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text("FocusTrace · 专迹", style = MaterialTheme.typography.titleLarge)
            Text("记录每一次专注，也看见每一次分心。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("数据保存在此设备，无需登录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
