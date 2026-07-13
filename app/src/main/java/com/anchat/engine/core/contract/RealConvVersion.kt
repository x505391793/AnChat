package com.anchat.engine.core.contract

/**
 * 真实对话版本：控制「开启真实对话后具体走哪条行为生成路径」。
 * - V1：行为拆解（二次请求管理 AI → 拆成 behaviors → 分时调度），当前默认逻辑。
 * - V2：单次请求（在首条请求的 system 提示末尾追加输出约束，主 AI 直接输出已拆解的
 *         JSON 行为事件，不再二次请求）。
 *
 * 扩展新版本：在此加常量、扩充 [ALL]，并在 ConversationEngine 路由分支补充对应逻辑、
 * 在 RequestBuilder 补充该版本的提示注入与 response_format 设定。
 * 若最终确定只保留单一版本，把 [DEFAULT] 写死、并隐藏 UI 选择即可。
 */
object RealConvVersion {
    const val V1 = "v1"
    const val V2 = "v2"
    val ALL = listOf(V1, V2)
    val DEFAULT = V1

    /**
     * v2 专用：追加在「正常 system 提示（角色人设 + 用户身份）」末尾的输出约束。
     * 主 AI 据此直接输出已按时间拆好的行为事件 JSON，无需二次请求。
     */
    const val V2_INSTRUCTION = """输出内容为行为事件，按时间顺序输出 JSON 数组。

类型：
- speech：发消息。根据自然停顿、省略号或语气变化，将一段话拆为多条独立消息，每条为一个speech事件。
- leave：离开/暂离。当剧情发展到你需要离开时，使用这个事件。
每次只能有1个leave，1个emotion。

字段（每条事件）：
- order: 序号
- type: "speech" | "leave"
- content: 文本内容。（只有speech有。）
- duration: leave 时填离开时长:xxx min，其它类型固定 null

只输出 JSON，格式：
{"events":[{"order":1,"type":"speech","content":"...","duration":null}]}"""
}
