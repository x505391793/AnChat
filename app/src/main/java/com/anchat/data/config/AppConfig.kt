package com.anchat.data.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    /**
     * 已添加的模型列表（含各自的 apiKey / apiUrl）。
     * 聊天时按模型 id 反查对应的服务商凭证。
     */
    val models: List<ModelConfig> = emptyList(),
    /** 默认模型 id（空 = 取模型列表第一个） */
    val defaultModelId: String? = null,
    /**
     * 全局「聊天 AI 模型」：聊天时若角色/对话未单独指定模型，则使用此模型。
     * 在「模型管理」中通过下拉框选择，独立于真实对话管理 AI。
     */
    val chatModelId: String? = null,
    /**
     * 全局「真实对话管理 AI」：开启真实对话时，原始回复会再次发给此模型做行为拆解。
     * 仅当此字段非空，角色卡/对话的「真实对话」开关才可开启。
     */
    val realConversationModelId: String? = null,
    /**
     * Override for the config file location.
     * Blank = use the default path inside the app's internal storage.
     */
    val configFilePath: String = "",
    /** 用户主身份姓名（角色卡未设置用户身份时使用） */
    val defaultUserName: String = "",
    /** 用户主身份头像（角色卡未设置时使用） */
    val defaultUserAvatar: String = "",
    /** 用户主身份描述（角色卡未设置时使用） */
    val defaultUserDescription: String = "",
    /** 用户主身份性别（如 男 / 女；角色卡未设置时使用） */
    val defaultUserGender: String = "",
    /** 用户主身份微信号（角色卡未设置时使用） */
    val defaultUserWechatId: String = "",
    /**
     * 外观模式：system（跟随系统）/ light（浅色）/ dark（深色）。
     * 默认跟随系统。
     */
    val themeMode: String = "system",
    /**
     * 通知推送开关：收到新消息时是否弹出系统通知。
     * 默认开启；关闭后仅保留应用内未读红点，不再弹系统通知。
     */
    val notificationsEnabled: Boolean = true,
)
