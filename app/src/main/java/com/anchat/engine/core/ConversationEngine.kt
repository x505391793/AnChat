package com.anchat.engine.core

import com.anchat.engine.analyzer.ReplyAnalyzer
import com.anchat.engine.core.contract.ChatMessageRecord
import com.anchat.engine.core.contract.ConversationContext
import com.anchat.engine.core.contract.EngineEvent
import com.anchat.engine.core.contract.RawReply
import com.anchat.engine.core.contract.TurnInput
import com.anchat.engine.scheduler.BehaviorScheduler
import com.anchat.engine.sender.PassThroughInterceptor
import com.anchat.engine.sender.RequestBuilder
import com.anchat.engine.sender.UserMessageInterceptor
import com.anchat.engine.spi.EngineSink
import com.anchat.engine.spi.PersistenceSink
import com.anchat.engine.spi.RequestSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 门面：编排 发送前拦截 → 请求 → 原始回复 → 分析 → 行为入库 → 调度 → 渲染事件。
 * 对外只暴露 fire-and-forget 的 [send]，内部在自有 scope 中执行，不阻塞调用方。
 */
class ConversationEngine(
    private val scope: CoroutineScope,
    private val requestSink: RequestSink,
    private val persistenceSink: PersistenceSink,
    private val engineSink: EngineSink,
    private val requestBuilder: RequestBuilder,
    private val analyzer: ReplyAnalyzer,
    private val scheduler: BehaviorScheduler,
    private val interceptor: UserMessageInterceptor = PassThroughInterceptor()
) {
    fun send(input: TurnInput, context: ConversationContext) {
        scope.launch {
            try {
                val processed = interceptor.process(input)
                val history = persistenceSink.getHistory(context.conversationId)
                val spec = requestBuilder.buildSpec(context, history)
                val raw = try {
                    requestSink.send(spec)
                } catch (e: Exception) {
                    RawReply(
                        id = UUID.randomUUID().toString(),
                        conversationId = context.conversationId,
                        content = "❌ ${e.message ?: "发送失败"}",
                        isError = true
                    )
                }
                persistenceSink.persistRaw(raw.copy(conversationId = context.conversationId), context.conversationId)
                val behaviors = analyzer.analyze(raw)
                persistenceSink.persistBehaviors(behaviors)
                scheduler.onPersisted(raw.id)

                if (!raw.isError) {
                    val rec = ChatMessageRecord(
                        conversationId = context.conversationId,
                        role = "assistant",
                        content = raw.content,
                        reasoningContent = raw.reasoningContent,
                        usage = raw.usage
                    )
                    persistenceSink.persistAssistant(rec)
                    persistenceSink.updatePreview(context.conversationId, raw.content.take(50))
                    engineSink.emit(EngineEvent.AssistantMessage(rec))
                } else {
                    engineSink.emit(EngineEvent.Error(raw.content))
                }
            } catch (e: Exception) {
                engineSink.emit(EngineEvent.Error(e.message ?: "未知错误"))
            }
        }
    }

    /** app 启动补播未完成行为 */
    fun catchUp() = scheduler.catchUp()
}
