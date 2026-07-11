package com.anchat.engine.sender

import com.anchat.engine.core.contract.ApiMessage
import com.anchat.engine.core.contract.ConversationContext
import com.anchat.engine.core.contract.ChatMessageRecord
import com.anchat.engine.core.contract.RequestSpec
import com.anchat.engine.core.contract.TurnInput

/**
 * 用户消息的 Pre 拦截器（保留空接口）。
 * 首版不做任何分析，逻辑透传。后续在此挂命令/模板/@提及识别。
 */
interface UserMessageInterceptor {
    fun process(input: TurnInput): TurnInput
}

class PassThroughInterceptor : UserMessageInterceptor {
    override fun process(input: TurnInput): TurnInput = input
}

/**
 * 把上下文 + 历史拼成请求规格（身份快照、system 提示、模型与凭证）。
 * 不做网络。历史已包含刚发送的用户消息（由调用方先行落库），故不再追加 input。
 */
class RequestBuilder {
    fun buildSpec(
        context: ConversationContext,
        history: List<ChatMessageRecord>
    ): RequestSpec {
        val msgs = mutableListOf<ApiMessage>()
        if (!context.systemPrompt.isNullOrBlank()) {
            msgs += ApiMessage("system", context.systemPrompt)
        }
        history.forEach {
            if (it.role == "user" || it.role == "assistant") {
                msgs += ApiMessage(it.role, it.content)
            }
        }
        return RequestSpec(
            model = context.modelId,
            apiKey = context.apiKey,
            apiUrl = context.apiUrl,
            messages = msgs
        )
    }
}
