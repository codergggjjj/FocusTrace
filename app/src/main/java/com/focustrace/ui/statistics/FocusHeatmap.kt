package com.focustrace.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focustrace.statistics.StatisticsSummary
import com.focustrace.focus.formatDuration
import com.focustrace.ui.components.TraceCard
import java.time.LocalDate

fun heatLevel(seconds: Long): Int = when {
    seconds <= 0 -> 0
    seconds < 1800 -> 1
    seconds < 3600 -> 2
    seconds < 7200 -> 3
    else -> 4
}

@Composable
fun FocusHeatmap(summary: StatisticsSummary, onViewDay: (LocalDate) -> Unit) {
    var selected by rememberSaveable(summary.range.start.toString()) { mutableStateOf<String?>(null) }
    val colors = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primary.copy(alpha = .18f),
        MaterialTheme.colorScheme.primary.copy(alpha = .36f), MaterialTheme.colorScheme.primary.copy(alpha = .65f), MaterialTheme.colorScheme.primary)
    val today = LocalDate.now(summary.range.zone)
    val blanks = summary.range.start.dayOfWeek.value - 1
    TraceCard(Modifier.testTag("focus-heatmap")) {
        Text("专注日历 · ${summary.range.start.monthValue} 月", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach {
            Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        } }
        val rows = (blanks + summary.days.size + 6) / 7
        repeat(rows) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { weekday ->
                    val index = week * 7 + weekday - blanks
                    val day = summary.days.getOrNull(index)
                    if (day == null) Spacer(Modifier.weight(1f).height(48.dp))
                    else {
                        val level = heatLevel(day.totals.focusSeconds)
                        val future = day.date > today
                        val fill = if (future) Color.Transparent else colors[level]
                        Box(Modifier.weight(1f).height(48.dp)
                            .background(fill, RoundedCornerShape(6.dp))
                            .border(if (day.date == today) 2.dp else 0.dp,
                                if (day.date == today) MaterialTheme.colorScheme.secondary else Color.Transparent, RoundedCornerShape(6.dp))
                            .testTag("heat-day-${day.date}")
                            .semantics { contentDescription = "${day.date}，有效学习 ${formatDuration(day.totals.focusSeconds)}" }
                            .clickable(enabled = !future) { selected = day.date.toString() },
                            contentAlignment = Alignment.Center) {
                            Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp)).padding(horizontal = 3.dp),
                                color = MaterialTheme.colorScheme.onSurface)
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
        Text("点击日期查看当天记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val day = summary.days.firstOrNull { it.date.toString() == selected }
    if (day != null) AlertDialog(onDismissRequest = { selected = null },
        title = { Text(day.date.toString()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("学习时间：${formatDuration(day.totals.focusSeconds)}")
                Text("完成番茄：${day.totals.pomodoros} 个")
                Text("分心次数：${day.totals.distractions} 次")
                Text("分心时间：${formatDuration(day.totals.distractionSeconds)}")
            }
        },
        confirmButton = { TextButton(onClick = { selected = null; onViewDay(day.date) }) { Text("查看当天记录") } },
        dismissButton = { TextButton(onClick = { selected = null }) { Text("关闭") } })
}
