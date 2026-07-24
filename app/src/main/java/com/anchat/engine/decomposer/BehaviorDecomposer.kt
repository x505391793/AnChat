package com.anchat.engine.decomposer

import com.anchat.engine.core.contract.ApiMessage
import com.anchat.engine.core.contract.Behavior
import com.anchat.engine.core.contract.BehaviorType
import com.anchat.engine.core.contract.DecompositionSpec
import com.anchat.engine.core.contract.RawReply
import com.anchat.engine.core.contract.RequestSpec
import com.anchat.engine.spi.PersistenceSink
import com.anchat.engine.spi.RequestSink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.max
import kotlin.text.RegexOption

/**
 * 行为拆解 API（真实对话核心模块）。
 *
 * 嵌入位置：对话 API 拿到原始回复（raw data layer）之后、写入行为层之前。
 * 当「真实对话」开启且已配置管理 AI 时，把上一条原始回复文本再次包装成请求，
 * 发送给「真实对话管理 AI」，由其按微信拟人聊天语义拆解为可见行为事件
 * （speech / emotion / leave），解析 JSON 后落行为层，由调度器按 excuTime 分时推送。
 *
 * 不携带角色 / 身份快照——拆解只针对「上一条原始回复文本」。
 */
class BehaviorDecomposer(private val requestSink: RequestSink) {

    /** 行为分析器 system 提示（规则 + 输出格式约束） */
    private val analyzerSystem: String = buildString {
        appendLine("你是微信聊天行为分析器。把输入文本拆成界面可见的行为事件，按时间顺序输出 JSON 数组。")
        appendLine()
        appendLine("类型：")
        appendLine("- speech：发消息。按自然停顿或语气拆成多条，每条一个事件。")
        appendLine("- emotion：发表情包。仅当文本明确要发表情时才生成。")
        appendLine("- leave：离开/暂离。主动从文本提取离开意图，即使角色用 speech 表达也要额外生成 leave 事件并预测离开时长。")
        appendLine()
        appendLine("字段（每条事件）：")
        appendLine("- order: 序号")
        appendLine("- type: \"speech\" | \"emotion\" | \"leave\"")
        appendLine("- content: 文本内容")
        appendLine("- duration: leave 时填离开时长（有则提取，无则按原因预测），其它类型固定 null")
        appendLine()
        appendLine("只输出 JSON，格式：")
        appendLine("{\"events\":[{\"order\":1,\"type\":\"speech\",\"content\":\"...\",\"duration\":null}]}")
    }

    /**
     * @param rawContent 上一条原始回复全文
     * @param rawId 原始回复 id（行为表的外键，兼作 raw↔behavior 映射）
     * @param spec 真实对话管理 AI 规格
     * @return 拆解出的行为列表（已分配 excuTime，completed=false 待调度）
     * @throws Exception 拆解失败（网络错误 / 解析失败 / 空事件）时抛出，由调用方回退到直出模式
     */
    suspend fun decompose(rawContent: String, rawId: String, spec: DecompositionSpec, ownerConversationId: String = "", persistence: PersistenceSink): List<Behavior> {
        val convIdForBehavior = ownerConversationId
        val convId = ownerConversationId.ifBlank { "system" }
        val isDeepseek = spec.modelId.contains("deepseek", ignoreCase = true)
        val messages = listOf(
            ApiMessage("system", analyzerSystem),
            ApiMessage("user", "文本：$rawContent")
        )
        val req = RequestSpec(
            model = spec.modelId,
            apiKey = spec.apiKey,
            apiUrl = spec.apiUrl,
            messages = messages,
            // DeepSeek 模型需显式声明 json_object 输出，且 prompt 已含 json 字样
            responseFormat = if (isDeepseek) "json_object" else null,
            maxTokens = 2000
        )
        val reply = requestSink.send(req)
        if (reply.isError) {
            throw Exception("行为拆解请求失败：${reply.content}")
        }
        // 落库：拆解 API 的原始返回也记入 raw_replies（kind=decomp），便于审计/回溯；
        // 无对话上下文时 conversationId 填系统标识 "system"
        val decompRawId = UUID.randomUUID().toString()
        persistence.persistRaw(
            RawReply(
                id = decompRawId,
                conversationId = convId,
                role = "assistant",
                content = reply.content,
                reasoningContent = reply.reasoningContent,
                usage = reply.usage,
                isError = reply.isError,
                kind = "decomp"
            ),
            convId
        )
        return parseEvents(reply.content, rawId, ownerConversationId)
    }

    /**
     * 把模型返回的 JSON 行为事件解析为行为列表（v1 拆解与 v2 直接解析复用）。
     * 不发起网络；解析失败（无 JSON / 空事件 / 结构异常）时抛出，由调用方回退。
     */
    fun parseToBehaviors(jsonText: String, rawId: String, conversationId: String): List<Behavior> =
        parseEvents(jsonText, rawId, conversationId)

    /** 从模型回复中提取 ```json 代码块并解析为行为列表，按序分配 excuTime */
    private fun parseEvents(jsonText: String, rawId: String, conversationId: String): List<Behavior> {
        val block = extractJsonBlock(jsonText)
            ?: throw Exception("行为拆解返回非预期格式（未找到 JSON）")
        val resp = try {
            JSON.decodeFromString<DecompResponse>(block)
        } catch (e: Exception) {
            throw Exception("行为拆解 JSON 解析失败：${e.message}")
        }
        if (resp.events.isEmpty()) {
            throw Exception("行为拆解返回空事件")
        }
        val base = System.currentTimeMillis()
        var cursor = base
        val result = resp.events.sortedBy { it.order }.map { ev ->
            val type = when (ev.type.lowercase()) {
                "emotion" -> BehaviorType.EMOTION
                "leave" -> BehaviorType.LEAVE
                else -> BehaviorType.SPEECH
            }
            val behavior = Behavior(
                behaviorId = UUID.randomUUID().toString(),
                order = ev.order,
                type = type,
                role = "assistant",
                content = ev.content,
                duration = if (type == BehaviorType.LEAVE) ev.duration else null,
                excuTime = cursor,
                status = Behavior.STATUS_PENDING,
                conversationId = conversationId,
                batchId = rawId
            )
            // 每种行为占用一段间隔，让行为分时依次出现（模拟真人节奏）。
            // leave 的 duration（如「10分钟」）作为「离开时长」追加在自身之后，
            // 表示这句话发完才离开、且下一条行为要等其回来——符合「先发话再离开」语义。
            cursor += when (type) {
                BehaviorType.SPEECH -> max(600L, (ev.content.length * 50L).coerceAtMost(4000L))
                BehaviorType.EMOTION -> 800L
                BehaviorType.LEAVE -> 500L + parseDurationToMs(ev.duration)
                BehaviorType.TEXT -> 0L
            }
            behavior
        }
        return result
    }

    /** 抽取 ```json ... ``` 或 ``` ... ``` 代码块内容；找不到则返回 null */
    private fun extractJsonBlock(text: String): String? {
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```", setOf(RegexOption.IGNORE_CASE))
        val match = fence.find(text)
        if (match != null) {
            val inner = match.groupValues[1].trim()
            if (inner.isNotBlank()) return inner
        }
        // 没有代码围栏时，尝试把整段当 JSON 解析
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        return null
    }

    /**
     * 把「10分钟 / 2小时 / 30秒 / 1h」等时长文本解析为毫秒；
     * 无法解析返回 0（不影响既有节奏，等于没写时长）。
     */
    private fun parseDurationToMs(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val m = Regex("""(\d+(?:\.\d+)?)\s*(分钟|分|min|m|小时|时|h|秒|s)""", RegexOption.IGNORE_CASE).find(text)
        val num = m?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return 0L
        val unit = m.groupValues.getOrNull(2)?.lowercase() ?: return 0L
        val factor = when {
            unit.startsWith("分") -> 60_000L
            unit == "min" || unit == "m" -> 60_000L
            unit.startsWith("小") || unit == "时" || unit == "h" -> 3_600_000L
            unit.startsWith("秒") || unit == "s" -> 1_000L
            else -> 60_000L
        }
        return (num * factor).toLong().coerceAtMost(7L * 24 * 3600 * 1000)
    }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }

        @Serializable
        private data class DecompEvent(
            val order: Int = 0,
            val type: String = "",
            val content: String = "",
            @SerialName("duration") val duration: String? = null
        )

        @Serializable
        private data class DecompResponse(
            val events: List<DecompEvent> = emptyList()
        )
    }
}
