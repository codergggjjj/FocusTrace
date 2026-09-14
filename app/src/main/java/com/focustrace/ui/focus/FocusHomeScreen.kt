package com.focustrace.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.ui.components.*

@Composable
fun FocusHomeScreen(viewModel: FocusViewModel, onReport: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var stopwatch by rememberSaveable { mutableStateOf(false) }
    var minutes by rememberSaveable { mutableStateOf<String?>(null) }
    var rest by rememberSaveable { mutableStateOf<String?>(null) }
    var taskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var chooseTask by remember { mutableStateOf(false) }
    var showDistractions by rememberSaveable { mutableStateOf(false) }
    var showThreshold by rememberSaveable { mutableStateOf(false) }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var runningSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    val s = state.session
    LaunchedEffect(s?.id, s?.endTime) {
        if (s != null && s.endTime == null && s.status in listOf(1, 2)) runningSessionId = s.id
        else if (s?.endTime != null && runningSessionId == s.id) {
            runningSessionId = null
            onReport(s.id)
        }
    }
    BasePage("专注", "一次只做一件事") {
        state.lifecycleError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!state.ready) CircularProgressIndicator()
        else if (s != null && s.status in listOf(1, 2, 3)) {
            Text(state.tasks.firstOrNull { it.id == s.taskId }?.title ?: "自由专注", style = MaterialTheme.typography.titleLarge)
            val displayedSeconds = if (s.type == 1) state.elapsedSeconds else state.remainingSeconds
            Text("%02d:%02d".format(displayedSeconds / 60, displayedSeconds % 60), style = MaterialTheme.typography.displayLarge)
            Text(when (s.status) { 1 -> "正在专注"; 2 -> "已暂停"; else -> "正在休息" })
            if (s.type == 0) LinearProgressIndicator(progress = { 1f - state.remainingSeconds.toFloat() / (if (s.status == 3) s.restSeconds else s.plannedSeconds).coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.status == 1) Button(onClick = viewModel::pause, enabled = !state.busy) { Text("暂停") }
                if (s.status == 2) Button(onClick = viewModel::resume, enabled = !state.busy) { Text("继续") }
                OutlinedButton(onClick = { confirmEnd = true }, enabled = !state.busy) { Text(if (s.status == 3) "结束休息" else "结束专注") }
            }
            if (s.endTime != null) OutlinedButton(onClick = { onReport(s.id) }) { Text("查看专注报告") }
            TextButton(onClick = { showDistractions = true }) { Text("分心 ${s.distractionCount} 次 · ${s.distractionSeconds} 秒 · 查看记录") }
        } else if (state.ready) {
            if (s?.status == 4) {
                Button(onClick = { onReport(s.id) }) { Text("查看专注报告") }
                InfoCard(if (s.type == 0 && s.focusSeconds >= s.plannedSeconds) "本轮专注完成" else "本轮已结束", "已专注 ${s.focusSeconds / 60} 分 ${s.focusSeconds % 60} 秒 · 记录已保存")
                if (s.type == 0 && s.focusSeconds >= s.plannedSeconds) OutlinedButton(onClick = viewModel::rest, enabled = !state.busy) { Text("开始休息") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = !stopwatch, onClick = { stopwatch = false }, label = { Text("番茄钟") })
                FilterChip(selected = stopwatch, onClick = { stopwatch = true }, label = { Text("正向计时") })
            }
            TextButton(onClick = { showThreshold = true }, enabled = !state.busy) { Text("分心判定：${state.settings.distractionThreshold} 秒") }
            if (s?.status == 4) TextButton(onClick = { showDistractions = true }) { Text("分心 ${s.distractionCount} 次 · ${s.distractionSeconds} 秒 · 查看记录") }
            val focusValue = minutes ?: state.settings.pomodoroMinutes.toString()
            val restValue = rest ?: state.settings.breakMinutes.toString()
            if (!stopwatch) {
            OutlinedTextField(value = focusValue, onValueChange = { minutes = it.take(5) }, label = { Text("专注分钟") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(value = restValue, onValueChange = { rest = it.take(5) }, label = { Text("休息分钟") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            Text("时长范围：1–1440 分钟")
            } else Text("从 00:00 开始，直到你主动结束。")
            Box {
                OutlinedButton(onClick = { chooseTask = true }) { Text(state.tasks.firstOrNull { it.id == taskId }?.title ?: "自由专注（选择待办）") }
                DropdownMenu(expanded = chooseTask, onDismissRequest = { chooseTask = false }) {
                    DropdownMenuItem(text = { Text("自由专注") }, onClick = { taskId = null; chooseTask = false })
                    state.tasks.filter { !it.completed }.forEach { task ->
                        DropdownMenuItem(text = { Text(task.title) }, onClick = { taskId = task.id; chooseTask = false })
                    }
                }
            }
            Button(enabled = !state.busy && (stopwatch || (focusValue.toIntOrNull() in 1..1440 && restValue.toIntOrNull() in 1..1440)),
                onClick = {
                    val selected = taskId?.takeIf { id -> state.tasks.any { it.id == id } }
                    if (stopwatch) viewModel.startStopwatch(selected) else viewModel.start(selected, focusValue.toInt(), restValue.toInt())
                }) { Text(if (stopwatch) "开始正向计时" else "开始番茄钟") }
        }
    }
    if (showDistractions) DistractionHistoryDialog(state.distractions) { showDistractions = false }
    if (showThreshold) DistractionThresholdDialog(state.settings.distractionThreshold, onDismiss = { showThreshold = false },
        onSave = { viewModel.setThreshold(it); showThreshold = false })
    if (confirmEnd) AlertDialog(onDismissRequest = { confirmEnd = false }, title = { Text("结束本轮？") }, text = { Text("已完成的专注时间会保留。") },
        confirmButton = { TextButton(enabled = !state.busy, onClick = { confirmEnd = false; viewModel.finish() }) { Text("确认结束") } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("取消") } })
}
