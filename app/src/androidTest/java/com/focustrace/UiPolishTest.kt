package com.focustrace

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.focustrace.data.datastore.ThemeMode
import com.focustrace.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UiPolishTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        if (InstrumentationRegistry.getArguments().getString("capturePolish") == "true") {
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/FocusTrace-polish-$name.png")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }

    @Test fun mainPagesKeepActionsInLightAndDarkThemes() {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        val db = app.container.database
        val repo = app.container.settingsRepository
        val original = runBlocking { repo.settings.first() }
        val taskIds = mutableListOf<Long>()
        var sessionId: Long? = null
        try {
            runBlocking {
                taskIds += db.taskDao().insert(TaskEntity(title = "学习操作系统", targetMinutes = 25, createdAt = System.currentTimeMillis(), updatedAt = 1))
                taskIds += db.taskDao().insert(TaskEntity(title = "阅读 · 为重要的事情留出专注时间", targetMinutes = 50, timerType = 1, createdAt = System.currentTimeMillis(), updatedAt = 1))
                val start = LocalDate.now().atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                sessionId = db.focusSessionDao().insert(FocusSessionEntity(taskId = taskIds.first(), type = 1, startTime = start,
                    endTime = start + 8100000, plannedSeconds = 0, focusSeconds = 8100, status = 4, source = "MANUAL", taskTitleSnapshot = "学习操作系统"))
            }
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                runBlocking { repo.update(original.copy(darkMode = mode)) }
                compose.onAllNodesWithText("待办").onFirst().performClick()
                compose.onNodeWithTag("todo-list").performScrollToIndex(0)
                compose.onNodeWithContentDescription("创建待办").assertIsDisplayed()
                compose.onNodeWithTag("todo-list").performScrollToNode(hasTestTag("start-task-${taskIds.first()}"))
                compose.onNodeWithTag("start-task-${taskIds.first()}").assertIsDisplayed()
                capture("todo-${mode.name}")
                compose.onNodeWithText("学习操作系统").performClick()
                compose.onNodeWithText("添加专注记录").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("取消").performClick()
                compose.onAllNodesWithText("统计").onFirst().performClick()
                compose.onNodeWithText("本周").performClick()
                compose.onNodeWithText("本周").assertIsSelected()
                compose.onNodeWithText("今日").performClick()
                compose.onNodeWithTag("statistics-list").performScrollToIndex(0)
                capture("statistics-${mode.name}")
                compose.onNodeWithText("选择日期").performClick()
                compose.onNodeWithText("查看").assertIsEnabled()
                capture("datepicker-${mode.name}")
                compose.onNodeWithText("取消").performClick()
                compose.onAllNodesWithText("我的").onFirst().performClick()
                compose.onNodeWithTag("profile-自动开始休息").performScrollTo().assertExists()
                capture("settings-${mode.name}")
            }
            val before = runBlocking { repo.settings.first().autoStartBreak }
            compose.onNodeWithTag("profile-自动开始休息").performClick()
            compose.waitUntil(5000) { runBlocking { repo.settings.first().autoStartBreak != before } }
            compose.activityRule.scenario.recreate()
            assertEquals(!before, runBlocking { repo.settings.first().autoStartBreak })
        } finally {
            runBlocking {
                repo.update(original)
                sessionId?.let { db.focusSessionDao().getSession(it)?.let { s -> db.focusSessionDao().delete(s) } }
                taskIds.forEach { db.taskDao().getTask(it)?.let { t -> db.taskDao().delete(t) } }
            }
        }
    }
}
