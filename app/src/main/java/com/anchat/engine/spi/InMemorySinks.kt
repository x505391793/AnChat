package com.anchat.engine.spi

import com.anchat.engine.core.contract.Behavior
import com.anchat.engine.core.contract.ChatMessageRecord
import com.anchat.engine.core.contract.EngineEvent
import com.anchat.engine.core.contract.RawReply
import com.anchat.engine.core.contract.RequestSpec
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 引擎模块自带的内存假实现，便于独立测试 / 不依赖 android 跑通整条链路。
 * 真实实现在对话模块侧（com.anchat.data.engine）。
 */
class InMemoryRequestSink : RequestSink {
    var lastSpec: RequestSpec? = null
    override suspend fun send(spec: RequestSpec): RawReply {
        lastSpec = spec
        return RawReply(
            id = "raw-${spec.messages.size}",
            conversationId = "",
            content = "[stub] ${spec.messages.lastOrNull()?.content ?: ""}"
        )
    }
}

class InMemoryPersistenceSink : PersistenceSink {
    val raws = mutableListOf<RawReply>()
    val behaviors = mutableListOf<Behavior>()
    private val history = mutableListOf<ChatMessageRecord>()

    override suspend fun getHistory(conversationId: String): List<ChatMessageRecord> = history.toList()
    override suspend fun persistRaw(raw: RawReply, conversationId: String) { raws += raw }
    override suspend fun persistBehaviors(list: List<Behavior>) { behaviors += list }
    override suspend fun markCompleted(behaviorId: String) {
        behaviors.replaceAll { if (it.behaviorId == behaviorId) it.copy(status = Behavior.STATUS_SENT) else it }
    }
    override suspend fun markRead(behaviorId: String) {
        behaviors.replaceAll { if (it.behaviorId == behaviorId) it.copy(status = Behavior.STATUS_READ) else it }
    }
    override suspend fun getDueBehaviors(): List<Behavior> = behaviors.filter { it.status == Behavior.STATUS_PENDING }
}

class InMemoryEngineSink : EngineSink {
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()
    override fun emit(event: EngineEvent) { _events.tryEmit(event) }
}
