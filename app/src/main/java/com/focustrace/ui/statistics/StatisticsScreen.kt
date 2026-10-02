package com.focustrace.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.statistics.*
import com.focustrace.ui.components.*
import java.time.*

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun StatisticsScreen(viewModel: StatisticsViewModel, onReport: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val heatmap by viewModel.heatmap.collectAsStateWithLifecycle()
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val learningGoals by viewModel.learningGoals.collectAsStateWithLifecycle()
    var editingGoals by rememberSaveable { mutableStateOf(false) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showRecords by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val selectedTaskId by viewModel.selectedTaskId.collectAsStateWithLifecycle()
    val busy by viewModel.saving.collectAsStateWithLifecycle()
    val editError by viewModel.editError.collectAsStateWithLifecycle()
    if (editingGoals) (learningGoals as? LoadState.Ready)?.value?.let { goals ->
        LearningGoalsEditor(goals.daily.targetMinutes, goals.weekly.targetMinutes, busy, editError,
            onDismiss = { editingGoals = false },
            onSave = { daily, weekly -> viewModel.saveLearningGoals(daily, weekly) { editingGoals = false } })
    }
    val taskList = (tasks as? LoadState.Ready)?.value.orEmpty()
    val listState = rememberLazyListState()
    if (showDate) PeriodDateDialog(selection, onDismiss = { showDate = false }) {
        viewModel.selectDate(it); showDate = false
    }
    val summaryState: LoadState<StatisticsSummary> = when (val value = state) {
        LoadState.Loading -> LoadState.Loading
        is LoadState.Error -> value
        is LoadState.Ready -> LoadState.Ready(value.value.current)
    }
    if (showRecords) StudyRecordsDialog(summaryState, onDismiss = { showRecords = false }, onRetry = viewModel::retry,
        onAdd = { editingId = null; viewModel.clearEditError(); editorOpen = true },
        onEdit = { editingId = it; viewModel.clearEditError(); editorOpen = true },
        busy = busy, onDelete = { deletingId = it; viewModel.clearEditError() }) {
        showRecords = false; onReport(it)
    }
    val summary = (state as? LoadState.Ready)?.value?.current
    val editedRecord = summary?.sessions?.firstOrNull { it.id == editingId }
    summary?.sessions?.firstOrNull { it.id == deletingId }?.let { record ->
        DeleteFocusRecordDialog(record, busy, editError, onDismiss = { deletingId = null },
            onConfirm = { viewModel.deleteRecord(record.id) { deletingId = null } })
    }
    if (editorOpen && (editingId == null || editedRecord != null)) ManualRecordDialog(
        editedRecord, tasks, busy, editError, onDismiss = { editorOpen = false },
        onSave = { date, start, end, task, note ->
            viewModel.saveManual(editingId, date, start, end, task, note) { editorOpen = false }
        }, onDelete = { editingId?.let { viewModel.deleteManual(it) { editorOpen = false; editingId = null } } })
    if (showDetails && summary != null) FocusDetailsDialog(summary.totals) { showDetails = false }
    if (showRules) AlertDialog(onDismissRequest = { showRules = false }, title = { Text("统计说明") },
        text = { Text("只有严格超过 5 分钟的已结束专注才会生成记录并计入统计。") },
        confirmButton = { TextButton(onClick = { showRules = false }) { Text("知道了") } })
    LazyColumn(Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .42f))
        .testTag("statistics-list"), state = listState,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(key = "header", contentType = "header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("统计", style = MaterialTheme.typography.headlineLarge)
                    Text("看见投入，也看见变化", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showRules = true }) { Icon(Icons.Outlined.Info, contentDescription = "统计说明") }
            }
        }
        item(key = "period", contentType = "period") {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("统计范围", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        StatisticsPeriod.entries.forEachIndexed { index, period ->
                            SegmentedButton(selected = selection.period == period,
                                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                shape = SegmentedButtonDefaults.itemShape(index, StatisticsPeriod.entries.size), icon = {},
                                onClick = { viewModel.choose(period) }) { Text(period.label) }
                        }
                    }
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .48f)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.shift(-1) }) {
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上一${selection.period.unit}")
                            }
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(when (selection.period) {
                                    StatisticsPeriod.DAY -> selection.range.start.toString()
                                    StatisticsPeriod.MONTH -> "${selection.range.start.year} 年 ${selection.range.start.monthValue} 月"
                                    StatisticsPeriod.YEAR -> "${selection.range.start.year} 年"
                                    StatisticsPeriod.WEEK -> {
                                        val end = selection.range.endExclusive.minusDays(1)
                                        "${selection.range.start.monthValue}/${selection.range.start.dayOfMonth} - ${end.monthValue}/${end.dayOfMonth}"
                                    }
                                }, style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.testTag("statistics-range"))
                                TextButton(onClick = { showDate = true }, contentPadding = PaddingValues(horizontal = 8.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    Text(when (selection.period) {
                                        StatisticsPeriod.MONTH -> "选择月份"
                                        StatisticsPeriod.YEAR -> "选择年份"
                                        else -> "选择日期"
                                    })
                                }
                            }
                            IconButton(onClick = { viewModel.shift(1) }, enabled = selection.canNext) {
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下一${selection.period.unit}")
                            }
                        }
                    }
                    if (taskList.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                        StatisticsTaskFilter(taskList, selectedTaskId, viewModel::selectTask)
                    }
                }
            }
        }
        when (val value = state) {
            LoadState.Loading -> item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            is LoadState.Error -> item(key = "error") { Text(value.message); TextButton(onClick = viewModel::retry) { Text("重试") } }
            is LoadState.Ready -> {
                item(key = "overview", contentType = "overview") {
                    StudyOverview(value.value.current, value.value.previous,
                        onRecords = { showRecords = true }, onDetails = { showDetails = true })
                }
                item(key = "goals", contentType = "goals") {
                    when (val goals = learningGoals) {
                        is LoadState.Ready -> LearningGoalsCard(goals.value) {
                            viewModel.clearEditError(); editingGoals = true
                        }
                        is LoadState.Error -> TextButton(onClick = viewModel::retry) { Text("学习目标加载失败，点击重试") }
                        LoadState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                item(key = "chart", contentType = "chart") { StudyTrend(value.value.current, selection.period) }
                item(key = "distribution", contentType = "distribution") { TaskDistribution(value.value.current) }
                item(key = "rhythm", contentType = "rhythm") { FocusRhythm(value.value.current) }
                item(key = "heatmap", contentType = "heatmap") {
                    when (val calendar = heatmap) {
                        is LoadState.Ready -> FocusHeatmap(
                            summary = calendar.value,
                            selectedDate = selection.range.start.takeIf { selection.period == StatisticsPeriod.DAY },
                            onSelectDay = viewModel::viewDay)
                        is LoadState.Error -> TextButton(onClick = viewModel::retry) { Text("热力图加载失败，点击重试") }
                        LoadState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                item(key = "habits", contentType = "habits") {
                    when (val habitState = habits) {
                        is LoadState.Ready -> HabitOverview(habitState.value)
                        is LoadState.Error -> TextButton(onClick = viewModel::retry) { Text("习惯统计加载失败，点击重试") }
                        LoadState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                item(key = "records", contentType = "records") {
                    RecentRecordsSection(value.value.current,
                        onAdd = { editingId = null; viewModel.clearEditError(); editorOpen = true },
                        onAll = { showRecords = true },
                        onEdit = { editingId = it; viewModel.clearEditError(); editorOpen = true },
                        onReport = onReport)
                }
                item(key = "current", contentType = "current") {
                    TextButton(onClick = viewModel::current, modifier = Modifier.fillMaxWidth()) { Text("回到当前${selection.period.unit}") }
                }
            }
        }
    }
}

@Composable
private fun StatisticsTaskFilter(tasks: List<com.focustrace.data.local.entity.TaskEntity>, selectedTaskId: Long?,
    onSelect: (Long?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("筛选待办", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            item(key = "all") {
                FilterChip(selected = selectedTaskId == null, onClick = { onSelect(null) },
                    label = { Text("全部") }, modifier = Modifier.testTag("statistics-filter-all"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer), border = null)
            }
            items(tasks, key = { it.id }, contentType = { "task-filter" }) { task ->
                FilterChip(selected = selectedTaskId == task.id, onClick = { onSelect(task.id) },
                    label = { Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.widthIn(max = 180.dp).testTag("statistics-filter-${task.id}"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer), border = null)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PeriodDateDialog(selection: StatisticsSelection, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val month = selection.period == StatisticsPeriod.MONTH
    val year = selection.period == StatisticsPeriod.YEAR
    var manual by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    // The calendar has a minimum width; use the existing text form on compact displays.
    val compact = configuration.screenWidthDp < 360 || configuration.screenHeightDp < 600 || configuration.fontScale > 1.2f
    if (!month && !year && !manual && !compact) {
        val today = LocalDate.now()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = selection.range.start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = remember(today) { object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
                override fun isSelectableYear(year: Int) = year <= today.year
            } })
        DatePickerDialog(onDismissRequest = onDismiss,
            confirmButton = { Button(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text("查看") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { manual = true }) { Text("输入日期") }
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
            }) {
            DatePicker(state = picker, modifier = Modifier.weight(1f, fill = false),
                title = { Text("选择日期", Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
        return
    }
    var input by rememberSaveable { mutableStateOf(when {
        year -> selection.range.start.year.toString()
        month -> YearMonth.from(selection.range.start).toString()
        else -> selection.range.start.toString()
    }) }
    val date = runCatching { when {
        year -> LocalDate.of(input.trim().toInt(), 1, 1)
        month -> YearMonth.parse(input.trim()).atDay(1)
        else -> LocalDate.parse(input.trim())
    } }.getOrNull()
    val valid = date != null && date <= LocalDate.now()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(when { year -> "选择年份"; month -> "选择月份"; else -> "选择日期" }) },
        text = {
            OutlinedTextField(value = input, onValueChange = { input = it.take(10) }, singleLine = true,
                label = { Text(when { year -> "年份（yyyy）"; month -> "月份（yyyy-MM）"; else -> "日期（yyyy-MM-dd）" }) },
                isError = !valid, supportingText = { Text(when {
                    year -> "例如 2026，不可选择未来年份"
                    month -> "例如 2026-09，不可选择未来月份"
                    else -> "例如 2026-09-15，不可选择未来日期"
                }) })
        },
        confirmButton = { TextButton(enabled = valid, onClick = { date?.let(onConfirm) }) { Text("查看") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
