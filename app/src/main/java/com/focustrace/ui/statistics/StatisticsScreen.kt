package com.focustrace.ui.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showRecords by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val busy by viewModel.saving.collectAsStateWithLifecycle()
    val editError by viewModel.editError.collectAsStateWithLifecycle()
    if (showDate) PeriodDateDialog(selection, onDismiss = { showDate = false }) {
        viewModel.selectDate(it); showDate = false
    }
    if (showRecords) StudyRecordsDialog(state, onDismiss = { showRecords = false }, onRetry = viewModel::retry,
        onAdd = { editingId = null; viewModel.clearEditError(); editorOpen = true },
        onEdit = { editingId = it; viewModel.clearEditError(); editorOpen = true }) {
        showRecords = false; onReport(it)
    }
    val summary = (state as? LoadState.Ready)?.value
    val editedRecord = summary?.sessions?.firstOrNull { it.id == editingId }
    if (editorOpen && (editingId == null || editedRecord != null)) ManualRecordDialog(
        editedRecord, tasks, busy, editError, onDismiss = { editorOpen = false },
        onSave = { date, start, end, task, note ->
            viewModel.saveManual(editingId, date, start, end, task, note) { editorOpen = false }
        }, onDelete = { editingId?.let { viewModel.deleteManual(it) { editorOpen = false; editingId = null } } })
    if (showDetails && summary != null) FocusDetailsDialog(summary.totals) { showDetails = false }
    if (showRules) AlertDialog(onDismissRequest = { showRules = false }, title = { Text("统计说明") },
        text = { Text("学习时间为有效专注时长，不含暂停和分心。记录按开始日归属；日视图的时段按开始小时归属，不代表实际逐小时分配。周一为每周起点。\n\n专注率 = 有效专注 ÷（有效专注 + 分心时间）。首次分心均值仅统计发生过分心的记录。") },
        confirmButton = { TextButton(onClick = { showRules = false }) { Text("知道了") } })
    LazyColumn(Modifier.fillMaxSize().testTag("statistics-list"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item(key = "header", contentType = "header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("统计", style = MaterialTheme.typography.headlineLarge)
                    Text("看见专注，也理解分心", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showRules = true }) { Icon(Icons.Outlined.Info, contentDescription = "统计说明") }
            }
        }
        item(key = "period", contentType = "period") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                StatisticsPeriod.entries.forEachIndexed { index, period ->
                    SegmentedButton(selected = selection.period == period,
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        shape = SegmentedButtonDefaults.itemShape(index, StatisticsPeriod.entries.size), icon = {},
                        onClick = { viewModel.choose(period) }) { Text(period.label) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.shift(-1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上一${selection.period.unit}") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (selection.period == StatisticsPeriod.DAY) selection.range.start.toString()
                        else if (selection.period == StatisticsPeriod.MONTH) "${selection.range.start.year} 年 ${selection.range.start.monthValue} 月"
                        else "${selection.range.start} 至 ${selection.range.endExclusive.minusDays(1)}",
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("statistics-range"))
                    TextButton(onClick = { showDate = true }, contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        Text(if (selection.period == StatisticsPeriod.MONTH) "选择月份" else "选择日期")
                    }
                }
                IconButton(onClick = { viewModel.shift(1) }, enabled = selection.canNext) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下一${selection.period.unit}") }
            }
        }
        when (val value = state) {
            LoadState.Loading -> item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            is LoadState.Error -> item(key = "error") { Text(value.message); TextButton(onClick = viewModel::retry) { Text("重试") } }
            is LoadState.Ready -> {
                item(key = "overview", contentType = "overview") {
                    StudyOverview(value.value.totals, selection.period, onRecords = { showRecords = true }, onDetails = { showDetails = true })
                }
                item(key = "chart", contentType = "chart") { StudyTrend(value.value, selection.period) }
                item(key = "heatmap", contentType = "heatmap") {
                    when (val calendar = heatmap) {
                        is LoadState.Ready -> FocusHeatmap(calendar.value) { viewModel.viewDay(it); showRecords = true }
                        is LoadState.Error -> TextButton(onClick = viewModel::retry) { Text("热力图加载失败，点击重试") }
                        LoadState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                item(key = "current", contentType = "current") {
                    TextButton(onClick = viewModel::current, modifier = Modifier.fillMaxWidth()) { Text("回到${selection.period.label}") }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PeriodDateDialog(selection: StatisticsSelection, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val month = selection.period == StatisticsPeriod.MONTH
    var manual by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    // The calendar has a minimum width; use the existing text form on compact displays.
    val compact = configuration.screenWidthDp < 360 || configuration.screenHeightDp < 600 || configuration.fontScale > 1.2f
    if (!month && !manual && !compact) {
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
    var input by rememberSaveable { mutableStateOf(if (month) YearMonth.from(selection.range.start).toString() else selection.range.start.toString()) }
    val date = runCatching { if (month) YearMonth.parse(input.trim()).atDay(1) else LocalDate.parse(input.trim()) }.getOrNull()
    val valid = date != null && date <= LocalDate.now()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (month) "选择月份" else "选择日期") },
        text = {
            OutlinedTextField(value = input, onValueChange = { input = it.take(10) }, singleLine = true,
                label = { Text(if (month) "月份（yyyy-MM）" else "日期（yyyy-MM-dd）") },
                isError = !valid, supportingText = { Text(if (month) "例如 2026-09，不可选择未来月份" else "例如 2026-09-15，不可选择未来日期") })
        },
        confirmButton = { TextButton(enabled = valid, onClick = { date?.let(onConfirm) }) { Text("查看") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
