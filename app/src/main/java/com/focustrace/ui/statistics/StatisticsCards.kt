package com.focustrace.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focustrace.focus.formatDuration
import com.focustrace.statistics.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

internal fun compactDuration(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600} 小时 ${seconds % 3600 / 60} 分"
    seconds >= 60 -> "${seconds / 60} 分钟"
    seconds > 0 -> "不足 1 分钟"
    else -> "0 分钟"
}

@Composable
internal fun StudyOverview(current: StatisticsSummary, previous: StatisticsSummary, period: StatisticsPeriod,
    onRecords: () -> Unit, onDetails: () -> Unit) {
    val totals = current.totals
    val comparison = remember(totals.focusSeconds, previous.totals.focusSeconds) {
        when {
            totals.focusSeconds == 0L && previous.totals.focusSeconds == 0L -> "本期还没有专注记录"
            previous.totals.focusSeconds == 0L -> "上期暂无记录"
            else -> {
                val change = ((totals.focusSeconds - previous.totals.focusSeconds) * 100.0 / previous.totals.focusSeconds).roundToInt()
                "较上期 ${if (change >= 0) "+" else ""}$change%"
            }
        }
    }
    val today = LocalDate.now(current.range.zone)
    val elapsedDays = current.days.count { it.date <= today }.coerceAtLeast(1)
    val averageDay = totals.focusSeconds / elapsedDays
    val averageSession = if (totals.sessions == 0) 0 else totals.focusSeconds / totals.sessions
    val taskCount = current.taskDistribution.count { it.title != "自由专注" }
    Card(shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${period.label}专注", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f))
                Text(compactDuration(totals.focusSeconds), style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("study-total").semantics {
                        contentDescription = "累计学习时间：${formatDuration(totals.focusSeconds)}"
                    })
                Text(comparison, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewNumber("${totals.sessions}", "专注次数", Modifier.weight(1f))
                OverviewNumber("${totals.pomodoros}", "完成番茄", Modifier.weight(1f))
                OverviewNumber("$taskCount", "关联待办", Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .12f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewNumber(compactDuration(averageDay), "日均专注", Modifier.weight(1f), compact = true)
                OverviewNumber(compactDuration(averageSession), "单次平均", Modifier.weight(1f), compact = true)
            }
            FilledTonalButton(onClick = onRecords, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface)) { Text("查看专注记录") }
        }
    }
    TextButton(onClick = onDetails, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
        Text("专注率 ${totals.focusPercent?.let { "$it%" } ?: "—"} · 分心 ${totals.distractions} 次")
    }
}

@Composable
private fun OverviewNumber(value: String, label: String, modifier: Modifier, compact: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .7f))
    }
}

private data class TrendPoint(val key: String, val shortLabel: String, val fullLabel: String, val seconds: Long, val current: Boolean)

@Composable
internal fun StudyTrend(summary: StatisticsSummary, period: StatisticsPeriod) {
    val today = LocalDate.now(summary.range.zone)
    val points = remember(summary, period, today) {
        when (period) {
            StatisticsPeriod.DAY -> summary.hours.mapIndexed { hour, totals ->
                TrendPoint("hour-$hour", "$hour", "%02d:00 - %02d:00".format(hour, hour + 1), totals.focusSeconds,
                    hour == java.time.LocalTime.now(summary.range.zone).hour && summary.range.start == today)
            }
            StatisticsPeriod.YEAR -> (1..12).map { month ->
                val seconds = summary.days.filter { it.date.monthValue == month }.sumOf { it.totals.focusSeconds }
                TrendPoint("month-$month", "$month 月", "${summary.range.start.year} 年 $month 月", seconds,
                    today.year == summary.range.start.year && today.monthValue == month)
            }
            else -> summary.days.map { day ->
                val label = if (period == StatisticsPeriod.WEEK)
                    day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA) else "${day.date.dayOfMonth}"
                TrendPoint(day.date.toString(), label, day.date.toString(), day.totals.focusSeconds, day.date == today)
            }
        }
    }
    var selected by rememberSaveable(summary.range.start.toString(), period) {
        mutableIntStateOf(points.indexOfFirst { it.current }.takeIf { it >= 0 }
            ?: points.indexOfFirst { it.seconds > 0 }.coerceAtLeast(0))
    }
    val activeIndex = selected.coerceIn(0, (points.size - 1).coerceAtLeast(0))
    val maximum = remember(points) { points.maxOfOrNull { it.seconds }?.coerceAtLeast(1) ?: 1 }
    val average = remember(points) { if (points.isEmpty()) 0 else points.sumOf { it.seconds } / points.size }
    val peak = remember(points) { points.maxByOrNull { it.seconds } }
    StatisticsSection("专注趋势", if (period == StatisticsPeriod.DAY) "按开始时段" else "专注时长") {
        if (points.all { it.seconds == 0L }) EmptyChart("这个周期还没有有效专注")
        else {
            val listState = rememberLazyListState()
            LaunchedEffect(summary.range.start, period) {
                withFrameNanos { }
                listState.scrollToItem((activeIndex - 2).coerceAtLeast(0))
            }
            LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("trend-bars")) {
                itemsIndexed(points, key = { _, point -> point.key }, contentType = { _, _ -> "bar" }) { index, point ->
                    TrendBar(point, maximum, activeIndex == index) { selected = index }
                }
            }
            Text("${points[activeIndex].fullLabel} · ${compactDuration(points[activeIndex].seconds)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SmallMetric("最高专注", peak?.let { "${it.shortLabel} · ${compactDuration(it.seconds)}" } ?: "—", Modifier.weight(1f))
                SmallMetric("平均", compactDuration(average), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TrendBar(point: TrendPoint, maximum: Long, selected: Boolean, onClick: () -> Unit) {
    val color = when {
        selected -> MaterialTheme.colorScheme.primary
        point.current -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary.copy(alpha = .32f)
    }
    Column(Modifier.width(46.dp).clickable(onClick = onClick).semantics {
        contentDescription = "${point.fullLabel}，${formatDuration(point.seconds)}"
    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (point.seconds == 0L) "" else if (point.seconds < 60) "<1" else "${point.seconds / 60}",
            style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Box(Modifier.height(112.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.width(24.dp)
                .height((112f * point.seconds.toFloat() / maximum).coerceAtLeast(if (point.seconds > 0) 4f else 0f).dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(color))
        }
        Text(point.shortLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
internal fun TaskDistribution(summary: StatisticsSummary) {
    val total = summary.totals.focusSeconds.coerceAtLeast(1)
    var selected by remember(summary.range.start) { mutableStateOf<TaskFocusDistribution?>(null) }
    StatisticsSection("专注分布", "按待办") {
        if (summary.taskDistribution.isEmpty()) EmptyChart("暂无待办分布")
        else summary.taskDistribution.take(6).forEachIndexed { index, item ->
            val ratio = item.focusSeconds.toFloat() / total
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth().clickable { selected = item }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${compactDuration(item.focusSeconds)} · ${(ratio * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
    selected?.let { item ->
        val ratio = item.focusSeconds.toDouble() / total
        AlertDialog(onDismissRequest = { selected = null }, title = {
            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallMetric("专注时长", compactDuration(item.focusSeconds), Modifier.fillMaxWidth())
                SmallMetric("专注次数", "${item.sessions} 次", Modifier.fillMaxWidth())
                SmallMetric("时间占比", "${(ratio * 100).roundToInt()}%", Modifier.fillMaxWidth())
                SmallMetric("单次平均", compactDuration(item.focusSeconds / item.sessions.coerceAtLeast(1)), Modifier.fillMaxWidth())
            }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text("关闭") } })
    }
}

@Composable
internal fun FocusRhythm(summary: StatisticsSummary) {
    val values = remember(summary.hours) { summary.hours.map { it.focusSeconds } }
    val max = remember(values) { values.maxOrNull()?.coerceAtLeast(1) ?: 1 }
    val peakStart = remember(values) {
        if (values.all { it == 0L }) null else (0..22).maxByOrNull { values[it] + values[it + 1] }
    }
    StatisticsSection("专注时间分布", "一天中的开始时段") {
        if (values.all { it == 0L }) EmptyChart("有记录后，这里会显示你的专注规律")
        else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(values, key = { hour, _ -> hour }) { hour, seconds ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.width(22.dp).height(76.dp), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().height((76f * seconds / max).coerceAtLeast(if (seconds > 0) 3f else 0f).dp)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = if (seconds == max) 1f else .45f)))
                        }
                        if (hour % 3 == 0) Text("$hour", style = MaterialTheme.typography.labelSmall)
                        else Spacer(Modifier.height(16.dp))
                    }
                }
            }
            peakStart?.let {
                Text("这个周期最常在 %02d:00 - %02d:00 开始专注".format(it, it + 2),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun HabitOverview(habits: HabitStatistics) {
    StatisticsSection("专注习惯", "长期坚持") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HabitMetric("${habits.currentStreak} 天", "连续专注", Modifier.weight(1f), emphasized = true)
            HabitMetric("${habits.longestStreak} 天", "最长连续", Modifier.weight(1f))
            HabitMetric("${habits.focusedDaysInMonth} / ${habits.monthDaysSoFar}", "本月专注", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HabitMetric(value: String, label: String, modifier: Modifier, emphasized: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StatisticsSection(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        content()
    }
}

@Composable
private fun SmallMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyChart(message: String) {
    Box(Modifier.fillMaxWidth().height(112.dp)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f), MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
