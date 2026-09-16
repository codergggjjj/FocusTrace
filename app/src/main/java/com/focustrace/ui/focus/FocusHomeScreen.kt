package com.focustrace.ui.focus

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.ui.components.*

@Composable
fun FocusHomeScreen(viewModel: FocusViewModel, onExit: () -> Unit, onReport: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    BackHandler(onBack = onExit)
    BasePage("专注", "一次只做一件事") {
        TextButton(onClick = onExit) { Text("返回待办") }
        state.lifecycleError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!state.ready) CircularProgressIndicator()
        else if (s != null && s.status in listOf(1, 2, 3)) {
            LiveTimer(viewModel, s, state.tasks.firstOrNull { it.id == s.taskId }?.title ?: s.taskTitleSnapshot ?: "自由专注")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                if (s.status == 1) Button(onClick = viewModel::pause, enabled = !state.busy) { Text("暂停") }
                if (s.status == 2) Button(onClick = viewModel::resume, enabled = !state.busy) { Text("继续") }
                OutlinedButton(onClick = { confirmEnd = true }, enabled = !state.busy) { Text(if (s.status == 3) "结束休息" else "结束专注") }
            }
            if (s.endTime != null) OutlinedButton(onClick = { onReport(s.id) }) { Text("查看专注报告") }
            TextButton(onClick = { showDistractions = true }) { Text("分心 ${s.distractionCount} 次 · ${s.distractionSeconds} 秒 · 查看记录") }
        } else if (state.ready) {
            if (s != null && s.endTime != null) {
                InfoCard("本轮已结束", "专注记录已保存")
                Button(onClick = { onReport(s.id) }) { Text("查看专注报告") }
            } else Text("当前没有进行中的计时，请返回待办选择任务开始。")
        }
        TextButton(onClick = { showThreshold = true }, enabled = !state.busy) { Text("分心判定：${state.settings.distractionThreshold} 秒") }
    }
    if (showDistractions) DistractionHistoryDialog(state.distractions) { showDistractions = false }
    if (showThreshold) DistractionThresholdDialog(state.settings.distractionThreshold, onDismiss = { showThreshold = false },
        onSave = { viewModel.setThreshold(it); showThreshold = false })
    if (confirmEnd) AlertDialog(onDismissRequest = { confirmEnd = false }, title = { Text("结束本轮？") }, text = { Text("已完成的专注时间会保留。") },
        confirmButton = { TextButton(enabled = !state.busy, onClick = { confirmEnd = false; viewModel.finish() }) { Text("确认结束") } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("取消") } })
}

@Composable
private fun LiveTimer(viewModel: FocusViewModel, session: com.focustrace.data.local.entity.FocusSessionEntity, title: String) {
    val timer by viewModel.timer.collectAsStateWithLifecycle()
    TimerDisplay(title = title, seconds = if (session.type == 1) timer.elapsedSeconds else timer.remainingSeconds,
        status = when (session.status) { 1 -> "正在专注"; 2 -> "已暂停"; else -> "正在休息" },
        progress = if (session.type == 0) (1f - timer.remainingSeconds.toFloat() /
            (if (session.status == 3) session.restSeconds else session.plannedSeconds).coerceAtLeast(1)).coerceIn(0f, 1f) else null)
}

@Composable
private fun TimerDisplay(title: String, seconds: Long, status: String, progress: Float?) {
    TraceCard {
        Text(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        // The ring is decorative; the timer and status remain readable with large system fonts.
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            if (progress != null && LocalDensity.current.fontScale <= 1.3f) CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(220.dp),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer, strokeWidth = 4.dp)
            Column(Modifier.padding(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%02d:%02d".format(seconds / 60, seconds % 60),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light))
                Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
