package com.anchat.engine.core.contract

/** 行为类型（枚举，后续逐步扩充更多处理类型） */
enum class BehaviorType(val value: String) {
    SPEECH("speech"),     // 发一句话
    EMOTION("emotion"),   // 发表情包
    MOVEMENT("movement")  // 离开 / 暂离等动作
}

/** speech 行为的渲染状态（仅 SPEECH 用；TYPING→SPEAKING 由 UI 在行为可见后推进，非存储字段） */
enum class SpeechStatus(val value: String) {
    TYPING("typing"),    // 前端显示「对方正在输入…」
    SPEAKING("speaking")  // 文本显现
}

/**
 * 单条行为：行为表 = 同一 rawId 下的所有 Behavior 行。
 * 无需独立「表实体」/ 映射表，rawId 即 raw↔behavior 映射。
 */
data class Behavior(
    val behaviorId: String,       // 主 id（PK）
    val rawId: String,            // 副 id（FK → RawReply，兼作映射）
    val order: Int,               // 执行顺序
    val type: BehaviorType,       // 行为类型
    val content: String,          // 说的内容 / 做的动作描述
    /** movement 时的离开时长文本（如「10分钟」）；其余类型固定 null */
    val duration: String? = null,
    val excuTime: Long,          // 行为触发的真实 wall-clock 时间戳
    /** 状态机：0=未到点(待播) / 1=已执行未读 / 2=已读。取代原 completed+isRead 两布尔（消除非法组合） */
    val status: Int = STATUS_PENDING,
    /** 所属对话 id（与 ConversationContext.conversationId 同值）；用于行为事件按对话隔离，避免串台 */
    val conversationId: String = ""
) {
    companion object {
        const val STATUS_PENDING = 0   // 未到点执行（待播，调度器还没轮到）
        const val STATUS_SENT = 1      // 已执行、未读（调度器翻牌并推到前端，用户尚未查看）
        const val STATUS_READ = 2      // 已读（用户实际看过该行为）
    }
}
