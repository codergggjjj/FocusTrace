package com.focustrace.ui.focus

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.focus.FocusReport
import com.focustrace.focus.formatDuration
import com.focustrace.ui.components.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FocusResultScreen(viewModel: FocusResultViewModel, backLabel: String = "返回待办", onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val deleteError by viewModel.deleteError.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    (state as? LoadState.Ready)?.value?.session?.takeIf { confirmDelete }?.let { record ->
        DeleteFocusRecordDialog(record, deleting, deleteError, onDismiss = { confirmDelete = false },
            onConfirm = { viewModel.deleteRecord(onBack) })
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("专注报告", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text(backLabel) }
        }
        when (val current = state) {
            LoadState.Loading -> CircularProgressIndicator(Modifier.padding(24.dp))
            is LoadState.Error -> Column(Modifier.padding(24.dp)) {
                Text(current.message)
                Button(onClick = viewModel::retry) { Text("重试") }
            }
            is LoadState.Ready -> {
                val report = current.value
                when {
                    report == null -> Text("找不到这条专注记录。", Modifier.padding(24.dp))
                    !report.available -> Text("本轮尚未结束，结束后即可查看完整报告。", Modifier.padding(24.dp))
                    else -> ReportContent(report, Modifier.weight(1f), deleting) {
                        viewModel.clearDeleteError(); confirmDelete = true
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportContent(report: FocusReport, modifier: Modifier, deleting: Boolean, onDelete: () -> Unit) {
    val s = report.session
    var showDistractions by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()) }
    LazyColumn(modifier.fillMaxWidth().testTag("report-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(if (report.completedPomodoro) "本轮专注完成" else "本轮已结束", style = MaterialTheme.typography.titleLarge)
            Text(report.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
            Text(if (s.type == 0) "番茄钟" else "正向计时", Modifier.padding(top = 8.dp))
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("专注时长", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(formatDuration(s.focusSeconds), style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (s.type == 0) Text("计划 ${formatDuration(s.plannedSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        item {
            TraceCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReportMetric("专注率", report.focusPercent?.let { "$it%" } ?: "—",
                        Modifier.weight(1f), valueTag = "report-focus-percent")
                    ReportMetric("分心", "${report.events.size} 次", Modifier.weight(1f))
                    ReportMetric("分心时长", formatDuration(report.distractionSeconds), Modifier.weight(1f))
                }
            }
        }
        item { InfoCard("起止时间", "开始：${formatter.format(Instant.ofEpochMilli(s.startTime))}\n结束：${formatter.format(Instant.ofEpochMilli(s.endTime!!))}") }
        if (report.events.isNotEmpty()) item {
            FilledTonalButton(onClick = { showDistractions = true }, modifier = Modifier.fillMaxWidth()) {
                Text("查看分心记录")
            }
        }
        item {
            TextButton(onClick = onDelete, enabled = !deleting && s.status == 4,
                modifier = Modifier.fillMaxWidth().testTag("report-delete-record"),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(if (s.status == 3) "正在休息，结束后可删除" else "删除专注记录")
            }
        }
    }
    if (showDistractions) DistractionHistoryDialog(report.events) { showDistractions = false }
}

@Composable
private fun ReportMetric(label: String, value: String, modifier: Modifier = Modifier, valueTag: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge,
            modifier = if (valueTag == null) Modifier else Modifier.testTag(valueTag))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
