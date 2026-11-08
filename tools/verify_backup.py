#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AnChat 备份文件 离线校验 / 迁移对比工具（纯标准库，不依赖安卓端、不改动任何 app 代码）

用法：
  python tools/verify_backup.py 备份.json              # 完整性 + 引用一致性 + 缓存前缀校验
  python tools/verify_backup.py --diff A.json B.json   # 迁移前后逐条对比（A=旧机备份，B=新机再导出）

校验内容（对应「迁移信息完整 / 迁移后无差异 / 对话排列一致」三条要求）：
  1. 文件层：UTF-8 合法、JSON 完整（以 }} 收尾）、format/version 正确
  2. 完整性：config / characters / conversations / rawReplies / behaviors / worldBooks / avatars 七个键齐全
  3. 唯一性：各表主键无重复
  4. 引用一致性：conversation.characterId → character.id
                   raw_replies.conversation_id → conversations.id
                   behaviors.batch_id → raw_replies.id
                   behaviors.conversation_id 与所属 raw 的 conversation_id 一致
                   character/conversation.world_book_ids → world_book.id
  5. 顺序：rawReplies 按 created_at 升序、behaviors 按 excu_time 升序（导入按此顺序落库，决定展示与上下文顺序）
  6. 缓存前缀：按 buildSystemPrompt 的规则重建每个对话的 system 文本，
               连同按时间排序的历史消息算出 sha256 前缀指纹（A/B 指纹相同 = 换机后 prompt cache 仍可命中）
  7. 头像：所有被引用的头像文件名都能在 avatars map 里找到
"""

import sys
import os
import json
import hashlib
from collections import Counter, defaultdict

FORMAT = "anchat-backup"
MAX_VERSION = 1

OK, WARN, ERR = "OK", "WARN", "ERR"


class Checker:
    def __init__(self):
        self.rows = []  # (level, text)

    def ok(self, t):
        self.rows.append((OK, t))

    def warn(self, t):
        self.rows.append((WARN, t))

    def err(self, t):
        self.rows.append((ERR, t))

    def has_error(self):
        return any(lv == ERR for lv, _ in self.rows)

    def dump(self):
        icon = {OK: "  [OK]  ", WARN: "  [警告] ", ERR: "  [错误] "}
        for lv, t in self.rows:
            print(icon[lv] + t)


# ────────────────────────── 读取 ──────────────────────────

def load_payload(path, ck, label="文件"):
    if not os.path.isfile(path):
        ck.err(f"{label}不存在：{path}")
        return None, 0

    raw = open(path, "rb").read()
    size = len(raw)
    ck.ok(f"{label}大小 {size:,} 字节")

    text = None
    try:
        text = raw.decode("utf-8")
        ck.ok(f"{label} UTF-8 编码合法")
    except UnicodeDecodeError as e:
        ck.err(f"{label} UTF-8 非法（第 {e.start} 字节处截断/损坏）→ 文件不完整：{e.reason}")
        text = raw.decode("utf-8", "replace")

    if not text.endswith("}}"):
        tail = text[-40:].replace("\n", " ")
        ck.err(f"{label}结尾不是 '}}}}'（实际以 …{tail!r} 结尾）→ 写入未完成，文件被截断")
    else:
        ck.ok(f"{label}结尾为 '}}}}'，JSON 结构收尾完整")

    try:
        obj = json.loads(text)
    except Exception as e:
        ck.err(f"{label}不是合法 JSON：{str(e)[:160]}")
        return None, size

    ck.ok(f"{label} JSON 解析通过")
    return obj, size


# ────────────────────────── 字段默认值（对齐 Kotlin data class） ──────────────────────────

def nz(v):
    """空串 / None → None（对齐 Kotlin 的 ifBlank { null }）"""
    if v is None:
        return None
    s = str(v).strip()
    return s or None


def raw_defaults(r):
    return {
        "id": r.get("id", ""),
        "conversationId": r.get("conversationId", ""),
        "role": r.get("role", ""),
        "content": r.get("content", ""),
        "reasoningContent": r.get("reasoningContent"),
        "kind": r.get("kind", "chat"),
        "createdAt": r.get("createdAt", 0),
        "isError": r.get("isError", False),
    }


def beh_defaults(b):
    return {
        "id": b.get("id", ""),
        "batchId": b.get("batchId", ""),
        "order": b.get("order", 0),
        "type": b.get("type", ""),
        "role": b.get("role", ""),
        "content": b.get("content", ""),
        "duration": b.get("duration"),
        "excuTime": b.get("excuTime", 0),
        "status": b.get("status", 0),
        "conversationId": b.get("conversationId", ""),
    }


def base_name(p):
    if not p:
        return ""
    return str(p).replace("\\", "/").rsplit("/", 1)[-1].strip()


def fp(*parts):
    """与 Kotlin Fingerprint 同构的稳定指纹"""
    s = "\x01".join("\x00" if p is None else str(p) for p in parts)
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


# ────────────────────────── 校验 ──────────────────────────

def verify(path):
    print(f"\n===== 校验：{os.path.basename(path)} =====")
    ck = Checker()
    obj, size = load_payload(path, ck)
    if obj is None:
        ck.dump()
        return 1

    # 1. 顶层字段
    if obj.get("format") != FORMAT:
        ck.err(f"format 字段为 {obj.get('format')!r}，应为 {FORMAT!r}")
    else:
        ck.ok(f"format = {FORMAT}")
    ver = obj.get("version")
    if not isinstance(ver, int) or ver > MAX_VERSION:
        ck.err(f"version = {ver!r}，高于当前支持的 v{MAX_VERSION}")
    else:
        ck.ok(f"version = v{ver}")

    required = ["config", "characters", "conversations", "rawReplies", "behaviors", "worldBooks", "avatars"]
    missing = [k for k in required if k not in obj]
    if missing:
        ck.err(f"缺少顶层字段：{missing}（导出未写完或被裁剪）")
    else:
        ck.ok("七个顶层字段齐全（config / characters / conversations / rawReplies / behaviors / worldBooks / avatars）")

    config = obj.get("config") or {}
    chars = obj.get("characters") or []
    convs = obj.get("conversations") or []
    raws = obj.get("rawReplies") or []
    behs = obj.get("behaviors") or []
    wbs = obj.get("worldBooks") or []
    avatars = obj.get("avatars") or {}

    ck.ok(f"条数：角色 {len(chars)} · 对话 {len(convs)} · 原始回复 {len(raws)} · 行为消息 {len(behs)} · 世界书 {len(wbs)} · 头像 {len(avatars)}")

    # 2. 主键唯一
    for name, arr, key in [
        ("characters", chars, "id"),
        ("conversations", convs, "id"),
        ("rawReplies", raws, "id"),
        ("behaviors", behs, "id"),
        ("worldBooks", wbs, "id"),
    ]:
        dup = [k for k, c in Counter(x.get(key) for x in arr).items() if c > 1]
        if dup:
            ck.err(f"{name} 主键重复 {len(dup)} 个：{dup[:5]}")
        else:
            ck.ok(f"{name} 主键无重复")

    # 3. 引用一致性
    char_ids = {c.get("id") for c in chars}
    conv_ids = {c.get("id") for c in convs}
    raw_by_id = {raw_defaults(r)["id"]: r for r in raws}
    wb_ids = {w.get("id") for w in wbs}

    dangling_conv = [c.get("id") for c in convs if c.get("characterId", -1) not in (-1, None) and c.get("characterId") not in char_ids]
    if dangling_conv:
        ck.err(f"对话引用了不存在的角色卡：{(dangling_conv)[:5]}")
    else:
        ck.ok("conversation.characterId 全部能在 characters 中找到")

    dangling_raw = [raw_defaults(r)["id"] for r in raws if str(raw_defaults(r)["conversationId"]) not in {str(i) for i in conv_ids}]
    if dangling_raw:
        ck.warn(f"原始回复引用了不存在的对话（源库遗留的孤儿数据，导入后同样不可见，不影响差异判定）：{len(dangling_raw)} 条，如 {dangling_raw[:3]}")
    else:
        ck.ok("raw_replies.conversation_id 全部能在 conversations 中找到")

    orphan = []
    mismatch = []
    for b in behs:
        bd = beh_defaults(b)
        bid = bd["batchId"]
        if bid and bid not in raw_by_id:
            orphan.append(bd["id"])
            continue
        if bid:
            r_conv = str(raw_defaults(raw_by_id[bid])["conversationId"])
            if bd["conversationId"] and str(bd["conversationId"]) != r_conv:
                mismatch.append(bd["id"])
    if orphan:
        ck.warn(f"行为消息的 batch_id 找不到对应原始回复（源库遗留，A/B 两侧同样不显示，不影响差异判定）：{len(orphan)} 条，如 {orphan[:3]}")
    else:
        ck.ok("behaviors.batch_id 全部能对应到 raw_replies.id")
    if mismatch:
        ck.warn(f"behaviors.conversation_id 与所属 raw 的 conversation_id 不一致：{len(mismatch)} 条")
    else:
        ck.ok("behaviors.conversation_id 与所属 raw 的 conversation_id 一致")

    bad_wb_char = [c.get("name") for c in chars
                   if any(x and x.isdigit() and int(x) not in wb_ids for x in (nz(c.get("worldBookIds")) or "").split(","))]
    if bad_wb_char:
        ck.warn(f"角色卡引用了不存在的世界书 id：{bad_wb_char[:5]}")
    else:
        ck.ok("character.world_book_ids 全部能在 worldBooks 中找到")

    bad_wb_conv = [c.get("title") for c in convs
                   if any(x and x.isdigit() and int(x) not in wb_ids for x in (nz(c.get("worldBookIds")) or "").split(","))]
    if bad_wb_conv:
        ck.warn(f"对话引用了不存在的世界书 id：{bad_wb_conv[:5]}")
    else:
        ck.ok("conversation.world_book_ids 全部能在 worldBooks 中找到")

    # 4. 顺序（导入按文件顺序逐条落库，决定换机后的展示顺序与上下文顺序）
    raw_times = [raw_defaults(r)["createdAt"] for r in raws]
    bad = [i for i in range(1, len(raw_times)) if raw_times[i] < raw_times[i - 1]]
    if bad:
        ck.err(f"rawReplies 未按 created_at 升序（第 {bad[0]} 条起倒序）→ 换机后上下文顺序会变")
    else:
        ck.ok("rawReplies 全局按 created_at 升序（与 getHistory 排序一致）")

    beh_times = [beh_defaults(b)["excuTime"] for b in behs]
    bad = [i for i in range(1, len(beh_times)) if beh_times[i] < beh_times[i - 1]]
    if bad:
        ck.err(f"behaviors 未按 excu_time 升序（第 {bad[0]} 条起倒序）→ 换机后消息顺序会变")
    else:
        ck.ok("behaviors 全局按 excu_time 升序（与对话内展示顺序一致）")

    # 同一对话内：行为时间序是否与原始回复时间序一致（决定「上下文顺序 vs 气泡顺序」是否吻合）
    raw_conv_seq = defaultdict(list)
    for r in raws:
        rd = raw_defaults(r)
        if rd["role"] in ("user", "assistant") and rd["kind"] != "decomp":
            raw_conv_seq[str(rd["conversationId"])].append(rd["createdAt"])
    beh_conv_seq = defaultdict(list)
    for b in behs:
        bd = beh_defaults(b)
        if bd["status"] >= 1:
            beh_conv_seq[str(bd["conversationId"])].append(bd["excuTime"])
    ck.ok(f"对话数（含消息）：上下文 {len(raw_conv_seq)} 个 · 展示 {len(beh_conv_seq)} 个")

    # 5. 时间重复（跨设备顺序敏感点）
    dup_times = 0
    for cid, seq in raw_conv_seq.items():
        dup_times += len(seq) - len(set(seq))
    if dup_times:
        ck.warn(f"同一对话内存在 {dup_times} 处 created_at 完全相同的时间戳（顺序依赖 rowid，导入顺序正确即无碍，但需保证文件顺序未被改动）")
    else:
        ck.ok("同一对话内 created_at 无重复时间戳（顺序完全确定）")

    # 6. 头像覆盖
    need = set()
    for c in chars:
        for f in ("avatar", "userAvatar"):
            n = base_name(c.get(f))
            if n:
                need.add(n)
    for c in convs:
        for f in ("charAvatar", "userAvatar"):
            n = base_name(c.get(f))
            if n:
                need.add(n)
    n = base_name(config.get("defaultUserAvatar"))
    if n:
        need.add(n)
    have = {base_name(k) for k in avatars.keys()}
    lack = sorted(need - have)
    if lack:
        ck.warn(f"有 {len(lack)} 个头像文件未被内嵌（换机后显示默认头像）：{lack[:5]}")
    else:
        ck.ok(f"被引用的 {len(need)} 个头像全部已内嵌")

    # 7. 缓存前缀指纹（每个对话一行）
    table = []
    char_by_id = {c.get("id"): c for c in chars}
    wb_by_id = {w.get("id"): w for w in wbs}
    multi_wb = 0
    flipped = []          # 旧 id 序 与 新 createdAt 序 不同的对话（本次改动会让它们的前缀变一次）
    for conv in convs:
        old_txt = build_system_text(conv, char_by_id.get(conv.get("characterId")), config, wb_by_id, order="old")
        txt = build_system_text(conv, char_by_id.get(conv.get("characterId")), config, wb_by_id)
        hist = history_of(conv, raws)
        h = prefix_hash(txt, hist)
        _, _, _, _, _, _, wb_ids = resolve_profile(conv, char_by_id.get(conv.get("characterId")), config)
        n_book = len([x for x in (wb_ids or "").split(",") if x.strip().isdigit()])
        if n_book >= 2:
            multi_wb += 1
        if old_txt != txt:
            flipped.append((conv.get("id"), conv.get("title"), n_book))
        table.append(f"     conv {str(conv.get('id')):>4}  {str(conv.get('title'))[:18]:<20} 消息 {len(hist):>4}  "
                     f"system {len(txt or '')} 字  前缀指纹 {h[:16]}")

    if multi_wb == 0:
        ck.ok("没有对话勾选 ≥2 本世界书：世界书拼接顺序不影响任何前缀")
    elif not flipped:
        ck.ok(f"{multi_wb} 个对话勾选 ≥2 本世界书，但「id 序」与「createdAt 序」完全一致 → "
              f"换成 createdAt 排序后 system 文本逐字节不变，已有缓存零影响")
    else:
        detail = "、".join(f"conv {i}({str(t)[:10]}, {n} 本)" for i, t, n in flipped[:6])
        ck.warn(f"{multi_wb} 个对话勾选 ≥2 本世界书，其中 {len(flipped)} 个会因排序键改为 createdAt 而"
                f"改变 system 文本顺序（该对话首次请求前缀未命中，之后恢复命中）：{detail}")

    ck.dump()
    print("\n  ── 各对话 prompt 前缀指纹（A/B 相同即可命中缓存）──")
    for line in table:
        print(line)
    print()
    return 1 if ck.has_error() else 0


# ────────────────────────── 缓存前缀重建（对齐 buildSystemPrompt / getHistory） ──────────────────────────

def resolve_profile(conv, char, config):
    char = char or {}
    conv = conv or {}
    char_remark = nz(conv.get("charRemark")) or nz(char.get("remark"))
    char_name = nz(conv.get("charName")) or nz(char.get("name")) or nz(conv.get("title")) or "AI"
    system_prompt = nz(conv.get("systemPrompt")) or nz(char.get("systemPrompt")) or ""
    overridden = bool(conv.get("userIdentityOverridden", False))
    if overridden:
        user_name = nz(conv.get("userName"))
        user_desc = nz(conv.get("userDescription"))
    else:
        user_name = nz(conv.get("userName")) or nz(char.get("userName")) or nz(config.get("defaultUserName"))
        user_desc = nz(conv.get("userDescription")) or nz(char.get("userDescription")) or nz(config.get("defaultUserDescription"))
    # 性别只来自全局主身份（角色卡 / 对话快照均无该字段）
    user_gender = nz(config.get("defaultUserGender"))
    wb_ids = nz(conv.get("worldBookIds")) or nz(char.get("worldBookIds")) or ""
    return char_remark, char_name, system_prompt, user_name, user_desc, user_gender, wb_ids


def build_system_text(conv, char, config, wb_by_id, order="new"):
    """order="new"：按 (createdAt, title, content) 排序（跨设备稳定）；
    order="old"：按 id 升序（旧 APK 行为，仅用于评估改动的缓存影响）。"""
    char_remark, char_name, system_prompt, user_name, user_desc, user_gender, wb_ids = resolve_profile(conv, char, config)
    parts = []
    display_name = char_remark or char_name
    if display_name:
        parts.append(f"你的名字是{display_name}。")
    if system_prompt:
        parts.append(system_prompt)
    identity = []
    if user_name:
        identity.append(f"我（用户）的名字叫{user_name}。")
    if user_gender:
        identity.append(f"性别为{user_gender}。")
    if user_desc:
        identity.append(user_desc)
    if identity:
        parts.append("\n".join(identity))
    ids = [int(x) for x in (wb_ids or "").split(",") if x.strip().isdigit()]
    if order == "old":
        books = [wb_by_id[i] for i in sorted(ids) if i in wb_by_id]
    else:
        books = sorted([wb_by_id[i] for i in ids if i in wb_by_id],
                       key=lambda w: (w.get("createdAt") or 0, w.get("title") or "", w.get("content") or ""))
    if books:
        parts.append("\n\n".join(f"【世界书：{w.get('title') or '世界书'}】\n{w.get('content', '')}" for w in books))
    return "\n\n".join(parts) if parts else None


def history_of(conv, raws):
    cid = str(conv.get("id"))
    seq = []
    for idx, r in enumerate(raws):
        rd = raw_defaults(r)
        if str(rd["conversationId"]) == cid and rd["role"] in ("user", "assistant") and rd["kind"] != "decomp":
            seq.append((rd["createdAt"], idx, rd["role"], rd["content"]))
    seq.sort(key=lambda x: (x[0], x[1]))  # 对齐 SQL: ORDER BY created_at ASC（同值按导入顺序）
    return [(s[2], s[3]) for s in seq]


def prefix_hash(system_text, history):
    h = hashlib.sha256()
    h.update((system_text or "").encode("utf-8"))
    h.update(b"\x1e")
    for role, content in history:
        h.update(role.encode("utf-8"))
        h.update(b"\x1f")
        h.update(content.encode("utf-8"))
        h.update(b"\x1e")
    return h.hexdigest()


# ────────────────────────── 迁移对比 ──────────────────────────

def diff(a_path, b_path):
    print("\n===== 迁移对比 =====")
    ck = Checker()
    A, _ = load_payload(a_path, ck, "旧机备份")
    B, _ = load_payload(b_path, ck, "新机备份")
    if A is None or B is None:
        ck.dump()
        return 1

    cfgA, cfgB = A.get("config") or {}, B.get("config") or {}
    keys = sorted(set(cfgA) | set(cfgB))
    diff_cfg = []
    for k in keys:
        va, vb = cfgA.get(k), cfgB.get(k)
        if k == "defaultUserAvatar":
            va, vb = base_name(va), base_name(vb)
        if va != vb:
            diff_cfg.append(k)
    if diff_cfg:
        ck.err(f"设置项不一致：{diff_cfg}")
    else:
        ck.ok("设置项（含主身份 / 模型 / API）逐项一致")

    def char_fp(c):
        return fp(c.get("name"), c.get("remark"), c.get("description"), c.get("systemPrompt"), c.get("greeting"),
                  c.get("userName"), c.get("userDescription"), c.get("modelId"),
                  c.get("thinkingEnabled", False), c.get("realConversation", False), c.get("realConvVersion", "v1"),
                  c.get("createdAt"), base_name(c.get("avatar")), base_name(c.get("userAvatar")))

    ca, cb = Counter(char_fp(c) for c in A.get("characters") or []), Counter(char_fp(c) for c in B.get("characters") or [])
    if ca == cb:
        ck.ok(f"角色卡 {sum(ca.values())} 张完全一致（内容指纹逐张比对）")
    else:
        ck.err(f"角色卡不一致：仅旧机 {sum((ca - cb).values())} 张，仅新机 {sum((cb - ca).values())} 张")

    def wb_fp(w):
        return fp(w.get("title"), w.get("content"))

    wa, wb_ = Counter(wb_fp(w) for w in A.get("worldBooks") or []), Counter(wb_fp(w) for w in B.get("worldBooks") or [])
    if wa == wb_:
        ck.ok(f"世界书 {sum(wa.values())} 条完全一致")
    else:
        ck.err(f"世界书不一致：仅旧机 {sum((wa - wb_).values())} 条，仅新机 {sum((wb_ - wa).values())} 条")

    ava = {base_name(k) for k in (A.get("avatars") or {})}
    avb = {base_name(k) for k in (B.get("avatars") or {})}
    lack = ava - avb
    if lack:
        ck.err(f"头像缺失 {len(lack)} 个：{sorted(lack)[:5]}")
    else:
        ck.ok(f"头像 {len(ava)} 个全部存在")

    charsA = {c.get("id"): c for c in A.get("characters") or []}
    charsB = {c.get("id"): c for c in B.get("characters") or []}
    fp2A = {char_fp(c): c.get("id") for c in A.get("characters") or []}
    fp2B = {char_fp(c): c.get("id") for c in B.get("characters") or []}

    def conv_fp(c, fp2):
        cf = char_fp(charsA.get(c.get("characterId"), {})) if fp2 is fp2A else char_fp(charsB.get(c.get("characterId"), {}))
        return fp(c.get("title"), c.get("isStar", False), c.get("isPinned", False), cf,
                  c.get("modelId"), c.get("systemPrompt"),
                  c.get("charRemark"), c.get("charName"), c.get("charDescription"), c.get("charGreeting"),
                  c.get("charThinkingEnabled", False), c.get("charRealConversation", False), c.get("charRealConvVersion", "v1"),
                  c.get("userIdentityOverridden", False), c.get("userName"), c.get("userDescription"),
                  c.get("createdAt"), base_name(c.get("charAvatar")), base_name(c.get("userAvatar")))

    convA = {conv_fp(c, fp2A): c for c in A.get("conversations") or []}
    convB = {conv_fp(c, fp2B): c for c in B.get("conversations") or []}
    onlyA = set(convA) - set(convB)
    onlyB = set(convB) - set(convA)
    if onlyA or onlyB:
        ck.err(f"对话不一致：仅旧机 {len(onlyA)} 个，仅新机 {len(onlyB)} 个")
    else:
        ck.ok(f"对话 {len(convA)} 个全部一一对应（按内容指纹匹配，不依赖自增 id）")

    # 同一对话内：消息序列 + 缓存前缀
    charA = {c.get("id"): c for c in A.get("characters") or []}
    charB = {c.get("id"): c for c in B.get("characters") or []}
    wbA = {w.get("id"): w for w in A.get("worldBooks") or []}
    wbB = {w.get("id"): w for w in B.get("worldBooks") or []}

    same_order = same_msgs = same_prefix = 0
    problems = []
    for k in set(convA) & set(convB):
        cA, cB = convA[k], convB[k]
        hA = history_of(cA, A.get("rawReplies") or [])
        hB = history_of(cB, B.get("rawReplies") or [])
        if hA == hB:
            same_msgs += 1
            if [r for r, _ in hA] == [r for r, _ in hB]:
                same_order += 1
        else:
            first = next((i for i in range(min(len(hA), len(hB))) if hA[i] != hB[i]), min(len(hA), len(hB)))
            problems.append(f"「{cA.get('title')}」消息序列不同（旧 {len(hA)} 条 / 新 {len(hB)} 条，第 {first + 1} 条起不一致）")
            continue
        sA = build_system_text(cA, charA.get(cA.get("characterId")), cfgA, wbA)
        sB = build_system_text(cB, charB.get(cB.get("characterId")), cfgB, wbB)
        if prefix_hash(sA, hA) == prefix_hash(sB, hB):
            same_prefix += 1
        else:
            problems.append(f"「{cA.get('title')}」system 提示词或前缀不同（缓存会失效）")

    if problems:
        for p in problems[:10]:
            ck.err(p)
        if len(problems) > 10:
            ck.err(f"…另有 {len(problems) - 10} 个对话存在差异")
    else:
        ck.ok(f"{same_msgs} 个对话的消息内容、顺序、system 提示词与缓存前缀完全一致")

    ck.dump()
    return 1 if ck.has_error() else 0


def main(argv):
    if hasattr(sys.stdout, "reconfigure"):
        try:
            sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass

    if len(argv) >= 2 and argv[1] == "--diff":
        if len(argv) < 4:
            print("用法：python tools/verify_backup.py --diff A.json B.json")
            return 2
        return diff(argv[2], argv[3])
    if len(argv) != 2:
        print(__doc__)
        return 2
    return verify(argv[1])


if __name__ == "__main__":
    sys.exit(main(sys.argv))
