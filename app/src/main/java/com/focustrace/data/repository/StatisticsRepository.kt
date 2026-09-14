package com.focustrace.data.repository

import com.focustrace.data.local.dao.FocusSessionDao
import com.focustrace.statistics.*
import kotlinx.coroutines.flow.map

class StatisticsRepository(private val sessions: FocusSessionDao) {
    fun sessionsBetween(start: Long, end: Long) = sessions.getSessionsBetween(start, end)
    fun statistics(range: StatisticsRange) = sessions.observeStatistics(range.startMillis, range.endMillis)
        .map { summarizeStatistics(it, range) }
}
