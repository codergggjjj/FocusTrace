package com.focustrace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.data.local.entity.TaskEntity
import com.focustrace.data.repository.FocusRepository
import com.focustrace.data.repository.StatisticsRepository
import com.focustrace.statistics.StatisticsRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskStudyRepositoryTest {
    @Test fun taskScopeThresholdLifetimeAndManualChangesStayConsistent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FocusTraceDatabase::class.java).build()
        try {
            val task = TaskEntity(title = "任务 A", targetMinutes = 25, createdAt = 1, updatedAt = 1)
            val id = db.taskDao().insert(task)
            val other = db.taskDao().insert(task.copy(title = "任务 B"))
            val today = LocalDate.of(2024, 10, 1)
            val zone = ZoneId.systemDefault()
            val start = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
            val dao = db.focusSessionDao()
            suspend fun insert(seconds: Long, taskId: Long = id, at: Long = start, status: Int = 4, source: String = "TIMER") =
                dao.insert(FocusSessionEntity(taskId = taskId, type = 1, startTime = at, endTime = at + seconds * 1000,
                    plannedSeconds = 0, focusSeconds = seconds, status = status, source = source, taskTitleSnapshot = "旧名称"))
            insert(600)
            val manual = insert(1200, source = "MANUAL")
            insert(1800, at = today.minusDays(40).atTime(10, 0).atZone(zone).toInstant().toEpochMilli())
            insert(300); insert(299); insert(7200, taskId = other); insert(3600, status = 1)
            val repository = StatisticsRepository(dao)
            val range = StatisticsRange(today.minusDays(6), today.plusDays(1), zone)
            val summary = repository.taskStudy(id, range).first()
            assertEquals(3600L, summary.focusSeconds)
            assertEquals(3, summary.sessions.size)
            assertEquals(2, summary.recordsByDay.size)
            assertEquals(7, summary.trend.days.size)
            assertEquals(1800L, summary.trend.totals.focusSeconds)
            assertEquals(6, summary.trend.days.count { it.totals.focusSeconds == 0L })
            assertEquals(dao.observeTaskFocusSeconds(id).first(), summary.focusSeconds)
            db.taskDao().update(task.copy(id = id, title = "新名称"))
            assertEquals("旧名称", repository.taskStudy(id, range).first().sessions.first().taskTitleSnapshot)
            val focus = FocusRepository(dao, db.distractionDao())
            focus.saveManual(manual, today.toString(), "10:00", "11:00", id, "延长学习")
            assertEquals(6000L, repository.taskStudy(id, range).first().focusSeconds)
            focus.saveManual(manual, today.toString(), "10:00", "11:00", other, "改绑待办")
            assertEquals(2400L, repository.taskStudy(id, range).first().focusSeconds)
            focus.deleteManual(manual)
            assertEquals(7200L, repository.taskStudy(other, range).first().focusSeconds)
        } finally { db.close() }
    }
}
