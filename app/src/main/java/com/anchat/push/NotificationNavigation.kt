package com.anchat.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知点击待导航：MainActivity 收到通知 intent 的 openConversationId 后写入，
 * AnChatApp 的 NavHost 在组合时读取并跳转，然后清空。
 */
object NotificationNavigation {
    private val _target = MutableStateFlow<Long?>(null)
    val target: StateFlow<Long?> = _target.asStateFlow()

    fun set(id: Long?) {
        _target.value = id
    }
}
