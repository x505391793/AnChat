package com.anchat.data.local.entity

import androidx.room.ColumnInfo

/**
 * 对话列表项投影：实时联查 behaviors 算出的预览（不再持久化缓存）。
 * 预览 = 该会话「可读状态（status>=1）」下最后一条 speech/text 内容，截断 50 字。
 */
data class ConversationListItem(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "is_star") val isStar: Boolean,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean,
    @ColumnInfo(name = "char_remark") val charRemark: String?,
    @ColumnInfo(name = "char_name") val charName: String?,
    @ColumnInfo(name = "char_avatar") val charAvatar: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** 实时联查算出的预览文本（非空时展示） */
    @ColumnInfo(name = "preview") val preview: String
)
