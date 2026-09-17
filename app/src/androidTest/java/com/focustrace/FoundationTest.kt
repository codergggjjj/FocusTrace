package com.focustrace

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.data.local.entity.*
import com.focustrace.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun todoEditorPrefillsTaskAndCurrentTimeForManualRecord() {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        val db = app.container.database
        val taskId = runBlocking { db.taskDao().insert(TaskEntity(title = "学习操作系统补录验证", targetMinutes = 25, createdAt = System.currentTimeMillis(), updatedAt = 1)) }
        try {
            compose.onNodeWithTag("todo-list").performScrollToNode(hasText("学习操作系统补录验证"))
            compose.onNodeWithText("学习操作系统补录验证").performClick()
            val before = java.time.LocalDateTime.now().withSecond(0).withNano(0)
            compose.onNodeWithText("添加专注记录").performScrollTo().performClick()
            val after = java.time.LocalDateTime.now().withSecond(0).withNano(0)
            val field = compose.onNodeWithText("开始时间（HH:mm）").fetchSemanticsNode()
            val entered = field.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
            val format = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            assertTrue(entered == before.format(format) || entered == after.format(format))
            compose.onNodeWithText("对应任务：学习操作系统补录验证").assertExists()
            compose.onNodeWithText("开始时间（HH:mm）").performTextReplacement("14:00")
            compose.onNodeWithText("专注时长（分钟）").performTextReplacement("120")
            compose.onNodeWithText("结束时间（自动计算）").assertTextContains("16:00")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("保存记录").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("专注时长（分钟）").fetchSemanticsNodes().isEmpty() }
            val row = runBlocking { db.focusSessionDao().getAll().first().single { it.taskId == taskId } }
            assertEquals("MANUAL", row.source)
            assertEquals(7200L, row.focusSeconds)
            assertEquals(7200L, runBlocking { app.container.focusRepository.taskFocusSeconds(taskId).first() })
        } finally {
            runBlocking {
                db.focusSessionDao().getAll().first().filter { it.taskId == taskId }.forEach { db.focusSessionDao().delete(it) }
                db.taskDao().getTask(taskId)?.let { db.taskDao().delete(it) }
            }
        }
    }

    @Test fun manualRecordCanBeAddedEditedAndDeletedFromHistory() {
        val db = ApplicationProvider.getApplicationContext<FocusTraceApplication>().container.database
        var manualId: Long? = null
        try {
            compose.onAllNodesWithText("统计").onFirst().performClick()
            compose.onNodeWithText("查看专注记录").performClick()
            compose.onNodeWithText("手动添加记录").performClick()
            compose.onNodeWithText("日期（yyyy-MM-dd）").performTextReplacement("2024-06-12")
            compose.onNodeWithText("开始时间（HH:mm）").performTextReplacement("14:00")
            compose.onNodeWithText("专注时长（分钟）").performTextReplacement("0")
            compose.onNodeWithText("保存记录").assertIsNotEnabled()
            compose.onNodeWithText("专注时长（分钟）").performTextReplacement("120")
            compose.onNodeWithText("专注时长：2 时 0 分 0 秒").assertExists()
            compose.onNodeWithText("备注（可选）").performScrollTo().performTextInput("手动界面验证")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("保存记录").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("添加专注记录").fetchSemanticsNodes().isEmpty() }
            manualId = runBlocking { db.focusSessionDao().getAll().first().single { it.note == "手动界面验证" }.id }
            compose.onNodeWithTag("statistics-records").performScrollToNode(hasTestTag("study-session-$manualId"))
            compose.onNodeWithTag("study-session-$manualId").performClick()
            compose.onNodeWithText("专注时长（分钟）").performTextReplacement("60")
            compose.onNodeWithText("保存记录").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("编辑专注记录").fetchSemanticsNodes().isEmpty() }
            assertEquals(3600L, runBlocking { db.focusSessionDao().getSession(manualId!!)!!.focusSeconds })
            compose.onNodeWithTag("study-session-$manualId").performClick()
            compose.onNodeWithText("删除记录").performScrollTo().performClick()
            compose.onNodeWithText("保留记录").performClick()
            compose.onNodeWithText("删除记录").performClick()
            compose.onNodeWithText("确认删除记录").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("study-session-$manualId").fetchSemanticsNodes().isEmpty() }
            assertNull(runBlocking { db.focusSessionDao().getSession(manualId!!) })
        } finally {
            runBlocking { manualId?.let { db.focusSessionDao().getSession(it)?.let { row -> db.focusSessionDao().delete(row) } } }
        }
    }

    @Test fun realScreenOffDoesNotCreateDistraction() {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        val container = app.container
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        compose.waitForIdle()
        val session = runBlocking {
            container.lifecycle.awaitEvents()
            container.pomodoro.startStopwatch(null)
            container.pomodoro.refresh()!!
        }
        try {
            shell("input keyevent 223") // Actual device sleep, not a simulated app callback.
            android.os.SystemClock.sleep(4500)
            assertFalse(app.getSystemService(android.os.PowerManager::class.java).isInteractive)
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            android.os.SystemClock.sleep(1500)
            runBlocking {
                container.lifecycle.awaitEvents()
                val current = container.pomodoro.refresh()!!
                assertEquals(0, current.distractionCount)
                assertNull(current.backgroundWall)
                assertTrue(container.pomodoro.elapsed(current) >= 4500)
            }
        } finally {
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            runBlocking {
                container.lifecycle.awaitEvents()
                container.database.focusSessionDao().getSession(session.id)?.let { container.database.focusSessionDao().delete(it) }
            }
        }
    }

    @Test fun threeTabsNavigateAndSurviveRecreation() {
        compose.onNodeWithText("把注意力留给重要的事").assertIsDisplayed()
        compose.onNodeWithText("专注").assertDoesNotExist()
        compose.onAllNodesWithText("统计").onFirst().performClick()
        compose.onNodeWithText("看见专注，也理解分心").assertIsDisplayed()
        compose.onAllNodesWithText("我的").onFirst().performClick()
        compose.onNodeWithText("找到适合自己的专注节奏").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("找到适合自己的专注节奏").assertIsDisplayed()
        compose.onAllNodesWithText("待办").onFirst().performClick()
        compose.onNodeWithText("把注意力留给重要的事").assertIsDisplayed()
    }

    @Test fun studyDateMonthTotalsAndSessionTimesOpenReport() {
        val db = ApplicationProvider.getApplicationContext<FocusTraceApplication>().container.database
        val zone = java.time.ZoneId.systemDefault()
        fun instant(day: Int, hour: Int) = java.time.LocalDate.of(2024, 6, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val firstId = runBlocking { db.focusSessionDao().insert(FocusSessionEntity(type = 1, startTime = instant(12, 22),
            endTime = instant(13, 0), plannedSeconds = 0, focusSeconds = 3600, status = 4, taskTitleSnapshot = "晚间学习")) }
        val secondId = runBlocking { db.focusSessionDao().insert(FocusSessionEntity(type = 0, startTime = instant(14, 10),
            endTime = instant(14, 11), plannedSeconds = 1800, focusSeconds = 1800, status = 4, taskTitleSnapshot = "上午学习")) }
        try {
            compose.onAllNodesWithText("统计").onFirst().performClick()
            compose.onNodeWithText("本月").performClick()
            compose.onNodeWithText("选择月份").performClick()
            compose.onNodeWithText("月份（yyyy-MM）").performTextReplacement("2024-06")
            compose.onNodeWithText("查看").performClick()
            compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("study-total"))
            compose.waitUntil(5000) { compose.onAllNodesWithText("1 小时 30 分").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("study-total").assertTextEquals("1 小时 30 分")
            if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("captureStatistics") == "true") {
                compose.onNodeWithTag("statistics-list").performScrollToIndex(0)
                val command = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                    .executeShellCommand("screencap -p /sdcard/Download/FocusTrace-statistics.png")
                android.os.ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
            }
            compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("heat-day-2024-06-12"))
            compose.onNodeWithTag("heat-day-2024-06-12").performClick()
            compose.onNodeWithText("学习时间：1 时 0 分 0 秒").assertIsDisplayed()
            compose.onNodeWithText("查看当天记录").performClick()
            compose.onNodeWithText("关闭记录").performClick()
            compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-range"))
            compose.onNodeWithTag("statistics-range").assertTextEquals("2024-06-12")
            compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("今日"))
            compose.onNodeWithText("今日").performClick()
            compose.onNodeWithText("选择日期").performClick()
            compose.onNodeWithText("输入日期").performClick()
            compose.onNodeWithText("日期（yyyy-MM-dd）").performTextReplacement("2024-06-12")
            compose.onNodeWithText("查看").performClick()
            compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("study-total"))
            compose.waitUntil(5000) { compose.onAllNodesWithText("1 小时 0 分").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("study-total").assertTextEquals("1 小时 0 分")
            compose.onNodeWithText("查看专注记录").performClick()
            compose.onNodeWithTag("statistics-records").performScrollToNode(hasTestTag("study-session-$firstId"))
            compose.onNodeWithText("开始：2024-06-12 22:00:00").assertExists()
            compose.onNodeWithText("结束：2024-06-13 00:00:00").assertExists()
            compose.onNodeWithTag("study-session-$firstId").performClick()
            compose.onNodeWithText("专注报告").assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("返回统计").performClick()
            compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("statistics-range"))
            compose.onNodeWithTag("statistics-range").assertTextEquals("2024-06-12")
        } finally {
            runBlocking {
                db.focusSessionDao().getSession(firstId)?.let { db.focusSessionDao().delete(it) }
                db.focusSessionDao().getSession(secondId)?.let { db.focusSessionDao().delete(it) }
            }
        }
    }

    @Test fun settingsEditValidatesPersistsAndAppliesNewTaskDefault() {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        val original = runBlocking { app.container.settingsRepository.settings.first() }
        try {
            compose.onAllNodesWithText("我的").onFirst().performClick()
            compose.onNodeWithText("编辑设置").performScrollTo().performClick()
            compose.onNodeWithText("默认番茄分钟").performScrollTo().performTextReplacement("0")
            compose.onNodeWithText("保存设置").assertIsNotEnabled()
            compose.onNodeWithText("默认番茄分钟").performTextReplacement("42")
            compose.onNodeWithText("默认休息分钟").performScrollTo().performTextReplacement("7")
            compose.onNodeWithText("分心阈值秒数").performScrollTo().performTextReplacement("10")
            compose.onNodeWithTag("自动开始休息").performScrollTo().performClick()
            compose.onNodeWithTag("自动开始下一轮").performScrollTo().performClick()
            compose.onNodeWithTag("theme-DARK").performScrollTo().performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("保存设置").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("保存设置").fetchSemanticsNodes().isEmpty() }
            val saved = runBlocking { app.container.settingsRepository.settings.first() }
            assertEquals(42, saved.pomodoroMinutes)
            assertEquals(7, saved.breakMinutes)
            assertEquals(10, saved.distractionThreshold)
            assertEquals(!original.autoStartBreak, saved.autoStartBreak)
            assertEquals(!original.autoStartFocus, saved.autoStartFocus)
            assertEquals(com.focustrace.data.datastore.ThemeMode.DARK, saved.darkMode)
            compose.onAllNodesWithText("待办").onFirst().performClick()
            compose.onNodeWithContentDescription("创建待办").performClick()
            compose.onNodeWithText("目标分钟数").assertTextContains("42")
            compose.onNodeWithText("取消").performClick()
        } finally { runBlocking { app.container.settingsRepository.update(original) } }
    }

    @Test fun statisticsPeriodsAndHistorySurviveRecreation() {
        compose.onAllNodesWithText("统计").onFirst().performClick()
        compose.onNodeWithText("本月").performClick()
        compose.onNodeWithContentDescription("下一月").assertIsNotEnabled()
        compose.onNodeWithContentDescription("上一月").performClick()
        val start = java.time.LocalDate.now().withDayOfMonth(1).minusMonths(1)
        val label = "${start.year} 年 ${start.monthValue} 月"
        compose.waitUntil(5000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithContentDescription("下一月").assertIsEnabled()
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("回到本月"))
        compose.onNodeWithText("回到本月").performClick()
        compose.onNodeWithTag("statistics-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("下一月").assertIsNotEnabled()
        compose.onNodeWithText("本周").performClick()
        compose.onNodeWithContentDescription("上一周").assertIsDisplayed()
    }

    @Test fun stopwatchTaskPersistsAndSelectsStopwatchWhenStarting() {
        compose.onNodeWithContentDescription("创建待办").performClick()
        compose.onNodeWithText("待办名称").performTextInput("正向待办验证")
        compose.onNodeWithTag("timer-type-stopwatch").performScrollTo().performClick()
        compose.onNodeWithText("目标分钟数").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("timer-type-stopwatch").assertIsSelected()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("正向待办验证").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("todo-list").performScrollToNode(hasText("正向待办验证"))
        compose.onNodeWithText("正向待办验证").performScrollTo().performClick()
        compose.onNodeWithTag("timer-type-stopwatch").assertIsSelected()
        compose.onNodeWithTag("timer-type-pomodoro").performClick()
        compose.onNodeWithText("目标分钟数").performTextReplacement("15")
        compose.onNodeWithTag("timer-type-stopwatch").performClick()
        compose.onNodeWithText("保存").performClick()
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        val task = runBlocking { app.container.database.taskDao().getAll().first().first { it.title == "正向待办验证" } }
        assertEquals(1, task.timerType)
        compose.onNodeWithTag("todo-list").performScrollToNode(hasTestTag("start-task-${task.id}"))
        compose.onNodeWithTag("start-task-${task.id}").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("正在专注").fetchSemanticsNodes().isNotEmpty() }
        val session = runBlocking { app.container.database.focusSessionDao().latest()!! }
        assertEquals(task.id, session.taskId)
        assertEquals(1, session.type)
        compose.onNodeWithText("结束专注").performScrollTo().performClick()
        compose.onNodeWithText("确认结束").performClick()
        runBlocking { app.container.database.taskDao().delete(task) }
    }

    @Test fun todoStartsPomodoroDirectlyAndProtectsPausedSession() = verifyDirectTaskStart(0)
    @Test fun todoStartsStopwatchDirectlyAndProtectsPausedSession() = verifyDirectTaskStart(1)

    private fun verifyDirectTaskStart(timerType: Int) {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        val db = app.container.database
        val taskId = runBlocking {
            app.container.lifecycle.awaitEvents()
            app.container.pomodoro.finish()
            db.taskDao().insert(TaskEntity(title = "一键开始验证", targetMinutes = 12,
                timerType = timerType, createdAt = 1, updatedAt = 1))
        }
        try {
        compose.onNodeWithTag("todo-list").performScrollToNode(hasTestTag("start-task-$taskId"))
        compose.onNodeWithTag("start-task-$taskId").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("正在专注").fetchSemanticsNodes().isNotEmpty() }
        val session = runBlocking { db.focusSessionDao().latest()!! }
        assertEquals(taskId, session.taskId)
        assertEquals(timerType, session.type)
        assertEquals(if (timerType == 0) 720L else 0L, session.plannedSeconds)
        compose.onNodeWithText("暂停").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("已暂停").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("返回待办").performScrollTo().performClick()
        val other = TaskEntity(title = "另一个任务", targetMinutes = 5, createdAt = 2, updatedAt = 2)
        val otherId = runBlocking { db.taskDao().insert(other) }
        try {
            compose.onNodeWithTag("todo-list").performScrollToNode(hasTestTag("start-task-$otherId"))
            compose.onNodeWithTag("start-task-$otherId").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("已有其他计时，请点击「返回计时」处理当前专注或休息。").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("知道了").performClick()
        } finally { runBlocking { db.taskDao().delete(other.copy(id = otherId)) } }
        compose.onNodeWithTag("todo-list").performScrollToNode(hasTestTag("start-task-$taskId"))
        compose.onNodeWithTag("start-task-$taskId").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("已暂停").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(session.id, runBlocking { db.focusSessionDao().latest()!!.id })
        assertEquals(2, runBlocking { db.focusSessionDao().latest()!!.status })
        } finally {
            runBlocking {
                app.container.pomodoro.finish()
                db.taskDao().getAll().first().firstOrNull { it.id == taskId }?.let { db.taskDao().delete(it) }
            }
        }
    }

    @Test fun taskCrudPersistsAndValidatesInput() {
        compose.onNodeWithContentDescription("创建待办").performClick()
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithText("待办名称").performTextInput("验证任务")
        compose.onNodeWithText("目标分钟数").performTextReplacement("0")
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithText("目标分钟数").performTextReplacement("45")
        compose.onNodeWithText("选择分类").assertDoesNotExist()
        compose.onNodeWithText("添加分类").assertDoesNotExist()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("验证任务").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("验证任务").performClick()
        compose.onNodeWithText("待办名称").performTextReplacement("修改后的任务")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("修改后的任务").fetchSemanticsNodes().isNotEmpty() }
        val editedId = runBlocking { ApplicationProvider.getApplicationContext<FocusTraceApplication>()
            .container.database.taskDao().getAll().first().first { it.title == "修改后的任务" }.id }
        compose.onNodeWithTag("complete-task-$editedId").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("已完成").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("delete-task-$editedId").assertDoesNotExist()
        compose.onNodeWithText("修改后的任务").performClick()
        compose.onNodeWithTag("delete-task-$editedId").performClick()
        compose.onAllNodesWithText("取消").onLast().performClick()
        compose.onNodeWithText("编辑待办").assertIsDisplayed()
        compose.onNodeWithTag("delete-task-$editedId").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("修改后的任务").fetchSemanticsNodes().isEmpty() }
    }

    private fun enterRunningFocus(type: Int = 0) {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        runBlocking {
            app.container.lifecycle.awaitEvents()
            app.container.pomodoro.finish()
            if (type == 1) app.container.pomodoro.startStopwatch(null)
            else app.container.pomodoro.start(null, 1500, 300, false, false)
        }
        openRunningFocus()
    }
    private fun openRunningFocus() {
        compose.waitUntil(5000) { compose.onAllNodes(hasText("返回计时", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("返回计时", substring = true)).performClick()
    }

    @Test fun pomodoroUiPauseResumeAndFinish() {
        enterRunningFocus()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("暂停").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("已暂停").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("继续").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("正在专注").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("结束专注").performClick()
        compose.onNodeWithText("确认结束").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("本轮已结束").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun stopwatchUiModePauseAndFinish() {
        enterRunningFocus(1)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("暂停").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("继续").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("继续").performScrollTo().performClick()
        compose.onNodeWithText("结束专注").performClick()
        compose.onNodeWithText("确认结束").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("本轮已结束").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("开始休息").assertDoesNotExist()
    }

    @Test fun thresholdPresetsAndValidationPersist() {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        val original = runBlocking { app.container.settingsRepository.settings.first() }
        try {
            enterRunningFocus()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("分心判定：${original.distractionThreshold} 秒").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("分心判定：${original.distractionThreshold} 秒").performScrollTo().performClick()
            compose.onNodeWithText("宽松 · 10 秒").performClick()
            compose.onNodeWithText("自定义秒数").performTextReplacement("-1")
            compose.onNodeWithText("保存判定时间").assertIsNotEnabled()
            compose.onNodeWithText("自定义秒数").performTextReplacement("10")
            compose.onNodeWithText("保存判定时间").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("分心判定：10 秒").fetchSemanticsNodes().isNotEmpty() }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("分心判定：10 秒").performScrollTo().assertIsDisplayed()
            assertEquals(10, runBlocking { app.container.settingsRepository.settings.first().distractionThreshold })
        } finally { runBlocking { app.container.pomodoro.finish(); app.container.settingsRepository.update(original) } }
    }

    @Test fun reportDisplaysPersistedMetricsAndSurvivesRecreation() {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        val db = app.container.database
        val sessionId = runBlocking {
            app.container.lifecycle.awaitEvents()
            app.container.pomodoro.finish()
            val id = db.focusSessionDao().insert(FocusSessionEntity(type = 0, startTime = 1000000, endTime = 4403000,
                plannedSeconds = 3000, focusSeconds = 3000, status = 4, taskTitleSnapshot = "报告样例", distractionCount = 3, distractionSeconds = 403))
            db.distractionDao().insert(DistractionEventEntity(sessionId = id, backgroundTime = 1751000, foregroundTime = 1822000, durationSeconds = 71))
            db.distractionDao().insert(DistractionEventEntity(sessionId = id, backgroundTime = 2635000, foregroundTime = 2930000, durationSeconds = 295))
            db.distractionDao().insert(DistractionEventEntity(sessionId = id, backgroundTime = 3666000, foregroundTime = 3703000, durationSeconds = 37))
            id
        }
        try {
            compose.waitUntil(5000) { compose.onAllNodesWithText("查看专注报告").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("查看专注报告").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("报告样例").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("report-list").performScrollToNode(hasTestTag("report-focus-percent"))
            compose.onNodeWithTag("report-focus-percent").assertTextEquals("88%")
            compose.onNodeWithTag("report-list").performScrollToNode(hasText("开始后 12 分 31 秒"))
            compose.onNodeWithText("开始后 12 分 31 秒").assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("专注报告").assertIsDisplayed()
            compose.onNodeWithTag("report-list").performScrollToNode(hasTestTag("report-focus-percent"))
            compose.onNodeWithTag("report-focus-percent").assertTextEquals("88%")
            compose.onNodeWithText("返回待办").performClick()
            compose.onNodeWithText("查看专注报告").assertIsDisplayed()
        } finally {
            runBlocking { db.focusSessionDao().getSession(sessionId)?.let { db.focusSessionDao().delete(it) } }
        }
    }

    @Test fun naturalCompletionOpensReportAndKeepsRestRunning() {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        runBlocking {
            app.container.lifecycle.awaitEvents()
            app.container.pomodoro.finish()
            app.container.pomodoro.start(null, 8, 60, true, false)
        }
        try {
            openRunningFocus()
            compose.waitUntil(15000) { compose.onAllNodesWithText("专注报告").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(5000) { compose.onAllNodesWithText("本轮专注完成").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("返回待办").performClick()
            openRunningFocus()
            compose.onNodeWithText("正在休息").assertIsDisplayed()
            compose.onNodeWithText("查看专注报告").assertIsDisplayed()
        } finally { runBlocking { app.container.pomodoro.finish() } }
    }

    @Test fun roomRelationsAndTimeBoundaries() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java)
            .build()
        try {
            assertTrue(db.categoryDao().getAll().first().isEmpty())
            val task = TaskEntity(title = "测试待办", targetMinutes = 25, createdAt = 100, updatedAt = 100)
            val taskId = db.taskDao().insert(task)
            val session = FocusSessionEntity(taskId = taskId, type = 0, startTime = 100, plannedSeconds = 1500)
            val sessionId = db.focusSessionDao().insert(session)
            db.distractionDao().insert(DistractionEventEntity(sessionId = sessionId, backgroundTime = 110, foregroundTime = 115, durationSeconds = 5))
            assertEquals(1, db.focusSessionDao().getSessionsBetween(100, 101).first().size)
            assertTrue(db.focusSessionDao().getSessionsBetween(0, 100).first().isEmpty())
            assertEquals(5L, db.distractionDao().getBySession(sessionId).first().single().durationSeconds)
            assertNull(db.taskDao().getAll().first().single().categoryId)
            db.taskDao().delete(task.copy(id = taskId))
            assertNull(db.focusSessionDao().getSession(sessionId)!!.taskId)
            db.focusSessionDao().delete(session.copy(id = sessionId, taskId = null))
            assertTrue(db.distractionDao().getBySession(sessionId).first().isEmpty())
        } finally { db.close() }
    }

    @Test fun settingsPersistAcrossRepositoryInstances() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SettingsDataStore(context)
        val previous = store.settings.first()
        try {
            store.update(previous.copy(pomodoroMinutes = 40, distractionThreshold = 10))
            val restored = SettingsDataStore(context).settings.first()
            assertEquals(40, restored.pomodoroMinutes)
            assertEquals(10, restored.distractionThreshold)
        } finally { store.update(previous) }
    }
}
