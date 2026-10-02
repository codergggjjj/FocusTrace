package com.focustrace

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.focustrace.data.datastore.ThemeMode
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.data.local.entity.StatisticsRecord
import com.focustrace.statistics.*
import com.focustrace.ui.statistics.FocusHeatmap
import com.focustrace.ui.theme.FocusTraceTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FocusCalendarUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun hoursInsideAndDateBelowRemainSelectableInBothThemes() {
        val date = LocalDate.of(2024, 6, 12)
        val zone = ZoneId.systemDefault()
        val start = date.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val summary = summarizeStatistics(listOf(StatisticsRecord(FocusSessionEntity(
            id = 1, type = 1, startTime = start, endTime = start + 5400000,
            plannedSeconds = 0, focusSeconds = 5400, status = 4), emptyList())),
            statisticsRange(date, StatisticsPeriod.MONTH, zone))
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var selected by mutableStateOf<LocalDate?>(null)
        compose.setContent { FocusTraceTheme(mode) {
            Box(Modifier.width(328.dp)) { FocusHeatmap(summary, selected) { selected = it } }
        } }
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            compose.runOnIdle { mode = theme }
            compose.onNodeWithTag("heat-day-$date").performClick().assertIsSelected()
            val hours = compose.onNodeWithText("1.5h", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val day = compose.onNodeWithText("12", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("日期应在小时数下方", day.top > hours.bottom)
            val app = ApplicationProvider.getApplicationContext<android.content.Context>()
            java.io.File(app.getExternalFilesDir(null), "calendar-${theme.name.lowercase()}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
