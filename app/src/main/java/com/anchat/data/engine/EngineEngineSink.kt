package com.anchat.data.engine

import com.anchat.engine.core.contract.EngineEvent
import com.anchat.engine.spi.EngineSink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 真实 EngineSink：用 SharedFlow 把渲染事件抛给 UI（真实推送 FCM 先 stub） */
class EngineEngineSink : EngineSink {
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    val events: Flow<EngineEvent> = _events.asSharedFlow()

    override fun emit(event: EngineEvent) {
        _events.tryEmit(event)
    }
}
