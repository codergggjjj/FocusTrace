package com.focustrace.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.FocusTraceApplication
import com.focustrace.focus.MusicSource

@Composable
internal fun ExternalMusicSettings() {
    val context = LocalContext.current
    val player = (context.applicationContext as FocusTraceApplication).container.externalMusic
    val state by player.state.collectAsStateWithLifecycle()
    var launchError by remember(state.source) { mutableStateOf<String?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        player.refresh()
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("播放来源", style = MaterialTheme.typography.titleSmall)
        MusicSource.entries.forEach { source ->
            val selected = state.source == source
            val onSelect = { player.select(source) }
            if (selected) {
                OutlinedButton(onClick = onSelect, modifier = Modifier.fillMaxWidth().testTag("music-source-${source.name}")) {
                    Text("${source.label} · 已选")
                }
            } else {
                TextButton(onClick = onSelect, modifier = Modifier.fillMaxWidth().testTag("music-source-${source.name}")) {
                    Text(source.label)
                }
            }
        }
        if (state.source != MusicSource.LOCAL) {
            Text(when {
                !state.installed -> "手机上还没有安装${state.source.label}。"
                !state.granted -> "允许通知访问后，专迹即可识别播放器并控制音乐。专迹不会保存通知内容。"
                state.connected -> "已连接${state.source.label}，可在专注页控制播放。"
                else -> "开始专注前，先打开${state.source.label}选歌并播放。"
            }, style = MaterialTheme.typography.bodySmall)
            if (state.installed && !state.granted) {
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } catch (_: ActivityNotFoundException) {
                        launchError = "请在手机设置中开启专迹的通知访问权限。"
                    }
                }) { Text("开启通知访问") }
            }
            if (state.installed) {
                TextButton(onClick = {
                    val intent = state.source.packageName?.let(context.packageManager::getLaunchIntentForPackage)
                    try {
                        if (intent != null) context.startActivity(intent)
                        else launchError = "无法打开${state.source.label}。"
                    } catch (_: ActivityNotFoundException) {
                        launchError = "无法打开${state.source.label}。"
                        player.refresh()
                    }
                }) { Text("打开${state.source.label}选歌") }
            }
            launchError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
