package com.focustrace.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustrace.data.AppContainer
import com.focustrace.data.datastore.UserSettings
import com.focustrace.data.local.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class FocusUiState(val ready: Boolean = false, val session: FocusSessionEntity? = null,
    val tasks: List<TaskEntity> = emptyList(), val settings: UserSettings = UserSettings(),
    val distractions: List<DistractionEventEntity> = emptyList(), val lifecycleError: String? = null,
    val busy: Boolean = false, val error: String? = null, val backgroundId: String? = null)
data class TimerUiState(val remainingSeconds: Long = 0, val elapsedSeconds: Long = 0)
class FocusViewModel(private val container: AppContainer) : ViewModel() {
    private val engine = container.pomodoro
    private val _state = MutableStateFlow(FocusUiState())
    val uiState = _state.asStateFlow()
    private val _timer = MutableStateFlow(TimerUiState())
    val timer = _timer.asStateFlow()
    init {
        viewModelScope.launch {
            uiState.map { state ->
                Triple(state.session?.id, state.session?.status in listOf(1, 2, 3), state.settings)
            }.distinctUntilChanged().collectLatest { (id, active, settings) ->
                val background = try {
                    when {
                        !active || id == null || !settings.focusBackgroundEnabled -> null
                        settings.focusBackgroundCustomPath != null -> settings.focusBackgroundCustomPath
                        settings.focusBackgroundRandom && settings.focusBackgroundIds.isNotEmpty() ->
                            container.settingsRepository.imageForSession(id)
                        !settings.focusBackgroundRandom -> settings.focusBackgroundSelectedId
                        else -> null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(error = "无法加载专注背景") }
                    null
                }
                _state.update { it.copy(backgroundId = background) }
            }
        }
        viewModelScope.launch {
            container.lifecycle.error.collect { message -> _state.update { it.copy(lifecycleError = message) } }
        }
        viewModelScope.launch {
            uiState.map { it.session?.id }.distinctUntilChanged().collectLatest { id ->
                if (id == null) _state.update { it.copy(distractions = emptyList()) }
                else container.focusRepository.distractionsFor(id)
                    .catch { _state.update { it.copy(error = "无法读取分心记录") } }
                    .collect { events -> _state.update { it.copy(distractions = events) } }
            }
        }
        viewModelScope.launch {
            combine(container.settingsRepository.settings, container.taskRepository.allTasks) { settings, tasks -> settings to tasks }
                .catch { _state.update { it.copy(error = "无法读取设置或待办") } }
                .collect { (settings, tasks) -> _state.update { it.copy(settings = settings, tasks = tasks) } }
        }
        viewModelScope.launch {
            container.database.focusSessionDao().observeLatest().distinctUntilChanged()
                .retryWhen { cause, _ ->
                    if (cause is CancellationException) throw cause
                    _state.update { it.copy(error = "无法恢复专注记录，请重试") }
                    delay(1000)
                    true
                }.collectLatest { session ->
                // Keep active timer transitions, but do not poll Room for paused/finished/absent sessions.
                do {
                    try { refresh() } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { _state.update { it.copy(error = "无法恢复专注记录，请重试") } }
                    if (session?.status !in listOf(1, 3) || session?.backgroundWall != null) break
                    delay(1000)
                } while (isActive)
            }
        }
    }
    private suspend fun refresh() {
        container.lifecycle.awaitEvents()
        val s = engine.refresh()
        val total = if (s?.status == 3) s.restSeconds else s?.plannedSeconds ?: 0
        val elapsed = s?.let(engine::elapsed) ?: 0
        val remaining = if (s == null || s.status == 4) 0 else ((total * 1000 - elapsed).coerceAtLeast(0) + 999) / 1000
        _timer.value = TimerUiState(remaining, elapsed / 1000)
        _state.update { it.copy(ready = true, session = s) }
    }
    private fun action(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { container.lifecycle.awaitEvents(); block(); refresh() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = "操作失败，请重试；待办可能已被删除。") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun start(taskId: Long?, minutes: Int, rest: Int) = action {
        require(minutes in 1..1440 && rest in 1..1440)
        val settings = _state.value.settings
        engine.start(taskId, minutes * 60L, rest * 60L, settings.autoStartBreak, settings.autoStartFocus)
        container.startTimerNotification()
    }
    fun startStopwatch(taskId: Long?) = action { engine.startStopwatch(taskId); container.startTimerNotification() }
    fun setThreshold(seconds: Int) = action { container.settingsRepository.setDistractionThreshold(seconds) }
    fun pause() = action { engine.pause() }
    fun resume() = action { engine.resume() }
    fun finish() = action { engine.finish() }
    fun rest() = action { engine.rest(); container.startTimerNotification() }
}
