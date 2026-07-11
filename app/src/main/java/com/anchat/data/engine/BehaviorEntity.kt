package com.anchat.data.engine

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 行为表：用户面向，服务展示。行为表 = 同一 rawId 下的所有 BehaviorEntity 行。
 * rawId 即 raw↔behavior 映射，无需额外映射表。
 * `order` 是 SQLite 关键字，列名用反引号包裹。
 */
@Entity(tableName = "behaviors")
data class BehaviorEntity(
    @PrimaryKey val id: String,                 // behaviorId
    @ColumnInfo(name = "raw_id") val rawId: String,
    @ColumnInfo(name = "order") val order: Int,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "excu_time") val excuTime: Long,
    @ColumnInfo(name = "completed") val completed: Boolean,
    @ColumnInfo(name = "is_read") val isRead: Boolean
)
