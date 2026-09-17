package com.focustrace.data.repository
import com.focustrace.data.local.dao.FocusSessionDao
import com.focustrace.data.local.dao.DistractionDao
import com.focustrace.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
class FocusRepository(private val sessions: FocusSessionDao, private val distractions: DistractionDao) {
    suspend fun saveManual(id: Long?, date: String, start: String, end: String, taskId: Long?, note: String) {
        val input = com.focustrace.focus.parseManualFocusInput(date, start, end)
        require(note.length <= 1000) { "备注最多 1000 字" }
        val title = taskId?.let { requireNotNull(sessions.taskTitle(it)) { "任务已删除，请重新选择" } }
        val memo = note.trim().takeIf { it.isNotEmpty() }
        if (id == null) sessions.insert(FocusSessionEntity(taskId = taskId, type = 1,
            startTime = input.startMillis, endTime = input.endMillis, plannedSeconds = 0,
            focusSeconds = input.seconds, elapsedMillis = input.seconds * 1000, status = 4,
            autoBreak = false, autoFocus = false, taskTitleSnapshot = title, source = "MANUAL", note = memo))
        else check(sessions.updateManual(id, input.startMillis, input.endMillis, input.seconds,
            input.seconds * 1000, taskId, title, memo) == 1) { "记录不存在或不是手动记录" }
    }
    suspend fun deleteManual(id: Long) {
        check(sessions.deleteManual(id) == 1) { "记录不存在或不是手动记录" }
    }
    fun taskFocusSeconds(taskId: Long) = sessions.observeTaskFocusSeconds(taskId)
    fun reportFor(id: Long) = sessions.observeReport(id).distinctUntilChanged()
        .map { it?.let(com.focustrace.focus.FocusReport::from) }.flowOn(Dispatchers.Default)
    val allSessions = sessions.getAll()
    suspend fun create(session: FocusSessionEntity) = sessions.insert(session)
    suspend fun update(session: FocusSessionEntity) = sessions.update(session)
    suspend fun getSession(id: Long) = sessions.getSession(id)
    suspend fun saveDistraction(event: DistractionEventEntity) = distractions.insert(event)
    fun distractionsFor(sessionId: Long) = distractions.getBySession(sessionId)
}
