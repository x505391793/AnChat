# 行为表完整逻辑

> 行为表 `behaviors` 是用户视角唯一消息表，覆盖发送队列 → 打包发送 → AI 拆解 → 分时推送 → UI 渲染的全生命周期。

## 1. 状态模型

| 状态值 | 常量 | 含义 |
|---|---|---|
| `-1` | `STATUS_QUEUED` | 用户已输入，等待发送批次 |
| `0` | `STATUS_PENDING` | 已发送，未到展示时刻（AI 行为） |
| `1` | `STATUS_SENT` | 已推送未读 |
| `2` | `STATUS_READ` | 已读 |

## 2. 两种发送模式

### 2.1 直发模式（非真实对话 / 当前实现）

一次用户输入 = 一条 raw_replies(role=user) + 一条 behaviors(role=user, status=2) + engine.send。

```
用户输入 text
  ├─ raw_replies ← { role:user, content:text }
  ├─ behaviors   ← { role:user, type:text, status:READ, batchId=raw.id }
  └─ engine.send
```

### 2.2 排队模式（真实对话下的 trigger 触发）

用户连发多条消息，暂存 `behaviors(status=-1)`，**不落 raw、不发引擎**。trigger 触发时打包全部排队消息成一条 raw，统一发送。

```
A → behaviors(status=-1), 无 raw
B → behaviors(status=-1), 无 raw
C → behaviors(status=-1), 无 raw
    │
    trigger
    │
    ├─ 查 status=-1 的全部 behavior（按 order 序）
    ├─ 拼接内容 → 建一条 raw_replies(role=user, content=拼A+B+C)
    ├─ 更新全部排队 behavior: batchId=新raw.id, status=READ
    └─ engine.send(raw)
         │
         D → behaviors(status=-1), 无 raw   （下一批次，不参与本次发送）
```

**trigger 执行期间新增的排队消息隔离到下一批次**，不污染当前批次。

## 3. AI 行为展示（已实现）
