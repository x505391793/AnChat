package com.anchat.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局「当前正打开的会话」跟踪。
 * 推送判定依赖它：助手消息到达时，若其会话 == 当前打开的会话，
 * 则不弹通知（你正在看）；否则弹系统通知 + 保留未读红点。
 * ChatViewModel 在会话 id 确定 / 离开时更新它。
 */
object ActiveConversation {
    private val _id = MutableStateFlow<Long?>(null)
    val id: StateFlow<Long?> = _id.asStateFlow()

    fun set(id: Long?) {
        _id.value = id
    }
}
