package com.anchat.engine.core

import com.anchat.engine.analyzer.ReplyAnalyzer
import com.anchat.engine.decomposer.BehaviorDecomposer
import com.anchat.engine.core.contract.BehaviorType
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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
    /** 行为拆解模块（真实对话）：复用同一 RequestSink 发起第二次请求 */
    private val decomposer = BehaviorDecomposer(requestSink)
    private val turnJobs = ConcurrentHashMap<String, Job>()

    fun send(input: TurnInput, context: ConversationContext) {
        val job = scope.launch {
            try {
                val processed = interceptor.process(input)
                val history = persistenceSink.getHistory(context.conversationId)
                val spec = requestBuilder.buildSpec(context, history)
                val raw = try {
                    requestSink.send(spec).copy(id = context.batchId)
                } catch (e: CancellationException) {
                // 用户删除气泡时取消尚未完成的回合；取消不是一次失败回复。
                throw e
            } catch (e: Exception) {
                    RawReply(
                        id = context.batchId,
                        conversationId = context.conversationId,
                        content = "❌ ${e.message ?: "发送失败"}",
                        isError = true
                    )
                }
                persistenceSink.persistRaw(raw.copy(conversationId = context.conversationId), context.conversationId)

                // ── 行为层分两路：真实对话开启→进入拆解 API；否则直出单条 speech ──
                val decompEnabled = context.realConversation && context.decompSpec != null && !raw.isError
                val behaviors = if (decompEnabled) {
                    try {
                        decomposer.decompose(raw.content, raw.id, context.decompSpec!!, context.conversationId, persistenceSink)
                    } catch (e: Exception) {
                        // 拆解失败（网络/解析/空事件）→ 回退直出，保证至少有可见回复
                        analyzer.analyze(raw)
                    }
                } else {
                    analyzer.analyze(raw)
                }
                persistenceSink.persistBehaviors(behaviors)
                scheduler.onPersisted(raw.id)

                // 列表预览取本批行为中「最后一条可见说话(speech)」的内容：
                // 末尾若是 movement(离开) / emotion(表情包) 则往前取，确保对应最后看到的文字气泡，
                // 而非整段回复开头——旧逻辑 raw.content.take(50) 显示的是这一批的第一条。
                val previewText = behaviors
                    .lastOrNull { it.type == BehaviorType.SPEECH }
                    ?.content
                    ?: behaviors.lastOrNull()?.content
                    ?: raw.content

                if (!raw.isError) {
                    val rec = ChatMessageRecord(
                        conversationId = context.conversationId,
                        role = "assistant",
                        content = raw.content,
                        reasoningContent = raw.reasoningContent,
                        usage = raw.usage,
                        batchId = context.batchId,
                        // 真实对话：原始整段回复仅入库供上下文，由行为层数据驱动 UI，不直接展示
                        hidden = decompEnabled
                    )
                    val newId = persistenceSink.persistAssistant(rec)
                    persistenceSink.updatePreview(context.conversationId, previewText.take(50))
                    // 始终 emit：VM 收到 hidden 标记后只清 loading，不进展示列表
                    engineSink.emit(EngineEvent.AssistantMessage(rec.copy(id = newId)))
                } else {
                    engineSink.emit(EngineEvent.Error(raw.content))
                }
            } catch (e: CancellationException) {
                // 用户删除气泡时取消尚未完成的回合；取消不是一次失败回复。
                throw e
            } catch (e: Exception) {
                engineSink.emit(EngineEvent.Error(e.message ?: "未知错误"))
            }
        }
        turnJobs.put(context.batchId, job)?.cancel()
        job.invokeOnCompletion { turnJobs.remove(context.batchId, job) }
    }

    /** 取消尚未落库的回合，防止删除后迟到的网络响应重新写回数据。 */
    fun cancel(batchId: String) {
        turnJobs.remove(batchId)?.cancel()
    }

    /** app 启动补播未完成行为 */
    fun catchUp() = scheduler.catchUp()
}
