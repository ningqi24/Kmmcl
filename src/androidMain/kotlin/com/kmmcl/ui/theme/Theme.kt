package com.kmmcl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand colors
val DarkBackground = Color(0xFF0D0D1A)
val DarkSurface = Color(0xFF1A1A2E)
val DarkSurfaceVariant = Color(0xFF252540)
val PrimaryGreen = Color(0xFF5D8A3C)
val SecondaryPurple = Color(0xFF7B5EA7)
val AccentGold = Color(0xFFD4A843)

val LightBackground = Color(0xFFF2F0EB)
val LightSurface = Color(0xFFFFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    secondary = SecondaryPurple,
    tertiary = AccentGold,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFE8E8EC),
    onSurface = Color(0xFFE8E8EC),
    onSurfaceVariant = Color(0xFFB0B0C0)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    secondary = SecondaryPurple,
    tertiary = AccentGold,
    background = LightBackground,
    surface = LightSurface
)

@Composable
fun KmmclTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
