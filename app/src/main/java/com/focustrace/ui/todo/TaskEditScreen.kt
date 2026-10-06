package com.focustrace.ui.todo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.focustrace.data.local.entity.TaskEntity

@Composable
fun TaskEditScreen(task: TaskEntity?, busy: Boolean, defaultMinutes: Int,
    onDelete: () -> Unit, onDismiss: () -> Unit, onSave: (String, Int, Int, String) -> Unit,
    onAddRecord: () -> Unit, onStudy: () -> Unit) {
    var title by rememberSaveable(task?.id) { mutableStateOf(task?.title ?: "") }
    var minutes by rememberSaveable(task?.id) { mutableStateOf(task?.targetMinutes?.toString() ?: defaultMinutes.toString()) }
    var timerType by rememberSaveable(task?.id) { mutableIntStateOf(task?.timerType ?: 0) }
    var repeatType by rememberSaveable(task?.id) { mutableStateOf(task?.repeatType ?: "NONE") }
    val duration = minutes.toIntOrNull()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (task == null) "创建待办" else "编辑待办") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(100) }, label = { Text("待办名称") }, singleLine = true, enabled = !busy)
                Text("计时方式", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = timerType == 0, onClick = { timerType = 0 }, enabled = !busy,
                        modifier = Modifier.testTag("timer-type-pomodoro"), label = { Text("番茄钟") })
                    FilterChip(selected = timerType == 1, onClick = { timerType = 1 }, enabled = !busy,
                        modifier = Modifier.testTag("timer-type-stopwatch"), label = { Text("正向计时") })
                }
                if (timerType == 1) Text("从零开始计时，手动结束，无需设置目标时长。",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else OutlinedTextField(value = minutes, onValueChange = { minutes = it.take(5) }, label = { Text("目标分钟数") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy,
                    isError = duration == null || duration !in 1..1440,
                    supportingText = { Text("请输入 1–1440 分钟") })
                if (task?.repeatParentId == null) {
                    Text("重复", style = MaterialTheme.typography.titleSmall)
                    listOf("NONE", "DAILY", "WEEKDAYS", "DAYS:").forEach { rule ->
                        FilterChip(selected = if (rule == "DAYS:") repeatType.startsWith(rule) else repeatType == rule,
                            onClick = { repeatType = rule }, enabled = !busy,
                            modifier = Modifier.testTag("repeat-$rule"),
                            label = { Text(if (rule == "DAYS:") "指定星期" else com.focustrace.data.repository.TaskRepeat.label(rule)) })
                    }
                    if (repeatType.startsWith("DAYS:")) {
                        listOf(1..4, 5..7).forEach { days ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                days.forEach { day ->
                                    val selected = day.toString() in repeatType.removePrefix("DAYS:")
                                    FilterChip(selected = selected, enabled = !busy,
                                        onClick = {
                                            val values = repeatType.removePrefix("DAYS:").toCharArray().toMutableSet()
                                            if (selected) values.remove('0' + day) else values.add('0' + day)
                                            repeatType = "DAYS:" + values.sorted().joinToString("")
                                        }, modifier = Modifier.testTag("repeat-day-$day"), label = { Text("一二三四五六日"[day - 1].toString()) })
                                }
                            }
                        }
                    }
                    if (repeatType != "NONE") Text("从下个符合日期起，打开待办页时生成当天任务；不补建错过的日期。",
                        style = MaterialTheme.typography.bodySmall)
                } else Text("本次来自重复待办；重复规则在原待办中修改。", style = MaterialTheme.typography.bodySmall)
                if (task != null) {
                    OutlinedButton(onClick = onStudy, enabled = !busy) { Text("学习详情") }
                    FilledTonalButton(onClick = onAddRecord, enabled = !busy) { Text("添加专注记录") }
                    TextButton(onClick = onDelete, enabled = !busy,
                        modifier = Modifier.testTag("delete-task-${task.id}"),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("删除待办")
                    }
                }
            }
        },
        confirmButton = { Button(enabled = !busy && com.focustrace.data.repository.TaskRepeat.valid(repeatType) && title.isNotBlank() && (timerType == 1 || (duration != null && duration in 1..1440)),
            onClick = { onSave(title, duration?.takeIf { it in 1..1440 } ?: 25, timerType, repeatType) }) { Text(if (busy) "保存中…" else "保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } })
}
