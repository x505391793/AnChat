package com.anchat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF07C160),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA9F1D7),
    secondary = Color(0xFF185FA5),
    background = Color(0xFFF7F7F7),
    surface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF888888)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF07C160),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF085041),
    secondary = Color(0xFF85B7EB),
    background = Color(0xFF111111),
    surface = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF9A9A9A)
)

@Composable
fun AnChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
