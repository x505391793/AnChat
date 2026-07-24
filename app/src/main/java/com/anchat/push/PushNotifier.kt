package com.anchat.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.anchat.MainActivity
import com.anchat.R
import com.anchat.data.repository.LocalRepository

/**
 * 系统通知推送：收到助手消息且当前没在看该会话时，弹系统通知 + 保留未读红点。
 * 正在看（ActiveConversation == 该会话）则不弹，直接标记已读。
 */
class PushNotifier(
    private val context: Context,
    private val repo: LocalRepository,
    private val isEnabled: () -> Boolean = { true },
) {
    companion object {
        const val CHANNEL_ID = "anchat_messages"
        private const val CHANNEL_NAME = "消息通知"
    }

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Android 8+ 必须建通道；可重复调用（幂等）。 */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "新消息提醒"
                // 高重要性：弹顶部浮动横幅 + 提示音 + 振动，并允许应用角标
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * 一条助手回复气泡（行为层）到达，按需要弹系统通知。
     * @return true=已弹通知；false=当前正查看该会话（已标记已读，不弹）。
     */
    suspend fun onReply(convId: Long, title: String, preview: String): Boolean {
        if (ActiveConversation.id.value == convId) {
            // 正在看这个会话：直接清未读，不弹通知
            repo.markRead(convId)
            return false
        }
        // 通知推送关闭：不弹系统通知，但保留未读红点（应用内可见）
        if (!isEnabled()) return false
        // 未读红点由 messages.is_read=0 体现；此处弹系统通知
        notify(convId, title, preview)
        return true
    }

    private fun notify(convId: Long, title: String, preview: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openConversationId", convId)
        }
        val pi = PendingIntent.getActivity(
            context,
            convId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(preview)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(convId.toInt(), notification)
    }
}
