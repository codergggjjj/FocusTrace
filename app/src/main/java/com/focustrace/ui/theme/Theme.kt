package com.focustrace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustrace.data.datastore.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFFD6336C), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE9F0), onPrimaryContainer = Color(0xFF641F39),
    secondary = Color(0xFF087DA4), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF3FC), onSecondaryContainer = Color(0xFF075571),
    tertiary = Color(0xFF7854AA), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEFE6FB), onTertiaryContainer = Color(0xFF523476),
    background = Color(0xFFF7F7F9), onBackground = Color(0xFF242632),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF242632),
    surfaceVariant = Color(0xFFEEF0F6), onSurfaceVariant = Color(0xFF656B7B),
    surfaceTint = Color(0xFFFFFFFF),
    outline = Color(0xFF858C9D), outlineVariant = Color(0xFFE7EAF2),
    error = Color(0xFFBA1A1A), onError = Color.White
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFADCB), onPrimary = Color(0xFF650C32),
    primaryContainer = Color(0xFF572138), onPrimaryContainer = Color(0xFFFFD9E7),
    secondary = Color(0xFF89D4F3), onSecondary = Color(0xFF003548),
    secondaryContainer = Color(0xFF143E50), onSecondaryContainer = Color(0xFFC4EDFF),
    tertiary = Color(0xFFD6BBFF), onTertiary = Color(0xFF402066),
    tertiaryContainer = Color(0xFF493361), onTertiaryContainer = Color(0xFFECDDFF),
    background = Color(0xFF15171E), onBackground = Color(0xFFE8EAF3),
    surface = Color(0xFF20232D), onSurface = Color(0xFFE8EAF3),
    surfaceVariant = Color(0xFF2B303D), onSurfaceVariant = Color(0xFFB9C0D1),
    surfaceTint = Color(0xFF20232D),
    outline = Color(0xFF8D96A9), outlineVariant = Color(0xFF3B4354)
)
private val FocusTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)
@Composable
fun FocusTraceTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = FocusTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)),
        content = content)
}
