package com.anchat.data.config

import kotlinx.serialization.Serializable

/**
 * 一个已添加的模型（来源：用户通过「模型管理」手填模型名后保存）。
 * 每个模型绑定自己的提供方 + apiKey；聊天地址由 [ModelProviders] 按提供方解析，
 * 不再由用户填写。按模型 id 反查即可拿到 key / url / provider 发起请求。
 */
@Serializable
data class ModelConfig(
    /** 模型 id，即 API 调用时使用的 model 字段（如 deepseek-v4-pro）；同时是全局唯一引用键 */
    val id: String,
    /** 展示名称（手填添加时等于 id） */
    val name: String,
    val description: String = "",
    /** 该模型的 API Key */
    val apiKey: String = "",
    /** API 基地址。新模型写入内置地址（兼容旧版 App 读备份）；旧数据保留原值供 LEGACY 兜底 */
    val apiUrl: String = "",
    /** 提供方 id（见 ModelProviders）。空字符串 = 旧数据未分类，由 resolve() 归一 */
    val provider: String = "",
    /** 真正传给 API 的 model 值。旧数据为空 → 由 ModelProviders.apiModel() 回退到 id */
    val modelName: String = ""
)
