package com.focustrace

import com.focustrace.data.datastore.UserSettings
import com.focustrace.data.local.entity.DistractionEventEntity
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.data.local.entity.TaskEntity
import com.focustrace.data.repository.BackupData
import com.focustrace.data.repository.BackupFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    @Test fun roundTripPreservesTaskSessionDistractionAndSettings() {
        val task = TaskEntity(id = 7, title = "操作系统", targetMinutes = 25,
            createdAt = 100, updatedAt = 200, timerType = 1)
        val session = FocusSessionEntity(id = 12, taskId = 7, type = 1,
            startTime = 1000, endTime = 721000, plannedSeconds = 0, focusSeconds = 700,
            distractionCount = 1, distractionSeconds = 20, status = 4,
            taskTitleSnapshot = "操作系统", source = "TIMER", note = "复习")
        val event = DistractionEventEntity(id = 15, sessionId = 12,
            backgroundTime = 2000, foregroundTime = 22000, durationSeconds = 20)
        val data = BackupData(12345, emptyList(), listOf(task), listOf(session), listOf(event),
            UserSettings(pomodoroMinutes = 40, focusBackgroundCustomPath = "/old/device/path", dailyGoalMinutes = 120, weeklyGoalMinutes = 600),
            byteArrayOf(1, 2, 3))

        val restored = BackupFormat.decode(BackupFormat.encode(data), data.customBackground)

        assertEquals(data.tasks, restored.tasks)
        assertEquals(data.sessions, restored.sessions)
        assertEquals(data.distractions, restored.distractions)
        assertEquals(40, restored.settings.pomodoroMinutes)
        assertEquals(120, restored.settings.dailyGoalMinutes)
        assertEquals(600, restored.settings.weeklyGoalMinutes)
        assertNull(restored.settings.focusBackgroundCustomPath)
        assertEquals(12345, restored.createdAt)
    }

    @Test fun oldBackupsWithoutGoalsRestoreWithGoalsDisabled() {
        val data = BackupData(1, emptyList(), emptyList(), emptyList(), emptyList(), UserSettings(), null)
        val json = org.json.JSONObject(BackupFormat.encode(data))
        json.getJSONObject("settings").remove("dailyGoalMinutes")
        json.getJSONObject("settings").remove("weeklyGoalMinutes")
        val restored = BackupFormat.decode(json.toString(), null)
        assertEquals(0, restored.settings.dailyGoalMinutes)
        assertEquals(0, restored.settings.weeklyGoalMinutes)
    }

    @Test fun rejectsInvalidGoalsInBackups() {
        val data = BackupData(1, emptyList(), emptyList(), emptyList(), emptyList(), UserSettings(), null)
        listOf("dailyGoalMinutes" to -1, "dailyGoalMinutes" to 1441,
            "weeklyGoalMinutes" to -1, "weeklyGoalMinutes" to 10081).forEach { (key, value) ->
            val json = org.json.JSONObject(BackupFormat.encode(data))
            json.getJSONObject("settings").put(key, value)
            assertTrue(runCatching { BackupFormat.decode(json.toString(), null) }.isFailure)
        }
    }

    @Test fun rejectsBrokenReferencesAndActiveSessionsBeforeRestore() {
        val task = TaskEntity(id = 1, title = "任务", targetMinutes = 25, createdAt = 1, updatedAt = 1)
        val session = FocusSessionEntity(id = 2, taskId = 1, type = 0,
            startTime = 1, endTime = 400000, plannedSeconds = 1500, focusSeconds = 400, status = 4)
        val valid = BackupData(1, emptyList(), listOf(task), listOf(session), emptyList(), UserSettings(), null)
        val dangling = valid.copy(distractions = listOf(DistractionEventEntity(3, 999, 1, 2, 0)))
        val active = valid.copy(sessions = listOf(session.copy(status = 1, endTime = null)))
        assertTrue(runCatching { BackupFormat.decode(BackupFormat.encode(dangling), null) }.isFailure)
        assertTrue(runCatching { BackupFormat.decode(BackupFormat.encode(active), null) }.isFailure)
    }
}
