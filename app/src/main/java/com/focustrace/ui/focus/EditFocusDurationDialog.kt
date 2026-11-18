package com.focustrace.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.focustrace.statistics.MIN_VALID_FOCUS_SECONDS

@Composable
internal fun EditFocusDurationDialog(initialSeconds: Long, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var minutes by rememberSaveable { mutableStateOf((initialSeconds / 60).toString()) }
    var seconds by rememberSaveable { mutableStateOf((initialSeconds % 60).toString()) }
    val parsedMinutes = minutes.toIntOrNull()?.takeIf { it >= 0 }
    val parsedSeconds = seconds.toIntOrNull()?.takeIf { it in 0..59 }
    val total = if (parsedMinutes != null && parsedSeconds != null) parsedMinutes * 60L + parsedSeconds else null
    val inputError = when {
        total == null -> "请输入有效的分钟数，秒数须为 0–59。"
        total <= MIN_VALID_FOCUS_SECONDS -> "专注时长必须超过 5 分钟。"
        else -> null
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("修改专注时长") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(minutes, { minutes = it.take(10) }, label = { Text("分钟") },
                        modifier = Modifier.weight(1f).testTag("edit-focus-minutes"), enabled = !busy,
                        singleLine = true, isError = parsedMinutes == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
                    OutlinedTextField(seconds, { seconds = it.take(2) }, label = { Text("秒") },
                        modifier = Modifier.weight(1f).testTag("edit-focus-seconds"), enabled = !busy,
                        singleLine = true, isError = parsedSeconds == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
                }
                val message = inputError ?: error
                Text(message ?: "统计将使用修改后的专注时长。", style = MaterialTheme.typography.bodySmall,
                    color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { total?.let(onSave) }, enabled = !busy && inputError == null && total != initialSeconds) {
                Text(if (busy) "保存中…" else "保存时长")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } })
}
