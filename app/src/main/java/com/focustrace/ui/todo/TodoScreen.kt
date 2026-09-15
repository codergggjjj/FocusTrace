package com.focustrace.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.ui.components.*

@Composable
fun TodoScreen(viewModel: TodoViewModel, onStarted: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryOpen by rememberSaveable { mutableStateOf(false) }
    var categoryName by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeader("待办", "把注意力留给重要的事")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editingId = null; editorOpen = true }, enabled = !busy) { Text("创建待办") }
            TextButton(onClick = { categoryOpen = true }, enabled = !busy) { Text("添加分类") }
        }
        StateContent(state) { data ->
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("全部") })
                data.categories.forEach { category ->
                    FilterChip(selected = filter == category.id, onClick = { filter = category.id }, label = { Text(category.name) })
                }
            }
            val tasks = data.tasks.filter { filter == null || it.categoryId == filter }
            Text("待完成 ${tasks.count { !it.completed }} · 已完成 ${tasks.count { it.completed }}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (tasks.isEmpty()) InfoCard("给重要的事留一点时间", "还没有待办，给今天留一点专注的空间。")
            LazyColumn(Modifier.testTag("todo-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(tasks, key = { it.id }) { task ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = task.completed, onCheckedChange = { viewModel.toggle(task) }, enabled = !busy)
                            Column(Modifier.weight(1f).clickable(enabled = !busy) { editingId = task.id; editorOpen = true }) {
                                Text(task.title, style = MaterialTheme.typography.titleMedium, textDecoration = if (task.completed) TextDecoration.LineThrough else null, color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                Text("${data.categories.firstOrNull { it.id == task.categoryId }?.name ?: "未分类"} · ${if (task.timerType == 1) "正向计时" else "番茄钟 · ${task.targetMinutes} 分钟"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (task.completed) Text("已完成")
                            }
                            TextButton(onClick = { deletingId = task.id }, enabled = !busy) { Text("删除") }
                        }
                        if (!task.completed) {
                            Button(onClick = { viewModel.start(task.id, onStarted) }, enabled = !busy,
                                modifier = Modifier.testTag("start-task-${task.id}").align(Alignment.End).padding(end = 12.dp, bottom = 12.dp)) { Text("开始") }
                        }
                    }
                }
            }
            if (editorOpen) {
                val original = data.tasks.firstOrNull { it.id == editingId }
                TaskEditScreen(original, data.categories, busy,
                    onDismiss = { editorOpen = false },
                    onSave = { title, minutes, category, timerType -> viewModel.save(original, title, minutes, category, timerType) { editorOpen = false } })
            }
            data.tasks.firstOrNull { it.id == deletingId }?.let { task ->
                AlertDialog(onDismissRequest = { if (!busy) deletingId = null }, title = { Text("删除待办？") },
                    text = { Text("将删除“${task.title}”，已有专注记录会保留。") },
                    confirmButton = { TextButton(enabled = !busy, onClick = { viewModel.delete(task) { deletingId = null } }) { Text("确认删除") } },
                    dismissButton = { TextButton(enabled = !busy, onClick = { deletingId = null }) { Text("取消") } })
            }
        }
    }
    if (categoryOpen) AlertDialog(onDismissRequest = { if (!busy) categoryOpen = false }, title = { Text("添加分类") },
        text = { OutlinedTextField(value = categoryName, onValueChange = { categoryName = it.take(30) }, label = { Text("分类名称") }, singleLine = true, enabled = !busy) },
        confirmButton = { TextButton(enabled = !busy && categoryName.isNotBlank(), onClick = { viewModel.addCategory(categoryName) { categoryOpen = false; categoryName = "" } }) { Text("保存分类") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { categoryOpen = false }) { Text("取消") } })
    error?.let { message -> AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("操作未完成") }, text = { Text(message) }, confirmButton = { TextButton(onClick = viewModel::clearError) { Text("知道了") } }) }
}
