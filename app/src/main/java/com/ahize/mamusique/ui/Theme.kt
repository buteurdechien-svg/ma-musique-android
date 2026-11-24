package com.ahize.mamusique.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFA2D48)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4E8),
    onPrimaryContainer = Color(0xFFB0102A),
    secondaryContainer = Color(0xFFF1F1F4),
    onSecondaryContainer = Accent,
    background = Color.White,
    surface = Color.White,
    surfaceContainer = Color(0xFFF7F7F8),
    onSurfaceVariant = Color(0xFF8A8A8E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF4560),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A0F1A),
    onPrimaryContainer = Color(0xFFFFB3BE),
    secondaryContainer = Color(0xFF2A2A2D),
    onSecondaryContainer = Color(0xFFFF4560),
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF98989D),
)

@Composable
fun MaMusiqueTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
