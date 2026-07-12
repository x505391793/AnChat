package com.anchat.ui.theme

import androidx.compose.ui.graphics.Color

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

/**
 * 默认头像配色：对传入的「稳定种子」做哈希，取模映射到固定色板（6 色均匀分布）。
 *
 * ⚠️ 调用方必须传入对**同一角色永远不变**的值（角色原名 / 角色 id 的字符串），
 *    严禁传入备注(remark)或对话标题(title)——否则同一角色在通讯录与对话列表会
 *    算出不同颜色与首字母，破坏「跨列表一致」。
 * 同名（同种子）同色，因此本函数本身保证确定性、可复现。
 */
fun avatarColor(seed: String?): Color {
    val key = seed?.takeIf { it.isNotBlank() } ?: "?"
    // 与 Int.MAX_VALUE 按位与：清掉符号位，规避 abs(Int.MIN_VALUE) 仍为负数的陷阱；
    // 再对色板大小取模，得到 0..size-1 的均匀分布（clamp 会全部截断到末位色）。
    val idx = (key.hashCode() and Int.MAX_VALUE) % AnChatAvatarPalette.size
    return AnChatAvatarPalette[idx]
}
