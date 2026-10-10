package com.example.wattson.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = RivianGreen,
    onPrimary = RivianBlack,
    primaryContainer = RivianGreenDark,
    onPrimaryContainer = TextPrimary,
    secondary = RivianAmber,
    onSecondary = RivianBlack,
    secondaryContainer = RivianAmberLight,
    onSecondaryContainer = RivianBlack,
    tertiary = RivianBlue,
    background = RivianBlack,
    onBackground = TextPrimary,
    surface = RivianDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = RivianSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = RivianBorder,
    error = RivianRed,
    onError = TextPrimary
)

@Composable
fun WattsonTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
