package com.chaskifood.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ChaskiPrimary,
    onPrimary = ChaskiOnPrimary,
    primaryContainer = ChaskiSurfaceVariant,
    onPrimaryContainer = ChaskiTextPrimary,
    secondary = ChaskiSecondary,
    onSecondary = ChaskiOnSecondary,
    background = ChaskiBackground,
    onBackground = ChaskiTextPrimary,
    surface = ChaskiSurface,
    onSurface = ChaskiTextPrimary,
    surfaceVariant = ChaskiSurfaceVariant,
    onSurfaceVariant = ChaskiTextTertiary,
    error = ChaskiError,
    outline = ChaskiTextDisabled,
    outlineVariant = ChaskiBorder,
)

private val DarkColors = darkColorScheme(
    primary = ChaskiPrimary,
    onPrimary = ChaskiOnPrimary,
    primaryContainer = Color(0xFF3A1B1B),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = ChaskiSecondary,
    onSecondary = Color(0xFF08130B),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFBDBDBD),
    error = ChaskiError,
    outline = Color(0xFF757575),
    outlineVariant = Color(0xFF333333),
)

@Composable
fun ChaskiTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ChaskiTypography,
        shapes = ChaskiShapes,
        content = content,
    )
}