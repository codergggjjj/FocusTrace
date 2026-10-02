package com.focustrace.ui.todo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.data.local.entity.TaskEntity
import com.focustrace.ui.components.*

private val TodoPagePadding = 20.dp
private val TodoSectionSpacing = 16.dp
private val TodoCardSpacing = 10.dp
private val TodoCardRadius = 20.dp

private enum class TodoFilter { PENDING, COMPLETED }

@Composable
fun TodoScreen(viewModel: TodoViewModel, onReport: (Long) -> Unit, onStudy: (Long) -> Unit, onStarted: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var recordTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var filter by rememberSaveable { mutableStateOf(TodoFilter.PENDING) }
    val listState = rememberLazyListState()
    val expandedFab by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 80 } }
    val readyData = (state as? LoadState.Ready)?.value
    val completedCount = remember(readyData?.tasks) { readyData?.tasks?.count { it.completed } ?: 0 }
    val visibleTasks = remember(readyData?.tasks, filter) {
        readyData?.tasks.orEmpty().filter { if (filter == TodoFilter.PENDING) !it.completed else it.completed }
    }
    val openNewTask = remember(busy) { { if (!busy) { editingId = null; editorOpen = true } } }
    val onEditTask = remember { { id: Long -> editingId = id; editorOpen = true } }
    val onToggleTask = remember(viewModel) { { task: TaskEntity -> viewModel.toggle(task) } }
    val onStartTask = remember(viewModel, onStarted) { { id: Long -> viewModel.start(id, onStarted) } }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), floatingActionButton = {
        ExtendedFloatingActionButton(onClick = openNewTask, expanded = expandedFab,
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Outlined.Add, contentDescription = "创建待办") }, text = { Text("新建待办") })
    }) { padding ->
        LazyColumn(state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).testTag("todo-list"),
            contentPadding = PaddingValues(start = TodoPagePadding, end = TodoPagePadding, top = TodoPagePadding, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(TodoCardSpacing)) {
            item(key = "header", contentType = "header") {
                PageHeader("待办", "把注意力留给重要的事")
                Spacer(Modifier.height(6.dp))
            }
            when (val current = state) {
                LoadState.Loading -> item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                is LoadState.Error -> item(key = "error") { Text(current.message, color = MaterialTheme.colorScheme.error) }
                is LoadState.Ready -> {
                    val data = current.value
                    val completed = completedCount
                    val pending = data.tasks.size - completedCount
                    item(key = "today", contentType = "summary") {
                        TodayOverview(pending, completed, data.todayFocusSeconds)
                        Spacer(Modifier.height(6.dp))
                    }
                    data.activeSession?.let { session ->
                        item(key = "active", contentType = "summary") {
                            FilledTonalButton(onClick = onStarted, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.Timer, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("返回计时 · ${session.taskTitleSnapshot ?: "自由专注"}", maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    data.reportId?.let { id ->
                        item(key = "report", contentType = "summary") {
                            FocusReportEntry { onReport(id) }
                        }
                    }
                    item(key = "filters", contentType = "filters") {
                        TodoFilters(filter, pending, completed) { filter = it }
                        Spacer(Modifier.height(6.dp))
                    }
                    if (visibleTasks.isEmpty()) item(key = "empty-${filter.name}", contentType = "empty") {
                        TodoEmptyState(filter, data.tasks.isEmpty(), openNewTask)
                    }
                    items(visibleTasks, key = { it.id }, contentType = { "task" }) { task ->
                        TaskCard(task = task, todaySessions = data.todayTaskSessions[task.id] ?: 0,
                            active = data.activeSession?.taskId == task.id, busy = busy,
                            onEdit = onEditTask, onToggle = onToggleTask, onStart = onStartTask)
                    }
                }
            }
        }
    }

    val data = readyData
    if (data != null) {
        if (editorOpen) {
            val original = data.tasks.firstOrNull { it.id == editingId }
            TaskEditScreen(original, busy, data.defaultMinutes,
                onAddRecord = { recordTaskId = original?.id },
                onStudy = { original?.id?.let(onStudy) },
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
                confirmButton = { TextButton(enabled = !busy,
                    onClick = { viewModel.delete(task) { deletingId = null; editorOpen = false; editingId = null } },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("确认删除") } },
                dismissButton = { TextButton(enabled = !busy, onClick = { deletingId = null }) { Text("取消") } })
        }
    }
    error?.let { message ->
        AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("操作未完成") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("知道了") } })
    }
}

@Composable
private fun TodayOverview(pending: Int, completed: Int, focusSeconds: Long) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(TodoCardRadius),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .58f)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("今日概览", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewItem("$pending", "待完成", Modifier.weight(1f))
                OverviewItem("$completed", "已完成", Modifier.weight(1f))
                OverviewItem(compactTodayDuration(focusSeconds), "今日专注", Modifier.weight(1.25f), emphasized = true)
            }
        }
    }
}

@Composable
private fun OverviewItem(value: String, label: String, modifier: Modifier, emphasized: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .68f))
    }
}

private fun compactTodayDuration(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
    seconds >= 60 -> "${seconds / 60}m"
    else -> "0m"
}

@Composable
private fun FocusReportEntry(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text("专注报告", style = MaterialTheme.typography.titleMedium)
            Text("查看最近一次专注详情", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = "查看专注报告",
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TodoFilters(selected: TodoFilter, pending: Int, completed: Int, onSelect: (TodoFilter) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == TodoFilter.PENDING, onClick = { onSelect(TodoFilter.PENDING) },
            label = { Text("待完成 $pending") }, modifier = Modifier.testTag("todo-filter-pending"),
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer), border = null)
        FilterChip(selected = selected == TodoFilter.COMPLETED, onClick = { onSelect(TodoFilter.COMPLETED) },
            label = { Text("已完成 $completed") }, modifier = Modifier.testTag("todo-filter-completed"),
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer), border = null)
    }
}

@Composable
private fun TodoEmptyState(filter: TodoFilter, noTasks: Boolean, onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = .72f), modifier = Modifier.size(38.dp))
        Text(when {
            noTasks -> "今天还没有待办"
            filter == TodoFilter.PENDING -> "今天的待办都完成了"
            else -> "还没有已完成的待办"
        }, style = MaterialTheme.typography.titleMedium)
        Text(if (filter == TodoFilter.COMPLETED) "完成任务后会显示在这里" else "添加一个任务，开始今天的专注吧",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (filter == TodoFilter.PENDING) TextButton(onClick = onAdd) { Text("新建待办") }
    }
}

/** Stable callbacks and lazy keys preserve skipping for unchanged task rows. */
@Composable
private fun TaskCard(task: TaskEntity, todaySessions: Int, active: Boolean, busy: Boolean,
    onEdit: (Long) -> Unit, onToggle: (TaskEntity) -> Unit, onStart: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(enabled = !busy, onClickLabel = "编辑待办") { onEdit(task.id) },
        shape = RoundedCornerShape(TodoCardRadius),
        colors = CardDefaults.cardColors(containerColor = if (task.completed)
            MaterialTheme.colorScheme.surface.copy(alpha = .72f) else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(modifier = Modifier.testTag("complete-task-${task.id}"), checked = task.completed,
                onCheckedChange = { onToggle(task) }, enabled = !busy)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                val mode = if (task.timerType == 1) "正向计时" else "番茄钟 · ${task.targetMinutes} 分钟"
                val progress = when {
                    task.completed -> "已完成"
                    active -> "进行中"
                    todaySessions > 0 -> "今日专注 $todaySessions 次"
                    else -> "尚未开始"
                }
                Text("$mode · $progress", style = MaterialTheme.typography.bodySmall,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!task.completed) {
                if (active) FilledTonalIconButton(onClick = { onStart(task.id) }, enabled = !busy,
                    modifier = Modifier.size(44.dp).testTag("start-task-${task.id}")) {
                    Icon(Icons.Outlined.Timer, contentDescription = "返回专注：${task.title}")
                } else FilledIconButton(onClick = { onStart(task.id) }, enabled = !busy,
                    modifier = Modifier.size(44.dp).testTag("start-task-${task.id}")) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "开始专注：${task.title}")
                }
            }
        }
    }
}
