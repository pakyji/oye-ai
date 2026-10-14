package com.oyeai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Brand colours: near-black night, violet and cyan light. */
val OyeNight = Color(0xFF0E0F13)
val OyeViolet = Color(0xFF8B7CFF)
val OyeCyan = Color(0xFF22D3EE)
val OyeMist = Color(0xFFB8BCD0)

private val OyeDark = darkColorScheme(
    primary = OyeViolet,
    onPrimary = Color.White,
    secondary = OyeCyan,
    onSecondary = OyeNight,
    tertiary = OyeCyan,
    background = OyeNight,
    onBackground = Color.White,
    surface = Color(0xFF171922),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF232634),
    onSurfaceVariant = Color(0xFFE0E0E8),
    outline = Color(0xFF3A3F55)
)

/** Light weights and open letter spacing for titles give a calm, premium feel. */
private val OyeTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Light, fontSize = 26.sp, letterSpacing = 6.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, letterSpacing = 2.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.6.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 1.2.sp)
)

@Composable
fun OyeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OyeDark, typography = OyeTypography, content = content)
}
