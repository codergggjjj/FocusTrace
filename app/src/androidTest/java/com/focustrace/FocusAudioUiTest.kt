package com.focustrace

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import com.focustrace.ui.focus.FocusHomeScreen
import com.focustrace.ui.focus.FocusViewModel
import com.focustrace.ui.theme.FocusTraceTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class FocusAudioUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun focusScreenOffersImportAndMusicSelection() {
        val container = ApplicationProvider.getApplicationContext<FocusTraceApplication>().container
        runBlocking {
            container.lifecycle.awaitEvents()
            container.pomodoro.finish()
            container.pomodoro.startStopwatch(null)
        }
        try {
            compose.setContent {
                FocusTraceTheme {
                    FocusHomeScreen(viewModel(factory = viewModelFactory {
                        initializer { FocusViewModel(container) }
                    }), onExit = {}, onReport = {})
                }
            }
            compose.waitUntil(5000) { compose.onAllNodesWithText("正在专注").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("专注音乐").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("focus-audio-library").performClick()
            compose.onNodeWithTag("focus-audio-import").assertIsDisplayed()
        } finally { runBlocking { container.pomodoro.finish() } }
    }
}
