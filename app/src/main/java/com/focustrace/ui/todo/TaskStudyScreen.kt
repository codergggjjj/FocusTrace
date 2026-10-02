package com.focustrace.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.statistics.StatisticsPeriod
import com.focustrace.ui.components.LoadState
import com.focustrace.ui.components.TraceCard
import com.focustrace.ui.components.DeleteFocusRecordButton
import com.focustrace.ui.components.DeleteFocusRecordDialog
import com.focustrace.ui.statistics.ManualRecordDialog
import com.focustrace.ui.statistics.StudyTrend
import com.focustrace.ui.statistics.compactDuration
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun TaskStudyScreen(viewModel: TaskStudyViewModel, onBack: () -> Unit, onReport: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val days by viewModel.days.collectAsStateWithLifecycle()
    val busy by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val data = (state as? LoadState.Ready)?.value
    val record = data?.summary?.sessions?.firstOrNull { it.id == editingId }
    val openEditor: (Long?) -> Unit = { editingId = it; viewModel.clearError(); editorOpen = true }
    data?.summary?.sessions?.firstOrNull { it.id == deletingId }?.let { session ->
        DeleteFocusRecordDialog(session, busy, error, onDismiss = { deletingId = null },
            onConfirm = { viewModel.deleteRecord(session.id) { deletingId = null } })
    }
    if (editorOpen && data != null && (editingId == null || record != null)) {
        ManualRecordDialog(record, LoadState.Ready(data.tasks), busy, error,
            defaultTaskId = viewModel.taskId,
            onDismiss = { editorOpen = false },
            onSave = { date, start, end, task, note ->
                viewModel.saveManual(editingId, date, start, end, task, note) { editorOpen = false }
            }, onDelete = { editingId?.let { viewModel.deleteManual(it) { editorOpen = false } } })
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回待办编辑") }
            Text("学习详情", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.fillMaxSize().testTag("task-study-list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val value = state) {
                LoadState.Loading -> item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                is LoadState.Error -> item("error") {
                    Text(value.message)
                    TextButton(onClick = viewModel::retry) { Text("重试") }
                }
                is LoadState.Ready -> {
                    val current = value.value
                    val summary = current.summary
                    val task = current.task
                    if (task == null) {
                        item("missing") { Text("待办已删除，历史专注仍可在统计页查看。") }
                    } else {
                        item("overview", contentType = "overview") {
                            Text(task.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (task.completed) "已完成" else "待完成", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("累计学习", style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text(compactDuration(summary.focusSeconds), Modifier.testTag("task-study-total"),
                                        style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        StudyMetric("专注次数", "${summary.sessions.size} 次", Modifier.weight(1f), "task-study-count")
                                        StudyMetric("学习天数", "${summary.recordsByDay.size} 天", Modifier.weight(1f))
                                        StudyMetric("平均每次", compactDuration(if (summary.sessions.isEmpty()) 0 else summary.focusSeconds / summary.sessions.size), Modifier.weight(1.3f))
                                    }
                                }
                            }
                        }
                        item("trend", contentType = "trend") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(7, 30).forEach { count ->
                                    FilterChip(selected = days == count, onClick = { viewModel.chooseDays(count) }, label = { Text("近 $count 天") },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer), border = null)
                                }
                            }
                            key(days, summary.trend.range.start) {
                                StudyTrend(summary.trend, if (days == 7) StatisticsPeriod.WEEK else StatisticsPeriod.MONTH)
                            }
                        }
                        item("records-heading", contentType = "heading") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("学习记录 · ${summary.sessions.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { openEditor(null) }) {
                                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                                    Text("补录")
                                }
                            }
                        }
                        if (summary.sessions.isEmpty()) item("empty") {
                            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("还没有学习记录", style = MaterialTheme.typography.titleMedium)
                                Text("开始一次专注，或补录之前的学习", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        summary.recordsByDay.forEach { (date, records) ->
                            item("day-$date", contentType = "date") {
                                Text(date.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            items(records, key = { "record-${it.id}" }, contentType = { "record" }) { session ->
                                val formatter = remember(summary.trend.range.zone) {
                                    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(summary.trend.range.zone)
                                }
                                TaskStudyRecord(session, formatter, !busy,
                                    onDelete = { deletingId = session.id; viewModel.clearError() },
                                    onClick = { if (session.source == "MANUAL") openEditor(session.id) else onReport(session.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyMetric(label: String, value: String, modifier: Modifier, tag: String = "") {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, Modifier.testTag(tag), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f))
    }
}

@Composable
private fun TaskStudyRecord(session: FocusSessionEntity, formatter: DateTimeFormatter, enabled: Boolean,
    onDelete: () -> Unit, onClick: () -> Unit) {
    TraceCard(Modifier.testTag("task-study-record-${session.id}").clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (session.source == "MANUAL") "手动添加" else "计时记录", Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(compactDuration(session.focusSeconds), style = MaterialTheme.typography.titleMedium)
            DeleteFocusRecordButton(session, enabled, onDelete)
        }
        Text("${formatter.format(Instant.ofEpochMilli(session.startTime))} — ${formatter.format(Instant.ofEpochMilli(session.endTime!!))}",
            style = MaterialTheme.typography.bodySmall)
        Text(session.taskTitleSnapshot ?: "自由专注", style = MaterialTheme.typography.bodyMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        session.note?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
