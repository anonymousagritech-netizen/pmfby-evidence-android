package com.pmfby.evidence.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = White,
    primaryContainer = SlateGray100,
    onPrimaryContainer = NavyDark,
    secondary = SlateGray500,
    onSecondary = White,
    background = SlateGray50,
    onBackground = SlateGray900,
    surface = White,
    onSurface = SlateGray900,
    surfaceVariant = SlateGray100,
    onSurfaceVariant = SlateGray700,
    outline = SlateGray300,
    error = RedEmergency,
    onError = White,
    errorContainer = RedEmergencyContainer,
    onErrorContainer = RedEmergency,
)

private val DarkColors = darkColorScheme(
    primary = SlateGray100,
    onPrimary = NavyDark,
    primaryContainer = NavyPrimaryVariant,
    onPrimaryContainer = SlateGray100,
    secondary = SlateGray300,
    onSecondary = NavyDark,
    background = DarkSurface,
    onBackground = SlateGray100,
    surface = DarkSurfaceVariant,
    onSurface = SlateGray100,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = SlateGray300,
    outline = SlateGray500,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = RedEmergencyContainer,
)

@Composable
fun PmfbyEvidenceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = PmfbyTypography, content = content)
}
