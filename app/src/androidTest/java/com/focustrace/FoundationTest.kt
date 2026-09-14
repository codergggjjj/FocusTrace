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

    @Test fun fourTabsNavigateAndSurviveRecreation() {
        compose.onNodeWithText("把注意力留给重要的事").assertIsDisplayed()
        compose.onAllNodesWithText("专注").onFirst().performClick()
        compose.onNodeWithText("一次只做一件事").assertIsDisplayed()
        compose.onAllNodesWithText("统计").onFirst().performClick()
        compose.onNodeWithText("看见专注，也理解分心").assertIsDisplayed()
        compose.onAllNodesWithText("我的").onFirst().performClick()
        compose.onNodeWithText("找到适合自己的专注节奏").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("找到适合自己的专注节奏").assertIsDisplayed()
        compose.onAllNodesWithText("待办").onFirst().performClick()
        compose.onNodeWithText("把注意力留给重要的事").assertIsDisplayed()
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
        compose.onNode(isToggleable()).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("已完成").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("修改后的任务").assertIsDisplayed()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("修改后的任务").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun pomodoroUiPauseResumeAndFinish() {
        compose.onAllNodesWithText("专注").onFirst().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("开始番茄钟").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("开始番茄钟").performScrollTo().performClick()
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
        compose.onAllNodesWithText("专注").onFirst().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("正向计时").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("正向计时").performScrollTo().performClick()
        compose.onNodeWithText("专注分钟").assertDoesNotExist()
        compose.onNodeWithText("开始正向计时").performScrollTo().performClick()
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
