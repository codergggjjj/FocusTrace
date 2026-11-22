package com.focustrace.focus

import android.media.session.PlaybackState
import org.junit.Assert.*
import org.junit.Test

class ExternalMusicTargetTest {
    private class Player(val app: String, val token: Int) {
        val commands = mutableListOf<String>()
    }

    @Test fun twoSessionsOnlyRequestedPlatformReceivesCommandsAndLifecyclePause() {
        val qq = Player("com.tencent.qqmusic", 1)
        val netease = Player("com.netease.cloudmusic", 2)
        val target = MusicSessionTarget<Player>({ it.app }, { it.token })
        val sessions = listOf(qq, netease)
        target.bind(sessions, MusicSource.NETEASE) { _, _ -> }
        target.release { it.commands += "pause" }
        target.command(false) { it.commands += "previous" }
        target.command(true) { it.commands += "play" }
        target.bind(sessions.reversed(), MusicSource.NETEASE) { _, _ -> }
        target.command(true) { it.commands += "next" }
        repeat(2) { target.release { it.commands += "pause" } }
        assertEquals(listOf("play", "next", "pause"), netease.commands)
        assertTrue(qq.commands.isEmpty())

        target.bind(sessions, MusicSource.QQ) { _, _ -> }
        target.command(true) { it.commands += "previous" }
        val replacement = Player(qq.app, 3)
        target.bind(listOf(replacement, qq, netease), MusicSource.QQ) { _, _ -> }
        assertSame(qq, target.current)
        target.bind(listOf(replacement, netease), MusicSource.QQ) { _, _ -> }
        target.release { it.commands += "pause" }
        assertTrue(replacement.commands.isEmpty())
        assertEquals(listOf("previous"), qq.commands)

        target.bind(listOf(netease), MusicSource.QQ) { _, _ -> }
        target.command(true) { fail("Missing QQ session should disable command") }
        assertNull(target.current)
    }

    @Test fun toggleSelectsSupportedDirectionAndSupportsToggleOnlyPlayers() {
        val toggle = PlaybackState.ACTION_PLAY_PAUSE
        assertEquals(toggle, toggleAction(true, toggle))
        assertEquals(toggle, toggleAction(false, toggle))
        assertEquals(PlaybackState.ACTION_PAUSE, toggleAction(true, toggle or PlaybackState.ACTION_PAUSE))
        assertEquals(PlaybackState.ACTION_PLAY, toggleAction(false, toggle or PlaybackState.ACTION_PLAY))
        assertEquals(0L, toggleAction(true, PlaybackState.ACTION_PLAY))
        assertEquals(0L, toggleAction(false, PlaybackState.ACTION_PAUSE))
    }
}
