package com.focustrace.focus

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.room.withTransaction
import com.focustrace.data.local.FocusTraceDatabase
import com.focustrace.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class FocusStatus(val code: Int) { IDLE(0), FOCUSING(1), PAUSED(2), RESTING(3), FINISHED(4) }
interface TimerClock { fun wall(): Long; fun elapsed(): Long; fun boot(): Int }
class AndroidTimerClock(private val context: Context) : TimerClock {
    override fun wall() = System.currentTimeMillis()
    override fun elapsed() = SystemClock.elapsedRealtime()
    override fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
}

class PomodoroEngine(private val db: FocusTraceDatabase, private val clock: TimerClock) {
    private val lock = Mutex()
    private val dao = db.focusSessionDao()
    fun elapsed(s: FocusSessionEntity): Long {
        if (s.status !in listOf(1, 3)) return s.elapsedMillis
        val delta = if (s.bootCount >= 0 && s.bootCount == clock.boot()) clock.elapsed() - s.anchorElapsed
            else clock.wall() - s.anchorWall
        return s.elapsedMillis + delta.coerceAtLeast(0)
    }
    private fun anchored(s: FocusSessionEntity) = s.copy(anchorWall = clock.wall(), anchorElapsed = clock.elapsed(), bootCount = clock.boot())
    suspend fun start(taskId: Long?, seconds: Long, restSeconds: Long, autoBreak: Boolean, autoFocus: Boolean): Unit = lock.withLock {
        require(seconds in 1..86400 && restSeconds in 1..86400)
        db.withTransaction {
            check(dao.latest()?.status !in listOf(1, 2, 3)) { "已有专注正在进行" }
            dao.insert(anchored(FocusSessionEntity(taskId = taskId, type = 0, startTime = clock.wall(), plannedSeconds = seconds,
                status = 1, restSeconds = restSeconds, autoBreak = autoBreak, autoFocus = autoFocus)))
        }
    }
    suspend fun refresh(): FocusSessionEntity? = lock.withLock { db.withTransaction { advance() } }
    private suspend fun advance(): FocusSessionEntity? {
        var s = dao.latest() ?: return null
        if (s.status == 1 && elapsed(s) >= s.plannedSeconds * 1000) {
            val overdue = elapsed(s) - s.plannedSeconds * 1000
            s = anchored(s.copy(status = if (s.autoBreak) 3 else 4, focusSeconds = s.plannedSeconds,
                endTime = clock.wall() - overdue, elapsedMillis = if (s.autoBreak) overdue else s.plannedSeconds * 1000))
            dao.update(s)
        }
        if (s.status == 3 && elapsed(s) >= s.restSeconds * 1000) {
            s = s.copy(status = 4, elapsedMillis = s.plannedSeconds * 1000)
            dao.update(s)
            if (s.autoFocus) {
                val next = anchored(FocusSessionEntity(taskId = s.taskId, type = 0, startTime = clock.wall(), plannedSeconds = s.plannedSeconds,
                    status = 1, restSeconds = s.restSeconds, autoBreak = s.autoBreak, autoFocus = s.autoFocus))
                s = next.copy(id = dao.insert(next))
            }
        }
        return s
    }
    suspend fun pause(): Unit = lock.withLock { db.withTransaction {
        val s = advance() ?: return@withTransaction
        if (s.status == 1) {
            val progress = elapsed(s).coerceAtMost(s.plannedSeconds * 1000)
            dao.update(s.copy(status = 2, elapsedMillis = progress, focusSeconds = progress / 1000))
        }
    } }
    suspend fun resume(): Unit = lock.withLock {
        val s = dao.latest()
        if (s?.status == 2) dao.update(anchored(s.copy(status = 1)))
    }
    suspend fun finish(): Unit = lock.withLock { db.withTransaction {
        val s = dao.latest() ?: return@withTransaction
        if (s.status in listOf(1, 2)) {
            val progress = elapsed(s).coerceAtMost(s.plannedSeconds * 1000)
            dao.update(s.copy(status = 4, focusSeconds = progress / 1000, elapsedMillis = progress, endTime = clock.wall()))
        } else if (s.status == 3) dao.update(s.copy(status = 4, elapsedMillis = s.plannedSeconds * 1000))
    } }
    suspend fun rest(): Unit = lock.withLock {
        val s = dao.latest()
        if (s?.status == 4 && s.focusSeconds == s.plannedSeconds) dao.update(anchored(s.copy(status = 3, elapsedMillis = 0)))
    }
}
