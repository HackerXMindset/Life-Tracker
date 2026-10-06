package com.lifetracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF17785A),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFE8EDE8),
    onBackground = Color(0xFF14201A),
    surface = Color(0xFFF6F8F4),
    onSurface = Color(0xFF14201A),
    surfaceVariant = Color(0xFFC6D1C7),
    onSurfaceVariant = Color(0xFF55655B),
    outline = Color(0xFFC6D1C7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4CC79B),
    onPrimary = Color(0xFF07281D),
    background = Color(0xFF0E1411),
    onBackground = Color(0xFFE6EEE8),
    surface = Color(0xFF17201B),
    onSurface = Color(0xFFE6EEE8),
    surfaceVariant = Color(0xFF2A372F),
    onSurfaceVariant = Color(0xFF93A399),
    outline = Color(0xFF2A372F),
)

@Composable
fun LifeTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
