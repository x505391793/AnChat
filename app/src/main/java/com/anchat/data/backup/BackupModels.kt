package com.anchat.data.backup

import com.anchat.data.config.AppConfig
import com.anchat.data.engine.BehaviorEntity
import com.anchat.data.engine.RawReplyEntity
import com.anchat.data.local.entity.CharacterEntity
import com.anchat.data.local.entity.Conversation
import com.anchat.data.local.entity.WorldBook
import kotlinx.serialization.Serializable

/**
 * 备份文件（单个 JSON）的顶层结构。
 *
 * 一张备份文件 = 一份完整可迁移快照：设置/API（config）+ 角色卡 + 对话 + 聊天记录 + 头像。
 * 聊天记录拆两层：`rawReplies` 是 API 原始往返（日志页与 token 统计依赖它），
 * `behaviors` 是用户视角的消息表；二者靠 `behaviors.batch_id = raw_replies.id` 关联，
 * 必须同时导出，否则消息会因 JOIN 不到 raw 而不显示。
 */
@Serializable
data class BackupPayload(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long = 0L,
    val config: AppConfig = AppConfig(),
    val characters: List<CharacterEntity> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val rawReplies: List<RawReplyEntity> = emptyList(),
    val behaviors: List<BehaviorEntity> = emptyList(),
    /** 头像：原始绝对路径 → base64（PNG，最长边压到 256px） */
    val avatars: Map<String, String> = emptyMap(),
    /** 世界书：勾选后追加到 system 提示词的设定卡片 */
    val worldBooks: List<WorldBook> = emptyList()
) {
    companion object {
        const val FORMAT = "anchat-backup"
        const val VERSION = 1
        /** 扩展名（SAF CreateDocument 的 mime 用 application/json） */
        const val MIME = "application/json"
        const val FILE_EXT = ".json"
    }
}

/** 各类记录的新增 / 跳过计数（导入预览展示） */
data class BackupCounts(
    val characters: Int = 0,
    val conversations: Int = 0,
    val rawReplies: Int = 0,
    val behaviors: Int = 0,
    val avatars: Int = 0,
    val worldBooks: Int = 0
) {
    val total: Int get() = characters + conversations + rawReplies + behaviors + avatars + worldBooks

    operator fun plus(other: BackupCounts) = BackupCounts(
        characters + other.characters,
        conversations + other.conversations,
        rawReplies + other.rawReplies,
        behaviors + other.behaviors,
        avatars + other.avatars,
        worldBooks + other.worldBooks
    )
}

/**
 * 导入预检结果：只做「全量比对」，不写库。
 * UI 据此弹确认对话框，用户确认后再调 [com.anchat.data.backup.BackupRepository.applyImport]。
 */
data class ImportPreview(
    val exportedAt: Long,
    /** 备份里的设置与本地是否完全一致（一致则不导入设置项） */
    val configChanged: Boolean,
    /** 将被写入的记录数 */
    val added: BackupCounts,
    /** 因「本地已有一模一样的记录」而跳过的记录数 */
    val skipped: BackupCounts,
    val payload: BackupPayload
)

/** 导入执行结果（写入完成后回报 UI） */
data class ImportResult(
    val added: BackupCounts,
    val skipped: BackupCounts,
    val configChanged: Boolean,
    /** 导入前自动生成的兜底快照路径（应用私有文件）；为空表示未生成 */
    val safetyBackupPath: String? = null
)

/**
 * 内容指纹：判定「两条记录是否一模一样」的唯一依据。
 *
 * 规则（与用户约定一致）：
 *  - **排除主键**：换机后自增 id / UUID 本就不该参与比较，只看内容。
 *  - **排除运行时元数据**：`updatedAt`（本地使用后会自然变化）、`status`（已读/未读状态）
 *    不算「数据不同」，否则同一份备份会被判成新数据重复导入。
 *  - 参与比较的外键用**重映射后的值**，保证「同一条消息在同一对话下」才判定为重复。
 */
internal object Fingerprint {

    fun of(c: CharacterEntity): String = sha256(
        join(
            c.name, c.remark, c.description, c.systemPrompt, c.greeting,
            c.userName, c.userDescription, c.modelId,
            c.thinkingEnabled, c.realConversation, c.realConvVersion,
            c.createdAt,
            // 头像是文件引用：比文件名而非绝对路径，跨设备路径不同但同一张图应视为相同
            baseName(c.avatar), baseName(c.userAvatar)
        )
    )

    fun of(c: Conversation): String = sha256(
        join(
            c.title, c.isStar, c.isPinned, c.characterId,
            c.modelId, c.systemPrompt,
            c.charRemark, c.charName, c.charDescription, c.charGreeting,
            c.charThinkingEnabled, c.charRealConversation, c.charRealConvVersion,
            c.userIdentityOverridden, c.userName, c.userDescription,
            c.createdAt,
            baseName(c.charAvatar), baseName(c.userAvatar)
        )
    )

    fun of(r: RawReplyEntity): String = sha256(
        join(r.conversationId, r.role, r.content, r.reasoningContent, r.kind, r.createdAt)
    )

    /** [RawIdentity] 版（本地去重只查必要列，不载 content 之外的整行） */
    fun of(r: com.anchat.data.engine.RawIdentity): String = sha256(
        join(r.conversationId, r.role, r.content, r.reasoningContent, r.kind, r.createdAt)
    )

    fun of(b: BehaviorEntity): String = sha256(
        join(b.batchId, b.order, b.type, b.role, b.content, b.duration, b.excuTime, b.conversationId)
    )

    fun of(b: com.anchat.data.engine.BehaviorIdentity): String = sha256(
        join(b.batchId, b.order, b.type, b.role, b.content, b.duration, b.excuTime, b.conversationId)
    )

    /** 世界书：按「标题 + 内容」去重（id 是内部主键，换机后必然变化，不参与比较）。 */
    fun of(w: WorldBook): String = sha256(join(w.title, w.content))

    /** 头像路径只取文件名：跨设备 filesDir 绝对路径必不相同，但图片本身是同一张 */
    internal fun baseName(path: String?): String =
        path?.substringAfterLast('/')?.trim().orEmpty()

    private fun join(vararg parts: Any?): String =
        parts.joinToString("\u0001") { p -> p?.toString() ?: "\u0000" }

    private fun sha256(s: String): String {
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { b -> "%02x".format(b) }
    }
}
