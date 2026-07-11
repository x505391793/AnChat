package com.anchat.ui.chat

/**
 * 对话级身份快照：从主角色卡继承下来，写入 conversation 表后可二次编辑，
 * 不影响主角色卡本身。聊天运行时以这里的字段为准。
 *
 * 取值优先级（profileOf 中决定）：conversation 快照优先；快照为空时回退到
 * 主角色卡 / 全局配置主身份。
 */
data class ConversationProfile(
    /** 备注（用户对好友的备注名，优先于 charName 展示） */
    val charRemark: String? = null,
    /** 角色（AI）名称，用作聊天页标题与头像首字 */
    val charName: String,
    /** 角色头像（路径或 URL，可选） */
    val charAvatar: String?,
    /** 角色简介（可选） */
    val charDescription: String?,
    /** 系统提示词（发给 API，定义人格） */
    val systemPrompt: String,
    /** 开场白（可选） */
    val greeting: String?,
    /** 用户在此对话中的姓名（可选） */
    val userName: String?,
    /** 用户在此对话中的头像（可选） */
    val userAvatar: String?,
    /** 用户在此对话中的身份描述（可选） */
    val userDescription: String?,
    /** 该对话使用的模型（null = 跟随全局默认） */
    val modelId: String?,
    /** 是否开启思考模式 */
    val thinkingEnabled: Boolean
)
