package com.focustrace

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.focustrace.data.repository.FocusAudioRepository
import com.focustrace.notification.FocusTimerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FocusAudioTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<FocusTraceApplication>()

    @Test fun importCopiesAudioAndRejectsInvalidInputWithoutChangingLibrary() = runBlocking<Unit> {
        val repository = app.container.focusAudioRepository
        val previous = repository.library.first()
        val source = makeWave()
        val invalid = File.createTempFile("audio_invalid_", ".txt", app.cacheDir).apply { writeText("not audio") }
        val track = repository.importAudio(Uri.fromFile(source))
        try {
            assertTrue(source.delete())
            repository.open(track).use { assertTrue(it.statSize > 44) }
            val recreated = FocusAudioRepository(app).library.first()
            assertEquals(track.id, recreated.selectedId)
            assertTrue(recreated.tracks.contains(track))
            try { repository.importAudio(Uri.fromFile(invalid)); fail("Invalid audio was accepted") }
            catch (expected: Exception) { assertFalse(expected is CancellationException) }
            assertEquals(recreated, repository.library.first())
        } finally {
            repository.remove(track.id)
            previous.selectedId?.let { repository.select(it) }
            source.delete()
            invalid.delete()
        }
    }

    @Test fun playbackPausesWithFocusAndStopsOnFinishWithoutChangingTimer() = runBlocking<Unit> {
        val container = app.container
        val repository = container.focusAudioRepository
        val previous = repository.library.first()
        val source = makeWave()
        val track = repository.importAudio(Uri.fromFile(source))
        val audio = container.focusAudioPlayer
        try {
            container.lifecycle.awaitEvents()
            container.pomodoro.finish()
            container.pomodoro.startStopwatch(null)
            val session = requireNotNull(container.database.focusSessionDao().latest())
            withTimeout(5000) { audio.state.first { it.canPlay && it.track?.id == track.id } }
            withContext(Dispatchers.Main) { audio.play() }
            withTimeout(5000) { audio.state.first { it.playing } }
            assertEquals(session.id, container.database.focusSessionDao().latest()?.id)
            withContext(Dispatchers.Main) { audio.pause() }
            assertFalse(audio.state.value.playing)
            assertEquals(1, container.database.focusSessionDao().latest()?.status)
            withContext(Dispatchers.Main) { audio.play() }
            withTimeout(5000) { audio.state.first { it.playing } }
            container.pomodoro.pause()
            withTimeout(5000) { audio.state.first { !it.canPlay && !it.playing } }
            container.pomodoro.resume()
            withTimeout(5000) { audio.state.first { it.canPlay } }
            assertFalse(audio.state.value.playing) // Explicit user resume, never surprise autoplay.
            withContext(Dispatchers.Main) { audio.play() }
            withTimeout(5000) { audio.state.first { it.playing } }
            // An external-app background marker alone must not stop the audio.
            container.lifecycle.transition(false, false)
            container.lifecycle.awaitEvents()
            withContext(Dispatchers.Main) { assertTrue(audio.state.value.playing) }
            container.lifecycle.transition(false, true)
            container.lifecycle.awaitEvents()
            withContext(Dispatchers.Main) { assertTrue(audio.state.value.playing) }
            container.pomodoro.finish()
            withTimeout(5000) { audio.state.first { !it.canPlay && !it.foregroundActive } }
        } finally {
            container.pomodoro.finish()
            withContext(Dispatchers.Main) { audio.stop() }
            app.stopService(Intent(app, FocusTimerService::class.java))
            repository.remove(track.id)
            previous.selectedId?.let { repository.select(it) }
            source.delete()
        }
    }

    @Test fun batchImportAndPlaylistWrapRemainPausedForNewSession() = runBlocking<Unit> {
        val container = app.container
        val repository = container.focusAudioRepository
        val previous = repository.library.first()
        val first = makeWave(2)
        val second = makeWave(2)
        val invalid = File.createTempFile("audio_batch_invalid_", ".txt", app.cacheDir).apply { writeText("invalid") }
        val audio = container.focusAudioPlayer
        val model = com.focustrace.ui.settings.MusicLibraryViewModel(repository, audio)
        val collector = launch { model.uiState.collect {} }
        try {
            withContext(Dispatchers.Main) { model.importAudio(listOf(Uri.fromFile(first), Uri.fromFile(invalid), Uri.fromFile(second))) }
            withTimeout(10000) { model.uiState.first { !it.importing && it.message?.contains("已导入 2 首，1 首失败") == true } }
            val library = repository.library.first()
            val added = library.tracks.filter { track -> previous.tracks.none { it.id == track.id } }
            assertEquals(2, added.size)
            repository.select(added.first().id)
            container.lifecycle.awaitEvents()
            container.pomodoro.finish()
            container.pomodoro.startStopwatch(null)
            withTimeout(5000) { audio.state.first { it.canPlay && it.track?.id == added.first().id } }
            assertFalse(audio.state.value.foregroundActive)
            withContext(Dispatchers.Main) { audio.play() }
            withTimeout(5000) { audio.state.first { it.playing && it.track?.id == added.first().id } }
            withTimeout(6000) { audio.state.first { it.playing && it.track?.id == added.last().id } }
            withTimeout(6000) { audio.state.first { it.playing && it.track?.id == library.tracks.first().id } }
            withContext(Dispatchers.Main) { audio.pause() }
            delay(2200)
            assertFalse(audio.state.value.foregroundActive)
            container.pomodoro.finish()
            withTimeout(5000) { audio.state.first { !it.canPlay } }
            container.pomodoro.startStopwatch(null)
            withTimeout(5000) { audio.state.first { it.canPlay } }
            assertFalse(audio.state.value.foregroundActive)
        } finally {
            collector.cancelAndJoin()
            container.pomodoro.finish()
            withContext(Dispatchers.Main) { audio.stop() }
            app.stopService(Intent(app, FocusTimerService::class.java))
            repository.library.first().tracks.filter { track -> previous.tracks.none { it.id == track.id } }.forEach { repository.remove(it.id) }
            previous.selectedId?.let { repository.select(it) }
            first.delete(); second.delete(); invalid.delete()
        }
    }

    @Test fun newSessionResumesLastAutomaticallyPlayedTrack() = runBlocking<Unit> {
        val container = app.container
        val repository = container.focusAudioRepository
        val previous = repository.library.first()
        val first = makeWave(2)
        val second = makeWave(10)
        val added = mutableListOf<com.focustrace.data.repository.FocusAudioTrack>()
        val audio = container.focusAudioPlayer
        try {
            added += repository.importAudio(Uri.fromFile(first))
            added += repository.importAudio(Uri.fromFile(second))
            repository.select(added.first().id)
            container.lifecycle.awaitEvents()
            container.pomodoro.finish()
            container.pomodoro.startStopwatch(null)
            withTimeout(5000) { audio.state.first { it.canPlay && it.track?.id == added.first().id } }
            withContext(Dispatchers.Main) { audio.play() }
            withTimeout(6000) { audio.state.first { it.playing && it.track?.id == added.last().id } }
            withContext(Dispatchers.Main) { audio.pause() }
            container.pomodoro.finish()
            withTimeout(5000) { audio.state.first { !it.canPlay } }
            container.pomodoro.startStopwatch(null)
            withTimeout(5000) { audio.state.first { it.canPlay } }
            assertEquals("New session must keep the last played song", added.last().id, audio.state.value.track?.id)
            assertFalse(audio.state.value.foregroundActive)
            withTimeout(5000) { repository.library.first { it.selectedId == added.last().id } }
            assertEquals(added.last().id, FocusAudioRepository(app).library.first().selectedId)
            // Choosing another track explicitly still overrides the remembered song.
            repository.select(added.first().id)
            withTimeout(5000) { audio.state.first { it.track?.id == added.first().id } }
            assertFalse(audio.state.value.foregroundActive)
            assertFalse(repository.rememberPlaying(added.last().id, added.last().id))
            assertEquals(added.first().id, repository.library.first().selectedId)
            repository.remove(added.first().id)
            withTimeout(5000) { audio.state.first { it.track != null && it.track?.id != added.first().id } }
            assertFalse(audio.state.value.foregroundActive)
        } finally {
            container.pomodoro.finish()
            withContext(Dispatchers.Main) { audio.stop() }
            app.stopService(Intent(app, FocusTimerService::class.java))
            val remaining = repository.library.first().tracks.map { it.id }.toSet()
            added.filter { it.id in remaining }.forEach { repository.remove(it.id) }
            previous.selectedId?.let { repository.select(it) }
            first.delete()
            second.delete()
        }
    }

    private fun makeWave(seconds: Int = 10): File {
        val samples = 8_000 * seconds
        val buffer = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        buffer.putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { buffer.putShort((kotlin.math.sin(it * 2 * Math.PI * 220 / 8_000) * 500).toInt().toShort()) }
        return File.createTempFile("audio_test_", ".wav", app.cacheDir).apply { writeBytes(buffer.array()) }
    }
}
