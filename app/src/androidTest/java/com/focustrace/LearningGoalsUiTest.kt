package com.focustrace

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.data.datastore.ThemeMode
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LearningGoalsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun goalsCanBeConfiguredRestoredAndUpdatedWhenARecordIsDeleted() {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        val container = app.container
        val original = runBlocking { container.settingsRepository.settings.first() }
        runBlocking { container.settingsRepository.setLearningGoals(0, 0) }
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val recordId = runBlocking { container.database.focusSessionDao().insert(FocusSessionEntity(
            type = 1, startTime = start, endTime = start + 1800000, plannedSeconds = 0,
            focusSeconds = 1800, source = "MANUAL", status = 4)) }
        try {
            compose.onNodeWithText("我的").performClick()
            compose.onNodeWithText("学习目标").performScrollTo().performClick()
            compose.onNodeWithTag("goal-daily-enabled").performClick()
            compose.onNodeWithText("每日目标（分钟）").performTextReplacement("0")
            compose.onNodeWithText("保存目标").assertIsNotEnabled()
            compose.onNodeWithText("每日目标（分钟）").performTextReplacement("120")
            compose.onNodeWithTag("goal-weekly-enabled").performScrollTo().performClick()
            compose.onNodeWithText("每周目标（分钟）").performTextReplacement("600")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("每周目标（分钟）").assertTextContains("600")
            compose.onNodeWithText("保存目标").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("learning-goals-editor").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("统计").performClick()
            compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("learning-goals-section"))
            compose.onNodeWithTag("goal-daily-target").assertTextEquals("目标 2 小时")
            compose.onNodeWithTag("goal-weekly-target").assertTextEquals("目标 10 小时")
            fun progressText() = compose.onNodeWithTag("goal-daily-progress").fetchSemanticsNode()
                .config[SemanticsProperties.Text].joinToString { it.text }
            val before = progressText()
            compose.onNodeWithTag("statistics-list").performScrollToIndex(0)
            compose.onNodeWithText("月").performClick()
            compose.onNodeWithContentDescription("上一月").performClick()
            compose.onNodeWithTag("statistics-list").performScrollToNode(hasTestTag("learning-goals-section"))
            compose.onNodeWithTag("goal-daily-progress").assertTextEquals(before)
            val goalsSettings = runBlocking { container.settingsRepository.settings.first() }
            try {
                for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                    runBlocking { container.settingsRepository.update(goalsSettings.copy(darkMode = mode)) }
                    compose.waitForIdle()
                    compose.onNodeWithTag("learning-goals-section").assertIsDisplayed()
                    val image = compose.onRoot().captureToImage().asAndroidBitmap()
                    java.io.File(app.getExternalFilesDir(null), "learning-goals-${mode.name.lowercase()}.png").outputStream().use {
                        image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            } finally { runBlocking { container.settingsRepository.update(goalsSettings) } }
            runBlocking { container.focusRepository.deleteRecord(recordId) }
            compose.waitUntil(5000) { progressText() != before }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("goal-daily-target").assertTextEquals("目标 2 小时")
            compose.onNodeWithTag("learning-goals-edit").performClick()
            compose.onNodeWithTag("goal-daily-enabled").performClick()
            compose.onNodeWithText("保存目标").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("learning-goals-editor").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("goal-daily-progress").assertTextEquals("未开启")
            compose.onNodeWithTag("goal-weekly-target").assertTextEquals("目标 10 小时")
        } finally {
            runBlocking {
                container.settingsRepository.update(original)
                container.database.focusSessionDao().getSession(recordId)?.let { container.database.focusSessionDao().delete(it) }
            }
        }
    }
}
