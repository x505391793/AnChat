package com.anchat.data.engine

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 行为表：用户视角完整消息表。batchId = 源 raw.id，兼作 FK。
 * `order` 是 SQLite 关键字，列名用反引号包裹。
 */
@Entity(tableName = "behaviors")
data class BehaviorEntity(
    @PrimaryKey val id: String,                 // behaviorId
    @ColumnInfo(name = "batch_id") val batchId: String = "",
    @ColumnInfo(name = "order") val order: Int,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "role") val role: String, // user / assistant（与 API 的 role 对齐）
    @ColumnInfo(name = "content") val content: String,
    /** leave 时的离开时长文本；其余类型固定 null */
    @ColumnInfo(name = "duration") val duration: String? = null,
    @ColumnInfo(name = "excu_time") val excuTime: Long,
    @ColumnInfo(name = "status") val status: Int,
    /** 所属对话 id（与 raw_replies.conversation_id 同值）；用于行为事件按对话隔离，避免串台 */
    @ColumnInfo(name = "conversation_id") val conversationId: String = ""
)
