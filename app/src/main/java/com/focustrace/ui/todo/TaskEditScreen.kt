package com.focustrace.ui.todo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.focustrace.data.local.entity.*

@Composable
fun TaskEditScreen(task: TaskEntity?, categories: List<CategoryEntity>, busy: Boolean, defaultMinutes: Int,
    onDismiss: () -> Unit, onSave: (String, Int, Long?, Int) -> Unit) {
    var title by rememberSaveable(task?.id) { mutableStateOf(task?.title ?: "") }
    var minutes by rememberSaveable(task?.id) { mutableStateOf(task?.targetMinutes?.toString() ?: defaultMinutes.toString()) }
    var categoryId by rememberSaveable(task?.id) { mutableStateOf(task?.categoryId) }
    var timerType by rememberSaveable(task?.id) { mutableIntStateOf(task?.timerType ?: 0) }
    var expanded by remember { mutableStateOf(false) }
    val duration = minutes.toIntOrNull()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (task == null) "创建待办" else "编辑待办") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(100) }, label = { Text("待办名称") }, singleLine = true, enabled = !busy)
                Text("计时方式", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = timerType == 0, onClick = { timerType = 0 }, enabled = !busy, label = { Text("番茄钟") })
                    FilterChip(selected = timerType == 1, onClick = { timerType = 1 }, enabled = !busy, label = { Text("正向计时") })
                }
                if (timerType == 1) Text("从零开始计时，手动结束，无需设置目标时长。",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else OutlinedTextField(value = minutes, onValueChange = { minutes = it.take(5) }, label = { Text("目标分钟数") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy,
                    isError = duration == null || duration !in 1..1440,
                    supportingText = { Text("请输入 1–1440 分钟") })
                Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = !busy) { Text(categories.firstOrNull { it.id == categoryId }?.name ?: "选择分类") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("未分类") }, onClick = { categoryId = null; expanded = false })
                        categories.forEach { category -> DropdownMenuItem(text = { Text(category.name) }, onClick = { categoryId = category.id; expanded = false }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy && title.isNotBlank() && (timerType == 1 || (duration != null && duration in 1..1440)),
            onClick = { onSave(title, duration?.takeIf { it in 1..1440 } ?: 25, categoryId, timerType) }) { Text(if (busy) "保存中…" else "保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } })
}
