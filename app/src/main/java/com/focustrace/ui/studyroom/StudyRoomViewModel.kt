package com.focustrace.ui.studyroom

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focustrace.data.AppContainer
import com.focustrace.focus.FocusStartException
import com.focustrace.studyroom.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal class StudyRoomViewModel(private val container: AppContainer) : ViewModel() {
    private val room = container.studyRoom
    val state = room.state
    val discovery = room.discovery
    val profile = room.profile.profile.catch { error ->
        if (error !is java.io.IOException) throw error
        emit(RoomProfile())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoomProfile())
    val activeFocus = container.database.focusSessionDao().observeLatest()
        .map { it?.status in listOf(1, 2, 3) }.distinctUntilChanged()
    val choices = container.taskRepository.allTasks.onStart { container.taskRepository.refreshRepeats() }
        .map { tasks ->
            val today = java.time.LocalDate.now().toString()
            tasks.filter { !it.completed && (it.repeatDate == null || it.repeatDate == today) }
        }
    val defaultMinutes = container.settingsRepository.settings.map { it.pomodoroMinutes }.distinctUntilChanged()
    val starting = MutableStateFlow(false)
    val startError = MutableStateFlow<String?>(null)

    suspend fun browse() = room.browse()
    fun create(name: String) = room.create(name)
    fun join(listing: RoomListing) = room.join(listing)
    fun leave() = room.requestStop()
    suspend fun importAvatar(uri: Uri) = room.profile.import(uri)
    suspend fun saveProfile(nickname: String, avatar: String) = room.profile.save(nickname, avatar)

    fun start(taskId: Long?, onStarted: () -> Unit) {
        if (!starting.compareAndSet(false, true)) return
        startError.value = null
        viewModelScope.launch {
            try {
                container.focusStarter.open(taskId, returnAnyActive = true)
                onStarted()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: FocusStartException) {
                startError.value = failure.message
            } catch (_: Exception) {
                startError.value = "无法开始专注，请重试。"
            } finally { starting.value = false }
        }
    }
}
