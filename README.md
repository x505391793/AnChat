# AnChat

试图模拟真人聊天行为的 AI Chat 应用。

## 已实现

- 2026-07-10：基础对话功能移植完成（Spring Boot → Android 原生）
- 2026-07-10：微信式 UI 重构——底部 4 tab（AnChat / 通讯录 / 发现 / 我）、角色卡系统（创建 / 对话 / 开场白）、设置页微信白底绿辅风、各页顶栏标题居中

## 进行中

- UI 布局细节优化（顶部栏居中已完成，整体布局打磨进行中）

## 待实现

1. 仿真实聊天界面交互（长按菜单、滑动删除、打字动画）
2. 聊天行为逻辑管理模块
3. 长期记忆
4. 对话搜索 / 星标筛选
5. 模型列表自动拉取

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

## 数据库 (Room v2)

**conversations**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long PK 自增 | |
| title | String | 对话标题 |
| preview | String? | 最新消息摘要 |
| isStar | Boolean | 星标 |
| modelId | String? | 默认模型 |
| systemPrompt | String? | 系统提示词 |
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
| promptTokens | Int? | 提问 token |
| completionTokens | Int? | 回答 token |
| totalTokens | Int? | 总 token |
| reasoningTokens | Int? | 推理 token |
| promptCacheHitTokens | Int? | 缓存命中 |
| promptCacheMissTokens | Int? | 缓存未命中 |
| createdAt | Long | 时间戳 |

**model_entity**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | String PK | 模型 ID |
| name | String | 显示名称 |
| description | String? | 描述 |
| systemPrompt | String? | 配套提示词 |
| isDefault | Boolean | 默认模型 |

**character**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long PK 自增 | |
| name | String | 角色名 |
| avatar | String? | 头像 |
| description | String? | 描述 |
| systemPrompt | String? | 系统提示词 |
| greeting | String? | 开场白 |
| userAvatar | String? | 用户头像 |
| userName | String? | 用户身份名 |
| userDescription | String? | 用户身份描述 |
| createdAt / updatedAt | Long | 时间戳 |

## 项目结构

```
app/src/main/java/com/anchat/
├── AnChatApplication.kt
├── MainActivity.kt
├── data/
│   ├── config/          # AppConfig + ConfigManager
│   ├── local/           # Room: DB, DAO, Entity
│   ├── remote/          # DeepSeekApi + ChatModels
│   └── repository/      # Chat / Local / Settings
├── ui/
│   ├── chat/            # ChatScreen + ChatViewModel
│   ├── contacts/        # ContactsScreen + CharacterEditScreen
│   ├── discover/        # DiscoverScreen（待开发）
│   ├── history/         # HistoryScreen + HistoryViewModel
│   ├── main/            # AnChatApp + LocalApp
│   ├── me/              # MeScreen
│   ├── settings/        # SettingsScreen + SettingsViewModel
│   └── theme/
```

## 构建 & 运行

1. Android Studio 打开项目根目录
2. Sync Gradle
3. 创建模拟器或连接真机
4. Run 'app'
