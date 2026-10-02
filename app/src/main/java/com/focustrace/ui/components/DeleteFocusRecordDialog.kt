package com.focustrace.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.focus.formatDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DeleteFocusRecordButton(record: FocusSessionEntity, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled && record.status == 4 && record.endTime != null,
        modifier = Modifier.testTag("delete-session-${record.id}")) {
        Icon(Icons.Outlined.Delete,
            contentDescription = if (record.status == 3) "正在休息，结束后可删除" else "删除专注记录",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled && record.status == 4) 1f else .38f))
    }
}

@Composable
fun DeleteFocusRecordDialog(record: FocusSessionEntity, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val time = remember(zone) { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("删除这条专注记录？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(record.taskTitleSnapshot ?: "自由专注", style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(time.format(Instant.ofEpochMilli(record.startTime)), style = MaterialTheme.typography.bodyMedium)
                Text(formatDuration(record.focusSeconds), style = MaterialTheme.typography.bodyMedium)
                Text("删除后将更新统计，无法撤销。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(if (busy) "删除中…" else "确认删除记录")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("保留记录") } })
}
