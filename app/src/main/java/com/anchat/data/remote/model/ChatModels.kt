package com.anchat.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Request models ───

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val stream: Boolean = false,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null
)

/** OpenAI / DeepSeek 兼容的 JSON 输出格式声明 */
@Serializable
data class ResponseFormat(
    val type: String
)

// ─── Non-streaming response models ───

/** Full response from `POST /chat/completions` with `stream: false`. */
@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChatChoice> = emptyList(),
    val usage: ChatUsage? = null
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatCompletionMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatCompletionMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null
)

@Serializable
data class ChatUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    @SerialName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null,
    @SerialName("completion_tokens_details") val completionTokensDetails: CompletionTokensDetails? = null,
    @SerialName("prompt_cache_hit_tokens") val promptCacheHitTokens: Int? = null,
    @SerialName("prompt_cache_miss_tokens") val promptCacheMissTokens: Int? = null
)

@Serializable
data class PromptTokensDetails(
    @SerialName("cached_tokens") val cachedTokens: Int? = null
)

@Serializable
data class CompletionTokensDetails(
    @SerialName("reasoning_tokens") val reasoningTokens: Int? = null
)

// ─── SSE streaming response models (kept for future use) ───

@Serializable
data class StreamChunk(
    val choices: List<StreamChoice>? = null,
    val usage: ChatUsage? = null
)

@Serializable
data class StreamChoice(
    val delta: Delta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null
)

// ─── Models endpoint ───

@Serializable
data class ModelListResponse(
    val data: List<ModelItem> = emptyList()
)

@Serializable
data class ModelItem(
    val id: String,
    val `object`: String = "model"
)
