package com.focustrace

import android.content.ActivityNotFoundException
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import com.focustrace.ui.settings.ProfileScreen
import com.focustrace.ui.settings.SettingsViewModel
import com.focustrace.ui.theme.FocusTraceTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppUpdateUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun profileShowsInstalledVersionAndOpensLatestRelease() {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        var opened: String? = null
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened = uri }
            }) {
                FocusTraceTheme {
                    ProfileScreen(viewModel(factory = viewModelFactory {
                        initializer { SettingsViewModel(app.container.settingsRepository, app.container.backupRepository) }
                    }))
                }
            }
        }
        compose.onNodeWithTag("profile-app-update").performScrollTo().performClick()
        @Suppress("DEPRECATION")
        val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName
        compose.onNodeWithText("当前版本：v$version").assertIsDisplayed()
        compose.onNodeWithTag("app-update-open-release").performClick()
        compose.runOnIdle { assertEquals("https://github.com/codergggjjj/FocusTrace/releases/latest", opened) }
        compose.onNodeWithTag("app-update-dialog").assertDoesNotExist()
    }

    @Test fun missingBrowserAllowsRetryAndRestoresDialog() {
        val app = ApplicationProvider.getApplicationContext<FocusTraceApplication>()
        var attempts = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) {
                    attempts++
                    if (attempts == 1) throw ActivityNotFoundException("No browser")
                }
            }) {
                FocusTraceTheme {
                    ProfileScreen(viewModel(factory = viewModelFactory {
                        initializer { SettingsViewModel(app.container.settingsRepository, app.container.backupRepository) }
                    }))
                }
            }
        }
        compose.onNodeWithTag("profile-app-update").performScrollTo().performClick()
        compose.onNodeWithTag("app-update-open-release").performClick()
        compose.onNodeWithText("无法打开浏览器，请安装或启用浏览器后重试。").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("app-update-dialog").assertIsDisplayed()
        compose.onNodeWithText("无法打开浏览器，请安装或启用浏览器后重试。").assertIsDisplayed()
        compose.onNodeWithTag("app-update-open-release").performClick()
        compose.runOnIdle { assertEquals(2, attempts) }
        compose.onNodeWithTag("app-update-dialog").assertDoesNotExist()
    }
}
