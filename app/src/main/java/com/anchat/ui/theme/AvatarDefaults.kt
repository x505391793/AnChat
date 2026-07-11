package com.anchat.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.core.math.MathUtils

/** 统一的默认头像色板（微信绿系 + 辅色），全局共用，避免各页配色不一致 */
val AnChatAvatarPalette = listOf(
    Color(0xFF07C160),
    Color(0xFF10AEFF),
    Color(0xFFF76260),
    Color(0xFF6467F0),
    Color(0xFFFA9D3B),
    Color(0xFF9F8BFE),
)

/** 默认头像首字母：始终取「原名」首字符，不受备注影响 */
fun avatarInitial(name: String?): String =
    name?.firstOrNull()?.toString() ?: "?"

/** 默认头像配色：基于「原名」稳定映射，同名同色、跨页统一；备注/改名均不影响 */
fun avatarColor(name: String?): Color {
    val seed = name?.takeIf { it.isNotBlank() } ?: "?"
    val idx = MathUtils.clamp(kotlin.math.abs(seed.hashCode()), 0, AnChatAvatarPalette.size - 1)
    return AnChatAvatarPalette[idx]
}
