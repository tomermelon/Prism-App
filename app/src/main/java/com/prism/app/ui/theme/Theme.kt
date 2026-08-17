package com.prism.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF1A2332)
private val Paper = Color(0xFFF7F4EF)
private val Teal = Color(0xFF0F6B5C)
private val TealDark = Color(0xFF0A4F44)
private val Amber = Color(0xFFC47B2B)
private val Mist = Color(0xFFE7EFE9)
private val SoftRed = Color(0xFFB23A48)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Mist,
    onPrimaryContainer = Ink,
    secondary = Amber,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Ink.copy(alpha = 0.75f),
    error = SoftRed,
    outline = Ink.copy(alpha = 0.2f)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5CB8A5),
    onPrimary = Ink,
    primaryContainer = TealDark,
    onPrimaryContainer = Paper,
    secondary = Amber,
    onSecondary = Ink,
    background = Color(0xFF121820),
    onBackground = Paper,
    surface = Color(0xFF1A2332),
    onSurface = Paper,
    surfaceVariant = Color(0xFF243044),
    onSurfaceVariant = Paper.copy(alpha = 0.8f),
    error = Color(0xFFFF8A80),
    outline = Paper.copy(alpha = 0.25f)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
)

@Composable
fun PrismTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
