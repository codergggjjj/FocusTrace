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
import androidx.compose.ui.text.style.TextAlign
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
internal fun StudyOverview(current: StatisticsSummary, previous: StatisticsSummary,
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
    Card(shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("累计学习时间", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(compactDuration(totals.focusSeconds), style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("study-total").semantics {
                        contentDescription = "累计学习时间：${formatDuration(totals.focusSeconds)}"
                    })
                Text(comparison, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewNumber("${totals.sessions}", "专注次数", Modifier.weight(1f))
                OverviewNumber("${totals.pomodoros}", "完成番茄", Modifier.weight(1f))
                OverviewNumber(totals.focusPercent?.let { "$it%" } ?: "—", "专注率", Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewNumber(compactDuration(averageDay), "日均专注", Modifier.weight(1f), compact = true)
                OverviewNumber(compactDuration(averageSession), "单次平均", Modifier.weight(1f), compact = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRecords, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text("查看专注记录")
                }
                TextButton(onClick = onDetails, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text("分心详情")
                }
            }
        }
    }
}

@Composable
private fun OverviewNumber(value: String, label: String, modifier: Modifier, compact: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    StatisticsSection("专注趋势", if (period == StatisticsPeriod.DAY) "按实际覆盖小时" else "专注时长") {
        if (points.all { it.seconds == 0L }) EmptyChart("这个周期还没有有效专注")
        else {
            if (period == StatisticsPeriod.DAY) {
                CompactHourBars(points, maximum, activeIndex, { selected = it }, "trend-bars")
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(summary.range.start, period) {
                    withFrameNanos { }
                    listState.scrollToItem((activeIndex - 2).coerceAtLeast(0))
                }
                LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().testTag("trend-bars")) {
                    itemsIndexed(points, key = { _, point -> point.key }, contentType = { _, _ -> "bar" }) { index, point ->
                        TrendBar(point, maximum, activeIndex == index) { selected = index }
                    }
                }
            }
            Text("${points[activeIndex].fullLabel} · ${compactDuration(points[activeIndex].seconds)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
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
        point.current -> MaterialTheme.colorScheme.primary.copy(alpha = .58f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = .28f)
    }
    Column(Modifier.width(42.dp).clickable(onClick = onClick).semantics {
        contentDescription = "${point.fullLabel}，${formatDuration(point.seconds)}"
    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (point.seconds == 0L) "" else if (point.seconds < 60) "<1" else "${point.seconds / 60}",
            style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Box(Modifier.height(88.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.width(20.dp)
                .height((88f * point.seconds.toFloat() / maximum).coerceAtLeast(if (point.seconds > 0) 4f else 0f).dp)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(color))
        }
        Text(point.shortLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun CompactHourBars(points: List<TrendPoint>, maximum: Long, selected: Int,
    onSelect: (Int) -> Unit, testTag: String) {
    Row(Modifier.fillMaxWidth().height(104.dp).testTag(testTag), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        points.forEachIndexed { index, point ->
            val color = when {
                index == selected -> MaterialTheme.colorScheme.primary
                point.current -> MaterialTheme.colorScheme.primary.copy(alpha = .58f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = .24f)
            }
            Column(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(index) }.semantics {
                contentDescription = "${point.fullLabel}，${formatDuration(point.seconds)}"
            }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.width(6.dp)
                        .height((80f * point.seconds.toFloat() / maximum).coerceAtLeast(if (point.seconds > 0) 3f else 0f).dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(color))
                }
                if (index % 2 == 0) Text("$index", modifier = Modifier.requiredWidth(24.dp),
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
                else Spacer(Modifier.height(16.dp))
            }
        }
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
                    color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = .55f),
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
    val peakHour = remember(values) {
        if (values.all { it == 0L }) null else values.indices.maxByOrNull { values[it] }
    }
    var selectedHour by rememberSaveable(summary.range.start.toString(), summary.range.endExclusive.toString()) {
        mutableIntStateOf(peakHour ?: 0)
    }
    val points = remember(values) {
        values.mapIndexed { hour, seconds ->
            TrendPoint("rhythm-$hour", "$hour", "%02d:00 - %02d:00".format(hour, hour + 1), seconds, false)
        }
    }
    StatisticsSection("专注时间分布", "按实际覆盖时段") {
        if (values.all { it == 0L }) EmptyChart("有记录后，这里会显示你的专注规律")
        else {
            val activeHour = selectedHour.coerceIn(0, 23)
            CompactHourBars(points, max, activeHour, { selectedHour = it }, "focus-rhythm-bars")
            Text("%02d:00 - %02d:00 · %s".format(activeHour, activeHour + 1, compactDuration(values[activeHour])),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            peakHour?.let {
                Text("专注最多：%02d:00 - %02d:00 · %s".format(it, it + 1, compactDuration(values[it])),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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
internal fun StatisticsSection(title: String, subtitle: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
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
    Box(Modifier.fillMaxWidth().height(96.dp)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f), MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
