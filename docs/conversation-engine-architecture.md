# 对话处理机（Conversation Engine）结构

> 状态：**仅结构，未实现代码。**
> 目标：把「对话智能处理」从 `ChatViewModel` 抽成独立纯 Kotlin 模块，与对话模块（UI / 落库 / 网络）经接口解耦。
> 引擎只依赖 Kotlin stdlib + `kotlinx.coroutines`，**绝不** import `android.*` / `androidx.*` / `com.anchat.ui.*` / `com.anchat.data.*`。

## 1. 数据流（异步化 8 步）

```
① 用户发送
   TurnInput → RequestBuilder(Pre 拦截)
② 用户消息入库（经 spi.PersistenceSink，落 messages 表）
③ Pre 拦截保留空接口：不做任何处理，逻辑透传下去
④ 发往 API：fire-and-forget（发完不等待、不阻塞 UI，请求异步）
        │  （后台协程继续等响应，UI 不再显示「正在输入」）
        ▼
⑤ 后台收到 API 回复 → RawReply（原始回复）
        │  persistRaw()  ← 入库（给 API 看 / 命中缓存）
        ▼
⑥ RawReply 进 analyzer（Post 拦截）
⑦ analyzer → List<Behavior>  ← 【核心】分析 + 一次性分解
        │  persistBehaviors()  ← 入库（给用户看；每条带 rawId，即 raw↔behavior 映射）
        │  completed=false, isRead=false
        ▼
⑧ 调度器（非轮询）：
     - 取最早未完成 excuTime，设一次性 timer（前台 delay / 后台 AlarmManager.setExact）
     - 到点 → completed=true → （当前推送 stub）顺带 isRead=true（标记「已接受」）
     - 再设下一个最早未完成的 timer
     - app 启动 / 被杀重启：查一次「completed=0 最早一条」补播
```

> **关键改变**（相对旧版）：发送后 UI 不再等 API；「正在输入」不再在发送时显示，改为由行为调度在 `excuTime` 到点时驱动（SPEECH 先 TYPING 后 SPEAKING）。消息推送不靠周期轮询，靠**到点调度器 + Room Flow 观察**。

## 2. 包结构（只留有用的一层）

```
com.anchat.engine
├── core/
│   ├── ConversationEngine.kt     // 门面：编排 ①→⑧
│   ├── ConversationContext.kt     // 当前会话的身份/模型/会话id 快照
│   └── contract/                // 模块内 + 跨模块的数据契约
│       ├── TurnInput.kt
│       ├── RequestSpec.kt
│       ├── RawReply.kt           // 原始回复（API 面向）
│       ├── Behavior.kt           // 单条行为（行为表 = 同 rawId 的全部 Behavior 行）
│       └── BehaviorType.kt       // 枚举 + SpeechStatus
├── sender/
│   └── RequestBuilder.kt        // Pre 拦截：输入→请求规格（身份/上下文/system/凭证）
├── analyzer/
│   └── ReplyAnalyzer.kt         // 【核心】RawReply → List<Behavior>
├── scheduler/                    // 替代旧 player：到点调度 + 续播（无周期轮询）
│   └── BehaviorScheduler.kt
└── spi/                         // 唯一与 android/落库/网络 接触的接缝
    ├── RequestSink.kt           // send(spec): RawReply（fire-and-forget 由实现方决定）
    ├── PersistenceSink.kt        // persistRaw() / persistBehaviors() / markCompleted() / markRead()
    └── EngineSink.kt           // emit(EngineEvent)（真实推送先 stub）
```

## 3. 核心数据契约

```kotlin
// 原始回复：模型面向，用于缓存命中，不直接给用户看
data class RawReply(
    val id: String,               // rawId
    val conversationId: String,
    val content: String,           // API 原始全文
    val reasoningContent: String?, // 推理（如有）
    val usage: TokenUsage,
    val createdAt: Long,
    val isError: Boolean = false  // API 失败标记（fire-and-forget 下错误必须落盘，不能只 Toast）
)

// 行为类型（枚举，后续逐步扩充更多处理类型）
enum class BehaviorType(val value: String) {
    SPEECH("speech"),     // 说一句话
    LEAVE("leave")       // 离开 / 暂离等动作
}

// speech 行为的渲染状态（仅 SPEECH 用；TYPING→SPEAKING 由 UI 在行为可见后推进，非存储字段）
enum class SpeechStatus(val value: String) {
    TYPING("typing"),    // 前端显示「对方正在输入…」
    SPEAKING("speaking")  // 文本显现
}

// 单条行为：行为表 = 同一 rawId 下的所有 Behavior 行（无需独立「表实体」/映射表）
data class Behavior(
    val behaviorId: String,       // 主 id（PK）
    val rawId: String,            // 副 id（FK → RawReply，兼作 raw↔behavior 映射）
    val order: Int,               // 执行顺序
    val type: BehaviorType,       // 行为类型
    val content: String,          // 说的内容 / 做的动作描述
    val excuTime: Long,          // 行为触发的真实 wall-clock 时间戳
    val completed: Boolean,       // 调度器是否已到点执行
    val isRead: Boolean           // 是否已被推送/接受（当前 stub：调度器翻 completed 时一并翻转）
)
```

analyzer 产出 `List<Behavior>`（即一张「行为表」），每条共用同一个 `rawId`；scheduler 消费这张表。

## 4. 模块职责（逐条对应步骤）

- **sender/RequestBuilder**（Pre 拦截）：`TurnInput` + `ConversationContext` → `RequestSpec`（身份快照、system 提示、模型与凭证）。**保留空处理接口**（首版不做用户消息分析，逻辑透传）。不做网络。
- **analyzer/ReplyAnalyzer（核心）**：`RawReply → List<Behavior>`。首版朴素实现：直接把 API 原文作**一条 `SPEECH` 行为**，按默认间隔填 `excuTime`。真正的「多句分解 + 停顿 + 动作」算法后补——这是本模块唯一重点打磨点。
- **scheduler/BehaviorScheduler**（替代旧 player，**无周期轮询**）：
  - 行为入库后，查 `WHERE completed=0 ORDER BY excuTime ASC LIMIT 1` 取最早一条；
  - 无 → 空闲；`excuTime<=now` → 立即 `markCompleted()`（+ 当前 `isRead=true` stub）→ 再查下一条；
  - 否则设**一次性** timer：前台协程 `delay(excuTime-now)`，后台 `AlarmManager.setExact` 单次精确闹钟（非周期）；
  - 到点 → `markCompleted()` → 重设下一条；
  - app 启动 / 被杀重启：跑一次上述「补播查询」，靠 `completed=false + excuTime` 续播。
  - 纯协程 + 对 `spi.PersistenceSink` 的依赖，**无 android 依赖**（AlarmManager 适配放在 spi/对话模块侧）。
- **core/ConversationEngine**：把 ①→⑧ 串起来，持有 `ConversationContext`，对外只暴露 `suspend fun send(input)`（fire-and-forget，内部不阻塞调用方）。

## 5. spi 接缝（解耦点，引擎不碰 android/网络/数据库）

- `RequestSink.send(spec): RawReply` — 对话模块注入真实 HTTP 实现；**fire-and-forget 由实现方保证**（如用 `CompletableDeferred` + 后台协程，调用方不等）。
- `PersistenceSink`：
  - `persistRaw(raw)` — 原始回复入库（含 `isError=true` 的失败标记）。
  - `persistBehaviors(behaviors)` — 分解数据入库；每条 `rawId` 即映射，无额外映射表。
  - `markCompleted(behaviorId)` / `markRead(behaviorId)` — 调度器推进状态机。
- `EngineSink.emit(event)` — 把行为推给前端渲染 / 落库。**真实推送（FCM 长连）先 stub**：当前调度器翻 `completed` 时直接 `markRead` 标记「已接受」，不真正下发；UI 改为**观察 behaviors 表**（Room Flow）自动刷新，而非 collect 一个 Flow。

> **双数据模型（step 双入库）**：`RawReply` 模型面向、服务 API 缓存；`Behavior` 用户面向、服务展示。两者靠 `rawId` 互查（raw 表 `rawId` PK，behaviors 表 `rawId` FK），**不另建映射表**。

## 6. 与现有 ChatViewModel 的切分

| 现在 ChatViewModel 里的 | 迁入 |
|---|---|
| profileOf / snapshotFrom（身份装配） | sender/RequestBuilder |
| buildSystemPrompt | sender/RequestBuilder |
| 模型与凭证解析 | sender/RequestBuilder（经 spi 取配置） |
| 调 API + 解析响应 + token 记账 | spi.RequestSink + analyzer |
| 等待 API 期间显示「正在输入」 | **删除**：改为行为调度在 excuTime 驱动 TYPING |
| 一次性把消息返前端 | scheduler/BehaviorScheduler（按时分推 + 续播） |
| Toast / 落库 | spi.EngineSink / PersistenceSink（错误走 persistRaw(isError) 落盘） |

`ChatViewModel` 退化成：采集输入 → 调 `ConversationEngine.send()`（fire-and-forget）→ UI 观察 behaviors 表渲染。

## 7. 落地顺序（建议）

1. `core/contract` 定死 `RawReply` + `Behavior` + `BehaviorType/SpeechStatus`。
2. `spi/` 四个接口（先用内存假实现跑通）。
3. `sender/RequestBuilder`（空处理接口透传）+ `analyzer/ReplyAnalyzer`（首版：原文→单条 SPEECH）。
4. `scheduler/BehaviorScheduler`：一次性 timer + 补播查询（`markCompleted`/`markRead`）。
5. `core/ConversationEngine` 编排（fire-and-forget）。
6. 对话模块侧：`RequestSink`/`PersistenceSink`/`EngineSink` 真实实现（raw 表 + behaviors 表建表，behaviors 加 `isRead` 列）+ `ChatViewModel` 改造为「观察 behaviors 表」+ 后台 `AlarmManager` 单次闹钟适配。
