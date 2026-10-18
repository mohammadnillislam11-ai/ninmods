package com.ninmods.menu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NinPrimary,
    secondary = NinAccent,
    background = NinBackground,
    surface = NinBackground,
    onPrimary = NinBackground,
    onSecondary = NinTextPrimary,
    onBackground = NinTextPrimary,
    onSurface = NinTextPrimary,
    error = NinError
)

@Composable
fun NinmodsMenuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
