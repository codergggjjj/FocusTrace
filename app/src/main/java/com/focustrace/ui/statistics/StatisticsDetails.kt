package com.focustrace.ui.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focustrace.focus.formatDuration
import com.focustrace.statistics.*
import com.focustrace.ui.components.*
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
internal fun StudyRecordsDialog(state: LoadState<StatisticsSummary>, onDismiss: () -> Unit, onRetry: () -> Unit, onReport: (Long) -> Unit) {
    DetailWindow("专注记录", "关闭记录", onDismiss) {
        when (state) {
            LoadState.Loading -> CircularProgressIndicator()
            is LoadState.Error -> { Text(state.message); TextButton(onClick = onRetry) { Text("重试") } }
            is LoadState.Ready -> {
                val summary = state.value
                val formatter = remember(summary.range.zone) { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(summary.range.zone) }
                Text("${summary.range.start} · ${summary.sessions.size} 次", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f).testTag("statistics-records"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (summary.sessions.isEmpty()) item { Text("暂无专注记录") }
                    items(summary.sessions, key = { it.id }, contentType = { "session" }) { session ->
                        TraceCard(Modifier.testTag("study-session-${session.id}").clickable { onReport(session.id) }) {
                            Text(session.taskTitleSnapshot ?: "自由专注 / 原待办不可用", style = MaterialTheme.typography.titleMedium)
                            Text(compactDuration(session.focusSeconds), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleLarge)
                            Text("开始：${formatter.format(Instant.ofEpochMilli(session.startTime))}", style = MaterialTheme.typography.bodySmall)
                            Text("结束：${formatter.format(Instant.ofEpochMilli(session.endTime!!))}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FocusDetailsDialog(totals: StatisticsTotals, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("分心详情") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailMetric("分心次数", "${totals.distractions} 次")
            DetailMetric("分心时间", formatDuration(totals.distractionSeconds))
            DetailMetric("平均分心时长", totals.averageDistractionSeconds?.let(::formatDuration) ?: "—")
            DetailMetric("平均首次分心", totals.averageFirstDistractionSeconds?.let(::formatDuration) ?: "—")
            DetailMetric("专注率", totals.focusPercent?.let { "$it%" } ?: "—")
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
private fun DetailWindow(title: String, closeLabel: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight(.9f).padding(16.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text(closeLabel) }
                }
                content()
            }
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
