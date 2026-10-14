package com.oyeai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OyeDark = darkColorScheme(
    primary = Color(0xFF8B7CFF),
    onPrimary = Color.White,
    secondary = Color(0xFF4DD0E1),
    background = Color(0xFF0E0F13),
    onBackground = Color.White,
    surface = Color(0xFF171922),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF232634),
    onSurfaceVariant = Color(0xFFE0E0E8)
)

@Composable
fun OyeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OyeDark, content = content)
}
