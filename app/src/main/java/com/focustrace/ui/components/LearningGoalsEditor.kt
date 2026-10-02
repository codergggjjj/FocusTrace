package com.focustrace.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.focustrace.data.datastore.MAX_DAILY_GOAL_MINUTES
import com.focustrace.data.datastore.MAX_WEEKLY_GOAL_MINUTES

internal fun learningGoalDuration(seconds: Long): String = when {
    seconds >= 3600 && seconds % 3600 < 60 -> "${seconds / 3600} 小时"
    seconds >= 3600 -> "${seconds / 3600} 小时 ${seconds % 3600 / 60} 分"
    seconds >= 60 -> "${seconds / 60} 分钟"
    seconds > 0 -> "不足 1 分钟"
    else -> "0 分钟"
}

@Composable
fun LearningGoalsEditor(daily: Int, weekly: Int, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var dailyEnabled by rememberSaveable { mutableStateOf(daily > 0) }
    var weeklyEnabled by rememberSaveable { mutableStateOf(weekly > 0) }
    var dailyInput by rememberSaveable { mutableStateOf(daily.takeIf { it > 0 }?.toString() ?: "120") }
    var weeklyInput by rememberSaveable { mutableStateOf(weekly.takeIf { it > 0 }?.toString() ?: "600") }
    val valid = (!dailyEnabled || dailyInput.toIntOrNull() in 1..MAX_DAILY_GOAL_MINUTES) &&
        (!weeklyEnabled || weeklyInput.toIntOrNull() in 1..MAX_WEEKLY_GOAL_MINUTES)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("学习目标") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("learning-goals-editor"),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GoalInput("每日学习目标", "每日目标（分钟）", "daily", dailyEnabled, dailyInput,
                    MAX_DAILY_GOAL_MINUTES, listOf(60, 120, 180, 240), busy,
                    onEnabled = { dailyEnabled = it }, onInput = { dailyInput = it })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                GoalInput("每周学习目标", "每周目标（分钟）", "weekly", weeklyEnabled, weeklyInput,
                    MAX_WEEKLY_GOAL_MINUTES, listOf(300, 600, 900, 1200), busy,
                    onEnabled = { weeklyEnabled = it }, onInput = { weeklyInput = it })
                Text("本周按周一至周日累计，包含全部待办。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = valid && !busy, onClick = {
                onSave(if (dailyEnabled) dailyInput.toInt() else 0, if (weeklyEnabled) weeklyInput.toInt() else 0)
            }) { Text(if (busy) "保存中…" else "保存目标") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } })
}

@Composable
private fun GoalInput(title: String, label: String, tag: String, enabled: Boolean, input: String,
    max: Int, shortcuts: List<Int>, busy: Boolean, onEnabled: (Boolean) -> Unit, onInput: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Switch(checked = enabled, onCheckedChange = onEnabled, enabled = !busy,
                modifier = Modifier.testTag("goal-$tag-enabled"))
        }
        if (enabled) {
            OutlinedTextField(input, { onInput(it.take(5)) }, label = { Text(label) },
                enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = input.toIntOrNull() !in 1..max,
                supportingText = { Text(if (input.toIntOrNull() in 1..max)
                    learningGoalDuration(input.toLong() * 60) else "请输入 1–$max 分钟") })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shortcuts, key = { it }) { minutes ->
                    FilterChip(selected = input == minutes.toString(), enabled = !busy,
                        onClick = { onInput(minutes.toString()) }, label = { Text(learningGoalDuration(minutes * 60L)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer), border = null)
                }
            }
        } else Text("未开启", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
