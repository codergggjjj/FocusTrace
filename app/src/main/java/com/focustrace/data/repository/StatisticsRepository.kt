package com.focustrace.data.repository
import com.focustrace.data.local.dao.FocusSessionDao
class StatisticsRepository(private val sessions: FocusSessionDao) {
    fun sessionsBetween(start: Long, end: Long) = sessions.getSessionsBetween(start, end)
}
