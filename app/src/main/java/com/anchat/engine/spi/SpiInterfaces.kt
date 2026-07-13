package com.anchat.engine.spi

import com.anchat.engine.core.contract.Behavior
import com.anchat.engine.core.contract.ChatMessageRecord
import com.anchat.engine.core.contract.EngineEvent
import com.anchat.engine.core.contract.RawReply
import com.anchat.engine.core.contract.RequestSpec

/** 真正发 HTTP 请求的地方，由对话模块注入实现（fire-and-forget 由实现方保证） */
interface RequestSink {
    suspend fun send(spec: RequestSpec): RawReply
}

/** 落库接缝：引擎不碰 Room / android，全部经此接口 */
interface PersistenceSink {
    /** 取对话历史（user/assistant），供拼请求 */
    suspend fun getHistory(conversationId: String): List<ChatMessageRecord>

    /** 原始回复入库（给 API 看 / 命中缓存）；raw.id 即主键，批次键仅属行为层 */
    suspend fun persistRaw(raw: RawReply, conversationId: String)

    /** 分解行为入库；每条 rawId 即 raw↔behavior 映射 */
    suspend fun persistBehaviors(behaviors: List<Behavior>)

    /** 更新对话 preview */
    suspend fun updatePreview(conversationId: String, preview: String)

    /** 调度器推进状态机 */
    suspend fun markCompleted(behaviorId: String)
    suspend fun markRead(behaviorId: String)

    /** 查最早未完成行为（completed=0），供调度器续播 */
    suspend fun getDueBehaviors(): List<Behavior>
}

/** 引擎向外吐渲染事件；真实推送（FCM 长连）先 stub */
interface EngineSink {
    fun emit(event: EngineEvent)
}
