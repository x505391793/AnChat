package com.anchat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single message inside a conversation. Stores both the visible content
 * and the reasoning/thinking content from DeepSeek V4 models, plus full
 * token usage metrics.
 */
@Entity(
    tableName = "messages",
    indices = [Index("conversation_id")],
    foreignKeys = [ForeignKey(
        entity = Conversation::class,
        parentColumns = ["id"],
        childColumns = ["conversation_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: Long,
    @ColumnInfo(name = "role") val role: String,              // "user" | "assistant" | "system"
    @ColumnInfo(name = "content") val content: String,        // 正式回答内容
    @ColumnInfo(name = "reasoning_content") val reasoningContent: String? = null, // 思考过程（V4）
    @ColumnInfo(name = "model") val model: String? = null,
    @ColumnInfo(name = "finish_reason") val finishReason: String? = null,  // "stop" | "length"
    @ColumnInfo(name = "prompt_tokens") val promptTokens: Int? = null,
    @ColumnInfo(name = "completion_tokens") val completionTokens: Int? = null,
    @ColumnInfo(name = "total_tokens") val totalTokens: Int? = null,
    @ColumnInfo(name = "reasoning_tokens") val reasoningTokens: Int? = null,
    @ColumnInfo(name = "prompt_cache_hit_tokens") val promptCacheHitTokens: Int? = null,
    @ColumnInfo(name = "prompt_cache_miss_tokens") val promptCacheMissTokens: Int? = null,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false, // 未读红点依据；助手消息默认未读
    @ColumnInfo(name = "batch_id") val batchId: String? = null, // 同一回合（用户提问+AI回复+原始raw/behaviors）的关联键
    @ColumnInfo(name = "hidden") val hidden: Boolean = false, // 真实对话：原始整段回复仅入库供上下文，不在聊天界面直接展示
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
