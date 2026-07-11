package com.anchat.engine.scheduler

import com.anchat.engine.core.contract.Behavior
import com.anchat.engine.spi.PersistenceSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 行为调度器（替代旧 player，无周期轮询）。
 * - 行为入库后查最早未完成 excuTime，设一次性 timer（前台 delay / 后台 AlarmManager 由对话模块适配）。
 * - 到点 flip completed → 再设下一个最早未完成 timer。
 * - 启动 / 被杀重启：跑一次补播查询，靠 excuTime 续播。
 * 纯协程 + 对 spi.PersistenceSink 的依赖，无 android 依赖。
 */
class BehaviorScheduler(
    private val scope: CoroutineScope,
    private val persistence: PersistenceSink
) {
    /** 一批行为入库后调用 */
    fun onPersisted(rawId: String) {
        scope.launch { processDue() }
    }

    /** app 启动 / 被杀重启时调用，补播未完成行为 */
    fun catchUp() {
        scope.launch { processDue() }
    }

    private suspend fun processDue() {
        val now = System.currentTimeMillis()
        val pending = persistence.getDueBehaviors()       // completed=0，按 excu_time ASC
        val due = pending.filter { it.excuTime <= now }   // 仅处理已到点的
        due.forEach {
            persistence.markCompleted(it.behaviorId)
            persistence.markRead(it.behaviorId)
        }
        armNext(pending, now)
    }

    private suspend fun armNext(pending: List<Behavior>, now: Long) {
        // 在尚未完成的行为里找最早一条「未来」行为，挂一次性 timer；没有则结束。
        val next = pending.firstOrNull { it.excuTime > now } ?: return
        val delayMs = next.excuTime - now
        if (delayMs <= 0) {
            processDue()
        } else {
            scope.launch {
                delay(delayMs)
                processDue()
            }
        }
    }
}
