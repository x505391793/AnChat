package com.anchat.engine.core.contract

/** 用户一次输入 */
data class TurnInput(val text: String)

/** 引擎内部请求消息（不依赖 data 模块的 DTO） */
data class ApiMessage(val role: String, val content: String)

/** 交给 RequestSink 的请求规格 */
data class RequestSpec(
    val model: String,
    val apiKey: String,
    val apiUrl: String,
    val messages: List<ApiMessage>
)

/** token 用量（与原 ChatUsage 同构，引擎侧独立定义） */
data class TokenUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val reasoningTokens: Int? = null,
    val promptCacheHitTokens: Int? = null,
    val promptCacheMissTokens: Int? = null
)

/** 原始回复：模型面向，用于缓存命中，不直接给用户看 */
data class RawReply(
    val id: String,                 // rawId
    val conversationId: String,
    val content: String,
    val reasoningContent: String? = null,
    val usage: TokenUsage? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

/** 一条可落库 / 可渲染的对话消息记录 */
data class ChatMessageRecord(
    val conversationId: String,
    val role: String,
    val content: String,
    val reasoningContent: String? = null,
    val usage: TokenUsage? = null
)

/** 对话上下文：引擎只读取，不负责解析凭证（由调用方填充） */
data class ConversationContext(
    val conversationId: String,
    val modelId: String,
    val apiKey: String,
    val apiUrl: String,
    val systemPrompt: String? = null
)

/** 引擎对外吐出的渲染事件（真实推送先 stub，UI 据此更新） */
sealed interface EngineEvent {
    data class AssistantMessage(val record: ChatMessageRecord) : EngineEvent
    data class Error(val message: String) : EngineEvent
}
