package com.anchat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 世界书：用户自定义的设定卡片（标题 + 内容），可被角色卡勾选，
 * 其正文在对话时追加到 system 提示词末尾（拼接顺序按 createdAt 排序以保证跨设备缓存稳定）。
 */
@Entity(tableName = "world_book")
@Serializable
data class WorldBook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 卡片标题 */
    val title: String,
    /** 卡片正文（设定内容） */
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
