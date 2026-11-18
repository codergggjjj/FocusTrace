package com.focustrace.studyroom

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class StudyRoomConnectionTest {
    @Test fun closeBroadcastReachesJoinedGuest() = runBlocking<Unit> {
        exerciseConnection(graceful = true)
    }

    @Test fun unexpectedHostStopEndsRoomConnection() = runBlocking<Unit> {
        exerciseConnection(graceful = false)
    }

    @Test fun invitedGuestCanJoinWithoutLocalDiscovery() = runBlocking<Unit> {
        exerciseConnection(graceful = true, invited = true)
    }

    @Test fun staleInvitationCannotJoinEvenFromSameSubnet() = runBlocking<Unit> {
        withTimeout(8_000) {
            val ip = InetAddress.getLoopbackAddress()
            ServerSocket(0, ROOM_CAPACITY, ip).use { server ->
                val stop = CompletableDeferred<Unit>()
                val host = RoomHost("新房间", RoomProfile("房主"), RoomFocus(), { 0L }, "new-key") {}
                val job = launch { host.run(server, { true }, stop) }
                try {
                    Socket(ip, server.localPort).use { socket ->
                        val guest = RoomGuest(RoomProfile("同学"), RoomFocus(), { 0L }, "old-key") { error("旧邀请被接受") }
                        try { guest.run(socket, CompletableDeferred()); fail("旧邀请未被拒绝") }
                        catch (_: IOException) {}
                    }
                } finally { stop.complete(Unit); job.join() }
            }
        }
    }

    private suspend fun exerciseConnection(graceful: Boolean, invited: Boolean = false) = withTimeout(8_000) {
        val loopback = InetAddress.getLoopbackAddress()
        ServerSocket(0, ROOM_CAPACITY, loopback).use { server ->
            val clock = { System.nanoTime() / 1_000_000L }
            val hostRosters = MutableStateFlow<RoomRoster?>(null)
            val guestRosters = MutableStateFlow<RoomRoster?>(null)
            val hostStop = CompletableDeferred<Unit>()
            val host = RoomHost("自习室", RoomProfile("房主"), RoomFocus(), clock, "valid-key") { hostRosters.value = it }
            val hostJob = launch { host.run(server, { !invited && it.inetAddress == loopback }, hostStop) }
            Socket(loopback, server.localPort).use { socket ->
                try {
                    val continuing = RoomFocus(RoomStatus.FOCUSING, "操作系统", 123, true)
                    val guest = RoomGuest(RoomProfile("同学"), continuing, clock, if (invited) "valid-key" else "") { guestRosters.value = it }
                    val disconnected = async {
                        try { guest.run(socket, CompletableDeferred<Unit>()); error("No disconnect event") }
                        catch (e: IOException) { e }
                    }
                    val room = guestRosters.filterNotNull().first()
                    assertEquals(2, room.members.size)
                    val self = room.members.single { it.id == room.selfId }
                    assertEquals(continuing, self.focus)
                    val paused = continuing.copy(status = RoomStatus.PAUSED, ticking = false)
                    guest.updateSelf(paused)
                    val hostView = hostRosters.filterNotNull().first { roster ->
                        roster.members.any { it.id == self.id && it.focus.status == RoomStatus.PAUSED }
                    }
                    assertEquals(paused, hostView.members.single { it.id == self.id }.focus)
                    if (graceful) hostStop.complete(Unit) else hostJob.cancel()
                    assertEquals(graceful, disconnected.await() is RoomClosed)
                    hostJob.join()
                } finally { hostJob.cancel(); server.close() }
            }
        }
    }
}
