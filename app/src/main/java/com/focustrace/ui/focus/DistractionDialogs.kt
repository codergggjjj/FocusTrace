package com.focustrace.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.focustrace.data.local.entity.DistractionEventEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DistractionThresholdDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var seconds by rememberSaveable { mutableStateOf(current.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("分心判定") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("严格" to 0, "普通" to 3, "宽松" to 10).forEach { (name, value) ->
                FilterChip(selected = seconds.toIntOrNull() == value, onClick = { seconds = value.toString() }, label = { Text("$name · $value 秒") })
            }
            OutlinedTextField(value = seconds, onValueChange = { seconds = it.take(6) }, label = { Text("自定义秒数") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                isError = seconds.toIntOrNull() !in 0..86400, supportingText = { Text("范围 0–86400 秒，离开达到阈值才记为分心。") })
            Text("专注时切换应用或锁屏都视为离开；暂停和休息不计分心。")
        }
    }, confirmButton = { TextButton(enabled = seconds.toIntOrNull() in 0..86400, onClick = { onSave(seconds.toInt()) }) { Text("保存判定时间") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun DistractionHistoryDialog(events: List<DistractionEventEntity>, onDismiss: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("本轮分心记录") }, text = {
        if (events.isEmpty()) Text("本轮暂无分心记录。")
        else LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(events, key = { it.id }) { event ->
                Column {
                    Text("${formatter.format(Instant.ofEpochMilli(event.backgroundTime))} → ${formatter.format(Instant.ofEpochMilli(event.foregroundTime))}")
                    Text("离开 ${event.durationSeconds / 60} 分 ${event.durationSeconds % 60} 秒")
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}
