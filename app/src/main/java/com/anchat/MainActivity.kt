package com.anchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.anchat.ui.main.LocalApp
import com.anchat.ui.main.AnChatApp
import com.anchat.ui.theme.AnChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AnChatApplication
        setContent {
            CompositionLocalProvider(LocalApp provides app) {
                AnChatTheme {
                    AnChatApp()
                }
            }
        }
    }
}
