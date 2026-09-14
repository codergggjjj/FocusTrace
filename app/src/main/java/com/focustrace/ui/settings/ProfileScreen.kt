package com.focustrace.ui.settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.data.datastore.ThemeMode
import com.focustrace.ui.components.*
@Composable
fun ProfileScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BasePage("我的", "找到适合自己的专注节奏") {
        StateContent(state) { settings ->
            InfoCard("专注设置", "番茄时长：${settings.pomodoroMinutes} 分钟\n休息时长：${settings.breakMinutes} 分钟\n分心判定：${settings.distractionThreshold} 秒")
            InfoCard("外观", when (settings.darkMode) { ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色" })
        }
        InfoCard("FocusTrace · 专迹", "记录每一次专注，也看见每一次分心。\n数据保存在此设备，无需登录。")
    }
}
