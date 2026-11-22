package com.focustrace

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.focustrace.focus.MusicSessionTarget
import com.focustrace.focus.MusicSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ExternalMusicSessionTest {
    @Test fun nextAndOwnedPauseReachOnlySelectedSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var qq: MediaSession
        lateinit var netease: MediaSession
        instrumentation.runOnMainSync {
            qq = MediaSession(context, "focustrace-test-qq")
            netease = MediaSession(context, "focustrace-test-netease")
        }
        val delivered = CopyOnWriteArrayList<String>()
        val next = CountDownLatch(1)
        val paused = CountDownLatch(1)
        fun attach(session: MediaSession, label: String) {
            session.setCallback(object : MediaSession.Callback() {
                override fun onSkipToNext() {
                    delivered += "$label-next"
                    next.countDown()
                }
                override fun onPause() {
                    delivered += "$label-pause"
                    paused.countDown()
                }
            }, Handler(Looper.getMainLooper()))
            session.setPlaybackState(PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_PAUSE)
                .setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        }
        try {
            attach(qq, "qq")
            attach(netease, "netease")
            val packages = mapOf(qq.sessionToken to MusicSource.QQ.packageName!!,
                netease.sessionToken to MusicSource.NETEASE.packageName!!)
            val target = MusicSessionTarget<MediaController>({ packages.getValue(it.sessionToken) }, { it.sessionToken })
            target.bind(listOf(qq.controller, netease.controller), MusicSource.NETEASE) { _, _ -> }
            target.command(true) { it.transportControls.skipToNext() }
            assertTrue("Next callback timed out", next.await(3, TimeUnit.SECONDS))
            target.release { it.transportControls.pause() }
            assertTrue("Pause callback timed out", paused.await(3, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertEquals(listOf("netease-next", "netease-pause"), delivered.toList())
        } finally {
            netease.release()
            qq.release()
        }
    }
}
