package com.focustrace.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focustrace.statistics.StatisticsSummary
import com.focustrace.focus.formatDuration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.time.LocalDate

fun heatLevel(seconds: Long): Int = when {
    seconds <= 0 -> 0
    seconds < 1800 -> 1
    seconds < 3600 -> 2
    seconds < 7200 -> 3
    else -> 4
}

@Composable
fun FocusHeatmap(summary: StatisticsSummary, selectedDate: LocalDate?, onSelectDay: (LocalDate) -> Unit) {
    val colors = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primary.copy(alpha = .18f),
        MaterialTheme.colorScheme.primary.copy(alpha = .36f), MaterialTheme.colorScheme.primary.copy(alpha = .65f), MaterialTheme.colorScheme.primary)
    val today = LocalDate.now(summary.range.zone)
    val blanks = summary.range.start.dayOfWeek.value - 1
    StatisticsSection("专注日历", "${summary.range.start.year} 年 ${summary.range.start.monthValue} 月",
        modifier = Modifier.testTag("focus-heatmap")) {
        Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach {
            Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        val rows = (blanks + summary.days.size + 6) / 7
        repeat(rows) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(7) { weekday ->
                    val index = week * 7 + weekday - blanks
                    val day = summary.days.getOrNull(index)
                    if (day == null) Spacer(Modifier.weight(1f).height(64.dp))
                    else {
                        val level = heatLevel(day.totals.focusSeconds)
                        val future = day.date > today
                        val fill = if (future) Color.Transparent else colors[level]
                        val selected = day.date == selectedDate
                        val cellColor = fill.compositeOver(MaterialTheme.colorScheme.surface)
                        val textColor = if (cellColor.luminance() > .179f) Color.Black else Color.White
                        val outlineWidth = when {
                            selected -> 2.dp
                            day.date == today -> 1.dp
                            else -> 0.dp
                        }
                        val outlineColor = when {
                            selected -> MaterialTheme.colorScheme.primary
                            day.date == today -> MaterialTheme.colorScheme.outline
                            else -> Color.Transparent
                        }
                        val hours = remember(day.totals.focusSeconds) {
                            if (day.totals.focusSeconds <= 0) ""
                            else String.format(Locale.ROOT, "%.1fh", day.totals.focusSeconds / 3600.0)
                        }
                        Column(Modifier.weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .testTag("heat-day-${day.date}")
                            .semantics {
                                contentDescription = "${day.date}，有效学习 ${formatDuration(day.totals.focusSeconds)}${if (selected) "，已选择" else ""}"
                            }
                            .selectable(selected = selected, enabled = !future) { onSelectDay(day.date) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f)
                                .background(fill, RoundedCornerShape(10.dp))
                                .border(outlineWidth, outlineColor, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center) {
                                Text(hours, style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center,
                                    color = textColor)
                            }
                            Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("少", style = MaterialTheme.typography.bodySmall)
            colors.forEach { Box(Modifier.size(16.dp).background(it, RoundedCornerShape(3.dp))) }
            Text("多", style = MaterialTheme.typography.bodySmall)
        }
        Text("点击日期切换到当天统计", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
