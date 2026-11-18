package com.focustrace.studyroom

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.focus.TimerClock
import com.focustrace.focus.TimerInstant
import com.focustrace.focus.snapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket

internal sealed interface StudyRoomState {
    data object Lobby : StudyRoomState
    data class Connecting(val hosting: Boolean) : StudyRoomState
    data class Joined(val roster: RoomRoster) : StudyRoomState
}

internal class StudyRoomController(
    private val context: Context,
    private val sessions: Flow<FocusSessionEntity?>,
    private val dailyTotals: Flow<Long>,
    private val clock: TimerClock,
    private val elapsed: (FocusSessionEntity, TimerInstant) -> Long
) {
    private sealed interface Destination {
        data class Host(val name: String) : Destination
        data class Guest(val listing: RoomListing) : Destination
    }
    private class Request(val destination: Destination) {
        val stop = CompletableDeferred<Unit>()
        var joined = false
        var abort: () -> Unit = {}
        var wifiLost = false
        var latestFocus = RoomFocus()
    }

    private val lan = RoomLan(context)
    val profile = RoomProfileStore(context)
    private var request: Request? = null
    private val mutableState = MutableStateFlow<StudyRoomState>(StudyRoomState.Lobby)
    val state = mutableState.asStateFlow()
    private val mutableDiscovery = MutableStateFlow(RoomDiscovery())
    val discovery = mutableDiscovery.asStateFlow()
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice = mutableNotice.asStateFlow()

    fun consumeNotice(text: String) { mutableNotice.compareAndSet(text, null) }
    suspend fun browse() = lan.browse { mutableDiscovery.value = it }
    fun create(name: String) = begin(Destination.Host(name.trim().take(ROOM_NAME_LIMIT)))
    fun join(room: RoomListing) = begin(Destination.Guest(room))
    fun requestStop() { request?.stop?.complete(Unit) }
    // Called only when the independently owned room service is being stopped externally.
    fun abortTransport() { request?.abort?.invoke() }

    private fun begin(destination: Destination) {
        if (request != null) return
        if (destination is Destination.Host && destination.name.isBlank()) return
        if (lan.wifi() == null) {
            mutableNotice.value = "请先连接 Wi-Fi，再使用自习室"
            return
        }
        request = Request(destination)
        mutableState.value = StudyRoomState.Connecting(destination is Destination.Host)
        try {
            ContextCompat.startForegroundService(context, Intent(context, StudyRoomService::class.java))
        } catch (_: RuntimeException) {
            serviceUnavailable()
        }
    }

    fun serviceUnavailable() {
        request = null
        mutableState.value = StudyRoomState.Lobby
        mutableNotice.value = "暂时无法保持自习室连接，请在应用内重试"
    }

    suspend fun runForeground(stopService: () -> Unit) {
        val req = request ?: run { stopService(); return }
        try {
            coroutineScope {
                val runner = launch { connect(req) }
                val stopConnecting = launch {
                    req.stop.await()
                    if (!req.joined) {
                        req.abort()
                        runner.cancel()
                    }
                }
                try { runner.join() } finally { stopConnecting.cancel() }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            if (!req.stop.isCompleted) {
                val message = when {
                    req.wifiLost -> "Wi-Fi 已断开或切换，已离开自习室"
                    failure is RoomClosed -> "自习室已关闭"
                    req.joined -> "与房主的连接已断开"
                    failure is IOException && failure.message?.any { it in '\u4e00'..'\u9fff' } == true -> failure.message!!
                    else -> "无法连接自习室，请确认设备在同一 Wi-Fi 下，且路由器允许设备互连"
                }
                mutableNotice.value = if (req.latestFocus.status != RoomStatus.IDLE) "$message，本轮专注不受影响" else message
            }
        } finally {
            req.abort()
            if (request === req) {
                stopService()
                request = null
                mutableState.value = StudyRoomState.Lobby
            }
        }
    }

    private suspend fun connect(req: Request) = coroutineScope {
        val wifi = lan.wifi() ?: throw IOException("请先连接 Wi-Fi，再使用自习室")
        val identity = profile.profile.first()
        val initial = presence(sessions.first()).copy(todaySeconds = dailyTotals.first()).also { req.latestFocus = it }
        fun joined(roster: RoomRoster) {
            req.joined = true
            mutableState.value = StudyRoomState.Joined(roster)
        }
        val watchdog = launch {
            while (isActive) {
                delay(2_000)
                if (lan.wifi() != wifi) {
                    req.wifiLost = true
                    req.abort()
                    throw IOException("Wi-Fi 连接已变更")
                }
            }
        }
        try {
            when (val destination = req.destination) {
                is Destination.Host -> {
                    val server = ServerSocket()
                    req.abort = { runCatching { server.close() } }
                    withContext(Dispatchers.IO) {
                        server.bind(InetSocketAddress(wifi.address, 0), ROOM_CAPACITY)
                        server.soTimeout = 1_000
                    }
                    val ready = CompletableDeferred<String>()
                    val advertising = launch { lan.advertise(wifi, destination.name, server.localPort) { ready.complete(it) } }
                    try {
                        val host = RoomHost(ready.await(), identity, initial, clock::elapsed, ::joined)
                        val updates = launch { shareFocus(req, host::updateSelf) }
                        try { host.run(server, { wifi.contains(it.inetAddress) }, req.stop) }
                        finally { updates.cancel() }
                    } finally { advertising.cancel() }
                }
                is Destination.Guest -> {
                    val (address, port) = lan.resolve(destination.listing, wifi)
                    val socket = wifi.network.socketFactory.createSocket()
                    req.abort = socket::closeQuietly
                    withContext(Dispatchers.IO) {
                        socket.tcpNoDelay = true
                        socket.soTimeout = PEER_TIMEOUT_MS
                        socket.connect(InetSocketAddress(address, port), 5_000)
                    }
                    val guest = RoomGuest(identity, initial, clock::elapsed, ::joined)
                    val updates = launch { shareFocus(req, guest::updateSelf) }
                    try { guest.run(socket, req.stop) } finally { updates.cancel(); socket.closeQuietly() }
                }
            }
        } finally { watchdog.cancel(); req.abort() }
    }

    private fun presence(session: FocusSessionEntity?, at: TimerInstant = clock.snapshot()) =
        roomFocus(session, at, session?.let { elapsed(it, at) } ?: 0L)

    private suspend fun shareFocus(req: Request, send: (RoomFocus) -> Unit) {
        combine(sessions, dailyTotals) { session, total -> session to total }.collectLatest { (session, total) ->
            while (currentCoroutineContext().isActive) {
                val at = clock.snapshot()
                val focus = presence(session, at).copy(todaySeconds = total)
                req.latestFocus = focus
                send(focus)
                val deadline = departureRemaining(session, at)?.takeIf { it > 0 }
                delay(minOf(HEARTBEAT_MS, deadline ?: HEARTBEAT_MS))
            }
        }
    }
}
