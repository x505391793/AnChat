package com.anchat.ui.main

import androidx.compose.runtime.compositionLocalOf
import com.anchat.AnChatApplication

/** Provides the [AnChatApplication] container to every Composable. */
val LocalApp = compositionLocalOf<AnChatApplication> { error("LocalApp not provided") }

/**
 * Whether the dark theme is currently active.
 * Driven by the user's setting (system / light / dark), NOT just the OS setting,
 * so any Composable can read it (e.g. chat bubbles that pick colors by theme).
 */
val LocalIsDark = compositionLocalOf<Boolean> { false }
