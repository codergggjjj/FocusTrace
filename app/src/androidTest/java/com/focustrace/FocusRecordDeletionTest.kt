package com.focustrace

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FocusRecordDeletionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun historyDeletesTimerAndManualOnlyAfterConfirmation() {
        val container = ApplicationProvider.getApplicationContext<FocusTraceApplication>().container
        val db = container.database
        val task = TaskEntity(title = "记录删除验证", targetMinutes = 25, createdAt = 1, updatedAt = 1)
        val taskId = runBlocking { db.taskDao().insert(task) }
        val start = LocalDate.now().atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val ids = runBlocking {
            listOf("TIMER", "MANUAL").map { source ->
                db.focusSessionDao().insert(FocusSessionEntity(taskId = taskId, type = 1,
                    startTime = start, endTime = start + 3600000, plannedSeconds = 0,
                    focusSeconds = 3600, status = 4, source = source, taskTitleSnapshot = task.title))
            }
        }
        try {
            compose.onNodeWithText("统计", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("查看专注记录"))
            compose.onNodeWithText("查看专注记录").performClick()
            compose.onNodeWithTag("statistics-records").performScrollToNode(hasTestTag("delete-session-${ids[0]}"))
            compose.onNodeWithTag("delete-session-${ids[0]}").performClick()
            compose.onNodeWithText("保留记录").performClick()
            assertNotNull(runBlocking { db.focusSessionDao().getSession(ids[0]) })
            compose.onNodeWithTag("delete-session-${ids[0]}").performClick()
            compose.activityRule.scenario.recreate()
            compose.waitUntil(5000) { compose.onAllNodesWithText("删除这条专注记录？").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("删除这条专注记录？").assertIsDisplayed()
            compose.onNodeWithText("确认删除记录").performClick()
            compose.waitUntil(5000) { runBlocking { db.focusSessionDao().getSession(ids[0]) == null } }
            compose.onNodeWithTag("study-session-${ids[0]}").assertDoesNotExist()
            assertEquals(3600L, runBlocking { container.focusRepository.taskFocusSeconds(taskId).first() })
            compose.onNodeWithTag("statistics-records").performScrollToNode(hasTestTag("delete-session-${ids[1]}"))
            compose.onNodeWithTag("delete-session-${ids[1]}").performClick()
            compose.onNodeWithText("确认删除记录").performClick()
            compose.waitUntil(5000) { runBlocking { db.focusSessionDao().getSession(ids[1]) == null } }
            assertEquals(0L, runBlocking { container.focusRepository.taskFocusSeconds(taskId).first() })
            compose.onNodeWithText("关闭记录").performClick()
            compose.onNodeWithTag("study-total").assertExists()
        } finally {
            runBlocking {
                ids.forEach { id -> db.focusSessionDao().getSession(id)?.let { db.focusSessionDao().delete(it) } }
                db.taskDao().delete(task.copy(id = taskId))
            }
        }
    }
}
