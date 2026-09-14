package com.focustrace.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun FocusResultScreen(viewModel: FocusResultViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("专注报告", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("返回专注") }
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
                    else -> ReportContent(report, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReportContent(report: FocusReport, modifier: Modifier) {
    val s = report.session
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()) }
    LazyColumn(modifier.fillMaxWidth().testTag("report-list"), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(if (report.completedPomodoro) "本轮专注完成" else "本轮已结束", style = MaterialTheme.typography.titleLarge)
            Text(report.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
            Text(if (s.type == 0) "番茄钟" else "正向计时", Modifier.padding(top = 8.dp))
        }
        item {
            InfoCard("专注时长", "有效专注：${formatDuration(s.focusSeconds)}\n计划时长：${if (s.type == 0) formatDuration(s.plannedSeconds) else "不限时"}")
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("专注率", style = MaterialTheme.typography.titleLarge)
                    Text(report.focusPercent?.let { "$it%" } ?: "—", style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("report-focus-percent"))
                    Text("有效专注 ÷（有效专注 + 分心时间）")
                    if (report.focusPercent == null) Text("累计时长不足 1 秒，暂不计算比例。")
                }
            }
        }
        item { InfoCard("分心情况", "分心次数：${report.events.size} 次\n分心总时长：${formatDuration(report.distractionSeconds)}\n平均每次：${report.averageDistractionSeconds?.let(::formatDuration) ?: "—"}") }
        item {
            val first = if (report.events.isEmpty()) "未发生分心" else report.firstDistractionSeconds?.let { "开始后 ${formatDuration(it)}" } ?: "时间不可用"
            InfoCard("首次分心", first)
            Text("按开始到首次离开的时间计算，包含主动暂停。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        item { InfoCard("起止时间", "开始：${formatter.format(Instant.ofEpochMilli(s.startTime))}\n结束：${formatter.format(Instant.ofEpochMilli(s.endTime!!))}") }
        item { Text("分心明细", style = MaterialTheme.typography.titleLarge) }
        if (report.events.isEmpty()) item { Text("本轮暂无分心记录。") }
        items(report.events, key = { it.id }) { event ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("离开：${formatter.format(Instant.ofEpochMilli(event.backgroundTime))}")
                    Text("返回：${formatter.format(Instant.ofEpochMilli(event.foregroundTime))}")
                    Text("持续 ${formatDuration(event.durationSeconds)}")
                }
            }
        }
        item { Text("报告按已保存的整秒记录计算；休息时间不计入专注。", style = MaterialTheme.typography.bodySmall) }
    }
}
