package com.focustrace.ui.todo
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustrace.ui.components.*
@Composable
fun TodoScreen(viewModel: TodoViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BasePage("待办", "把注意力留给重要的事") {
        StateContent(state) { tasks ->
            InfoCard("待办清单", if (tasks.isEmpty()) "还没有待办，给今天留一点专注的空间。" else "共有 ${tasks.size} 项待办")
            tasks.forEach { Text(it.title) }
        }
        Text("记录每一次专注，也看见每一次分心。")
    }
}
