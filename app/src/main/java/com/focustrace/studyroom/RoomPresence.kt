package com.focustrace.studyroom

import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.focus.TimerInstant

internal const val ROOM_CAPACITY = 12
internal const val ROOM_NAME_LIMIT = 15 // Fits a DNS-SD instance label, including multibyte characters.
internal const val NICKNAME_LIMIT = 24
internal const val AVATAR_LIMIT = 32_768
internal const val TASK_TITLE_LIMIT = 120
internal const val HOST_ID = 1
internal const val HEARTBEAT_MS = 15_000L
internal const val PEER_TIMEOUT_MS = 60_000

internal enum class RoomStatus(val code: Int, val label: String) {
    IDLE(0, "未开始"), FOCUSING(1, "专注中"), PAUSED(2, "已暂停"),
    RESTING(3, "休息中"), DISTRACTED(4, "分心中")
}

internal data class RoomProfile(val nickname: String = "专注同学", val avatar: String = "")

internal data class RoomFocus(
    val status: RoomStatus = RoomStatus.IDLE,
    val task: String? = null,
    val seconds: Long = 0,
    val ticking: Boolean = false,
    val limitSeconds: Long = Long.MAX_VALUE,
    val todaySeconds: Long = 0
) {
    fun displaySeconds(receivedAt: Long, now: Long): Long =
        (seconds + if (ticking) ((now - receivedAt).coerceAtLeast(0) / 1000)
            .coerceAtMost(limitSeconds - seconds) else 0).coerceAtMost(limitSeconds)
}

internal data class RoomMember(
    val id: Int,
    val profile: RoomProfile,
    val focus: RoomFocus = RoomFocus(),
    val receivedAt: Long = 0
)

internal data class RoomRoster(val name: String, val selfId: Int, val members: List<RoomMember>) {
    val isHost get() = selfId == HOST_ID
}

internal fun departureRemaining(session: FocusSessionEntity?, at: TimerInstant): Long? {
    if (session?.status != 1) return null
    val backgroundWall = session.backgroundWall ?: return null
    val away = if (session.backgroundBoot >= 0 && session.backgroundBoot == at.boot)
        at.elapsed - session.backgroundElapsed else at.wall - backgroundWall
    return (session.backgroundThresholdMillis - away.coerceAtLeast(0)).coerceAtLeast(0)
}

internal fun roomFocus(session: FocusSessionEntity?, at: TimerInstant, elapsedMillis: Long): RoomFocus {
    if (session == null || session.status !in 1..3) return RoomFocus()
    val awayRemaining = departureRemaining(session, at)
    val status = when (session.status) {
        2 -> RoomStatus.PAUSED
        3 -> RoomStatus.RESTING
        else -> if (awayRemaining == 0L) RoomStatus.DISTRACTED else RoomStatus.FOCUSING
    }
    val limit = if (session.type == 0) session.plannedSeconds else Long.MAX_VALUE
    val seconds = if (status == RoomStatus.RESTING) session.focusSeconds else elapsedMillis / 1000
    return RoomFocus(status, session.taskTitleSnapshot?.take(TASK_TITLE_LIMIT),
        seconds.coerceIn(0, limit), status == RoomStatus.FOCUSING && awayRemaining == null, limit)
}
