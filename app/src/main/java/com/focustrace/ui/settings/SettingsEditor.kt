package com.focustrace.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.focustrace.data.datastore.*

@Composable
fun SettingsEditor(original: UserSettings, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (UserSettings) -> Unit) {
    var focus by rememberSaveable { mutableStateOf(original.pomodoroMinutes.toString()) }
    var rest by rememberSaveable { mutableStateOf(original.breakMinutes.toString()) }
    var threshold by rememberSaveable { mutableStateOf(original.distractionThreshold.toString()) }
    var autoBreak by rememberSaveable { mutableStateOf(original.autoStartBreak) }
    var autoFocus by rememberSaveable { mutableStateOf(original.autoStartFocus) }
    var theme by rememberSaveable { mutableStateOf(original.darkMode.name) }
    val valid = focus.toIntOrNull() in 1..1440 && rest.toIntOrNull() in 1..1440 && threshold.toIntOrNull() in 0..86400
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("编辑设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("settings-editor"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("默认番茄时长用于新建待办；时长与自动轮转设置从下次开始计时生效。", style = MaterialTheme.typography.bodySmall)
                NumberSetting("默认番茄分钟", focus, 1..1440, busy) { focus = it }
                NumberSetting("默认休息分钟", rest, 1..1440, busy) { rest = it }
                NumberSetting("分心阈值秒数", threshold, 0..86400, busy) { threshold = it }
                ToggleSetting("自动开始休息", autoBreak, busy) { autoBreak = it }
                ToggleSetting("自动开始下一轮", autoFocus, busy) { autoFocus = it }
                Text("外观", style = MaterialTheme.typography.titleMedium)
                ThemeMode.entries.forEach { mode ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = theme == mode.name, onClick = { theme = mode.name }, enabled = !busy,
                            modifier = Modifier.testTag("theme-${mode.name}"))
                        Text(when (mode) { ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色" })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(enabled = valid && !busy, onClick = {
            onSave(original.copy(pomodoroMinutes = focus.toInt(), breakMinutes = rest.toInt(),
                distractionThreshold = threshold.toInt(), autoStartBreak = autoBreak, autoStartFocus = autoFocus, darkMode = ThemeMode.valueOf(theme)))
        }) { Text(if (busy) "保存中…" else "保存设置") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun NumberSetting(label: String, value: String, range: IntRange, busy: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { onChange(it.take(5)) }, label = { Text(label) },
        singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = value.toIntOrNull() !in range, supportingText = { Text("${range.first}–${range.last}") })
}
@Composable
private fun ToggleSetting(label: String, value: Boolean, busy: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange, enabled = !busy, modifier = Modifier.testTag(label))
    }
}
