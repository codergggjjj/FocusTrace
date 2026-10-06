package com.focustrace

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RepeatingTaskUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun customDaysPersistAcrossRecreationAndCanBeDisabled() {
        val dao = ApplicationProvider.getApplicationContext<FocusTraceApplication>().container.database.taskDao()
        val title = "重复设置验证-${System.nanoTime()}"
        try {
            compose.onNodeWithContentDescription("创建待办").performClick()
            compose.onNodeWithText("待办名称").performTextInput(title)
            compose.onNodeWithTag("repeat-DAYS:").performScrollTo().performClick()
            compose.onNodeWithText("保存").assertIsNotEnabled()
            compose.onNodeWithTag("repeat-day-1").performScrollTo().performClick()
            compose.onNodeWithTag("repeat-day-5").performScrollTo().performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("repeat-day-5").performScrollTo().assertIsSelected()
            compose.onNodeWithText("保存").performClick()
            compose.waitUntil(5000) { runBlocking { dao.getAll().first().any { it.title == title } } }
            val task = runBlocking { dao.getAll().first().single { it.title == title } }
            assertEquals("DAYS:15",task.repeatType)
            compose.onNodeWithTag("todo-list").performScrollToNode(hasText(title))
            compose.onNodeWithText(title).performClick()
            compose.onNodeWithTag("repeat-NONE").performScrollTo().performClick()
            compose.onNodeWithText("保存").performClick()
            compose.waitUntil(5000) { runBlocking { dao.getTask(task.id)?.repeatType == "NONE" } }
        } finally { runBlocking { dao.getAll().first().filter { it.title == title }.forEach { dao.delete(it) } } }
    }
}
