package com.focustrace.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import java.time.*
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.focus.formatDuration
import com.focustrace.statistics.*
import com.focustrace.ui.components.*

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel, onReport: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val heatmap by viewModel.heatmap.collectAsStateWithLifecycle()
    var showDate by rememberSaveable { mutableStateOf(false) }
    if (showDate) PeriodDateDialog(selection, onDismiss = { showDate = false }) {
        viewModel.selectDate(it); showDate = false
    }
    LazyColumn(Modifier.fillMaxSize().testTag("statistics-list"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            PageHeader("统计", "看见专注，也理解分心")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatisticsPeriod.entries.forEach { period ->
                    FilterChip(selected = selection.period == period, onClick = { viewModel.choose(period) }, label = { Text(period.label) })
                }
            }
            TextButton(onClick = { showDate = true }) { Text(if (selection.period == StatisticsPeriod.MONTH) "选择月份" else "选择日期") }
            Text(if (selection.period == StatisticsPeriod.DAY) selection.range.start.toString()
                else "${selection.range.start} 至 ${selection.range.endExclusive.minusDays(1)}",
                modifier = Modifier.testTag("statistics-range"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { viewModel.shift(-1) }) { Text("上一${selection.period.unit}") }
                TextButton(onClick = viewModel::current) { Text("回到${selection.period.label}") }
                TextButton(onClick = { viewModel.shift(1) }, enabled = selection.canNext) { Text("下一${selection.period.unit}") }
            }
        }
        when (val value = state) {
            LoadState.Loading -> item { CircularProgressIndicator() }
            is LoadState.Error -> item {
                Text(value.message)
                Button(onClick = viewModel::retry) { Text("重试") }
            }
            is LoadState.Ready -> {
                val summary = value.value
                item {
                    val totals = summary.totals
                    TraceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("累计学习时间", style = MaterialTheme.typography.titleLarge)
                            Text(formatDuration(totals.focusSeconds), modifier = Modifier.testTag("study-total"), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                SummaryMetric("专注次数", "${totals.sessions} 次", Modifier.weight(1f))
                                SummaryMetric("完成番茄", "${totals.pomodoros} 个", Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                SummaryMetric("分心次数", "${totals.distractions} 次", Modifier.weight(1f))
                                SummaryMetric("分心时间", formatDuration(totals.distractionSeconds), Modifier.weight(1f))
                            }
                            Metric("专注率", totals.focusPercent?.let { "$it%" } ?: "—")
                            Metric("平均分心时长", totals.averageDistractionSeconds?.let(::formatDuration) ?: "—")
                            Metric("平均首次分心时间", totals.averageFirstDistractionSeconds?.let(::formatDuration) ?: "—")
                        }
                    }
                }
                if (summary.totals.sessions == 0) item { Text("这段时间暂无已结束的专注记录，完成一次专注后再来看看。") }
                item {
                    when (val calendar = heatmap) {
                        is LoadState.Ready -> FocusHeatmap(calendar.value, onViewDay = viewModel::viewDay)
                        is LoadState.Error -> TextButton(onClick = viewModel::retry) { Text("热力图加载失败，点击重试") }
                        LoadState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                item {
                    Text("学习记录 · ${summary.sessions.size} 次", style = MaterialTheme.typography.titleLarge)
                    Text("累计时间为有效专注时长；起止区间可能包含暂停和分心。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(summary.sessions, key = { "session-${it.id}" }) { session ->
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(summary.range.zone)
                    TraceCard(Modifier.testTag("study-session-${session.id}").clickable { onReport(session.id) }) {
                        Text(session.taskTitleSnapshot ?: "自由专注 / 原待办不可用", style = MaterialTheme.typography.titleMedium)
                        Text("开始：${formatter.format(Instant.ofEpochMilli(session.startTime))}")
                        Text("结束：${formatter.format(Instant.ofEpochMilli(session.endTime!!))}")
                        Text("${if (session.type == 0) "番茄钟" else "正向计时"} · 有效学习 ${formatDuration(session.focusSeconds)}",
                            color = MaterialTheme.colorScheme.primary)
                        Text("查看本次报告", style = MaterialTheme.typography.labelLarge)
                    }
                }
                item { DailyChart(summary) }
                item {
                    Text("分类统计", style = MaterialTheme.typography.titleLarge)
                    Text("按待办当前分类汇总；无关联待办的记录归入未分类。", style = MaterialTheme.typography.bodySmall)
                }
                summary.categories.forEach { category ->
                    item { Metric(category.name, "${formatDuration(category.focusSeconds)} · ${category.sessions} 次") }
                }
                item { Text("记录归入开始当天，周一为每周起点。专注率 = 总有效专注 /（总有效专注 + 总分心时间）。首次分心均值仅包含发生过分心的记录。", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DailyChart(summary: StatisticsSummary) {
    var metric by rememberSaveable { mutableIntStateOf(0) }
    var selectedDay by rememberSaveable(summary.range.start.toString(), summary.days.size) { mutableIntStateOf(0) }
    val labels = listOf("专注时间", "分心次数", "分心时间")
    fun amount(day: DailyStatistics): Long = when (metric) {
        0 -> day.totals.focusSeconds
        1 -> day.totals.distractions.toLong()
        else -> day.totals.distractionSeconds
    }
    fun display(day: DailyStatistics) = if (metric == 1) "${amount(day)} 次" else formatDuration(amount(day))
    val maximum = summary.days.maxOfOrNull(::amount)?.coerceAtLeast(1) ?: 1
    TraceCard {
        Text("每日趋势", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                FilterChip(selected = metric == index, onClick = { metric = index }, label = { Text(label) })
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom) {
            summary.days.forEachIndexed { index, day ->
                Column(Modifier.width(48.dp).semantics { contentDescription = "${day.date} ${labels[metric]} ${display(day)}" }
                    .clickable { selectedDay = index }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(120.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.width(24.dp).height((120f * amount(day).toDouble() / maximum).toFloat().dp)
                            .background(if (selectedDay == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary))
                    }
                    Text("${day.date.monthValue}/${day.date.dayOfMonth}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        summary.days.getOrNull(selectedDay)?.let { Text("${it.date} · ${labels[metric]}：${display(it)}") }
        Text("点击柱形查看数值，左右滑动查看更多日期。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun PeriodDateDialog(selection: StatisticsSelection, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val month = selection.period == StatisticsPeriod.MONTH
    var input by rememberSaveable { mutableStateOf(if (month) YearMonth.from(selection.range.start).toString() else selection.range.start.toString()) }
    val date = runCatching { if (month) YearMonth.parse(input.trim()).atDay(1) else LocalDate.parse(input.trim()) }.getOrNull()
    val valid = date != null && date <= LocalDate.now()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (month) "选择月份" else "选择日期") },
        text = {
            OutlinedTextField(value = input, onValueChange = { input = it.take(10) }, singleLine = true,
                label = { Text(if (month) "月份（yyyy-MM）" else "日期（yyyy-MM-dd）") },
                isError = !valid, supportingText = { Text(if (month) "例如 2026-09，不可选择未来月份" else "例如 2026-09-15，不可选择未来日期") })
        },
        confirmButton = { TextButton(enabled = valid, onClick = { date?.let(onConfirm) }) { Text("查看") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
