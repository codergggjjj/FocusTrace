package com.focustrace

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.focustrace.data.local.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections

/** Diagnostic frame sample, not a device-independent speed assertion. */
class ScrollPerformanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun largeListsScrollWithoutChangingData() {
        val db = ApplicationProvider.getApplicationContext<FocusTraceApplication>().container.database
        val tasks = mutableListOf<TaskEntity>()
        val categories = mutableListOf<CategoryEntity>()
        val sessions = mutableListOf<FocusSessionEntity>()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            runBlocking { db.withTransaction {
                repeat(40) { index ->
                    val c = CategoryEntity(name = "性能分类 $index", icon = "label", sortOrder = 100 + index, createdAt = start)
                    categories += c.copy(id = db.categoryDao().insert(c))
                }
                repeat(300) { index ->
                    val t = TaskEntity(title = "滑动测试任务 $index", categoryId = categories[index % 40].id, targetMinutes = 25, createdAt = start, updatedAt = start)
                    tasks += t.copy(id = db.taskDao().insert(t))
                }
                repeat(1000) { index ->
                    val s = FocusSessionEntity(taskId = tasks[index % 300].id, type = 0, startTime = start + index * 1000L,
                        endTime = start + index * 1000L + 60000, plannedSeconds = 60, focusSeconds = 60, status = 4, taskTitleSnapshot = "性能专注 $index")
                    sessions += s.copy(id = db.focusSessionDao().insert(s))
                }
            } }
            compose.waitUntil(15000) { compose.onAllNodesWithTag("todo-list").fetchSemanticsNodes().isNotEmpty() }
            sample("todo", "todo-list")
            compose.onAllNodesWithText("统计").onFirst().performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("study-total").fetchSemanticsNodes().isNotEmpty() }
            sample("statistics", "statistics-list")
        } finally {
            runBlocking { db.withTransaction {
                sessions.forEach { db.focusSessionDao().delete(it) }
                tasks.forEach { db.taskDao().delete(it) }
                categories.forEach { db.categoryDao().delete(it) }
            } }
        }
    }
    private fun sample(name: String, tag: String) {
        val durations = Collections.synchronizedList(mutableListOf<Long>())
        val thread = HandlerThread("frame-sample").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> durations.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION)) }
        compose.runOnUiThread { compose.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
        try {
            repeat(6) { compose.onNodeWithTag(tag).performTouchInput { swipeUp(durationMillis = 400) } }
            repeat(6) { compose.onNodeWithTag(tag).performTouchInput { swipeDown(durationMillis = 400) } }
            compose.waitForIdle()
        } finally {
            compose.runOnUiThread { compose.activity.window.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely(); thread.join()
        }
        val sorted = durations.toList().sorted()
        check(sorted.isNotEmpty())
        Log.i("FocusTracePerf", "$name frames=${sorted.size} p50Ms=${sorted[sorted.size / 2] / 1e6} p95Ms=${sorted[(sorted.size * .95).toInt().coerceAtMost(sorted.lastIndex)] / 1e6} over16ms=${sorted.count { it > 16666667 }}")
    }
}
