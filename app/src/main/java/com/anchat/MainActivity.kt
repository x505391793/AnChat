package com.anchat

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import com.anchat.push.NotificationNavigation
import com.anchat.ui.main.LocalApp
import com.anchat.ui.main.AnChatAppHost

class MainActivity : ComponentActivity() {

    private val requestPostNotify = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 用户授权结果：拒绝则通知静默不弹，不影响聊天 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        // Android 13+ 需要 POST_NOTIFICATIONS 权限才能弹系统通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPostNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val app = application as AnChatApplication
        setContent {
            CompositionLocalProvider(LocalApp provides app) {
                AnChatAppHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** 通知点击带 openConversationId 进来：写入待导航，由 NavHost 跳转 */
    private fun handleIntent(intent: Intent?) {
        val convId = intent?.getLongExtra("openConversationId", -1L) ?: -1L
        if (convId >= 0L) NotificationNavigation.set(convId)
    }
}
