package com.focustrace.focus

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.room.withTransaction
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.data.local.entity.DistractionEventEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class FocusStatus(val code: Int) { IDLE(0), FOCUSING(1), PAUSED(2), RESTING(3), FINISHED(4) }
data class TimerInstant(val wall: Long, val elapsed: Long, val boot: Int)
fun TimerClock.snapshot() = TimerInstant(wall(), elapsed(), boot())
interface TimerClock { fun wall(): Long; fun elapsed(): Long; fun boot(): Int }
class AndroidTimerClock(private val context: Context) : TimerClock {
    override fun wall() = System.currentTimeMillis()
    override fun elapsed() = SystemClock.elapsedRealtime()
    override fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
}

class PomodoroEngine(private val db: FocusTraceDatabase, private val clock: TimerClock) {
    private val lock = Mutex()
    private val dao = db.focusSessionDao()
    private var foreground = true
    fun elapsed(s: FocusSessionEntity): Long = elapsedAt(s, clock.snapshot())
    private fun elapsedAt(s: FocusSessionEntity, at: TimerInstant): Long {
        if (s.backgroundWall != null) return s.elapsedMillis
        if (s.status !in listOf(1, 3)) return s.elapsedMillis
        val delta = if (s.bootCount >= 0 && s.bootCount == at.boot) at.elapsed - s.anchorElapsed
            else at.wall - s.anchorWall
        return s.elapsedMillis + delta.coerceAtLeast(0)
    }
    private fun anchored(s: FocusSessionEntity, at: TimerInstant = clock.snapshot()) = s.copy(anchorWall = at.wall, anchorElapsed = at.elapsed, bootCount = at.boot)
    private suspend fun taskTitle(taskId: Long?): String = if (taskId == null) "自由专注"
        else checkNotNull(db.taskDao().getTask(taskId)) { "待办已被删除" }.title
    suspend fun start(taskId: Long?, seconds: Long, restSeconds: Long, autoBreak: Boolean, autoFocus: Boolean): Unit = lock.withLock {
        require(seconds in 1..86400 && restSeconds in 1..86400)
        db.withTransaction {
            check(dao.latest()?.status !in listOf(1, 2, 3)) { "已有专注正在进行" }
            dao.insert(anchored(FocusSessionEntity(taskId = taskId, type = 0, startTime = clock.wall(), plannedSeconds = seconds,
                status = 1, restSeconds = restSeconds, autoBreak = autoBreak, autoFocus = autoFocus, taskTitleSnapshot = taskTitle(taskId))))
        }
    }
    suspend fun startStopwatch(taskId: Long?): Unit = lock.withLock {
        db.withTransaction {
            check(dao.latest()?.status !in listOf(1, 2, 3)) { "已有专注正在进行" }
            dao.insert(anchored(FocusSessionEntity(taskId = taskId, type = 1, startTime = clock.wall(), plannedSeconds = 0,
                status = 1, autoBreak = false, autoFocus = false, taskTitleSnapshot = taskTitle(taskId))))
        }
    }
    private fun focusElapsed(s: FocusSessionEntity) = if (s.type == 1) elapsed(s) else elapsed(s).coerceAtMost(s.plannedSeconds * 1000)
    suspend fun refresh(): FocusSessionEntity? = lock.withLock { db.withTransaction { advance() } }
    private suspend fun advance(at: TimerInstant = clock.snapshot()): FocusSessionEntity? {
        var s = dao.latest() ?: return null
        if (s.type == 0 && s.status == 1 && s.backgroundWall == null && elapsedAt(s, at) >= s.plannedSeconds * 1000) {
            val overdue = elapsedAt(s, at) - s.plannedSeconds * 1000
            s = anchored(s.copy(status = if (s.autoBreak) 3 else 4, focusSeconds = s.plannedSeconds,
                endTime = at.wall - overdue, elapsedMillis = if (s.autoBreak) overdue else s.plannedSeconds * 1000), at)
            dao.update(s)
        }
        if (s.status == 3 && (foreground || !s.autoFocus) && elapsedAt(s, at) >= s.restSeconds * 1000) {
            s = s.copy(status = 4, elapsedMillis = s.plannedSeconds * 1000)
            dao.update(s)
            if (s.autoFocus) {
                val next = anchored(FocusSessionEntity(taskId = s.taskId, type = 0, startTime = at.wall, plannedSeconds = s.plannedSeconds,
                    status = 1, restSeconds = s.restSeconds, autoBreak = s.autoBreak, autoFocus = s.autoFocus, taskTitleSnapshot = s.taskTitleSnapshot), at)
                s = next.copy(id = dao.insert(next))
            }
        }
        return s
    }
    suspend fun onBackground(thresholdSeconds: Int, at: TimerInstant = clock.snapshot()): Unit = lock.withLock {
        require(thresholdSeconds >= 0)
        db.withTransaction {
            foreground = false
            val s = advance(at) ?: return@withTransaction
            if (s.status != 1 || s.backgroundWall != null || (s.bootCount == at.boot && at.elapsed < s.anchorElapsed)) return@withTransaction
            val progress = elapsedAt(s, at)
            dao.update(anchored(s.copy(elapsedMillis = progress, focusSeconds = progress / 1000,
                backgroundWall = at.wall, backgroundElapsed = at.elapsed, backgroundBoot = at.boot,
                backgroundThresholdMillis = thresholdSeconds * 1000L), at))
        }
    }
    suspend fun onForeground(at: TimerInstant = clock.snapshot()): Unit = lock.withLock {
        db.withTransaction {
            foreground = true
            settleDeparture(at)
            advance(at)
        }
    }
    private suspend fun settleDeparture(at: TimerInstant) {
        val s = dao.latest() ?: return
        val background = s.backgroundWall ?: return
        val duration = (if (s.backgroundBoot >= 0 && s.backgroundBoot == at.boot)
            at.elapsed - s.backgroundElapsed else at.wall - background).coerceAtLeast(0)
        val qualifies = duration >= s.backgroundThresholdMillis
        if (qualifies) db.distractionDao().insert(DistractionEventEntity(sessionId = s.id,
            backgroundTime = background, foregroundTime = at.wall, durationSeconds = duration / 1000))
        val progress = s.elapsedMillis + if (qualifies) 0 else duration
        dao.update(anchored(s.copy(backgroundWall = null, elapsedMillis = progress,
            focusSeconds = (if (s.type == 0) progress.coerceAtMost(s.plannedSeconds * 1000) else progress) / 1000,
            distractionCount = s.distractionCount + if (qualifies) 1 else 0,
            distractionSeconds = s.distractionSeconds + if (qualifies) duration / 1000 else 0), at))
    }
    suspend fun pause(): Unit = lock.withLock { db.withTransaction {
        settleDeparture(clock.snapshot())
        val s = advance() ?: return@withTransaction
        if (s.status == 1) {
            val progress = focusElapsed(s)
            dao.update(s.copy(status = 2, elapsedMillis = progress, focusSeconds = progress / 1000))
        }
    } }
    suspend fun resume(): Unit = lock.withLock {
        val s = dao.latest()
        if (s?.status == 2) dao.update(anchored(s.copy(status = 1)))
    }
    suspend fun finish(): Unit = lock.withLock { db.withTransaction {
        settleDeparture(clock.snapshot())
        val s = dao.latest() ?: return@withTransaction
        if (s.status in listOf(1, 2)) {
            val progress = focusElapsed(s)
            dao.update(s.copy(status = 4, focusSeconds = progress / 1000, elapsedMillis = progress, endTime = clock.wall()))
        } else if (s.status == 3) dao.update(s.copy(status = 4, elapsedMillis = s.plannedSeconds * 1000))
    } }
    suspend fun rest(): Unit = lock.withLock {
        val s = dao.latest()
        if (s?.type == 0 && s.status == 4 && s.focusSeconds == s.plannedSeconds) dao.update(anchored(s.copy(status = 3, elapsedMillis = 0)))
    }
}
