package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkAccentDarker,
    primaryContainer = DarkAccentDark,
    onPrimaryContainer = Color.White,
    secondary = DarkSurfaceVariant,
    onSecondary = DarkTextMain,
    background = DarkBackground,
    onBackground = DarkTextMain,
    surface = DarkSurface,
    onSurface = DarkTextMain,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkSecondaryText
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Always default to Dark Mode for Elegant Dark
    dynamicColor: Boolean = false, // Disable dynamic coloring to preserve our custom design tokens
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
