package com.focustrace

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.data.local.entity.StatisticsRecord
import com.focustrace.statistics.StatisticsPeriod
import com.focustrace.statistics.statisticsRange
import com.focustrace.statistics.summarizeStatistics
import com.focustrace.ui.statistics.StudyTrend
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatisticsHourLabelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun twoDigitHoursHaveEnoughRoomForBothDigits() {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val start = date.atTime(13, 30).atZone(zone).toInstant().toEpochMilli()
        val summary = summarizeStatistics(listOf(StatisticsRecord(FocusSessionEntity(
            id = 1, type = 1, startTime = start, endTime = start + 100 * 60_000,
            plannedSeconds = 0, focusSeconds = 100 * 60, status = 4), emptyList())),
            statisticsRange(date, StatisticsPeriod.DAY, zone))

        compose.setContent { MaterialTheme { StudyTrend(summary, StatisticsPeriod.DAY) } }

        for (hour in listOf("10", "12", "14", "22")) {
            val label = compose.onNodeWithText(hour, useUnmergedTree = true)
            val bounds = label.getUnclippedBoundsInRoot()
            assertTrue("$hour 点的标签不应被压成一位", bounds.right - bounds.left >= 20.dp)
        }
    }
}
