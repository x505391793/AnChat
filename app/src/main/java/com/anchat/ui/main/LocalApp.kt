package com.anchat.ui.main

import androidx.compose.runtime.compositionLocalOf
import com.anchat.AnChatApplication

/** Provides the [AnChatApplication] container to every Composable. */
val LocalApp = compositionLocalOf<AnChatApplication> { error("LocalApp not provided") }
