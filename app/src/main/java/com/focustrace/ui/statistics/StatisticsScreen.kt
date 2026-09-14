package com.focustrace.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.focustrace.ui.components.LoadState

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().testTag("statistics-list"), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text("统计", style = MaterialTheme.typography.headlineLarge)
            Text("看见专注，也理解分心", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatisticsPeriod.entries.forEach { period ->
                    FilterChip(selected = selection.period == period, onClick = { viewModel.choose(period) }, label = { Text(period.label) })
                }
            }
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
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("专注概览", style = MaterialTheme.typography.titleLarge)
                            Text(formatDuration(totals.focusSeconds), style = MaterialTheme.typography.headlineMedium)
                            Metric("专注次数", "${totals.sessions} 次")
                            Metric("完成番茄", "${totals.pomodoros} 个")
                            Metric("分心次数", "${totals.distractions} 次")
                            Metric("分心时间", formatDuration(totals.distractionSeconds))
                            Metric("专注率", totals.focusPercent?.let { "$it%" } ?: "—")
                            Metric("平均分心时长", totals.averageDistractionSeconds?.let(::formatDuration) ?: "—")
                            Metric("平均首次分心时间", totals.averageFirstDistractionSeconds?.let(::formatDuration) ?: "—")
                        }
                    }
                }
                if (summary.totals.sessions == 0) item { Text("这段时间暂无已结束的专注记录，完成一次专注后再来看看。") }
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
        Text(label, Modifier.weight(1f))
        Text(value, Modifier.weight(1f))
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            .background(if (selectedDay == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer))
                    }
                    Text("${day.date.monthValue}/${day.date.dayOfMonth}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        summary.days.getOrNull(selectedDay)?.let { Text("${it.date} · ${labels[metric]}：${display(it)}") }
        Text("点击柱形查看数值，左右滑动查看更多日期。", style = MaterialTheme.typography.bodySmall)
    }
}
