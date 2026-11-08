package com.anchat.data.repository

import com.anchat.data.engine.BehaviorDao
import com.anchat.data.engine.BehaviorEntity
import com.anchat.data.engine.RawReplyEntity
import com.anchat.data.local.dao.CharacterDao
import com.anchat.data.local.dao.ConversationDao
import com.anchat.data.local.dao.WorldBookDao
import com.anchat.data.local.AppDatabase
import com.anchat.data.engine.RawReplyDao
import com.anchat.data.engine.RawReplyTotals
import com.anchat.data.local.entity.CharacterEntity
import com.anchat.data.local.entity.Conversation
import com.anchat.data.local.entity.ConversationListItem
import com.anchat.data.local.entity.WorldBook
import com.anchat.engine.core.contract.Behavior
import com.anchat.engine.core.contract.BehaviorType
import com.anchat.engine.core.contract.RawReply
import com.anchat.engine.core.contract.TokenUsage
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class LocalRepository(
    private val database: AppDatabase,
    private val conversationDao: ConversationDao,
    private val characterDao: CharacterDao,
    private val rawReplyDao: RawReplyDao,
    private val behaviorDao: BehaviorDao,
    private val worldBookDao: WorldBookDao
) {
    // ─── 对话 ────────────────────────────────────────
    fun observeConversations(): Flow<List<ConversationListItem>> = conversationDao.observeAll()
    suspend fun getConversation(id: Long): Conversation? = conversationDao.getById(id)
    fun observeConversation(id: Long): Flow<Conversation?> = conversationDao.observeById(id)
    suspend fun createConversation(conv: Conversation): Long = conversationDao.insert(conv)
    suspend fun updateConversation(conv: Conversation) = conversationDao.update(conv)
    suspend fun renameConversation(id: Long, title: String) = conversationDao.rename(id, title)
    suspend fun setStar(id: Long, isStar: Boolean) = conversationDao.setStar(id, isStar)
    suspend fun setPinned(id: Long, pinned: Boolean) = conversationDao.setPinned(id, pinned)

    /** 封存为故事：把对话全部「可读」消息转为纯文本分段写入 story_text，并置 archived=1。
     *  底层 raw_replies/behaviors 全部保留，绝不删原聊天记录。
     *  联动清未读：该对话「已执行未读」行为（status=1）翻为已读——否则对话从主列表
     *  （archived=0 过滤）消失后，底栏红点挂着对应的未读永无法进去清除。 */
    suspend fun archiveConversation(id: Long) {
        val conv = conversationDao.getById(id) ?: return
        val speakerName = conv.charName?.takeIf { it.isNotBlank() } ?: conv.title
        val text = behaviorDao.getCompletedByConversation(id.toString())
            .filter { it.role == "user" || it.role == "assistant" }
            .sortedBy { it.excuTime }
            .joinToString(separator = "\n\n") { b ->
                val prefix = if (b.role == "user") "我" else speakerName
                "$prefix：${b.content}"
            }
        database.withTransaction {
            behaviorDao.markAllReadByConversation(id.toString())
            behaviorDao.markReadByConversationId(id.toString())
            conversationDao.update(
                conv.copy(
                    archived = true,
                    storyText = text,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** 故事集列表：所有封存对话 */
    fun getArchivedConversations(): Flow<List<Conversation>> = conversationDao.getArchived()

    /** 保存故事编辑：回写标题与纯文本 */
    suspend fun updateStory(id: Long, title: String, storyText: String) {
        val conv = conversationDao.getById(id) ?: return
        conversationDao.update(
            conv.copy(
                title = title,
                storyText = storyText,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 删除对话：联动清其 raw_replies / behaviors（二者无 FK 到 conversations） */
    suspend fun deleteConversation(id: Long) {
        database.withTransaction {
            rawReplyDao.deleteByConversation(id.toString())
            behaviorDao.deleteByConversation(id.toString())
            conversationDao.deleteById(id)
        }
    }

    // ─── 用户消息 / 开场白：统一写 raw_replies + behaviors（behaviors 即真正消息表） ──
    /**
     * 落一条用户发出的文本消息：raw_replies(role=user) + behaviors(role=user, type=text, status=READ)。
     * @return 该用户行为的 behaviorId，供 UI 气泡删除定位。
     */
    suspend fun persistUserTurn(convId: Long, text: String, userRawId: String, sendTime: Long): String {
        val userBehaviorId = UUID.randomUUID().toString()
        database.withTransaction {
            rawReplyDao.insert(
                RawReplyEntity(
                    id = userRawId,
                    conversationId = convId.toString(),
                    role = "user",
                    content = text,
                    reasoningContent = null,
                    promptTokens = null,
                    completionTokens = null,
                    totalTokens = null,
                    reasoningTokens = null,
                    promptCacheHitTokens = null,
                    promptCacheMissTokens = null,
                    isError = false,
                    kind = "chat",
                    createdAt = sendTime
                )
            )
            behaviorDao.insertAll(
                listOf(
                    BehaviorEntity(
                        id = userBehaviorId,
                        order = 0,
                        type = BehaviorType.TEXT.value,
                        role = "user",
                        content = text,
                        duration = null,
                        excuTime = sendTime,
                        status = Behavior.STATUS_READ,
                        conversationId = convId.toString(),
                        batchId = userRawId
                    )
                )
            )
        }
        return userBehaviorId
    }

    /**
     * 落开场白（assistant 行，立即可见）：raw_replies(role=assistant,kind=greeting) +
     * behaviors(role=assistant, type=text, status=READ)。
     * @return 该开场白行为的 behaviorId，供 UI 气泡删除定位。
     */
    suspend fun persistGreeting(convId: Long, greeting: String, greetingRawId: String): String {
        val greetingBehaviorId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.withTransaction {
            rawReplyDao.insert(
                RawReplyEntity(
                    id = greetingRawId,
                    conversationId = convId.toString(),
                    role = "assistant",
                    content = greeting,
                    reasoningContent = null,
                    promptTokens = null,
                    completionTokens = null,
                    totalTokens = null,
                    reasoningTokens = null,
                    promptCacheHitTokens = null,
                    promptCacheMissTokens = null,
                    isError = false,
                    kind = "greeting",
                    createdAt = now
                )
            )
            behaviorDao.insertAll(
                listOf(
                    BehaviorEntity(
                        id = greetingBehaviorId,
                        order = 0,
                        type = BehaviorType.TEXT.value,
                        role = "assistant",
                        content = greeting,
                        duration = null,
                        excuTime = now,
                        status = Behavior.STATUS_READ,
                        conversationId = convId.toString(),
                        batchId = greetingRawId
                    )
                )
            )
        }
        return greetingBehaviorId
    }

    /**
     * 排队模式：只写 behaviors(status=-1)，不落 raw、不发引擎。
     * @return behaviorId
     */
    suspend fun persistQueuedUserTurn(convId: Long, text: String, sendTime: Long): String {
        val behaviorId = UUID.randomUUID().toString()
        behaviorDao.insertAll(
            listOf(
                BehaviorEntity(
                    id = behaviorId,
                    order = 0,
                    type = BehaviorType.TEXT.value,
                    role = "user",
                    content = text,
                    excuTime = sendTime,
                    status = Behavior.STATUS_QUEUED,
                    conversationId = convId.toString()
                )
            )
        )
        return behaviorId
    }

    /**
     * 将对话下全部排队消息打包成一条 raw_replies。
     * @return Pair(rawId, combinedText)，null 表示队列为空
     */
    suspend fun flushQueue(convId: Long): Pair<String, String>? {
        val queued = behaviorDao.getQueuedByConversation(convId.toString())
        if (queued.isEmpty()) return null
        val rawId = UUID.randomUUID().toString()
        val combined = queued.joinToString("\n") { it.content }
        val now = System.currentTimeMillis()
        database.withTransaction {
            rawReplyDao.insert(
                RawReplyEntity(
                    id = rawId,
                    conversationId = convId.toString(),
                    role = "user",
                    content = combined,
                    reasoningContent = null,
                    promptTokens = null,
                    completionTokens = null,
                    totalTokens = null,
                    reasoningTokens = null,
                    promptCacheHitTokens = null,
                    promptCacheMissTokens = null,
                    isError = false,
                    kind = "chat",
                    createdAt = now
                )
            )
            behaviorDao.markQueuedAsSent(convId.toString(), rawId)
        }
        return rawId to combined
    }

    /** 删除一个批次（一条 raw 下的全部行为 + 该 raw）；按 behaviorId 定位源 raw */
    suspend fun deleteMessage(behaviorId: String) {
        database.withTransaction {
            val b = behaviorDao.getById(behaviorId) ?: return@withTransaction
            val convId = b.conversationId
            val rawId = b.batchId
            behaviorDao.deleteByBatchId(rawId)
            rawReplyDao.deleteById(rawId)
        }
    }

    /** 删除一个批次（一条 raw 下的全部行为 + 该 raw）；按 behaviorId 定位源 raw */
    suspend fun deleteBatch(behaviorId: String) {
        database.withTransaction {
            val b = behaviorDao.getById(behaviorId) ?: return@withTransaction
            val convId = b.conversationId
            val rawId = b.batchId
            behaviorDao.deleteByBatchId(rawId)
            rawReplyDao.deleteById(rawId)
        }
    }

    /**
     * 编辑单条气泡。
     *
     * 只改 behaviors 会导致「界面显示新文本、下次请求仍发旧文本」（上下文取 raw_replies），
     * 所以必须同步 raw。但只有行为与 raw 是 1:1 纯文本关系时才同步：
     *  - 真实对话 v2 的 assistant raw 是行为 JSON，直接改成纯文本会破坏后续请求格式；
     *  - v1 一条 raw 会拆成多条行为，改其中一条不代表整条 raw。
     * 判定条件用「旧行为文本 == raw 原文」，成立即说明这条行为就是 raw 的全貌。
     */
    suspend fun updateMessageContent(behaviorId: String, newText: String) {
        database.withTransaction {
            val b = behaviorDao.getById(behaviorId) ?: return@withTransaction
            val old = b.content
            behaviorDao.updateContent(behaviorId, newText)
            val raw = rawReplyDao.getById(b.batchId) ?: return@withTransaction
            if (raw.kind != "decomp" && raw.content == old) {
                rawReplyDao.updateContent(raw.id, newText)
            }
        }
    }

    /**
     * 重新生成准备：删掉该气泡所在批次及之后的所有行为与原始回复。
     * @return 剩余历史最后一条用户原文（作为重新生成的输入）；null = 前面没有用户消息（如开场白）
     */
    suspend fun truncateForRegenerate(behaviorId: String): RawReplyEntity? {
        val b = behaviorDao.getById(behaviorId) ?: return null
        val convId = b.conversationId
        val raw = rawReplyDao.getById(b.batchId) ?: return null
        database.withTransaction {
            behaviorDao.deleteFrom(convId, b.batchId, b.excuTime)
            rawReplyDao.deleteFrom(convId, raw.id, raw.createdAt)
        }
        return rawReplyDao.getLastUser(convId)
    }


    /** 打开会话清未读：把该对话下所有「已执行未读」行为翻为已读（status 1 → 2） */
    suspend fun markRead(conversationId: Long) =
        behaviorDao.markAllReadByConversation(conversationId.toString())

    /** 未读红点：按对话统计「已执行未读」行为（status=1），返回 Map<对话id, 数量> */
    fun observeUnread(): Flow<Map<Long, Int>> =
        behaviorDao.observeUnread().map { list ->
            list.associate { (it.conversationId.toLongOrNull() ?: -1L) to it.cnt }
        }

    /**
     * 一次性清理「无法消除的红点」（幂等，启动时调用）：
     *  ① 属于已封存对话的未读行为——对话不在主列表，用户永远点不掉；
     *  ② 孤儿未读：conversation_id 为空/非数字（会聚成 -1L 键让底栏常亮），
     *     或指向已不存在对话的历史数据。
     * 正常在册对话的未读不动。
     */
    suspend fun clearStuckUnread() {
        conversationDao.getArchivedIds().forEach { id ->
            behaviorDao.markAllReadByConversation(id.toString())
            behaviorDao.markReadByConversationId(id.toString())
        }
        behaviorDao.getUnreadConversationIds().forEach { cid ->
            val convId = cid.toLongOrNull()
            if (convId == null || conversationDao.getById(convId) == null) {
                behaviorDao.markReadByConversationId(cid)
            }
        }
    }

    // ─── 行为（真实对话分时推送） ─────────────────
    private fun BehaviorEntity.toBehavior() = Behavior(
        behaviorId = id,
        order = order,
        type = enumValues<BehaviorType>().firstOrNull { it.value == type } ?: BehaviorType.SPEECH,
        role = role,
        content = content,
        duration = duration,
        excuTime = excuTime,
        status = status,
        conversationId = conversationId,
        batchId = batchId
    )

    /** 实时观察某对话下已完成（已到点）的行为，供聊天界面按时序渲染 */
    fun observeCompletedBehaviors(conversationId: Long): Flow<List<Behavior>> =
        behaviorDao.observeCompletedByConversation(conversationId.toString()).map { list ->
            list.map { it.toBehavior() }
        }

    /** 进入对话时的初始加载：取已完成行为 */
    suspend fun getCompletedBehaviors(conversationId: Long): List<Behavior> =
        behaviorDao.getCompletedByConversation(conversationId.toString()).map { it.toBehavior() }

    /**
     * 进入对话初始加载 / 上滑增量加载：取「早于 beforeExcuTime」的最近 limit 条已完成行为。
     * DAO 返回降序（新→旧），这里翻转为升序（旧→新）以便正确前插到列表头部。
     */
    suspend fun getCompletedBehaviorsBefore(conversationId: Long, beforeExcuTime: Long, limit: Int): List<Behavior> =
        behaviorDao.getCompletedBefore(conversationId.toString(), beforeExcuTime, limit)
            .reversed()
            .map { it.toBehavior() }

    /** 取排队中的行为（status=-1），进入对话时加载展示 */
    suspend fun getQueuedBehaviors(conversationId: Long): List<Behavior> =
        behaviorDao.getQueuedByConversation(conversationId.toString()).map { it.toBehavior() }

    /** 对话打开：把该对话下所有「已执行未读」行为翻为已读（status 1 → 2） */
    suspend fun markBehaviorsRead(conversationId: Long) =
        behaviorDao.markAllReadByConversation(conversationId.toString())

    /** 单条行为：实时推到当前激活对话并立即可见 → 翻为已读（status 1 → 2） */
    suspend fun markBehaviorRead(behaviorId: String) =
        behaviorDao.markRead(behaviorId)

    // ─── 原始回复（日志页审计） ───────────────────
    private fun RawReplyEntity.toRawReply() = RawReply(
        id = id,
        conversationId = conversationId,
        content = content,
        reasoningContent = reasoningContent,
        usage = TokenUsage(
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            reasoningTokens = reasoningTokens,
            promptCacheHitTokens = promptCacheHitTokens,
            promptCacheMissTokens = promptCacheMissTokens
        ),
        createdAt = createdAt,
        isError = isError,
        kind = kind
    )

    /** 日志页：分页读取原始数据表（API 返回），最新在前 */
    suspend fun getRawRepliesPaged(limit: Int, offset: Int): List<RawReply> =
        rawReplyDao.getPaged(limit, offset).map { it.toRawReply() }

    /** 日志页顶部总览：全表 token 汇总 */
    suspend fun getRawReplyTotals(): RawReplyTotals = rawReplyDao.getTotals()

    // ─── 角色 ────────────────────────────────────────
    fun observeCharacters(): Flow<List<CharacterEntity>> = characterDao.observeAll()
    suspend fun getAllCharacters(): List<CharacterEntity> = characterDao.getAll()
    suspend fun getCharacter(id: Long): CharacterEntity? = characterDao.getById(id)
    fun observeCharacter(id: Long): Flow<CharacterEntity?> = characterDao.observeById(id)
    suspend fun insertCharacter(character: CharacterEntity): Long = characterDao.insert(character)
    suspend fun updateCharacter(character: CharacterEntity) = characterDao.update(character)
    suspend fun deleteCharacter(character: CharacterEntity) = characterDao.delete(character)

    // ─── 世界书 ──────────────────────────────────────
    fun observeWorldBooks(): Flow<List<WorldBook>> = worldBookDao.observeAll()
    suspend fun getAllWorldBooks(): List<WorldBook> = worldBookDao.getAll()
    suspend fun getWorldBook(id: Long): WorldBook? = worldBookDao.getById(id)
    suspend fun insertWorldBook(worldBook: WorldBook): Long = worldBookDao.insert(worldBook)
    suspend fun updateWorldBook(worldBook: WorldBook) = worldBookDao.update(worldBook)
    suspend fun deleteWorldBook(worldBook: WorldBook) = worldBookDao.delete(worldBook)
    /** 按 id 批量取世界书（用于拼 system 提示词；调用方负责排序） */
    suspend fun getWorldBooksByIds(ids: List<Long>): List<WorldBook> {
        if (ids.isEmpty()) return emptyList()
        return worldBookDao.getByIds(ids)
    }
}
