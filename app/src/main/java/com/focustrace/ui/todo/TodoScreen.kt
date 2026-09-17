package com.focustrace.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.data.local.entity.TaskEntity
import com.focustrace.ui.components.*

@Composable
fun TodoScreen(viewModel: TodoViewModel, onReport: (Long) -> Unit, onStarted: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var recordTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    val onEditTask = remember { { id: Long -> editingId = id; editorOpen = true } }
    val onToggleTask = remember(viewModel) { { task: TaskEntity -> viewModel.toggle(task) } }
    val onStartTask = remember(viewModel, onStarted) { { id: Long -> viewModel.start(id, onStarted) } }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader("待办", "把注意力留给重要的事")
        Button(onClick = { editingId = null; editorOpen = true }, enabled = !busy) { Text("创建待办") }
        StateContent(state) { data ->
            data.reportId?.let { id -> TextButton(onClick = { onReport(id) }) { Text("查看专注报告") } }
            data.activeSession?.let { session ->
                OutlinedButton(onClick = onStarted, modifier = Modifier.fillMaxWidth()) {
                    Text("返回计时 · ${session.taskTitleSnapshot ?: "自由专注"}")
                }
            }
            val tasks = data.tasks
            val completedCount = remember(tasks) { tasks.count { it.completed } }
            Text("待完成 ${tasks.size - completedCount} · 已完成 $completedCount", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (tasks.isEmpty()) InfoCard("给重要的事留一点时间", "还没有待办，给今天留一点专注的空间。")
            LazyColumn(Modifier.testTag("todo-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(tasks, key = { it.id }, contentType = { "task" }) { task ->
                    TaskCard(task, busy, onEditTask, onToggleTask, onStartTask)
                }
            }
            if (editorOpen) {
                val original = data.tasks.firstOrNull { it.id == editingId }
                TaskEditScreen(original, busy, data.defaultMinutes,
                    onAddRecord = { recordTaskId = original?.id },
                    onDelete = { deletingId = original?.id },
                    onDismiss = { editorOpen = false },
                    onSave = { title, minutes, timerType -> viewModel.save(original, title, minutes, timerType) { editorOpen = false } })
            }
            recordTaskId?.let { taskId ->
                com.focustrace.ui.statistics.ManualRecordDialog(
                    record = null, tasks = LoadState.Ready(data.tasks), busy = busy, error = null,
                    defaultTaskId = taskId, onDismiss = { recordTaskId = null },
                    onSave = { date, start, end, selectedTask, note ->
                        viewModel.addManual(date, start, end, selectedTask, note) { recordTaskId = null }
                    }, onDelete = {})
            }
            data.tasks.firstOrNull { it.id == deletingId }?.let { task ->
                AlertDialog(onDismissRequest = { if (!busy) deletingId = null }, title = { Text("删除待办？") },
                    text = { Text("将删除“${task.title}”，已有专注记录会保留。") },
                    confirmButton = { TextButton(enabled = !busy, onClick = { viewModel.delete(task) { deletingId = null; editorOpen = false; editingId = null } }) { Text("确认删除") } },
                    dismissButton = { TextButton(enabled = !busy, onClick = { deletingId = null }) { Text("取消") } })
            }
        }
    }
    error?.let { message -> AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("操作未完成") }, text = { Text(message) }, confirmButton = { TextButton(onClick = viewModel::clearError) { Text("知道了") } }) }
}

/** Stable row inputs let unchanged visible tasks skip composition when page state changes. */
@Composable
private fun TaskCard(task: TaskEntity, busy: Boolean,
    onEdit: (Long) -> Unit, onToggle: (TaskEntity) -> Unit, onStart: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(modifier = Modifier.testTag("complete-task-${task.id}"), checked = task.completed, onCheckedChange = { onToggle(task) }, enabled = !busy)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).clickable(enabled = !busy) { onEdit(task.id) }.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                Text(task.title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold), textDecoration = if (task.completed) TextDecoration.LineThrough else null, color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    }
                    if (!task.completed) {
                        Button(onClick = { onStart(task.id) }, enabled = !busy,
                            modifier = Modifier.testTag("start-task-${task.id}")) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("开始")
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().clickable(enabled = !busy) { onEdit(task.id) }) {
                Text(if (task.timerType == 1) "正向计时" else "番茄钟 · ${task.targetMinutes} 分钟", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (task.completed) Text("已完成")
                }
            }
        }
    }
}
