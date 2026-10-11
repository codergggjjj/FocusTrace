package com.focustrace.ui.todo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focustrace.data.local.entity.TaskEntity
import com.focustrace.data.repository.TaskMove

@Composable
internal fun TaskOrderDialog(tasks: List<TaskEntity>, busy: Boolean, onMove: (Long, TaskMove) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("调整待办顺序") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).testTag("todo-order-list")) {
                itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(task.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { onMove(task.id, TaskMove.UP) }, enabled = !busy && index > 0,
                            modifier = Modifier.size(48.dp).testTag("task-order-up-${task.id}")) {
                            Icon(Icons.Outlined.ArrowUpward, contentDescription = "上移 ${task.title}")
                        }
                        IconButton(onClick = { onMove(task.id, TaskMove.DOWN) }, enabled = !busy && index < tasks.lastIndex,
                            modifier = Modifier.size(48.dp).testTag("task-order-down-${task.id}")) {
                            Icon(Icons.Outlined.ArrowDownward, contentDescription = "下移 ${task.title}")
                        }
                        IconButton(onClick = { onMove(task.id, TaskMove.TOP) }, enabled = !busy && index > 0,
                            modifier = Modifier.size(48.dp).testTag("task-order-top-${task.id}")) {
                            Icon(Icons.Outlined.VerticalAlignTop, contentDescription = "置顶 ${task.title}")
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } })
}
