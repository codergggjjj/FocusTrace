package com.focustrace.studyroom

import com.focustrace.data.local.entity.FocusSessionEntity
import com.focustrace.focus.TimerInstant
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class StudyRoomTest {
    private val session = FocusSessionEntity(type = 0, startTime = 1, plannedSeconds = 1500,
        status = 1, elapsedMillis = 120_000, taskTitleSnapshot = "操作系统",
        backgroundWall = 200_000, backgroundElapsed = 400_000, backgroundBoot = 1,
        backgroundThresholdMillis = 3_000)

    @Test fun distractionThresholdUsesMonotonicClockAndFreezesFocusTime() {
        val pending = roomFocus(session, TimerInstant(999_000, 402_999, 1), 120_000)
        assertEquals(RoomStatus.FOCUSING, pending.status)
        assertFalse(pending.ticking)
        assertEquals(RoomFocus(RoomStatus.DISTRACTED, "操作系统", 120, false, 1500),
            roomFocus(session, TimerInstant(0, 403_000, 1), 120_000))
        val locked = roomFocus(session.copy(backgroundWall = null), TimerInstant(0, 404_000, 1), 124_000)
        assertEquals(124L, locked.seconds)
        assertTrue(locked.ticking)
    }

    @Test fun pauseAndBreakShowEffectiveFocusNotWallOrRestTime() {
        val at = TimerInstant(0, 0, 1)
        assertEquals(120L, roomFocus(session.copy(status = 2), at, 120_000).seconds)
        val rest = roomFocus(session.copy(status = 3, focusSeconds = 1500), at, 45_000)
        assertEquals(RoomStatus.RESTING, rest.status)
        assertEquals(1500L, rest.seconds)
        assertFalse(rest.ticking)
        assertEquals(RoomFocus(), roomFocus(session.copy(status = 4), at, 120_000))
    }

    @Test fun welcomeAndUpdatesRoundTripAndOversizedFramesAreRejectedBeforeAllocation() {
        val focus = RoomFocus(RoomStatus.FOCUSING, "算法", 60, true, 1500, todaySeconds = 7200)
        val welcome = RoomMessage.Welcome("夜间自习", 2, listOf(
            RoomMember(1, RoomProfile("小林"), focus), RoomMember(2, RoomProfile("我"))))
        fun roundTrip(message: RoomMessage): RoomMessage {
            val bytes = ByteArrayOutputStream()
            RoomWire.write(DataOutputStream(bytes), message)
            return RoomWire.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
        }
        assertEquals(welcome, roundTrip(welcome))
        assertEquals(RoomMessage.Update(1, focus), roundTrip(RoomMessage.Update(1, focus)))
        assertEquals(RoomMessage.Closed, roundTrip(RoomMessage.Closed))
        val invalid = ByteArrayOutputStream().also { DataOutputStream(it).writeInt(Int.MAX_VALUE) }
        assertThrows(IOException::class.java) {
            RoomWire.read(DataInputStream(ByteArrayInputStream(invalid.toByteArray())))
        }
    }

    @Test fun onlyAcceptsAnotherIpv4DeviceOnTheSelectedSubnet() {
        fun ip(address: String) = java.net.InetAddress.getByName(address)
        val local = ip("192.168.10.6") as java.net.Inet4Address
        assertTrue(isRoomPeerAddress(local, ip("192.168.10.126"), 25))
        assertFalse(isRoomPeerAddress(local, ip("192.168.10.130"), 25))
        assertFalse(isRoomPeerAddress(local, local, 25))
        assertFalse(isRoomPeerAddress(local, ip("2001:db8::1"), 25))
    }

    @Test fun displayedTimerIsCappedAndDoesNotGrowWhenPaused() {
        val focus = RoomFocus(RoomStatus.FOCUSING, null, 1495, true, 1500)
        assertEquals(1500L, focus.displaySeconds(42, 10_042))
        assertEquals(1495L, focus.copy(status = RoomStatus.PAUSED, ticking = false).displaySeconds(42, 10_042))
    }
}
