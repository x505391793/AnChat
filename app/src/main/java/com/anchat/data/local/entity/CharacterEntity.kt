package com.anchat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "character")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 角色名称 */
    val name: String,

    /** 备注（用户对好友的备注名，优先于 name 展示） */
    @ColumnInfo(name = "remark") val remark: String? = null,

    /** 角色头像（路径或 URL） */
    val avatar: String? = null,

    /** 角色简介（给用户看的） */
    val description: String? = null,

    /** 系统提示词（定义角色人格，发给 API） */
    val systemPrompt: String,

    /** 角色开场白，创建对话时自动发送第一条消息 */
    val greeting: String? = null,

    /** 用户在此角色对话中的头像 */
    val userAvatar: String? = null,

    /** 用户在此角色对话中的姓名 */
    val userName: String? = null,

    /** 用户在此角色对话中的身份描述 */
    val userDescription: String? = null,

    /** 该角色对话使用的模型（null = 跟随全局默认） */
    @ColumnInfo(name = "model_id") val modelId: String? = null,

    /** 是否开启思考模式（控制推理过程展示） */
    @ColumnInfo(name = "thinking_enabled") val thinkingEnabled: Boolean = false,

    /** 是否开启「真实对话」模式（角色卡级开关，对话快照可独立覆盖） */
    @ColumnInfo(name = "real_conversation") val realConversation: Boolean = false,

    /** 真实对话版本（real_conversation 开启后走哪条行为生成路径）；默认 v1 */
    @ColumnInfo(name = "real_conv_version") val realConvVersion: String = "v1",

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
