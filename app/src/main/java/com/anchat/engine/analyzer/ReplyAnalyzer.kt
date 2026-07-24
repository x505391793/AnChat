package com.anchat.engine.analyzer

import com.anchat.engine.core.contract.Behavior
import com.anchat.engine.core.contract.BehaviorType
import com.anchat.engine.core.contract.RawReply
import java.util.UUID

/**
 * 【核心】RawReply → List<Behavior>。
 * 首版朴素实现：把 API 原文（或错误文本）作为一条 SPEECH 行为，
 * excuTime 取当前时刻（即时执行）。真正的「多句分解 + 停顿 + 动作」算法后补。
 */
class ReplyAnalyzer {
    fun analyze(raw: RawReply): List<Behavior> {
        return listOf(
            Behavior(
                behaviorId = UUID.randomUUID().toString(),
                order = 0,
                type = BehaviorType.SPEECH,
                role = "assistant",
                content = raw.content,
                excuTime = System.currentTimeMillis(),
                status = Behavior.STATUS_PENDING,
                batchId = raw.id
            )
        )
    }
}
