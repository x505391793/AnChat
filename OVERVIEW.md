# 流式回复功能 — 交付概览

## 功能说明
在角色卡「模型设置」中新增「流式回复」开关，完全复用「思考模式」的继承范式：
- **角色卡级** `streamEnabled`（默认开启）
- **对话快照级** `charStreamEnabled`（对话内编辑只写快照列，不回源主卡）
- 继承规则：`对话快照 ?: 角色卡 ?: true`

开关开启且为非真实对话时，助手回复以**真·逐字流式**（打字机+闪烁光标）呈现；真实对话（v1/v2 行为拆解）路径强制一次性（拆解需完整内容）。

## 改动文件
- 数据层：`CharacterEntity.kt`（streamEnabled）、`Conversation.kt`（charStreamEnabled）、`AppDatabase.kt`（v23→v24 迁移，两表各加 `NOT NULL DEFAULT 1` 列）
- 设置 UI：`CharacterEditScreen.kt`（`ModelSettingsFields` 新增开关 + 预填/保存透传）
- Profile 链路：`ConversationProfile.kt`（streamEnabled）、`ChatViewModel.kt`（profileOf 继承、snapshotFrom 烤入、streamEnabled StateFlow、flushQueue 构造 `stream = streamEnabled && !realConv`）
- 引擎请求：`Messages.kt`（`RequestSpec.stream`/`ConversationContext.stream`/`EngineEvent.StreamDelta`）、`RequestBuilder.kt`（透传）
- SSE 核心：`DeepSeekApi.kt`（`sendChatStream`）、`EngineRequestSink.kt`（流式分支 + onDelta 回调）、`SpiInterfaces.kt`/`InMemorySinks.kt`（onDelta 参数）、`ConversationEngine.kt`（emit StreamDelta）
- UI 渲染：`ChatViewModel.kt`（StreamDelta 建/更新临时气泡、BehaviorDue 终态替换、Error 清理、防重叠）、`ChatScreen.kt`（流式光标闪烁 + 输入禁用）、`AnChatApplication.kt`（when 穷尽补 StreamDelta 分支）

## 验证
- `:app:assembleDebug` → **BUILD SUCCESSFUL**
- 仅旧代码的 deprecation warning（与本次改动无关）

## 已知限制
- 真实对话模式仍为一次性返回（行为拆解依赖完整回复内容，后续如需真流式需改 decomposer，超出本次范围）
- 需 `adb uninstall` 清库或升级安装以触发 v23→v24 迁移（Room 无 destructive 回退，老库直接升会崩）
