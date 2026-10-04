package com.cinemate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.cinemate.app.domain.model.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2E2000),
    secondary = AmberDim,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = OnDark,
    onSurface = OnDark,
    onSurfaceVariant = OnDarkMuted
)

private val LightScheme = lightColorScheme(
    primary = Brown,
    onPrimary = Color(0xFFFFFBF5),
    background = LightBackground,
    surface = LightSurface,
    onBackground = OnLight,
    onSurface = OnLight,
    onSurfaceVariant = OnLightMuted
)

@Composable
fun CinemateTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = CinemateTypography,
        content = content
    )
}
