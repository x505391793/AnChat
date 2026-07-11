package com.anchat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A chat conversation.
 */
@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String = "新对话",
    @ColumnInfo(name = "preview") val preview: String = "",
    @ColumnInfo(name = "is_star") val isStar: Boolean = false,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
    @ColumnInfo(name = "character_id") val characterId: Long = -1L,
    @ColumnInfo(name = "model_id") val modelId: String? = null,
    @ColumnInfo(name = "system_prompt") val systemPrompt: String? = null,

    // ── 对话级「角色」身份快照：从角色卡继承下来，可二次编辑，不影响主角色卡 ──
    /** 备注（用户对好友的备注名，优先于 char_name 展示） */
    @ColumnInfo(name = "char_remark") val charRemark: String? = null,
    @ColumnInfo(name = "char_name") val charName: String? = null,
    @ColumnInfo(name = "char_avatar") val charAvatar: String? = null,
    @ColumnInfo(name = "char_description") val charDescription: String? = null,
    @ColumnInfo(name = "char_greeting") val charGreeting: String? = null,
    @ColumnInfo(name = "char_thinking_enabled") val charThinkingEnabled: Boolean = false,

    // ── 对话级「用户自己」身份快照 ──
    @ColumnInfo(name = "user_name") val userName: String? = null,
    @ColumnInfo(name = "user_avatar") val userAvatar: String? = null,
    @ColumnInfo(name = "user_description") val userDescription: String? = null,

    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
