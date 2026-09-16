package com.focustrace.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focustrace.focus.formatDuration
import com.focustrace.statistics.*
import com.focustrace.ui.components.TraceCard

internal fun compactDuration(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600} 小时 ${seconds % 3600 / 60} 分"
    seconds >= 60 -> "${seconds / 60} 分钟"
    seconds > 0 -> "不足 1 分钟"
    else -> "0 分钟"
}

@Composable
internal fun StudyOverview(totals: StatisticsTotals, onRecords: () -> Unit, onDetails: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("累计学习时间", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(compactDuration(totals.focusSeconds), style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.testTag("study-total")
                    .semantics { contentDescription = "累计学习时间：${formatDuration(totals.focusSeconds)}" })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewNumber("${totals.sessions}", "专注次数", Modifier.weight(1f))
                OverviewNumber("${totals.pomodoros}", "完成番茄", Modifier.weight(1f))
                OverviewNumber(totals.focusPercent?.let { "$it%" } ?: "—", "专注率", Modifier.weight(1f))
            }
            FilledTonalButton(onClick = onRecords, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.secondary)) {
                Text("查看专注记录")
            }
        }
    }
    TextButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) {
        Text("分心 ${totals.distractions} 次 · ${compactDuration(totals.distractionSeconds)}   查看详情")
    }
}

@Composable
private fun OverviewNumber(value: String, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
internal fun StudyTrend(summary: StatisticsSummary, period: StatisticsPeriod) {
    var metric by rememberSaveable { mutableIntStateOf(0) }
    val hourly = period == StatisticsPeriod.DAY
    val values = remember(summary, metric, hourly) {
        if (hourly) summary.hours.map { when(metric) { 1 -> it.distractions.toLong(); 2 -> it.distractionSeconds; else -> it.focusSeconds } }
        else summary.days.map { when(metric) { 1 -> it.totals.distractions.toLong(); 2 -> it.totals.distractionSeconds; else -> it.totals.focusSeconds } }
    }
    var selected by rememberSaveable(summary.range.start.toString(), hourly, metric, values.size) { mutableIntStateOf(values.indexOfFirst { it > 0 }.coerceAtLeast(0)) }
    val activeIndex = selected.coerceIn(0, (values.size - 1).coerceAtLeast(0))
    val maximum = remember(values) { values.maxOrNull()?.coerceAtLeast(1) ?: 1 }
    val captions = listOf("学习", "分心次数", "分心时长")
    TraceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (hourly) "开始时段分布" else "每日趋势", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(if (metric == 1) "次" else "分钟", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            captions.forEachIndexed { i, label -> FilterChip(selected = metric == i, onClick = { metric = i }, label = { Text(label) }) }
        }
        if (values.all { it == 0L }) {
            Box(Modifier.fillMaxWidth().height(144.dp), contentAlignment = Alignment.Center) {
                Text(if (metric == 0) "还没有学习记录" else "暂无分心记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // 48dp touch targets and lazy bars preserve smooth scrolling for month/hour views.
            val barState = rememberLazyListState()
            LaunchedEffect(summary.range.start, hourly, metric) { barState.scrollToItem((activeIndex - 2).coerceAtLeast(0)) }
            LazyRow(state = barState, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().testTag("trend-bars")) {
                itemsIndexed(values, key = { i, _ -> i }, contentType = { _, _ -> "bar" }) { index, amount ->
                    val label = if (hourly) "$index 时" else "${summary.days[index].date.dayOfMonth} 日"
                    Column(Modifier.width(48.dp).clickable { selected = index }.semantics {
                        contentDescription = "$label，${if (metric == 1) "$amount 次" else formatDuration(amount)}"
                    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (metric == 1) "$amount" else if (amount in 1..59) "<1" else "${amount / 60}", style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.height(104.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.width(24.dp).height((104f * amount.toFloat() / maximum).coerceAtLeast(if (amount > 0) 3f else 0f).dp)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(if (activeIndex == index) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondary.copy(alpha = .35f)))
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("${if (hourly) "${activeIndex}:00 开始" else summary.days[activeIndex].date.toString()} · ${if (metric == 1) "${values[activeIndex]} 次" else formatDuration(values[activeIndex])}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
internal fun CategoryBreakdown(categories: List<CategoryStatistics>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val total = remember(categories) { categories.sumOf { it.focusSeconds }.coerceAtLeast(1) }
    TraceCard {
        Text("学习时间分配", style = MaterialTheme.typography.titleMedium)
        if (categories.isEmpty()) Text("完成一次专注后查看分类分布", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        categories.take(5).forEach { category ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(category.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(compactDuration(category.focusSeconds), style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(progress = { category.focusSeconds.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.secondary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        if (categories.size > 5) TextButton(onClick = { expanded = true }) { Text("查看全部 ${categories.size} 个分类") }
    }
    if (expanded) CategoryDetailsDialog(categories) { expanded = false }
}
