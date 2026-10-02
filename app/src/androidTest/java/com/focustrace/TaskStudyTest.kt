package com.focustrace

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import com.focustrace.data.datastore.ThemeMode
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.data.local.entity.TaskEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskStudyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun taskStudyOpensFromEditorRestoresAndUpdatesAfterManualEdit() {
        val db = ApplicationProvider.getApplicationContext<FocusTraceApplication>().container.database
        val task = TaskEntity(title = "学习详情验证", targetMinutes = 25, createdAt = 1, updatedAt = 1)
        val taskId = runBlocking { db.taskDao().insert(task) }
        val start = LocalDate.now().atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val recordId = runBlocking { db.focusSessionDao().insert(FocusSessionEntity(
            taskId = taskId, type = 1, startTime = start, endTime = start + 3600000,
            plannedSeconds = 0, focusSeconds = 3600, status = 4, source = "MANUAL", taskTitleSnapshot = task.title)) }
        val timerId = runBlocking { db.focusSessionDao().insert(FocusSessionEntity(
            taskId = taskId, type = 0, startTime = start - 86400000, endTime = start - 82800000,
            plannedSeconds = 3600, focusSeconds = 3600, status = 4, taskTitleSnapshot = task.title)) }
        try {
            compose.onNodeWithTag("todo-list").performScrollToNode(hasText(task.title))
            compose.onNodeWithText(task.title).performClick()
            compose.onNodeWithText("学习详情").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("task-study-total").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("task-study-count").assertTextEquals("2 次")
            compose.onNodeWithText("近 30 天").performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("近 30 天").assertIsSelected()
            compose.onNodeWithTag("task-study-list").performScrollToNode(hasTestTag("task-study-record-$recordId"))
            compose.onNodeWithTag("task-study-record-$recordId").performClick()
            compose.onNodeWithText("专注时长（分钟）").performTextReplacement("120")
            compose.onNodeWithText("保存记录").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("编辑专注记录").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("task-study-list").performScrollToIndex(0)
            compose.onNodeWithTag("task-study-total").assertTextContains("3 小时", substring = true)
            compose.onNodeWithTag("task-study-list").performScrollToNode(hasTestTag("task-study-record-$timerId"))
            compose.onNodeWithTag("task-study-record-$timerId").performClick()
            compose.onNodeWithText("专注报告").assertIsDisplayed()
            compose.onNodeWithText("返回学习详情").performClick()
            compose.onNodeWithTag("task-study-list").performScrollToIndex(0)
            compose.onNodeWithText("近 30 天").assertIsSelected()
            val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
            val settings = runBlocking { app.container.settingsRepository.settings.first() }
            try {
                for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                    runBlocking { app.container.settingsRepository.update(settings.copy(darkMode = mode)) }
                    compose.waitForIdle()
                    compose.onNodeWithTag("task-study-total").assertIsDisplayed()
                    val image = compose.onRoot().captureToImage().asAndroidBitmap()
                    java.io.File(app.getExternalFilesDir(null), "task-study-${mode.name.lowercase()}.png").outputStream().use {
                        image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            } finally { runBlocking { app.container.settingsRepository.update(settings) } }
            // Ordinary records can also be deleted from their existing report.
            compose.onNodeWithTag("task-study-list").performScrollToNode(hasTestTag("task-study-record-$timerId"))
            compose.onNodeWithTag("task-study-record-$timerId").performClick()
            compose.onNodeWithTag("report-list").performScrollToNode(hasTestTag("report-delete-record"))
            compose.onNodeWithTag("report-delete-record").performClick()
            compose.onNodeWithText("确认删除记录").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("task-study-list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("task-study-list").performScrollToIndex(0)
            compose.onNodeWithTag("task-study-count").assertTextEquals("1 次")
            compose.onNodeWithTag("task-study-total").assertTextContains("2 小时", substring = true)
            compose.onNodeWithTag("task-study-list").performScrollToNode(hasTestTag("delete-session-$recordId"))
            compose.onNodeWithTag("delete-session-$recordId").performClick()
            compose.onNodeWithText("确认删除记录").performClick()
            compose.waitUntil(5000) { runBlocking { db.focusSessionDao().getSession(recordId) == null } }
            compose.onNodeWithTag("task-study-list").performScrollToIndex(0)
            compose.onNodeWithTag("task-study-count").assertTextEquals("0 次")
            compose.onNodeWithContentDescription("返回待办编辑").performClick()
            compose.onNodeWithText("编辑待办").assertIsDisplayed()
        } finally {
            runBlocking {
                db.focusSessionDao().getSession(recordId)?.let { db.focusSessionDao().delete(it) }
                db.focusSessionDao().getSession(timerId)?.let { db.focusSessionDao().delete(it) }
                db.taskDao().delete(task.copy(id = taskId))
            }
        }
    }
}
