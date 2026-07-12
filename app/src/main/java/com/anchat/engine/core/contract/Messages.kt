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
    val messages: List<ApiMessage>,
    /** 指定 JSON 输出格式（如 "json_object"），由实现方决定是否写入请求体；null = 不附加 */
    val responseFormat: String? = null,
    /** 最大生成 token 数（null = 由实现方默认） */
    val maxTokens: Int? = null
)

/**
 * 行为拆解（真实对话）所需的第二模型规格。
 * 由调用方在「真实对话」开启且已配置管理 AI 时填充，引擎据此发起第二次请求。
 * 不携带角色/身份快照——拆解只针对「上一条原始回复文本」。
 */
data class DecompositionSpec(
    val modelId: String,
    val apiKey: String,
    val apiUrl: String
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
    val isError: Boolean = false,
    /** 来源：chat=聊天AI回复, decomp=行为拆解API回复, system=系统级请求 */
    val kind: String = "chat"
)

/** 一条可落库 / 可渲染的对话消息记录 */
data class ChatMessageRecord(
    val conversationId: String,
    val role: String,
    val content: String,
    val reasoningContent: String? = null,
    val usage: TokenUsage? = null,
    val batchId: String = "",
    /** 落库后的自增主键，供单条删除精准定位（未落库时 -1） */
    val id: Long = -1L,
    /** 真实对话模式下为 true：该助手消息仅入库供上下文，不在聊天界面直接展示（由行为层驱动 UI） */
    val hidden: Boolean = false
)

/** 对话上下文：引擎只读取，不负责解析凭证（由调用方填充） */
data class ConversationContext(
    val conversationId: String,
    val modelId: String,
    val apiKey: String,
    val apiUrl: String,
    val systemPrompt: String? = null,
    val batchId: String = "", // 同一回合关联键，等价于 rawId，用于整批删除
    /** 是否开启「真实对话」：开启则原始回复进入行为拆解 API，否则直接返回单条行为 */
    val realConversation: Boolean = false,
    /** 真实对话管理 AI 规格；realConversation=true 且非 null 时生效 */
    val decompSpec: DecompositionSpec? = null
)

/** 引擎对外吐出的渲染事件（真实推送先 stub，UI 据此更新） */
sealed interface EngineEvent {
    data class AssistantMessage(val record: ChatMessageRecord) : EngineEvent
    data class Error(val message: String) : EngineEvent
    /**
     * 某条行为到达执行时间（excuTime），由调度器翻 completed 后发出。
     * UI 据此实时把该行为追加进展示列表——不依赖 Room Flow 的失效重查，
     * 避免「退出重进才看得到」的推送断链。
     */
    data class BehaviorDue(val behavior: Behavior) : EngineEvent
}
