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
    primary = Color(0xFFAE483C), onPrimary = Color.White,
    primaryContainer = Color(0xFFFBE8E3), onPrimaryContainer = Color(0xFF762C23),
    secondary = Color(0xFF596657), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EBE2), onSecondaryContainer = Color(0xFF354231),
    tertiary = Color(0xFF795B36), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5E9D7), onTertiaryContainer = Color(0xFF523C21),
    background = Color(0xFFF7F6F2), onBackground = Color(0xFF292724),
    surface = Color(0xFFFFFEFB), onSurface = Color(0xFF292724),
    surfaceVariant = Color(0xFFEFEBE5), onSurfaceVariant = Color(0xFF6E675F),
    surfaceTint = Color(0xFFFFFEFB),
    outline = Color(0xFF8B8278), outlineVariant = Color(0xFFE3DED6),
    error = Color(0xFFBA1A1A), onError = Color.White
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A6), onPrimary = Color(0xFF65251D),
    primaryContainer = Color(0xFF54312C), onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFBDCDB5), onSecondary = Color(0xFF293624),
    secondaryContainer = Color(0xFF374332), onSecondaryContainer = Color(0xFFD9E7D0),
    tertiary = Color(0xFFE7C393), onTertiary = Color(0xFF432D10),
    tertiaryContainer = Color(0xFF584328), onTertiaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF191817), onBackground = Color(0xFFEAE3DB),
    surface = Color(0xFF22201E), onSurface = Color(0xFFEAE3DB),
    surfaceVariant = Color(0xFF302D29), onSurfaceVariant = Color(0xFFC3BAB0),
    surfaceTint = Color(0xFF22201E),
    outline = Color(0xFF938A80), outlineVariant = Color(0xFF49443E)
)
private val FocusTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)
@Composable
fun FocusTraceTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = FocusTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        content = content)
}
