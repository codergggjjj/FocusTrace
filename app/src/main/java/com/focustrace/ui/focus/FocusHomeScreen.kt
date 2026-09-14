package com.focustrace.ui.focus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.ui.components.*
@Composable
fun FocusHomeScreen(viewModel: FocusViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BasePage("专注", "一次只做一件事") {
        StateContent(state) { settings ->
            InfoCard("番茄钟", "${settings.pomodoroMinutes} 分钟专注 · ${settings.breakMinutes} 分钟休息")
            InfoCard("正向计时", "从零开始，按自己的节奏专注。")
            InfoCard("准备好再开始", "专注计时功能将在后续版本开放。")
        }
    }
}
