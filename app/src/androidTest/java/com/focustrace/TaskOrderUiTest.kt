package com.focustrace

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.focustrace.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class TaskOrderUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun movesTaskAndRestoresSortingDialogAfterRecreation() {
        val container = ApplicationProvider.getApplicationContext<FocusTraceApplication>().container
        val ids = mutableListOf<Long>()
        try {
            runBlocking {
                repeat(2) { ids += container.taskRepository.insert(TaskEntity(title="排序验证 $it",targetMinutes=25,createdAt=System.currentTimeMillis(),updatedAt=System.currentTimeMillis())) }
            }
            compose.onNodeWithTag("todo-list").performScrollToNode(hasTestTag("todo-sort"))
            compose.onNodeWithTag("todo-sort").performClick()
            compose.onNodeWithTag("task-order-up-${ids.first()}").performClick()
            compose.waitUntil(5000) { runBlocking { container.taskRepository.allTasks.first().firstOrNull { !it.completed }?.id == ids.first() } }
            compose.onNodeWithTag("task-order-up-${ids.first()}").assertIsNotEnabled()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("task-order-up-${ids.first()}").assertIsNotEnabled()
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithTag("todo-filter-completed").performClick()
            compose.onNodeWithTag("todo-sort").assertDoesNotExist()
        } finally {
            runBlocking { ids.forEach { id -> container.database.taskDao().getTask(id)?.let { container.database.taskDao().delete(it) } } }
        }
    }
}
