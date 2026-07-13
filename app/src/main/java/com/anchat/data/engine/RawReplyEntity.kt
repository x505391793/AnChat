package com.anchat.data.engine

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 原始回复表：模型面向，用于缓存命中 / 审计，不直接给用户看 */
@Entity(tableName = "raw_replies")
data class RawReplyEntity(
    @PrimaryKey val id: String,                    // rawId
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "role") val role: String, // user / assistant / system
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "reasoning_content") val reasoningContent: String?,
    @ColumnInfo(name = "prompt_tokens") val promptTokens: Int?,
    @ColumnInfo(name = "completion_tokens") val completionTokens: Int?,
    @ColumnInfo(name = "total_tokens") val totalTokens: Int?,
    @ColumnInfo(name = "reasoning_tokens") val reasoningTokens: Int?,
    @ColumnInfo(name = "prompt_cache_hit_tokens") val promptCacheHitTokens: Int?,
    @ColumnInfo(name = "prompt_cache_miss_tokens") val promptCacheMissTokens: Int?,
    @ColumnInfo(name = "is_error") val isError: Boolean,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
