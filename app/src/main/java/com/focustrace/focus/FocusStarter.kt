package com.focustrace.focus

import com.focustrace.data.AppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class FocusStartException(message: String) : IllegalStateException(message)

/** Common local entry point for starting a task from either tab. No room state is required. */
internal class FocusStarter(private val container: AppContainer) {
    private val lock = Mutex()

    suspend fun open(taskId: Long?, returnAnyActive: Boolean) = lock.withLock {
        container.lifecycle.awaitEvents()
        val engine = container.pomodoro
        val active = engine.refresh()
        if (active?.status in listOf(1, 2, 3)) {
            if (!returnAnyActive && active?.taskId != taskId) {
                throw FocusStartException("已有其他计时，请点击「返回计时」处理当前专注或休息。")
            }
            return@withLock
        }
        val task = taskId?.let { id ->
            container.taskRepository.allTasks.first().firstOrNull { it.id == id && !it.completed }
                ?: throw FocusStartException("待办已完成或已被删除，无法开始。")
        }
        val settings = container.settingsRepository.settings.first()
        if (task?.timerType == 1) engine.startStopwatch(task.id)
        else engine.start(task?.id, (task?.targetMinutes ?: settings.pomodoroMinutes) * 60L,
            settings.breakMinutes * 60L, settings.autoStartBreak, settings.autoStartFocus)
        container.startTimerNotification()
    }
}
