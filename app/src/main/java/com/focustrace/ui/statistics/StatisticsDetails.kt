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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun StudyRecordsDialog(state: LoadState<StatisticsSummary>, onDismiss: () -> Unit, onRetry: () -> Unit,
    onAdd: () -> Unit, onEdit: (Long) -> Unit, busy: Boolean, onDelete: (Long) -> Unit, onReport: (Long) -> Unit) {
    DetailWindow("专注记录", "关闭记录", onDismiss) {
        FilledTonalButton(onClick = onAdd) { Text("手动添加记录") }
        when (state) {
            LoadState.Loading -> CircularProgressIndicator()
            is LoadState.Error -> { Text(state.message); TextButton(onClick = onRetry) { Text("重试") } }
            is LoadState.Ready -> {
                val summary = state.value
                val formatter = remember(summary.range.zone) { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(summary.range.zone) }
                Text("${summary.range.start} · ${summary.sessions.size} 条记录", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f).testTag("statistics-records"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (summary.sessions.isEmpty()) item { Text("暂无专注记录") }
                    items(summary.sessions, key = { it.id }, contentType = { "session" }) { session ->
                        TraceCard(Modifier.testTag("study-session-${session.id}").clickable {
                            if (session.source == "MANUAL") onEdit(session.id) else onReport(session.id)
                        }) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (session.source == "MANUAL") "手动添加" else "计时记录", Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (session.source == "MANUAL") MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                                DeleteFocusRecordButton(session, !busy) { onDelete(session.id) }
                            }
                            Text(session.taskTitleSnapshot ?: "自由专注 / 原待办不可用", style = MaterialTheme.typography.titleMedium)
                            Text(compactDuration(session.focusSeconds), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                            Text("开始：${formatter.format(Instant.ofEpochMilli(session.startTime))}", style = MaterialTheme.typography.bodySmall)
                            Text("结束：${formatter.format(Instant.ofEpochMilli(session.endTime!!))}", style = MaterialTheme.typography.bodySmall)
                            session.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
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
            DetailMetric("专注率", totals.focusPercent?.let { "$it%" } ?: "—")
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
internal fun RecentRecordsSection(summary: StatisticsSummary, onAdd: () -> Unit, onAll: () -> Unit,
    onEdit: (Long) -> Unit, onReport: (Long) -> Unit) {
    val time = remember(summary.range.zone) { DateTimeFormatter.ofPattern("HH:mm").withZone(summary.range.zone) }
    val records = summary.sessions.take(4)
    StatisticsSection("专注记录", "${summary.sessions.size} 条") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("手动补录") }
            TextButton(onClick = onAll, modifier = Modifier.weight(1f)) { Text("查看全部") }
        }
        if (records.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                Text("这个周期还没有专注记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            var previousDate: LocalDate? = null
            records.forEach { session ->
                val date = Instant.ofEpochMilli(session.startTime).atZone(summary.range.zone).toLocalDate()
                if (date != previousDate) {
                    Text(recordDayLabel(date), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    previousDate = date
                }
                Row(Modifier.fillMaxWidth().clickable {
                    if (session.source == "MANUAL") onEdit(session.id) else onReport(session.id)
                }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.width(92.dp)) {
                        Text("${time.format(Instant.ofEpochMilli(session.startTime))} - ${time.format(Instant.ofEpochMilli(session.endTime!!))}",
                            style = MaterialTheme.typography.bodySmall)
                        Text(if (session.source == "MANUAL") "手动" else "计时",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (session.source == "MANUAL") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(session.taskTitleSnapshot ?: "自由专注", style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        session.note?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    Text(compactDuration(session.focusSeconds), style = MaterialTheme.typography.labelLarge)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))
            }
        }
    }
}

private fun recordDayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.toString()
    }
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
