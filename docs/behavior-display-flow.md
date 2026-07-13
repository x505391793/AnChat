# 行为拆解后：展示与处理流程

> 本文只记录**行为分解完成之后**，系统如何落库、调度、推送到 UI、以及按类型渲染。
> 分解方式（v1 二次请求 / v2 单次请求）不在本文范围，见 `conversation-engine-architecture.md`。

## 1. 数据形态（分解之后得到了什么）

- 行为数据由 `persistenceSink.persistBehaviors(...)` 写入 **`behaviors` 表**（原始整段回复此前已落 `raw_replies`，二者并存）。
- 每条行为的关键字段：

| 字段 | 含义 |
|---|---|
| `type` | 行为类型：`speech` / `leave` / `emotion` |
| `content` | 文本内容（speech 的说话内容；leave 一般为占位） |
| `duration` | leave 时的离开时长（如 `10 min`）；其余类型 `null` |
| `excuTime` | 计划展示时刻（毫秒时间戳），驱动"分时推送" |
| `completed` | 0=未执行 1=已执行未读 2=已读 |
| `isRead` | 是否已读标记 |

## 2. 流程总览

```
得到行为数据（behaviors 入库）
        │
        ▼
调度器分时推送（BehaviorScheduler，按 excuTime）
        │
        ▼
UI 接收 BehaviorDue 事件
        │
        ▼
按 behaviorType 分支渲染（ChatScreen）
   ├─ speech  →  左侧气泡，直接说话
   └─ leave   →  居中状态行「对方离开了」
```

## 3. 分步说明

### 3.1 得到行为数据
`ConversationEngine` 在 raw 落库后调用 `persistBehaviors(behaviors)`，行为进入 `behaviors` 表，状态 `completed=0`。

### 3.2 调度器分时推送（BehaviorScheduler）
- **实时路径 `onPersisted(rawId)`**：查 `excuTime <= now` 的待执行行为，逐条 `markCompleted(0→1)`，并通过 `engineSink.emit(EngineEvent.BehaviorDue)` 推给 UI；再对最早一条"未来"行为挂一次性 `delay(excuTime - now)` 续播。
- **重启补播 `catchUp()`**：App 启动 / 被杀重启时跑一次，已到点的历史行为静默 `markCompleted` 且 `markRead(→2)`，**不推 UI**（视为上一会话已看）。

### 3.3 UI 接收
- **实时**：`ChatViewModel` 监听 `EngineEvent.BehaviorDue` → `behaviorToChatMessage(b)` 生成 `ChatMessage(role="behavior", content=b.content, behaviorType=b.type.value, duration=b.duration)` → 追加到消息列表 → `markBehaviorRead(1→2)`。
- **进入会话**：`buildInitialDisplay` 合并「可见消息（用户 + 非隐藏助手）」与「已完成行为」，按时间序展示；真实对话模式下原始整段回复（`hidden`）不进气泡，改由行为层呈现。

### 3.4 按类型渲染（ChatScreen，依据 `msg.behaviorType`）
- **`speech`** → 正常聊天气泡（左侧 AI 头像 + 白底），即"直接说话"，`content` 为说话文本。
- **`leave`** → 不入气泡，居中显示状态行 **「对方离开了」**（`content` 作占位被忽略）。
- **`emotion`**（旁支类型）→ 居中胶囊「😊 对方发表情包」。

## 4. 隐藏原始回复
真实对话模式下，`messages` 表中对应原始整段回复标记为 `hidden`，不进入气泡；用户看到的内容完全由 `behaviors` 行为层驱动（speech 气泡 + leave 状态行）。普通（非真实对话）模式则仍是直出单条消息，无此分层。
