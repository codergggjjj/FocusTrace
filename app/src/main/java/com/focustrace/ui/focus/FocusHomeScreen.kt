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
fun FocusHomeScreen(viewModel: FocusViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var minutes by rememberSaveable { mutableStateOf<String?>(null) }
    var rest by rememberSaveable { mutableStateOf<String?>(null) }
    var taskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var chooseTask by remember { mutableStateOf(false) }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    val s = state.session
    BasePage("专注", "一次只做一件事") {
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!state.ready) CircularProgressIndicator()
        else if (s != null && s.status in listOf(1, 2, 3)) {
            Text(state.tasks.firstOrNull { it.id == s.taskId }?.title ?: "自由专注", style = MaterialTheme.typography.titleLarge)
            Text("%02d:%02d".format(state.remainingSeconds / 60, state.remainingSeconds % 60), style = MaterialTheme.typography.displayLarge)
            Text(when (s.status) { 1 -> "正在专注"; 2 -> "已暂停"; else -> "正在休息" })
            LinearProgressIndicator(progress = { 1f - state.remainingSeconds.toFloat() / (if (s.status == 3) s.restSeconds else s.plannedSeconds).coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.status == 1) Button(onClick = viewModel::pause, enabled = !state.busy) { Text("暂停") }
                if (s.status == 2) Button(onClick = viewModel::resume, enabled = !state.busy) { Text("继续") }
                OutlinedButton(onClick = { confirmEnd = true }, enabled = !state.busy) { Text(if (s.status == 3) "结束休息" else "结束专注") }
            }
        } else if (state.ready) {
            if (s?.status == 4) {
                InfoCard(if (s.focusSeconds >= s.plannedSeconds) "本轮专注完成" else "本轮已结束", "已专注 ${s.focusSeconds / 60} 分 ${s.focusSeconds % 60} 秒 · 记录已保存")
                if (s.focusSeconds >= s.plannedSeconds) OutlinedButton(onClick = viewModel::rest, enabled = !state.busy) { Text("开始休息") }
            }
            val focusValue = minutes ?: state.settings.pomodoroMinutes.toString()
            val restValue = rest ?: state.settings.breakMinutes.toString()
            OutlinedTextField(value = focusValue, onValueChange = { minutes = it.take(5) }, label = { Text("专注分钟") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(value = restValue, onValueChange = { rest = it.take(5) }, label = { Text("休息分钟") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            Text("时长范围：1–1440 分钟")
            Box {
                OutlinedButton(onClick = { chooseTask = true }) { Text(state.tasks.firstOrNull { it.id == taskId }?.title ?: "自由专注（选择待办）") }
                DropdownMenu(expanded = chooseTask, onDismissRequest = { chooseTask = false }) {
                    DropdownMenuItem(text = { Text("自由专注") }, onClick = { taskId = null; chooseTask = false })
                    state.tasks.filter { !it.completed }.forEach { task ->
                        DropdownMenuItem(text = { Text(task.title) }, onClick = { taskId = task.id; chooseTask = false })
                    }
                }
            }
            Button(enabled = !state.busy && focusValue.toIntOrNull() in 1..1440 && restValue.toIntOrNull() in 1..1440,
                onClick = { viewModel.start(taskId?.takeIf { id -> state.tasks.any { it.id == id } }, focusValue.toInt(), restValue.toInt()) }) { Text("开始番茄钟") }
        }
    }
    if (confirmEnd) AlertDialog(onDismissRequest = { confirmEnd = false }, title = { Text("结束本轮？") }, text = { Text("已完成的专注时间会保留。") },
        confirmButton = { TextButton(enabled = !state.busy, onClick = { confirmEnd = false; viewModel.finish() }) { Text("确认结束") } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("取消") } })
}
