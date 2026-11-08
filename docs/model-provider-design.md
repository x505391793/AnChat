# 模型管理改造设计（多提供方 + URL 内置）

> 状态：设计稿，未改动任何代码。
> 目标：在保留现有全部数据与引用关系的前提下，把「填 Key + URL → 拉取模型」改成「选提供方 + 填 Key + 填模型名 → 保存为一条可选模型」，并新增 Aimat 中转站。

---

## 1. 需求确认

| 项 | 结论 |
|---|---|
| 提供方 | 本期只有 `DeepSeek`、`Aimat` 两个，下拉选择；后续加提供方只改一处配置 |
| 添加参数 | 提供方 + API Key + 模型名（文本框手填，原样作为 API 请求体 `model` 字段） |
| 一条 = 一个模型 | 每次保存产生 1 条模型记录；同一 Key 可配多个模型名（如 `MiniMax-H3`、`gpt-4o`），各自独立可选 |
| URL | 写死在代码配置类，**UI 不再出现、不再可编辑** |
| 旧数据 | 已保存的模型、Key、以及角色卡/对话/全局的模型选择，一条不丢、行为不变 |

Aimat 与 DeepSeek 都是 OpenAI 兼容协议，请求/响应体同构，差异**只有请求地址**：

| 提供方 | 内置 baseUrl | 实际 POST |
|---|---|---|
| DeepSeek | `https://api.deepseek.com` | `https://api.deepseek.com/chat/completions` |
| Aimat | `https://aimat.cn/v1` | `https://aimat.cn/v1/chat/completions` |

现有 `DeepSeekApi.sendChat(apiKey, apiUrl, req)` 内部就是 `apiUrl + "/chat/completions"`（DeepSeekApi.kt:48），所以**网络层无需改造，只换 `apiUrl` 的来源**。

---

## 2. 现状关键事实（决定设计边界）

1. **`ModelConfig.id` 同时是三样东西**：API 传参的 `model` 值、模型列表主键、全局唯一引用键。
   - `ConfigManager.getModel/removeModel/addModels` 按 `id` 查重与覆盖（ConfigManager.kt:88-122）。
   - `AppConfig.defaultModelId / chatModelId / realConversationModelId` 存的是 `id` 字符串。
   - `CharacterEntity.model_id`、`Conversation.model_id`（Room 两张表）存的也是 `id` 字符串。
   - `ConversationContext.modelId` → `RequestBuilder` → `RequestSpec.model` → `ChatRequest.model`。
   ⇒ **绝不能改 `id` 的含义或取值**，否则配置文件、数据库、备份里的引用全部失效。
2. `AppConfig` / `BackupPayload.config` 是整份 `AppConfig` 序列化；`ConfigManager` 的 Json 已开 `ignoreUnknownKeys = true`，**加字段对旧配置 / 旧备份天然兼容**。
3. `ModelConfig.apiKey`、`apiUrl` 目前每个模型各存一份；UI 表单是「Key + 基地址 → 拉取 /models → 多选添加」。
4. `RequestBuilder.kt:53` 与 `BehaviorDecomposer.kt:59` 用 `modelId.contains("deepseek")` 决定是否发 `response_format=json_object`。
5. `AnChatApplication.onCreate` 在模型列表为空时补种两个内置 DeepSeek 模型（Key 为空）。
6. Room 当前 version = 25，**本次不涉及任何实体/表变更，不需要数据库迁移**。

---

## 3. 核心设计决策

### 决策 A：`id` 保持原样 = 用户输入的模型名

不引入 UUID 主键、不改引用格式。理由：改主键要同步迁移 config 3 个字段 + Room 2 张表 + 备份指纹逻辑，风险与工作量都远超收益。

**保存时的查重规则**（在 `ConfigManager.addModels` 语义上加一层前置校验）：

| 情形 | 行为 |
|---|---|
| 新模型名 | 新增一条 |
| 同提供方 + 同名 | 覆盖该条（等于「重新填 Key 保存」） |
| **不同提供方 + 同名** | 拒绝并提示「模型名 X 已属于 DeepSeek，请先删除或换一个名称」 |

用户要的「一个 Key 对应多个服务端」= 多个**不同**模型名，天然支持，不受此限制。

### 决策 B：新增 `provider` 字段，URL 由代码配置类解析

`ModelConfig` 加一个字段，**其余字段一个不删**：

```kotlin
@Serializable
data class ModelConfig(
    val id: String,                    // 不变：API 传参 model 值 + 主键
    val name: String,                  // 不变：新增时 = id
    val description: String = "",      // 不变
    val apiKey: String = "",           // 不变
    val apiUrl: String = "",           // 保留，不再由 UI 编辑
    val provider: String = ""          // 新增："" = 旧数据未分类
)
```

新建配置类（唯一真相源，UI 不暴露 URL）：

```kotlin
object ModelProviders {
    const val DEEPSEEK = "deepseek"
    const val AIMAT = "aimat"
    const val LEGACY = "legacy"

    data class Spec(val id: String, val label: String, val baseUrl: String)
    val ALL = listOf(
        Spec(DEEPSEEK, "DeepSeek", "https://api.deepseek.com"),
        Spec(AIMAT, "Aimat", "https://aimat.cn/v1"),
    )
    fun baseUrl(provider: String): String? = ALL.firstOrNull { it.id == provider }?.baseUrl

    /** 归一化：返回 (provider, 实际请求基地址) */
    fun resolve(m: ModelConfig): Pair<String, String> { ... }
}
```

`resolve()` 规则（**旧数据零丢失的关键**）：

| 条件 | 结果 |
|---|---|
| `provider` 命中 `ALL` | 用该 provider 的**内置** baseUrl |
| `provider == ""`（旧数据）且 `apiUrl` 是官方 DeepSeek 地址或为空 | 归一为 `DEEPSEEK` + 内置地址（行为与现在完全一致） |
| `provider == ""` 且 `apiUrl` 是其它自定义地址 | 归一为 `LEGACY`，**继续用原 `apiUrl`**（曾填过第三方地址的老模型照常工作） |
| `provider` 未知（未来降级/回滚） | `LEGACY` + 原 `apiUrl` 兜底 |

新保存的模型一律写入**显式 provider**，同时把内置 baseUrl 冗余写进 `apiUrl`。这样做有两个好处：备份文件在旧版 App 上导入时（`ignoreUnknownKeys` 会忽略 `provider`）仍能靠 `apiUrl` 正常发请求；`LEGACY` 兜底也不会拿到空地址。

### 决策 C：`apiUrl` 不删、不改、不展示

只把「URL 的来源」从用户输入改为 `resolve()` 结果。字段保留 = 旧 JSON 反序列化不丢、备份可往返、未知 provider 有兜底。

### 决策 D：`isDeepseek` 判断改为按 provider（必须同步修）

`modelId.contains("deepseek")` 在 Aimat 下会误判（Aimat 可填 `deepseek-chat` 这类名字），导致 v2 真实对话 / 行为拆解误发 `response_format=json_object`。
给 `ConversationContext`、`DecompositionSpec` 各加一个 `provider: String = "deepseek"`，由 `ChatViewModel` 从 `modelCfg` 填入；两处判断改为 `provider == ModelProviders.DEEPSEEK`（`LEGACY` 沿用原字符串判断）。约 3 个文件、10 行改动。

---

## 4. 数据流（改动后）

```
[模型管理 UI] 提供方下拉 + Key + 模型名 → 保存
   → ModelManageViewModel.save() 校验（非空、跨提供方同名）
   → SettingsRepository.addModel(...) → ConfigManager.addModels() → anchat_config.json
                                          （provider=deepseek|aimat, apiUrl=内置地址）

[聊天] ChatViewModel 按 modelId 取 ModelConfig
   → ModelProviders.resolve(modelCfg) 得 (provider, baseUrl)
   → ConversationContext(apiKey, apiUrl=baseUrl, provider, modelId=id)
   → RequestSpec → EngineRequestSink → DeepSeekApi.sendChat → POST baseUrl + /chat/completions
```

Aimat 响应体沿用现有 `ChatCompletionResponse`：DeepSeek 专有字段（`reasoning_content`、`prompt_cache_hit_tokens` 等）本来就是可空带默认值，缺失不影响解析，**DTO 无需改动**。

---

## 5. UI 改造（ModelManageScreen）

- **「添加模型」卡片**：
  - 模型提供方：`ExposedDropdownMenuBox`，选项从 `ModelProviders.ALL` 生成（DeepSeek / Aimat）。
  - API Key：密码输入框。
  - 模型名：普通文本框，提示「将原样作为请求中的 model 值」。
  - 「保存」按钮 → 直接生成 1 条模型，即时出现在下方列表。
  - **删除**「拉取模型」「拉取到的模型（可多选）」「添加所选」整块 UI 与 ViewModel 的 `fetch/addSelected/fetched/isFetching` 状态。
- **「已添加模型」列表**：每条显示 `模型名 · 提供方标签 · Key ****abcd`；`LEGACY` 条目显示「自定义地址」。保留删除。
- **「模型分配」两个下拉**：完全不动。
- **可选（低成本，建议做）**：点已有条目回填表单，保存即覆盖同 id —— 给旧 `LEGACY` 条目一个归入正式提供方的出口。不做也可以通过「删掉重加」达到同样效果。

`DeepSeekApi.getModels()` / `SettingsRepository.fetchModels()` 代码保留不删（无副作用），仅 UI 不再调用，避免多余改动。

---

## 6. 文件改动清单

| 文件 | 改动 |
|---|---|
| `data/config/ModelConfig.kt` | 加 `provider: String = ""` |
| `data/config/ModelProviders.kt` | **新建**：常量、baseUrl 表、`resolve()` |
| `data/repository/SettingsRepository.kt` | 加 `addModel(provider, key, model)`；`fetchModels` 标注弃用但不删 |
| `data/config/ConfigManager.kt` | 基本不动（`addModels` / `removeModel` 已够用；跨提供方同名校验放 ViewModel 或此处） |
| `ui/settings/ModelManageViewModel.kt` | 表单状态改为 provider / key / model；`save()` 校验并构造 `ModelConfig` |
| `ui/settings/ModelManageScreen.kt` | 表单与列表改版，移除拉取区块 |
| `ui/chat/ChatViewModel.kt` | 3 处取 `apiUrl` 改为 `ModelProviders.resolve(cfg)`（`flushQueue` / `regenerateMessage` / `buildDecompSpec`），并填 `provider` |
| `AnChatApplication.kt` | 内置种子模型补 `provider = DEEPSEEK` |
| `engine/core/contract/Messages.kt` + `RequestBuilder.kt` + `BehaviorDecomposer.kt` | 决策 D：加 `provider` 字段、改 2 处 `isDeepseek` 判断 |
| `data/remote/DeepSeekApi.kt` | **不改**（URL 已参数化） |
| `data/remote/model/ChatModels.kt` | **不改**（OpenAI 兼容，字段可空） |
| `data/backup/*` | **不改**（整份 AppConfig 序列化，自动兼容） |
| `data/local/**`（Room） | **不改，无需迁移，无需升 version** |

`AppConfig` 结构本身不变（`models: List<ModelConfig>` 类型不变），只是元素多一个字段。

---

## 7. 数据不丢失论证

1. **结构**：不删任何字段、不改 `id` 语义、不动 Room 表 → 所有引用链（config 3 个 id、2 张表 `model_id`、备份指纹）保持有效。
2. **反序列化**：Json 开了 `ignoreUnknownKeys`、`explicitNulls=false`，旧 `anchat_config.json` 缺 `provider` 时自动取 `""`，走 `LEGACY`/DeepSeek 归一，不报错、不丢模型。
3. **旧备份**：`BackupPayload.config: AppConfig = AppConfig()` 有默认值，旧备份可正常导入；新备份在旧版 App 上导入时 `provider` 被忽略，但 `apiUrl` 已冗余写入内置地址，模型仍可用。
4. **Key**：`apiKey` 字段原样保留，不在本次做任何格式或存储方式变更。
5. **唯一的行为变化**：URL 不再由用户指定。这是需求本身要求的；对曾填自定义地址的老模型，通过 `LEGACY` 分支保留原地址，把变化面压到最小。

---

## 8. 验收清单

- [ ] 用现有配置文件升级：模型列表、默认模型、聊天 AI / 真实对话管理 AI 选择、角色卡与对话的模型下拉，全部与升级前一致；发消息地址仍是 `https://api.deepseek.com/chat/completions`。
- [ ] 选 Aimat + 填 Key + 填 `MiniMax-H3` 保存 → 列表出现该模型；发消息实际 POST `https://aimat.cn/v1/chat/completions`，请求体 `"model":"MiniMax-H3"`，Header `Authorization: Bearer <key>`。
- [ ] 同一个 Aimat Key 依次添加 2~3 个不同模型名 → 全部可选、互不覆盖；跨提供方填同名 → 有明确提示且不静默覆盖。
- [ ] 导出备份 → 清空/导入，模型条目与 Key 完整；旧版 App 打开新备份不崩。
- [ ] Aimat 模型设为「真实对话管理 AI」或开启 v2 时，不误发 `response_format=json_object`。
- [ ] `:app:assembleDebug --no-daemon` 构建通过。

---

## 9. 明确不做 / 已知限制

- **不做**：自定义 URL 输入、`/models` 拉取、模型重命名、独立主键/UUID、`apiKey` 加密存储、按提供方差异化请求体、流式差异处理、Room 迁移。
- **已知限制**：
  - `ChatRequest` 恒发 `temperature = 0.7`；部分推理模型中转站可能拒绝该参数。本期不逐个适配，遇到 400 再按报错单点处理（属于既有行为，非本次引入）。
  - API Key 仍明文存 JSON（现状），本次不动。
  - ~~跨提供方同名模型本期不支持共存（见决策 A）~~ —— 已被下方 v2 修订推翻。

---

## 10. v2 修订：唯一性改为「服务方 + 模型名」（2026-11-07）

用户反馈：DeepSeek 官方与 Aimat 各有一个 `deepseek-...-pro`，原实现的「模型名全局唯一 + 跨提供方同名拒绝」导致第二条加不了。以下条款**覆盖**第 3 节决策 A、第 4 节表格、第 6 节校验与第 8/9 节相关验收项；其余设计不变。

- **唯一键语义**：`(resolve(m).provider, apiModel(m))`。同组合 = 同一条（保存即更新 Key，沿用原 `id`，已存的默认模型 / 角色卡 / 对话引用不失效）；不同服务方同名 = 两条独立记录。
- **引用键与传参名解耦**：`ModelConfig` 只加 `modelName: String = ""`；`ModelProviders.apiModel(m) = m.modelName.ifBlank { m.id }`。旧数据缺字段自动回退到 `id`（旧版 id 就是模型名），行为完全不变。
- **冲突时才加前缀**：`nextModelId()` 默认仍用模型名本身作 `id`，只有该名字已被另一服务方占用时才生成 `provider|model`。因此绝大多数模型的 id 格式与旧版一致，旧版 App 导入新备份仍能正常发请求；只有冲突的第二条在旧版上会拿到带前缀的 model 值（降级场景，可接受）。
- **引擎零改动**：`RequestBuilder` / `BehaviorDecomposer` 里 `modelId` 仍直接作为 API 的 `model` 值；改由 `ChatViewModel` 三处（`flushQueue` / 重新生成 / `buildDecompSpec`）传入 `apiModel(m)`，而不是配置引用键。
- **Room 仍零改动、无迁移**：`defaultModelId` / `chatModelId` / `realConversationModelId` / `character.model_id` / `conversation.model_id` 存的都是不透明字符串，不做任何批量改写；备份整份 `AppConfig` 序列化，新字段带默认值，旧备份可正常反序列化。
- 显示层：已添加模型列表与两处下拉统一用 `ModelProviders.apiModel(m)` + 提供方标签，同名两条显示为 `xxx [DeepSeek]` 与 `xxx [Aimat]`；角色卡下拉选中态优先按 `id` 精确匹配，避免同名时显示错标签。
