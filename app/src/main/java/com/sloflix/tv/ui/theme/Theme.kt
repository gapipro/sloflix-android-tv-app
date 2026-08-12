package com.sloflix.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val CinematicColorScheme = darkColorScheme(
    primary = Color(0xFFE52B3D),
    onPrimary = Color.White,
    background = Color(0xFF090C12),
    onBackground = Color.White,
    surface = Color(0xFF141923),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF252C38),
    onSurfaceVariant = Color(0xFFC5CBD6),
    error = Color(0xFFFFA9B1),
    onError = Color(0xFF090C12),
)

@Composable
fun SloflixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CinematicColorScheme,
        content = content,
    )
}
