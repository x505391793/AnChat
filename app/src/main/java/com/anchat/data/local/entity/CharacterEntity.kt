package com.anchat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "character")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 角色名称 */
    val name: String,

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

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
