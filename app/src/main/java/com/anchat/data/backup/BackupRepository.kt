package com.anchat.data.backup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.anchat.data.avatar.AvatarRepository
import com.anchat.data.config.AppConfig
import com.anchat.data.config.ConfigManager
import com.anchat.data.engine.BehaviorDao
import com.anchat.data.engine.BehaviorEntity
import com.anchat.data.engine.RawReplyDao
import com.anchat.data.engine.RawReplyEntity
import com.anchat.data.local.AppDatabase
import com.anchat.data.local.dao.CharacterDao
import com.anchat.data.local.dao.ConversationDao
import com.anchat.data.local.dao.WorldBookDao
import com.anchat.data.local.entity.CharacterEntity
import com.anchat.data.local.entity.Conversation
import com.anchat.data.local.entity.WorldBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.UUID

/**
 * 全量备份 / 恢复：把「设置 + API（config）」与四张表打包成一个 JSON 文件。
 *
 * 设计要点：
 *  - **导出流式写**：raw_replies / behaviors 可能上万行，分批查、分批拼，
 *    绝不把整份备份在内存里 encodeToString 成一个大字符串。
 *  - **去重按内容不按主键**：见 [Fingerprint]。已经有一模一样的数据就跳过，
 *    内容不同则**作为新记录插入**（主键冲突时让自增列重新分配 / UUID 重新生成），
 *    绝不覆盖本地已有数据。
 *  - **id 重映射**：新插入的对话会拿到新自增 id，它底下的 raw_replies.conversation_id
 *    与 behaviors.conversation_id 必须跟着改，否则消息会「串台」到别的对话或干脆不显示。
 *    behaviors.batch_id 关联 raw_replies.id，同理需要重映射。
 *  - **头像内嵌**：库里只存路径，换机即失效。导出时压到 256px 转 base64，
 *    导入时按文件名写回 `filesDir/avatars/`，并把记录里的旧绝对路径改写成新路径。
 */
class BackupRepository(
    private val context: Context,
    private val configManager: ConfigManager,
    private val database: AppDatabase,
    private val characterDao: CharacterDao,
    private val conversationDao: ConversationDao,
    private val rawReplyDao: RawReplyDao,
    private val behaviorDao: BehaviorDao,
    private val worldBookDao: WorldBookDao,
    private val avatarRepository: AvatarRepository
) {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** 预览阶段算好的导入方案；apply 阶段复用，避免对同一份备份重复做全量比对。 */
    @Volatile
    private var pendingPlan: ImportPlan? = null

    // ────────────────── 导出 ──────────────────

    /** 把所有数据写到 [uri]（SAF CreateDocument 给的目标），返回各类记录条数。 */
    suspend fun exportTo(uri: Uri): BackupCounts = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalArgumentException("无法写入目标文件")
        OutputStreamWriter(out, Charsets.UTF_8).buffered().use { w -> writeBackup(w) }
    }

    /** 内部保险：把当前全量数据写进应用私有文件（不走 SAF），用于导入前的兜底快照。 */
    private suspend fun exportToInternal(file: File): BackupCounts = withContext(Dispatchers.IO) {
        file.outputStream().bufferedWriter(Charsets.UTF_8).use { w -> writeBackup(w) }
    }

    /** 把备份内容流式写到已打开的 [w]（调用方负责关闭流），避免大表一次性驻留内存。 */
    private suspend fun writeBackup(w: Writer): BackupCounts {
        val config = configManager.load()
        val characters = characterDao.getAll()
        val conversations = conversationDao.getAllFull()
        val avatars = collectAvatarPaths(config, characters, conversations)
            .mapNotNull { p -> encodeAvatar(p)?.let { p to it } }
            .toMap()

        var rawCount = 0
        var behaviorCount = 0

        w.write("{")
            w.write("\"format\":${enc(BackupPayload.FORMAT)},")
            w.write("\"version\":${BackupPayload.VERSION},")
            w.write("\"exportedAt\":${System.currentTimeMillis()},")
            w.write("\"config\":${enc(AppConfig.serializer(), config)},")
            w.write("\"characters\":${enc(ListSerializer(CharacterEntity.serializer()), characters)},")
            w.write("\"conversations\":${enc(ListSerializer(Conversation.serializer()), conversations)},")

            // 大表：分批查 + 分批拼，避免一次性驻留内存
            w.write("\"rawReplies\":[")
            var offset = 0
            var first = true
            while (true) {
                val batch = rawReplyDao.getPagedAll(PAGE, offset)
                if (batch.isEmpty()) break
                if (!first) w.write(",")
                w.write(enc(ListSerializer(RawReplyEntity.serializer()), batch).removeSurrounding("[", "]"))
                first = false
                rawCount += batch.size
                offset += batch.size
                if (batch.size < PAGE) break
            }
            w.write("],")

            w.write("\"behaviors\":[")
            offset = 0
            first = true
            while (true) {
                val batch = behaviorDao.getPagedAll(PAGE, offset)
                if (batch.isEmpty()) break
                if (!first) w.write(",")
                w.write(enc(ListSerializer(BehaviorEntity.serializer()), batch).removeSurrounding("[", "]"))
                first = false
                behaviorCount += batch.size
                offset += batch.size
                if (batch.size < PAGE) break
            }
            w.write("],")

            // 世界书（通常数量很少，整体写出即可）
            val worldBooks = worldBookDao.getAll()
            w.write("\"worldBooks\":${enc(ListSerializer(WorldBook.serializer()), worldBooks)},")

            w.write("\"avatars\":{")
            avatars.entries.forEachIndexed { i, e ->
                if (i > 0) w.write(",")
                w.write("${enc(e.key)}:${enc(e.value)}")
            }
            w.write("}}")
        return BackupCounts(characters.size, conversations.size, rawCount, behaviorCount, avatars.size, worldBooks.size)
    }

    // ────────────────── 导入：预检 ──────────────────

    /**
     * 只做「读文件 + 全量比对」，不写任何数据。
     * 返回的统计供 UI 弹确认对话框；确认后调 [applyImport]。
     */
    suspend fun previewImport(uri: Uri): ImportPreview = withContext(Dispatchers.IO) {
        val payload = readPayload(uri)
        val plan = buildPlan(payload)
        pendingPlan = plan
        ImportPreview(
            exportedAt = payload.exportedAt,
            configChanged = plan.configChanged,
            added = plan.addedCounts(),
            skipped = plan.skipped,
            payload = payload
        )
    }

    /**
     * 执行上一次 [previewImport] 算好的方案。
     *
     * 数据安全铁律（绝不丢原数据）：
     *  - **零变更直达返回**：若备份与本地完全一致（无任何新增、且设置相同），不写库、不 IO，零风险。
     *  - **兜底快照**：任何实际写入前，先把当前全量数据导出一份应用私有文件快照；
     *    万一导入过程异常，可重新导入该快照恢复。
     *  - **核心数据单事务追加**：所有表只 INSERT 新记录（自增 / UUID 新 id），绝不 DELETE、绝不 REPLACE 本地已有数据。
     *  - **设置项默认保留**：仅当 [overwriteConfig]=true（用户在确认框主动勾选）且备份设置确实不同，才覆盖本地设置；
     *    否则保留本地当前的 API Key / 模型等配置。
     *
     * @param overwriteConfig 是否覆盖本地设置项。默认 false —— 不悄悄丢弃本地配置。
     */
    suspend fun applyImport(overwriteConfig: Boolean = false): ImportResult = withContext(Dispatchers.IO) {
        val plan = pendingPlan ?: throw IllegalStateException("没有待导入的备份，请先预览")
        pendingPlan = null

        // ① 零变更直接返回：安全 + 省 IO（全部重复且设置与本地一致）
        if (plan.addedCounts().total == 0 && !plan.configChanged) {
            return@withContext ImportResult(
                added = plan.addedCounts(),
                skipped = plan.skipped,
                configChanged = false,
                safetyBackupPath = null
            )
        }

        // ② 兜底保险：写库前先把当前全量数据导成内部快照，万一异常可重新导入恢复
        var safetyBackupPath: String? = null
        val safetyFile = File(context.filesDir, SAFETY_BACKUP_NAME)
        runCatching { exportToInternal(safetyFile) }.onSuccess {
            if (safetyFile.exists()) safetyBackupPath = safetyFile.absolutePath
        }

        // ③ 核心数据：单事务追加插入，绝不删除本地、绝不覆盖本地记录
        val charMap = plan.charMap.toMutableMap()
        val convMap = plan.convMap.toMutableMap()
        val wbMap = plan.wbMap.toMutableMap()

        database.withTransaction {
            // 0. 世界书（id 置 0 走自增，返回新 id，回填 wbMap 供角色卡/对话引用重映射）
            if (plan.pendingWb.isNotEmpty()) {
                val newIds = worldBookDao.insertAll(plan.pendingWb)
                plan.pendingWb.forEachIndexed { i, w -> wbMap[w.id] = newIds.getOrNull(i) ?: 0L }
            }

            // 1. 角色卡（id 置 0 走自增，返回新 id；worldBookIds 已按 wbMap 重映射）
            if (plan.pendingChars.isNotEmpty()) {
                val rows = plan.pendingChars.map { (_, c) -> c.copy(worldBookIds = remapIds(c.worldBookIds, wbMap)) }
                val newIds = characterDao.insertAll(rows)
                plan.pendingChars.forEachIndexed { i, p -> charMap[p.first] = newIds.getOrNull(i) ?: 0L }
            }

            // 2. 对话（characterId 指向重映射后的角色卡；worldBookIds 已按 wbMap 重映射）
            if (plan.pendingConvs.isNotEmpty()) {
                val rows = plan.pendingConvs.map { (_, c) ->
                    c.copy(
                        id = 0L,
                        characterId = charMap[c.characterId] ?: c.characterId,
                        worldBookIds = remapIds(c.worldBookIds, wbMap)
                    )
                }
                val newIds = conversationDao.insertAll(rows)
                plan.pendingConvs.forEachIndexed { i, p -> convMap[p.first] = newIds.getOrNull(i) ?: 0L }
            }

            // 3. 原始回复（关联到重映射后的对话）
            if (plan.pendingRaws.isNotEmpty()) {
                val rows = plan.pendingRaws.map { r ->
                    val newConvId = r.conversationId.toLongOrNull()?.let { convMap[it] }?.toString()
                    r.copy(conversationId = newConvId ?: r.conversationId)
                }
                rawReplyDao.insertAll(rows)
            }

            // 4. 行为消息（batch_id → raw.id，conversation_id → 对话 id）
            if (plan.pendingBehaviors.isNotEmpty()) {
                val rows = plan.pendingBehaviors.map { b ->
                    val newConvId = b.conversationId.toLongOrNull()?.let { convMap[it] }?.toString()
                    b.copy(
                        batchId = plan.rawMap[b.batchId] ?: b.batchId,
                        conversationId = newConvId ?: b.conversationId
                    )
                }
                behaviorDao.insertAll(rows)
            }
        }

        // ④ 头像落盘（事务后、IO）：已存在则跳过，绝不覆盖本地头像
        val restored = restoreAvatarFiles(plan.payload.avatars, plan.avatarTargets)

        // ⑤ 设置项：仅当用户显式同意覆盖、且备份设置确实不同、且新配置非空，才覆盖本地
        var configActuallyChanged = false
        if (plan.configChanged && overwriteConfig && plan.newConfig != null) {
            configManager.save(plan.newConfig)
            configManager.resync()
            configActuallyChanged = true
        }

        // 头像文件已换 → 清解码缓存，避免 UI 仍用旧 ImageBitmap
        if (restored > 0) avatarRepository.evict()

        ImportResult(
            added = plan.addedCounts(),
            skipped = plan.skipped,
            configChanged = configActuallyChanged,
            safetyBackupPath = safetyBackupPath
        )
    }

    fun clearPending() { pendingPlan = null }

    // ────────────────── 导入：比对（核心） ──────────────────

    private suspend fun buildPlan(payload: BackupPayload): ImportPlan {
        val avatarTargets = payload.avatars.keys
            .mapNotNull { p -> avatarTargetPath(p)?.let { p to it } }
            .toMap()

        val localConfig = configManager.load()
        val configChanged = enc(AppConfig.serializer(), localConfig) != enc(AppConfig.serializer(), payload.config)
        val newConfig = if (configChanged) {
            payload.config.copy(defaultUserAvatar = rebaseRequired(payload.config.defaultUserAvatar, avatarTargets))
        } else null

        // ── 世界书（先计算，供角色卡 / 对话引用重映射） ──
        // 按「标题 + 内容」去重：本地已有同内容世界书则复用其 id，否则标记为新增。
        // wbMap：备份里的旧 world_book id → 最终写入的本地 id（既可能是已存在的本地 id，也可能是新插入的 id）。
        // 角色卡 / 对话里引用的 world_book id 必须用 wbMap 重映射，否则勾选会指向错误（或不存在的）世界书。
        val localWb = worldBookDao.getAll().associate { Fingerprint.of(it) to it.id }
        val wbMap = mutableMapOf<Long, Long>()
        val pendingWb = mutableListOf<WorldBook>()
        var skipWb = 0
        payload.worldBooks.forEach { w ->
            val localId = localWb[Fingerprint.of(w)]
            if (localId != null) {
                wbMap[w.id] = localId
                skipWb++
            } else {
                pendingWb += w.copy(id = 0L)
            }
        }

        // ── 角色卡 ──
        val localChars = characterDao.getAll().associate { Fingerprint.of(it) to it.id }
        val charMap = mutableMapOf<Long, Long>()
        val pendingChars = mutableListOf<Pair<Long, CharacterEntity>>()
        var skipChars = 0
        payload.characters.forEach { c ->
            val localId = localChars[Fingerprint.of(c)]
            if (localId != null) {
                charMap[c.id] = localId
                skipChars++
            } else {
                pendingChars += c.id to c.copy(
                    id = 0L,
                    worldBookIds = remapIds(c.worldBookIds, wbMap),
                    avatar = rebase(c.avatar, avatarTargets),
                    userAvatar = rebase(c.userAvatar, avatarTargets)
                )
            }
        }

        // ── 对话 ──
        // 本地指纹用本地的 characterId；备份指纹用「重映射后」的 characterId。
        // 若该角色卡是本次待插入的（本地没有同内容卡），本地不可能存在同内容对话 → 直接新增。
        val localConvs = conversationDao.getAllFull().associate { Fingerprint.of(it) to it.id }
        val convMap = mutableMapOf<Long, Long>()
        val pendingConvs = mutableListOf<Pair<Long, Conversation>>()
        var skipConvs = 0
        payload.conversations.forEach { c ->
            val mappedCharId = charMap[c.characterId]
            if (mappedCharId == null) {
                // 角色卡是新增的 → 对话必然是新增
                pendingConvs += c.id to c.copy(
                    id = 0L,
                    worldBookIds = remapIds(c.worldBookIds, wbMap),
                    charAvatar = rebase(c.charAvatar, avatarTargets),
                    userAvatar = rebase(c.userAvatar, avatarTargets)
                )
                return@forEach
            }
            val fp = Fingerprint.of(c.copy(characterId = mappedCharId))
            val localId = localConvs[fp]
            if (localId != null) {
                convMap[c.id] = localId
                skipConvs++
            } else {
                pendingConvs += c.id to c.copy(
                    id = 0L,
                    characterId = mappedCharId,
                    worldBookIds = remapIds(c.worldBookIds, wbMap),
                    charAvatar = rebase(c.charAvatar, avatarTargets),
                    userAvatar = rebase(c.userAvatar, avatarTargets)
                )
            }
        }

        // ── 原始回复 ──
        val localRawFp = mutableMapOf<String, String>()   // 指纹 → 本地 id
        val localRawIds = mutableSetOf<String>()
        var offset = 0
        while (true) {
            val batch = rawReplyDao.getPagedIdentity(PAGE * 2, offset)
            if (batch.isEmpty()) break
            batch.forEach { localRawFp[Fingerprint.of(it)] = it.id; localRawIds += it.id }
            offset += batch.size
            if (batch.size < PAGE * 2) break
        }
        val rawMap = mutableMapOf<String, String>()       // 备份 id → 最终写入的 id
        val pendingRaws = mutableListOf<RawReplyEntity>()
        var skipRaws = 0
        payload.rawReplies.forEach { r ->
            val mappedConvId = convMap[r.conversationId.toLongOrNull() ?: -1L]
            if (mappedConvId == null) {
                // 所属对话是新增的 → 本地不可能有同内容记录
                val newId = if (r.id in localRawIds) UUID.randomUUID().toString() else r.id
                rawMap[r.id] = newId
                pendingRaws += r.copy(id = newId)
                return@forEach
            }
            val fp = Fingerprint.of(r.copy(conversationId = mappedConvId.toString()))
            val localId = localRawFp[fp]
            if (localId != null) {
                rawMap[r.id] = localId
                skipRaws++
            } else {
                val newId = if (r.id in localRawIds) UUID.randomUUID().toString() else r.id
                rawMap[r.id] = newId
                pendingRaws += r.copy(id = newId)
            }
        }

        // ── 行为消息 ──
        val localBehaviorFp = mutableSetOf<String>()
        val localBehaviorIds = mutableSetOf<String>()
        offset = 0
        while (true) {
            val batch = behaviorDao.getPagedIdentity(PAGE * 2, offset)
            if (batch.isEmpty()) break
            batch.forEach { localBehaviorFp += Fingerprint.of(it); localBehaviorIds += it.id }
            offset += batch.size
            if (batch.size < PAGE * 2) break
        }
        val pendingBehaviors = mutableListOf<BehaviorEntity>()
        var skipBehaviors = 0
        payload.behaviors.forEach { b ->
            // batch_id 指向的 raw 是新增的 → 这批行为也是新增（用最终 id 是否属于本地已有判断）
            val rawIsNew = (rawMap[b.batchId] ?: b.batchId) !in localRawIds
            if (rawIsNew) {
                pendingBehaviors += b
                return@forEach
            }
            val mappedBatch = rawMap[b.batchId] ?: b.batchId
            val mappedConv = b.conversationId.toLongOrNull()?.let { convMap[it] }?.toString() ?: b.conversationId
            val fp = Fingerprint.of(b.copy(batchId = mappedBatch, conversationId = mappedConv))
            if (fp in localBehaviorFp) {
                skipBehaviors++
            } else {
                val newId = if (b.id in localBehaviorIds) UUID.randomUUID().toString() else b.id
                pendingBehaviors += b.copy(id = newId)
            }
        }

        val skipped = BackupCounts(skipChars, skipConvs, skipRaws, skipBehaviors, payload.avatars.size - avatarTargets.size, skipWb)
        return ImportPlan(
            payload = payload,
            configChanged = configChanged,
            newConfig = newConfig,
            avatarTargets = avatarTargets,
            charMap = charMap,
            convMap = convMap,
            rawMap = rawMap,
            wbMap = wbMap,
            pendingChars = pendingChars,
            pendingConvs = pendingConvs,
            pendingRaws = pendingRaws,
            pendingBehaviors = pendingBehaviors,
            pendingWb = pendingWb,
            skipped = skipped
        )
    }

    // ────────────────── 头像 ──────────────────

    private fun collectAvatarPaths(
        config: AppConfig,
        characters: List<CharacterEntity>,
        conversations: List<Conversation>
    ): Set<String> {
        val paths = mutableSetOf<String>()
        config.defaultUserAvatar.takeIf { it.isNotBlank() }?.let { paths += it }
        characters.forEach { c ->
            c.avatar?.takeIf { it.isNotBlank() }?.let { paths += it }
            c.userAvatar?.takeIf { it.isNotBlank() }?.let { paths += it }
        }
        conversations.forEach { c ->
            c.charAvatar?.takeIf { it.isNotBlank() }?.let { paths += it }
            c.userAvatar?.takeIf { it.isNotBlank() }?.let { paths += it }
        }
        // 只导出应用内部文件；content:// 是临时授权 URI，换机即失效，导了也没用
        return paths.filter { p -> !p.startsWith("content://") && File(p).exists() }.toSet()
    }

    /** 采样解码到最长边 256px 后转 PNG base64（头像显示只需 256px，避免原图几 MB 撑爆备份文件）。 */
    private fun encodeAvatar(path: String): String? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var sample = 1
        while (opts.outWidth / (sample * 2) >= AVATAR_MAX && opts.outHeight / (sample * 2) >= AVATAR_MAX) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scaled = if (bmp.width <= AVATAR_MAX && bmp.height <= AVATAR_MAX) bmp else {
            val ratio = AVATAR_MAX.toFloat() / maxOf(bmp.width, bmp.height)
            Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
        }
        val buf = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, buf)
        Base64.encodeToString(buf.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    /** 备份里头像的写入目标：`filesDir/avatars/<原文件名>`（跨设备路径不同，只认文件名）。 */
    private fun avatarTargetPath(origin: String): String? {
        val name = origin.substringAfterLast('/')
        if (name.isBlank()) return null
        val dir = File(context.filesDir, AVATAR_DIR)
        return File(dir, name).absolutePath
    }

    private fun restoreAvatarFiles(avatars: Map<String, String>, targets: Map<String, String>): Int {
        if (avatars.isEmpty()) return 0
        val dir = File(context.filesDir, AVATAR_DIR).apply { if (!exists()) mkdirs() }
        var n = 0
        avatars.forEach { (origin, b64) ->
            val target = targets[origin] ?: return@forEach
            val file = File(target)
            if (file.exists()) return@forEach
            runCatching {
                file.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                n++
            }
        }
        return n
    }

    /** 把记录里的旧头像路径改写成本地将写入的路径；没导出过的头像保持原值。 */
    private fun rebase(path: String?, targets: Map<String, String>): String? =
        path?.takeIf { it.isNotBlank() }?.let { targets[it] ?: it }

    /** 非空头像路径版（config 的主身份头像字段是非空 String） */
    private fun rebaseRequired(path: String, targets: Map<String, String>): String =
        targets[path] ?: path

    /** 把逗号分隔的 world_book id 列表按 [map] 重映射（缺失的 id 原样保留；空白返回空串）。 */
    private fun remapIds(csv: String?, map: Map<Long, Long>): String {
        val src = csv?.takeIf { it.isNotBlank() } ?: return ""
        return src.split(',').mapNotNull { it.trim().toLongOrNull() }
            .map { map[it] ?: it }
            .joinToString(",")
    }

    // ────────────────── 工具 ──────────────────

    private fun readPayload(uri: Uri): BackupPayload {
        val text = context.contentResolver.openInputStream(uri)?.use { s ->
            s.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw IllegalArgumentException("无法读取所选文件")
        val payload = runCatching { json.decodeFromString<BackupPayload>(text) }
            .getOrElse { throw IllegalArgumentException("不是有效的 AnChat 备份文件") }
        if (payload.format != BackupPayload.FORMAT) {
            throw IllegalArgumentException("不是 AnChat 备份文件（format=${payload.format}）")
        }
        if (payload.version > BackupPayload.VERSION) {
            throw IllegalArgumentException("备份文件版本 v${payload.version} 高于当前应用支持的 v${BackupPayload.VERSION}")
        }
        return payload
    }

    private fun <T> enc(serializer: kotlinx.serialization.SerializationStrategy<T>, value: T): String =
        json.encodeToString(serializer, value)

    /** 字符串字面量转义（头像路径 / base64 都是 ASCII，手写比撞重载歧义稳） */
    private fun enc(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }

    companion object {
        private const val PAGE = 500
        private const val AVATAR_MAX = 256
        private const val AVATAR_DIR = "avatars"
        /** 导入前自动生成的兜底快照文件名（应用私有目录，覆盖式，仅保留最新一份） */
        private const val SAFETY_BACKUP_NAME = "auto_backup_before_import.json"
    }
}

/** 预览阶段产出的导入方案（内部持有，UI 只看统计）。 */
private class ImportPlan(
    val payload: BackupPayload,
    val configChanged: Boolean,
    val newConfig: AppConfig?,
    val avatarTargets: Map<String, String>,
    val charMap: Map<Long, Long>,
    val convMap: Map<Long, Long>,
    val rawMap: Map<String, String>,
    val wbMap: Map<Long, Long>,
    val pendingChars: List<Pair<Long, CharacterEntity>>,
    val pendingConvs: List<Pair<Long, Conversation>>,
    val pendingRaws: List<RawReplyEntity>,
    val pendingBehaviors: List<BehaviorEntity>,
    val pendingWb: List<WorldBook>,
    val skipped: BackupCounts
) {
    fun addedCounts() = BackupCounts(
        characters = pendingChars.size,
        conversations = pendingConvs.size,
        rawReplies = pendingRaws.size,
        behaviors = pendingBehaviors.size,
        avatars = avatarTargets.size,
        worldBooks = pendingWb.size
    )
}
