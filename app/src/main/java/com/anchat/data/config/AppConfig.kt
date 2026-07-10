package com.anchat.data.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    /** DeepSeek API key. Blank = not set. */
    val apiKey: String = "",
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
)
