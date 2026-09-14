package com.focustrace.focus

import com.focustrace.data.local.entity.*
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Read-only calculations over one transactional Room snapshot, never over the live timer. */
data class FocusReport(
    val session: FocusSessionEntity,
    val events: List<DistractionEventEntity>,
    val distractionSeconds: Long,
    val averageDistractionSeconds: Long?,
    val firstDistractionSeconds: Long?,
    val focusPercent: Int?
) {
    val title: String get() = session.taskTitleSnapshot ?: "自由专注 / 原待办不可用"
    val available: Boolean get() = session.endTime != null && session.status in listOf(3, 4)
    val completedPomodoro: Boolean get() = session.type == 0 && session.focusSeconds >= session.plannedSeconds
    companion object {
        fun from(record: SessionWithDistractions): FocusReport {
            val s = record.session
            // IDs reflect recorded event order even when the wall clock is changed.
            val events = record.events.sortedBy { it.id }
            val total = events.sumOf { it.durationSeconds.coerceAtLeast(0) }
            val focus = s.focusSeconds.coerceAtLeast(0)
            val denominator = focus.toDouble() + total.toDouble()
            val first = events.firstOrNull()?.let { event ->
                (event.backgroundTime - s.startTime).takeIf { it >= 0 }?.div(1000)
            }
            return FocusReport(s, events, total,
                if (events.isEmpty()) null else (total.toDouble() / events.size).roundToLong(), first,
                if (denominator == 0.0) null else (focus / denominator * 100).roundToInt().coerceIn(0, 100))
        }
    }
}

fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return if (safe >= 3600) "${safe / 3600} 时 ${safe % 3600 / 60} 分 ${safe % 60} 秒"
        else "${safe / 60} 分 ${safe % 60} 秒"
}
