package com.focustrace.ui.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focustrace.data.local.entity.*
import com.focustrace.focus.*
import com.focustrace.ui.components.LoadState
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
internal fun ManualRecordDialog(record: FocusSessionEntity?, tasks: LoadState<List<TaskEntity>>,
    busy: Boolean, error: String?, onDismiss: () -> Unit,
    onSave: (String, String, String, Long?, String) -> Unit, onDelete: () -> Unit,
    defaultTaskId: Long? = null) {
    val zone = remember { ZoneId.systemDefault() }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val openedAt = rememberSaveable { System.currentTimeMillis() }
    var date by rememberSaveable(record?.id) { mutableStateOf(Instant.ofEpochMilli(record?.startTime ?: openedAt).atZone(zone).toLocalDate().toString()) }
    var start by rememberSaveable(record?.id) { mutableStateOf(timeFormat.format(Instant.ofEpochMilli(record?.startTime ?: openedAt).atZone(zone))) }
    var minutes by rememberSaveable(record?.id) { mutableStateOf(record?.let { (it.focusSeconds / 60).toString() } ?: "") }
    var taskId by rememberSaveable(record?.id) { mutableStateOf(record?.taskId ?: defaultTaskId) }
    var note by rememberSaveable(record?.id) { mutableStateOf(record?.note.orEmpty()) }
    var choosingTask by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val endResult = remember(start, minutes) { runCatching { manualEndTime(start, minutes) } }
    val end = endResult.getOrNull().orEmpty()
    val parsed = remember(date, start, minutes, zone) { runCatching {
        val input = parseManualFocusInput(date, start, endResult.getOrThrow(), zone)
        require(input.seconds == minutes.trim().toLong() * 60) { "该时段发生时区切换，请调整开始时间或时长" }
        input
    } }
    val taskList = (tasks as? LoadState.Ready)?.value.orEmpty()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (record == null) "添加专注记录" else "编辑专注记录") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(date, { date = it.take(10) }, label = { Text("日期（yyyy-MM-dd）") }, singleLine = true, enabled = !busy)
                OutlinedTextField(start, { start = it.take(5) }, label = { Text("开始时间（HH:mm）") }, singleLine = true, enabled = !busy)
                OutlinedTextField(minutes, { minutes = it.take(5) }, label = { Text("专注时长（分钟）") }, singleLine = true, enabled = !busy,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(6, key = { it }) { index ->
                        val value = listOf(25, 30, 45, 60, 90, 120)[index]
                        FilterChip(selected = minutes == value.toString(), onClick = { minutes = value.toString() }, enabled = !busy, label = { Text("$value 分钟") })
                    }
                }
                OutlinedTextField(end, {}, readOnly = true, label = { Text("结束时间（自动计算）") }, singleLine = true)
                parsed.getOrNull()?.let {
                    Text("$start - $end")
                    Text("专注时长：" + formatDuration(it.seconds), color = MaterialTheme.colorScheme.primary)
                } ?: Text(parsed.exceptionOrNull()?.message.orEmpty(), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { choosingTask = true }, enabled = !busy && tasks is LoadState.Ready) {
                    Text("对应任务：" + (taskList.firstOrNull { it.id == taskId }?.title ?: if (taskId == null) "不关联任务" else "原任务不可用"))
                }
                if (tasks is LoadState.Error) Text("任务加载失败，请关闭后重试", color = MaterialTheme.colorScheme.error)
                OutlinedTextField(note, { note = it.take(1000) }, label = { Text("备注（可选）") }, enabled = !busy, maxLines = 4)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (record != null) TextButton(onClick = { confirmDelete = true }, enabled = !busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除记录") }
            }
        },
        confirmButton = { Button(enabled = !busy && parsed.isSuccess,
            onClick = { onSave(date, start, end, taskId, note) }) { Text(if (busy) "保存中…" else "保存记录") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } })
    if (choosingTask) AlertDialog(onDismissRequest = { choosingTask = false }, title = { Text("选择任务") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 320.dp)) {
                item { TextButton(onClick = { taskId = null; choosingTask = false }) { Text("不关联任务") } }
                items(taskList.size, key = { taskList[it].id }) { index ->
                    val task = taskList[index]
                    TextButton(onClick = { taskId = task.id; choosingTask = false }) { Text(task.title) }
                }
            }
        }, confirmButton = { TextButton(onClick = { choosingTask = false }) { Text("关闭") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!busy) confirmDelete = false },
        title = { Text("删除这条手动记录？") }, text = { Text("删除后将同步更新专注统计。") },
        confirmButton = { TextButton(onClick = onDelete, enabled = !busy) { Text("确认删除记录") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }, enabled = !busy) { Text("保留记录") } })
}
