package com.anchat.data.engine

import com.anchat.data.remote.DeepSeekApi
import com.anchat.data.remote.model.ChatCompletionResponse
import com.anchat.data.remote.model.ChatMessageDto
import com.anchat.data.remote.model.ChatRequest
import com.anchat.data.remote.model.ChatUsage
import com.anchat.engine.core.contract.RawReply
import com.anchat.engine.core.contract.RequestSpec
import com.anchat.engine.core.contract.TokenUsage
import com.anchat.engine.spi.RequestSink
import java.util.UUID

/** 真实 RequestSink：委托 DeepSeekApi 发请求（非流式），失败转 RawReply(isError) */
class EngineRequestSink(private val api: DeepSeekApi) : RequestSink {
    override suspend fun send(spec: RequestSpec): RawReply {
        val dtos = spec.messages.map { ChatMessageDto(role = it.role, content = it.content) }
        val req = ChatRequest(model = spec.model, messages = dtos, stream = false)
        return try {
            val resp: ChatCompletionResponse = api.sendChat(spec.apiKey, spec.apiUrl, req)
            val msg = resp.choices.firstOrNull()?.message
            RawReply(
                id = UUID.randomUUID().toString(),
                conversationId = "",
                content = msg?.content ?: "",
                reasoningContent = msg?.reasoningContent,
                usage = resp.usage?.toTokenUsage(),
                createdAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            RawReply(
                id = UUID.randomUUID().toString(),
                conversationId = "",
                content = "❌ ${e.message ?: "发送失败"}",
                isError = true,
                createdAt = System.currentTimeMillis()
            )
        }
    }

    private fun ChatUsage.toTokenUsage(): TokenUsage = TokenUsage(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens,
        reasoningTokens = completionTokensDetails?.reasoningTokens,
        promptCacheHitTokens = promptCacheHitTokens,
        promptCacheMissTokens = promptCacheMissTokens
    )
}
