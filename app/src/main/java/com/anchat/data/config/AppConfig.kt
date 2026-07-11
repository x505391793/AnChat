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
    /**
     * 外观模式：system（跟随系统）/ light（浅色）/ dark（深色）。
     * 默认跟随系统。
     */
    val themeMode: String = "system",
)
