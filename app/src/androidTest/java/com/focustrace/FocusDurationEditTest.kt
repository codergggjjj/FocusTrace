package com.focustrace

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.focustrace.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FocusDurationEditTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun historyReportEditsDurationWithSecondPrecision() {
        val dao = ApplicationProvider.getApplicationContext<FocusTraceApplication>().container.database.focusSessionDao()
        val start = LocalDate.now().atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val id = runBlocking { dao.insert(FocusSessionEntity(type = 1, startTime = start,
            endTime = start + 900_000, plannedSeconds = 0, focusSeconds = 900, status = 4,
            taskTitleSnapshot = "时长修改验证")) }
        try {
            compose.onNodeWithText("统计", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("查看全部"))
            compose.onNodeWithText("查看全部").performClick()
            compose.onNodeWithTag("statistics-records").performScrollToNode(hasTestTag("study-session-$id"))
            compose.onNodeWithTag("study-session-$id").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("report-list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("report-edit-duration").performScrollTo().performClick()
            val minutes = compose.onNodeWithTag("edit-focus-minutes")
            val seconds = compose.onNodeWithTag("edit-focus-seconds")
            minutes.assertTextContains("15").performTextReplacement("5")
            seconds.assertTextContains("0")
            compose.onNodeWithText("保存时长").assertIsNotEnabled()
            minutes.performTextReplacement("12")
            seconds.performTextReplacement("34")
            compose.onNodeWithText("保存时长").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("12 分 34 秒").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("12 分 34 秒").assertIsDisplayed()
        } finally {
            runBlocking { dao.getSession(id)?.let { dao.delete(it) } }
        }
    }
}
