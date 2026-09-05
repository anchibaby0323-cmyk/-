package com.novatune.engine.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NovaBlack = Color(0xFF0A0A0E)
val NovaPanel = Color(0xFF111118)
val NovaCyan = Color(0xFF00FFE0)
val NovaPurple = Color(0xFF7B2CBF)
val NovaWhite = Color(0xFFF3F7FF)
val NovaMuted = Color(0xFF9AA4B2)
val NovaDanger = Color(0xFFFF4D6D)

private val NovaColors = darkColorScheme(
    primary = NovaCyan,
    secondary = NovaPurple,
    background = NovaBlack,
    surface = NovaPanel,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = NovaWhite,
    onSurface = NovaWhite,
    error = NovaDanger
)

@Composable
fun NovaTuneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NovaColors, content = content)
}
