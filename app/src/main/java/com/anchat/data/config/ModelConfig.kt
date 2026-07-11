package com.anchat.data.config

import kotlinx.serialization.Serializable

/**
 * 一个已添加的模型（来源：用户通过「模型管理」拉取后添加）。
 * 每个模型都绑定自己所属服务商的 apiKey 与 apiUrl，
 * 聊天时按模型 id 反查对应的 key / url 发起请求。
 */
@Serializable
data class ModelConfig(
    /** 模型 id，即 API 调用时使用的 model 字段（如 deepseek-v4-pro） */
    val id: String,
    /** 展示名称（拉取时默认等于 id） */
    val name: String,
    val description: String = "",
    /** 该模型所属服务商的 API Key */
    val apiKey: String = "",
    /** 该模型所属服务商的 API 基地址（如 https://api.deepseek.com，不含 /chat/completions） */
    val apiUrl: String = ""
)
