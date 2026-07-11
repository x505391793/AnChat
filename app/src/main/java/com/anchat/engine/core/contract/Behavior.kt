package com.anchat.engine.core.contract

/** 行为类型（枚举，后续逐步扩充更多处理类型） */
enum class BehaviorType(val value: String) {
    SPEECH("speech"),     // 说一句话
    MOVEMENT("movement")  // 做一个动作（移动 / 跳转 / 工具 …）
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
    val excuTime: Long,          // 行为触发的真实 wall-clock 时间戳
    val completed: Boolean,       // 调度器是否已到点执行
    val isRead: Boolean           // 是否已被推送/接受（当前 stub：调度器翻 completed 时一并翻转）
)
