package com.focustrace.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.focustrace.data.repository.BackupPreview
import java.text.DateFormat
import java.util.Date

@Composable
fun BackupDialog(
    preview: BackupPreview?,
    busy: Boolean,
    message: String?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRestore: () -> Unit,
    onCancelImport: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) { onCancelImport(); onDismiss() } },
        title = { Text(if (preview == null) "备份与恢复" else "确认恢复备份") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (preview == null) {
                    Text("把待办、专注记录、设置和自定义背景保存到你选择的文件。")
                    Button(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("backup-export")) {
                        Text("导出备份")
                    }
                    OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("backup-import")) {
                        Text("从文件恢复")
                    }
                    Text("恢复会替换本机现有数据。请先导出一份当前备份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("备份时间：${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(preview.createdAt))}")
                    Text("${preview.taskCount} 个待办 · ${preview.sessionCount} 条专注记录 · ${preview.distractionCount} 条分心记录")
                    Text(if (preview.hasCustomBackground) "包含自定义背景图" else "无自定义背景图")
                    Text("确认后将替换本机全部待办、专注记录和设置。",
                        color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("backup-restore")) {
                        Text("确认替换并恢复")
                    }
                }
                if (busy) Text("正在处理…", style = MaterialTheme.typography.bodySmall)
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = { TextButton(onClick = { onCancelImport(); onDismiss() }, enabled = !busy) { Text("关闭") } }
    )
}
