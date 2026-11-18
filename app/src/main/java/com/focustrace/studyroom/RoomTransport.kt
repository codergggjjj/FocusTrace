package com.focustrace.studyroom

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/** One bounded writer per socket. Only the completed messages renew the read deadline. */
internal class RoomPeer(
    scope: CoroutineScope,
    private val socket: Socket,
    private val now: () -> Long,
    private val frameLimit: Int? = null
) {
    private val outbound = Channel<RoomMessage>(32)
    private var lastReadAt = now()
    var handshaken = false
    private var closed = false
    private var input: DataInputStream? = null
    private val writer = scope.launch(Dispatchers.IO) {
        try {
            val stream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            for (message in outbound) RoomWire.write(stream, message)
        } catch (_: IOException) {
            socket.closeQuietly()
        }
    }
    suspend fun read(): RoomMessage = withContext(Dispatchers.IO) {
        val stream = input ?: DataInputStream(BufferedInputStream(socket.getInputStream())).also { input = it }
        if (frameLimit == null) RoomWire.read(stream) else RoomWire.read(stream, frameLimit)
    }.also { lastReadAt = now() }

    fun send(message: RoomMessage) {
        if (!closed && outbound.trySend(message).isFailure) close()
    }
    fun expireIfNeeded() {
        if (now() - lastReadAt >= if (handshaken) PEER_TIMEOUT_MS.toLong() else 8_000L) close()
    }
    suspend fun drain() {
        outbound.close()
        withTimeoutOrNull(700) { writer.join() }
    }
    fun close() {
        if (closed) return
        closed = true
        socket.closeQuietly()
        outbound.cancel()
        writer.cancel()
    }
}

/** Host owns membership. Incoming clients can replace only their own focus snapshot. */
internal class RoomHost(
    private val name: String,
    private val profile: RoomProfile,
    initial: RoomFocus,
    private val now: () -> Long,
    private val publish: (RoomRoster) -> Unit
) {
    private val members = linkedMapOf(HOST_ID to RoomMember(HOST_ID, profile, initial, now()))
    private val connections = mutableSetOf<RoomPeer>()
    private val guests = linkedMapOf<Int, RoomPeer>()
    private var nextId = HOST_ID + 1

    private fun emit() = publish(RoomRoster(name, HOST_ID, members.values.toList()))
    private fun broadcast(message: RoomMessage, except: Int? = null) {
        guests.forEach { (id, peer) -> if (id != except) peer.send(message) }
    }
    fun updateSelf(focus: RoomFocus) {
        members[HOST_ID] = RoomMember(HOST_ID, profile, focus, now())
        emit()
        broadcast(RoomMessage.Update(HOST_ID, focus))
    }

    suspend fun run(server: ServerSocket, allow: (Socket) -> Boolean, stop: Deferred<Unit>) = coroutineScope {
        emit()
        val roomScope = this
        val accept = launch {
            while (isActive) {
                val socket = try { acceptSocket(server) }
                catch (_: SocketTimeoutException) { continue }
                catch (failure: IOException) {
                    currentCoroutineContext().ensureActive()
                    throw failure
                }
                if (!allow(socket)) { socket.closeQuietly(); continue }
                if (connections.size >= ROOM_CAPACITY - 1) {
                    // Keep the number of closing or unauthenticated sockets bounded as well.
                    launch(Dispatchers.IO) {
                        socket.use { runCatching { RoomWire.write(DataOutputStream(it.getOutputStream()), RoomMessage.Full) } }
                    }.join()
                    continue
                }
                val peer = RoomPeer(roomScope, socket, now, RoomWire.CLIENT_FRAME_LIMIT)
                connections += peer
                roomScope.launch { serve(peer) }
            }
        }
        val watchdog = launch {
            while (isActive) {
                delay(1_000)
                connections.forEach(RoomPeer::expireIfNeeded)
            }
        }
        try {
            stop.await()
            accept.cancel()
            broadcast(RoomMessage.Closed)
            withTimeoutOrNull(800) { guests.values.toList().map { async { it.drain() } }.awaitAll() }
        } finally {
            accept.cancel()
            watchdog.cancel()
            runCatching { server.close() }
            connections.toList().forEach(RoomPeer::close)
        }
    }

    private suspend fun serve(peer: RoomPeer) {
        var memberId: Int? = null
        try {
            val hello = peer.read() as? RoomMessage.Hello ?: throw IOException("无效的加入请求")
            val id = nextId++
            memberId = id
            peer.handshaken = true
            val member = RoomMember(id, hello.profile, hello.focus, now())
            members[id] = member
            guests[id] = peer
            val snapshotAt = now()
            val snapshot = members.values.map {
                it.copy(focus = it.focus.copy(seconds = it.focus.displaySeconds(it.receivedAt, snapshotAt)))
            }
            peer.send(RoomMessage.Welcome(name, id, snapshot))
            broadcast(RoomMessage.Joined(member), except = id)
            emit()
            while (currentCoroutineContext().isActive) {
                val update = peer.read() as? RoomMessage.Update ?: throw IOException("无效的成员更新")
                if (update.id != id) throw IOException("无效的成员编号")
                members[id] = member.copy(focus = update.focus, receivedAt = now())
                broadcast(update, except = id)
                emit()
            }
        } catch (_: IOException) {
            // A guest disappearing does not affect the room or the other guests.
        } finally {
            peer.close()
            connections.remove(peer)
            if (memberId != null) {
                members.remove(memberId)
                guests.remove(memberId)
                broadcast(RoomMessage.Left(memberId))
                emit()
            }
        }
    }
}

internal class RoomGuest(
    private val profile: RoomProfile,
    initial: RoomFocus,
    private val now: () -> Long,
    private val publish: (RoomRoster) -> Unit
) {
    private var focus = initial
    private var self: Int? = null
    private var name = ""
    private var connection: RoomPeer? = null
    private val members = linkedMapOf<Int, RoomMember>()

    private fun emit() { self?.let { publish(RoomRoster(name, it, members.values.toList())) } }
    fun updateSelf(next: RoomFocus) {
        focus = next
        self?.let { id ->
            members[id] = RoomMember(id, profile, next, now())
            connection?.send(RoomMessage.Update(id, next))
            emit()
        }
    }

    suspend fun run(socket: Socket, stop: Deferred<Unit>) = coroutineScope {
        val peer = RoomPeer(this, socket, now)
        connection = peer
        val stopMonitor = launch { stop.await(); peer.close() }
        val watchdog = launch {
            while (isActive) { delay(1_000); peer.expireIfNeeded() }
        }
        try {
            peer.send(RoomMessage.Hello(profile, focus))
            when (val welcome = peer.read()) {
                is RoomMessage.Welcome -> {
                    name = welcome.name
                    self = welcome.selfId
                    welcome.members.forEach { members[it.id] = it.copy(receivedAt = now()) }
                    peer.handshaken = true
                    updateSelf(focus)
                }
                RoomMessage.Full -> throw IOException("自习室已满（最多 $ROOM_CAPACITY 人）")
                RoomMessage.Closed -> throw RoomClosed()
                else -> throw IOException("无法加入自习室")
            }
            while (isActive && !stop.isCompleted) {
                when (val message = peer.read()) {
                    is RoomMessage.Joined -> {
                        if (members.size >= ROOM_CAPACITY || message.member.id in members) throw IOException("无效的成员列表")
                        members[message.member.id] = message.member.copy(receivedAt = now())
                    }
                    is RoomMessage.Left -> {
                        if (message.id == HOST_ID || message.id == self) throw IOException("无效的成员列表")
                        members.remove(message.id)
                    }
                    is RoomMessage.Update -> members[message.id]?.let {
                        if (message.id != self) members[message.id] = it.copy(focus = message.focus, receivedAt = now())
                    }
                    RoomMessage.Closed -> throw RoomClosed()
                    else -> throw IOException("无效的自习室同步消息")
                }
                emit()
            }
        } finally {
            self = null
            connection = null
            peer.close()
            stopMonitor.cancel()
            watchdog.cancel()
        }
    }
}

internal class RoomClosed : IOException("自习室已关闭")
internal fun Socket.closeQuietly() { runCatching { close() } }

private suspend fun acceptSocket(server: ServerSocket): Socket {
    var pending: Socket? = null
    try {
        return withContext(Dispatchers.IO) { server.accept().also { pending = it } }.also { pending = null }
    } finally { pending?.closeQuietly() }
}
