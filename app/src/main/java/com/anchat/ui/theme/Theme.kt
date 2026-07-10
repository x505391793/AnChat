package com.anchat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6E56),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA9F1D7),
    secondary = Color(0xFF185FA5),
    background = Color(0xFFF7F8F8),
    surface = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5DCAA5),
    onPrimary = Color(0xFF04231B),
    primaryContainer = Color(0xFF085041),
    secondary = Color(0xFF85B7EB),
    background = Color(0xFF121417),
    surface = Color(0xFF1C1F23)
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
