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

    @Test fun statisticsPeriodsAndHistorySurviveRecreation() {
        compose.onAllNodesWithText("统计").onFirst().performClick()
        compose.onNodeWithText("本月").performClick()
        compose.onNodeWithText("下一月").assertIsNotEnabled()
        compose.onNodeWithText("上一月").performClick()
        val start = java.time.LocalDate.now().withDayOfMonth(1).minusMonths(1)
        val label = "$start 至 ${start.plusMonths(1).minusDays(1)}"
        compose.waitUntil(5000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithText("下一月").assertIsEnabled()
        compose.onNodeWithText("回到本月").performClick()
        compose.onNodeWithText("下一月").assertIsNotEnabled()
        compose.onNodeWithText("本周").performClick()
        compose.onNodeWithText("上一周").assertIsDisplayed()
    }

    @Test fun stopwatchTaskPersistsAndSelectsStopwatchWhenStarting() {
        compose.onNodeWithText("创建待办").performClick()
        compose.onNodeWithText("待办名称").performTextInput("正向待办验证")
        compose.onNodeWithText("正向计时").performScrollTo().performClick()
        compose.onNodeWithText("目标分钟数").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("正向计时").assertIsSelected()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("正向待办验证").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("todo-list").performScrollToNode(hasText("正向待办验证"))
        compose.onNodeWithText("正向待办验证").performScrollTo().performClick()
        compose.onNodeWithText("正向计时").assertIsSelected()
        compose.onNodeWithText("番茄钟").performClick()
        compose.onNodeWithText("目标分钟数").performTextReplacement("15")
        compose.onNodeWithText("正向计时").performClick()
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
        compose.onNodeWithText("创建待办").performClick()
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithText("待办名称").performTextInput("验证任务")
        compose.onNodeWithText("目标分钟数").performTextReplacement("0")
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithText("目标分钟数").performTextReplacement("45")
        compose.onNodeWithText("选择分类").performClick()
        compose.onAllNodesWithText("学习").onLast().performClick()
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
        compose.onNodeWithTag("delete-task-$editedId").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("修改后的任务").assertIsDisplayed()
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
            .addCallback(FocusTraceDatabase.SeedCategories).build()
        try {
            val categories = db.categoryDao().getAll().first()
            assertEquals(listOf("学习", "工作", "阅读", "运动", "其他"), categories.map { it.name })
            val task = TaskEntity(categoryId = categories.first().id, title = "测试待办", targetMinutes = 25, createdAt = 100, updatedAt = 100)
            val taskId = db.taskDao().insert(task)
            val session = FocusSessionEntity(taskId = taskId, type = 0, startTime = 100, plannedSeconds = 1500)
            val sessionId = db.focusSessionDao().insert(session)
            db.distractionDao().insert(DistractionEventEntity(sessionId = sessionId, backgroundTime = 110, foregroundTime = 115, durationSeconds = 5))
            assertEquals(1, db.focusSessionDao().getSessionsBetween(100, 101).first().size)
            assertTrue(db.focusSessionDao().getSessionsBetween(0, 100).first().isEmpty())
            assertEquals(5L, db.distractionDao().getBySession(sessionId).first().single().durationSeconds)
            db.categoryDao().delete(categories.first())
            assertNull(db.taskDao().getAll().first().single().categoryId)
            db.taskDao().delete(task.copy(id = taskId, categoryId = null))
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
