package com.focustrace.ui.statistics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.ui.components.*
@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BasePage("统计", "看见专注，也理解分心") {
        StateContent(state) { sessions ->
            InfoCard("专注记录", if (sessions.isEmpty()) "暂无专注记录" else "已记录 ${sessions.size} 次专注")
            InfoCard("每一次投入都有迹可循", "完成专注后，你的记录将保存在这里。")
        }
    }
}
