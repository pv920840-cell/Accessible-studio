package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalHighContrast = staticCompositionLocalOf { false }

private val StandardDarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF003548),
    primaryContainer = Color(0xFF004D68),
    onPrimaryContainer = CyanPrimaryLight,
    secondary = IndigoSecondary,
    onSecondary = Color(0xFF1E1E60),
    secondaryContainer = Color(0xFF373A8A),
    onSecondaryContainer = Color(0xFFE0E0FF),
    tertiary = AmberAccent,
    onTertiary = Color(0xFF452B00),
    background = DarkBg,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = RoseError,
    onError = Color.White
)

private val HighContrastColorScheme = darkColorScheme(
    primary = HighContrastPrimary,
    onPrimary = Color.Black,
    primaryContainer = HighContrastPrimary,
    onPrimaryContainer = Color.Black,
    secondary = HighContrastSecondary,
    onSecondary = Color.Black,
    secondaryContainer = HighContrastSecondary,
    onSecondaryContainer = Color.Black,
    tertiary = HighContrastSecondary,
    onTertiary = Color.Black,
    background = HighContrastBg,
    onBackground = HighContrastText,
    surface = HighContrastSurface,
    onSurface = HighContrastText,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = HighContrastTextMuted,
    error = HighContrastError,
    onError = Color.Black
)

@Composable
fun AccessibleStudioTheme(
    isHighContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isHighContrast) HighContrastColorScheme else StandardDarkColorScheme

    CompositionLocalProvider(LocalHighContrast provides isHighContrast) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
