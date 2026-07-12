package com.anchat.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Keeps an in-flight chat request alive while the app is in the background. */
class RequestForegroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("AnChat")
            .setContentText("正在等待回复…")
            .setOngoing(true)
            .build())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "聊天请求", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val CHANNEL_ID = "chat_requests"
        private const val NOTIFICATION_ID = 2001

        fun begin(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RequestForegroundService::class.java))
        }

        fun finish(context: Context) {
            context.stopService(Intent(context, RequestForegroundService::class.java))
        }
    }
}