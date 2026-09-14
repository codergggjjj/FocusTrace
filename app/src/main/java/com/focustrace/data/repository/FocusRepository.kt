package com.focustrace.data.repository
import com.focustrace.data.local.dao.FocusSessionDao
import com.focustrace.data.local.dao.DistractionDao
import com.focustrace.data.local.entity.*
import kotlinx.coroutines.flow.map
class FocusRepository(private val sessions: FocusSessionDao, private val distractions: DistractionDao) {
    fun reportFor(id: Long) = sessions.observeReport(id).map { it?.let(com.focustrace.focus.FocusReport::from) }
    val allSessions = sessions.getAll()
    suspend fun create(session: FocusSessionEntity) = sessions.insert(session)
    suspend fun update(session: FocusSessionEntity) = sessions.update(session)
    suspend fun getSession(id: Long) = sessions.getSession(id)
    suspend fun saveDistraction(event: DistractionEventEntity) = distractions.insert(event)
    fun distractionsFor(sessionId: Long) = distractions.getBySession(sessionId)
}
