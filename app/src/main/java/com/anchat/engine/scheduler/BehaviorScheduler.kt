package com.anchat.engine.scheduler

import com.anchat.engine.core.contract.Behavior
import com.anchat.engine.core.contract.EngineEvent
import com.anchat.engine.spi.EngineSink
import com.anchat.engine.spi.PersistenceSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 行为调度器（替代旧 player，无周期轮询）。
 * - 行为入库后查最早未完成 excuTime，设一次性 timer（前台 delay / 后台 AlarmManager 由对话模块适配）。
 * - 到点 flip completed → 再设下一个最早未完成 timer。
 * - 启动 / 被杀重启：跑一次补播查询，靠 excuTime 续播。
 * 纯协程 + 对 spi.PersistenceSink / EngineSink 的依赖，无 android 依赖。
 */
class BehaviorScheduler(
    private val scope: CoroutineScope,
    private val persistence: PersistenceSink,
    private val engineSink: EngineSink
) {
    /** 一批行为入库后调用（实时路径：翻 completed 并推送 BehaviorDue 事件给 UI） */
    fun onPersisted(rawId: String) {
        scope.launch { processDue(emitEvents = true) }
    }

    /** app 启动 / 被杀重启时调用，补播未完成行为（静默翻 completed，不推送 UI 事件） */
    fun catchUp() {
        scope.launch { processDue(emitEvents = false) }
    }

    private suspend fun processDue(emitEvents: Boolean) {
        val now = System.currentTimeMillis()
        val pending = persistence.getDueBehaviors()       // completed=0，按 excu_time ASC
        val due = pending.filter { it.excuTime <= now }   // 仅处理已到点的
        due.forEach {
            persistence.markCompleted(it.behaviorId)   // 0 → 1（已执行未读）
            if (emitEvents) {
                // 实时路径：行为到点即发事件，UI 据此实时追加；不依赖 Room Flow 失效重查
                engineSink.emit(EngineEvent.BehaviorDue(it))
            } else {
                // 重启补播：历史已到点行为视为已读（用户上一会话已看），静默不推 UI
                persistence.markRead(it.behaviorId)    // 1 → 2
            }
        }
        armNext(pending, now, emitEvents)
    }

    private suspend fun armNext(pending: List<Behavior>, now: Long, emitEvents: Boolean) {
        // 在尚未完成的行为里找最早一条「未来」行为，挂一次性 timer；没有则结束。
        val next = pending.firstOrNull { it.excuTime > now } ?: return
        val delayMs = next.excuTime - now
        if (delayMs <= 0) {
            processDue(emitEvents)
        } else {
            scope.launch {
                delay(delayMs)
                processDue(emitEvents)
            }
        }
    }
}
