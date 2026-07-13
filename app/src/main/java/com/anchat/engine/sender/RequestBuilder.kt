package com.anchat.engine.sender

import com.anchat.engine.core.contract.ApiMessage
import com.anchat.engine.core.contract.ConversationContext
import com.anchat.engine.core.contract.ChatMessageRecord
import com.anchat.engine.core.contract.RealConvVersion
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
        // 正常 system 提示（角色人设 + 用户身份）；v2 在其末尾追加行为拆解输出约束
        val systemParts = mutableListOf<String>()
        if (!context.systemPrompt.isNullOrBlank()) {
            systemParts += context.systemPrompt
        }
        val isV2 = context.realConvVersion == RealConvVersion.V2
        if (isV2) {
            systemParts += RealConvVersion.V2_INSTRUCTION
        }
        val systemText = if (systemParts.isEmpty()) null else systemParts.joinToString("\n\n")

        val msgs = mutableListOf<ApiMessage>()
        if (systemText != null) {
            msgs += ApiMessage("system", systemText)
        }
        history.forEach {
            if (it.role == "user" || it.role == "assistant") {
                msgs += ApiMessage(it.role, it.content)
            }
        }
        // v2：主请求即要求 JSON 输出（DeepSeek 需显式声明 response_format）
        val isDeepseek = context.modelId.contains("deepseek", ignoreCase = true)
        val responseFormat = if (isV2 && isDeepseek) "json_object" else null
        return RequestSpec(
            model = context.modelId,
            apiKey = context.apiKey,
            apiUrl = context.apiUrl,
            messages = msgs,
            responseFormat = responseFormat
        )
    }
}
