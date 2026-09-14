package com.focustrace.lifecycle

import com.focustrace.data.repository.SettingsRepository
import com.focustrace.focus.PomodoroEngine
import com.focustrace.focus.TimerClock
import com.focustrace.focus.TimerInstant
import com.focustrace.focus.snapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/** Application-owned ordered queue; survives page changes and ViewModel cancellation. */
class FocusLifecycleCoordinator(
    private val engine: PomodoroEngine,
    private val settings: SettingsRepository,
    private val clock: TimerClock
) {
    private sealed interface Event {
        data class Transition(val foreground: Boolean, val at: TimerInstant) : Event
        data class Barrier(val done: CompletableDeferred<Unit>) : Event
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        scope.launch {
            for (event in events) {
                if (event is Event.Barrier) { event.done.complete(Unit); continue }
                event as Event.Transition
                var threshold: Int? = null
                while (isActive) {
                    try {
                        if (event.foreground) engine.onForeground(event.at)
                        else {
                            val seconds = threshold ?: settings.settings.first().distractionThreshold.also { threshold = it }
                            engine.onBackground(seconds, event.at)
                        }
                        _error.value = null
                        break
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        _error.value = "分心记录暂时无法保存，正在重试。"
                        delay(1000)
                    }
                }
            }
        }
    }
    fun onBackground() { events.trySend(Event.Transition(false, clock.snapshot())) }
    fun onForeground() { events.trySend(Event.Transition(true, clock.snapshot())) }

    /** UI actions must not overtake a queued return-to-foreground event. */
    suspend fun awaitEvents() {
        val done = CompletableDeferred<Unit>()
        events.send(Event.Barrier(done))
        done.await()
    }
}
