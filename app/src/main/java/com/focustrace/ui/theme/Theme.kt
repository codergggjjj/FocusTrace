package com.focustrace.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.focustrace.data.datastore.ThemeMode
private val LightColors = lightColorScheme(primary = Color(0xFF416653), secondary = Color(0xFF526456), background = Color(0xFFF8FAF5), surface = Color(0xFFF8FAF5))
private val DarkColors = darkColorScheme(primary = Color(0xFFA6D0B4), secondary = Color(0xFFBACCBF), background = Color(0xFF101510), surface = Color(0xFF101510))
@Composable
fun FocusTraceTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
