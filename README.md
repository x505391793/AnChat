# AnChat

试图模拟真人聊天行为的 AI Chat 应用。

## 已实现

- 2026-07-10：基础对话功能移植完成（Spring Boot → Android 原生）
- 2026-07-10：微信式 UI 重构
- 2026-07-10：深色模式
- 2026-07-11：UI 更新（通讯录 / 角色卡 / 个人名片 / 对话列表 / 推送通知）—— 暂时完结一阶段，后续持续调整
- 2026-07-11：对话处理引擎（行为机）框架接入 + 原始 / 行为两层数据层
- 2026-07-12：真实对话行为拆解引擎 + API 日志 + 头像/预览/状态机等多项修复


## 进行中

- 行为分时推送交互打磨（可视化 / 切表编辑）
- 长期记忆

## 待实现

1. 仿真实聊天界面交互（长按菜单、滑动删除、打字动画）
2. 长期记忆
3. 对话搜索 / 星标筛选
4. 模型列表自动拉取

## 技术栈

| 层 | 技术 |
|---|------|
| UI | Jetpack Compose + Material3 |
| 状态 | ViewModel + StateFlow |
| 导航 | Navigation Compose |
| 存储 | Room (SQLite) |
| 网络 | OkHttp + kotlinx-serialization |
| 配置 | JSON 文件 |
| 语言 | Kotlin 2.0.21 / JVM 17 |

## 数据库 (Room v18)

> 开发期 `fallbackToDestructiveMigration`：表结构变动直接重建，后续需写 Migration 保数据。模型已移出 Room 存 JSON 配置，无 `models` 表。

**conversations**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long PK 自增 | |
| title | String | 对话标题 |
| preview | String? | 最新消息摘要 |
| isStar | Boolean | 星标 |
| isPinned | Boolean | 置顶 |
| modelId | String? | 绑定模型（空=跟随全局默认） |
| characterId | Long? | 关联角色卡（空=无角色，用全局主身份） |
| systemPrompt | String? | 对话级系统提示（覆盖角色卡） |
| charRemark | String? | 对话内备注（覆盖角色名显示） |
| charName / charAvatar / charDescription / charGreeting | String? | 角色信息快照（继承自主角色卡，可对话内二次编辑） |
| charThinkingEnabled | Boolean | 对话级思考开关 |
| userName / userAvatar / userDescription | String? | 用户身份快照 |
| createdAt / updatedAt | Long | 时间戳 |

**messages**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long PK 自增 | |
| conversationId | Long FK | 所属对话 |
| role | String | user / assistant / system |
| content | String? | 回答正文 |
| reasoningContent | String? | 思考过程 |
| model | String? | 模型 |
| finishReason | String? | stop / length |
| promptTokens / completionTokens / totalTokens / reasoningTokens | Int? | token 统计 |
| promptCacheHitTokens / promptCacheMissTokens | Int? | 缓存命中 / 未命中 |
| createdAt | Long | 时间戳 |

**characters**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long PK 自增 | |
| name | String | 角色名 |
| remark | String? | 主卡备注（通讯录/列表优先于 name 显示） |
| avatar | String? | 头像 |
| description | String? | 描述 |
| systemPrompt | String? | 系统提示词 |
| greeting | String? | 开场白 |
| userName / userAvatar / userDescription | String? | 用户身份 |
| model_id | String? | 绑定模型（空=跟随全局） |
| thinking_enabled | Boolean | 思考开关 |
| createdAt / updatedAt | Long | 时间戳 |

**raw_replies（原始数据层 · 引擎写入）**

模型原始回复，面向 API 上下文 / 缓存命中，不直接展示给用户。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | String PK (UUID) | |
| conversationId | String | 所属对话 |
| content | String | 原始回复正文 |
| reasoningContent | String? | 思考过程 |
| promptTokens / completionTokens / totalTokens / reasoningTokens | Int? | token 统计 |
| promptCacheHitTokens / promptCacheMissTokens | Int? | 缓存命中 / 未命中 |
| isError | Boolean | 是否为错误回复 |
| kind | String | 来源类型（chat=聊天AI / decomp=行为拆解 / system=系统） |
| createdAt | Long | 时间戳 |

**behaviors（行为数据层 · 引擎写入）**

`ReplyAnalyzer` 把一条 `RawReply` 分解为多条 `Behavior`（多句 + 动作 + 停顿），面向用户展示；经 `rawId` 锚定来源原始回复。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | String PK (UUID) | |
| rawId | String FK | 来源 raw_replies.id |
| order | Int | 同条回复内行为顺序 |
| type | String | 行为类型（SPEECH / ACTION 等） |
| content | String? | 行为内容（如本句文本） |
| excuTime | Long | 计划执行（推送）时间戳，支持分时推送 |
| status | Int | 状态机：0=未到点 / 1=已执行未读 / 2=已读 |
| conversationId | String | 所属对话（行为按对话隔离，避免串台） |

## 对话处理引擎（行为机）运行逻辑

把「对话智能处理」从 `ChatViewModel` 抽成独立纯 Kotlin 模块 `com.anchat.engine`（仅依赖 stdlib + coroutines，不 import android / androidx / ui / data），与对话侧经三个 spi 接缝解耦：

- `RequestSink` —— 真正发起 API 请求
- `PersistenceSink` —— 真正落库（raw_replies / behaviors / messages）
- `EngineSink` —— 向 UI 发渲染事件

一次发送的 5 步数据流：

1. `ChatViewModel.send()` 采集输入 → 建/取对话 → 落用户消息 → 构造 `ConversationContext`（含 system 提示、模型凭证），调用 `engine.send(TurnInput, context)`（fire-and-forget，不阻塞 UI）。
2. 引擎 `RequestBuilder` 拼请求 → `RequestSink.send()` 拿回 `RawReply`（原始回复）。
3. `PersistenceSink.persistRaw(raw)` 写入 **raw_replies** 表（含 `kind` 区分来源；供 API 上下文 / 缓存命中 / 全量审计）。
4. 行为拆解分两路：
   - **真实对话**（开启且配置了管理 AI）：`BehaviorDecomposer` 把整段 `RawReply` 二次包装请求发给「真实对话管理 AI」，由其按微信拟人语义拆解为多条 `Behavior`（speech / emotion / movement），经 `persistBehaviors` 写入 **behaviors** 表；原始整段回复仅入库供上下文，不直接展示。
   - **非真实对话**：`ReplyAnalyzer.analyze(raw)` 直出单条 `Behavior`。
5. `BehaviorScheduler` 按 `excuTime` 分时派发：到点翻 `status`（0→1）并经 `EngineSink.emit(EngineEvent.BehaviorDue)` **显式**推给 UI（取代旧版依赖 Room Flow 失效重查的不可靠方案）；同时 `persistAssistant` 落 messages 表、更新对话 preview 为最后一条可见说话；`ChatViewModel` 经 `engineEvents` 收事件渲染气泡。

分层结果：`RawReply`（模型面向）与 `BehaviorTable`（用户面向）双数据并存、可互查；`ChatViewModel` 退化为「输入采集 + 引擎驱动 + 结果落库渲染」的薄适配层。

## 项目结构

```
app/src/main/java/com/anchat/
├── AnChatApplication.kt
├── MainActivity.kt
├── engine/              # 对话处理引擎（纯 Kotlin，与 android 解耦）
│   ├── decomposer/      # BehaviorDecomposer（真实对话二次拆解）
│   ├── core/            # ConversationEngine 门面 + contract
│   ├── sender/          # RequestBuilder
│   ├── analyzer/        # ReplyAnalyzer（RawReply→BehaviorTable）
│   ├── scheduler/       # BehaviorScheduler（分时派发）
│   └── spi/            # RequestSink / PersistenceSink / EngineSink 接缝
├── service/             # 前台推送服务
├── data/
│   ├── config/          # AppConfig + ConfigManager
│   ├── local/           # Room: DB, DAO, Entity
│   ├── engine/          # 引擎三接缝真实现 + raw_replies/behaviors 实体
│   ├── remote/          # DeepSeekApi + ChatModels
│   └── repository/      # Chat / Local / Settings
├── ui/
│   ├── chat/            # ChatScreen + ChatViewModel
│   ├── contacts/        # ContactsScreen + CharacterEditScreen
│   ├── discover/        # DiscoverScreen（待开发）
│   ├── history/         # HistoryScreen + HistoryViewModel
│   ├── main/            # AnChatApp + LocalApp
│   ├── me/              # MeScreen
│   ├── settings/        # SettingsScreen + SettingsViewModel + LogScreen/LogViewModel（API 日志）
│   └── theme/
```

## 构建 & 运行

1. Android Studio 打开项目根目录
2. Sync Gradle
3. 创建模拟器或连接真机
4. Run 'app'
