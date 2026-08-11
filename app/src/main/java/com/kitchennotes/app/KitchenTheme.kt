package com.kitchennotes.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Terracotta = Color(0xFFA94F23)
val TerracottaDark = Color(0xFF7E3517)
val WarmBackground = Color(0xFFFFFBF8)
val WarmSurface = Color(0xFFFFFFFF)
val WarmSurfaceVariant = Color(0xFFF6E9E1)
val WarmOutline = Color(0xFFEADBD1)
val WarmText = Color(0xFF302019)
val WarmMuted = Color(0xFF80685D)

private val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4E4D9),
    onPrimaryContainer = TerracottaDark,
    secondary = Color(0xFF7A594A),
    onSecondary = Color.White,
    background = WarmBackground,
    onBackground = WarmText,
    surface = WarmSurface,
    onSurface = WarmText,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = WarmMuted,
    outline = WarmOutline,
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB18B),
    onPrimary = Color(0xFF54200A),
    primaryContainer = Color(0xFF6F2E12),
    onPrimaryContainer = Color(0xFFFFDBCA),
    secondary = Color(0xFFE5BEAD),
    background = Color(0xFF2E231E),
    onBackground = Color(0xFFFAEEE7),
    surface = Color(0xFF382C26),
    onSurface = Color(0xFFFAEEE7),
    surfaceVariant = Color(0xFF493830),
    onSurfaceVariant = Color(0xFFCDB9AC),
    outline = Color(0xFF5A443A)
)

private val SavorNotesTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
)

private val SavorNotesShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(11.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(15.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

@Composable
fun SavorNotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = SavorNotesTypography,
        shapes = SavorNotesShapes,
        content = content
    )
}
