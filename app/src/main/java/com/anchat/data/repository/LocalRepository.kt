package com.anchat.data.repository

import com.anchat.data.engine.BehaviorDao
import com.anchat.data.engine.BehaviorEntity
import com.anchat.data.engine.RawReplyEntity
import com.anchat.data.local.dao.CharacterDao
import com.anchat.data.local.dao.ConversationDao
import com.anchat.data.local.AppDatabase
import com.anchat.data.engine.RawReplyDao
import com.anchat.data.engine.RawReplyTotals
import com.anchat.data.local.entity.CharacterEntity
import com.anchat.data.local.entity.Conversation
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
    private val behaviorDao: BehaviorDao
) {
    // ─── 对话 ────────────────────────────────────────
    fun observeConversations(): Flow<List<Conversation>> = conversationDao.observeAll()
    suspend fun getConversation(id: Long): Conversation? = conversationDao.getById(id)
    fun observeConversation(id: Long): Flow<Conversation?> = conversationDao.observeById(id)
    suspend fun createConversation(conv: Conversation): Long = conversationDao.insert(conv)
    suspend fun updateConversation(conv: Conversation) = conversationDao.update(conv)
    suspend fun renameConversation(id: Long, title: String) = conversationDao.rename(id, title)
    suspend fun updatePreview(id: Long, preview: String) = conversationDao.updatePreview(id, preview)
    suspend fun setStar(id: Long, isStar: Boolean) = conversationDao.setStar(id, isStar)
    suspend fun setPinned(id: Long, pinned: Boolean) = conversationDao.setPinned(id, pinned)

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
                        rawId = userRawId,
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
                        rawId = greetingRawId,
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

    /** 删除一个批次（一条 raw 下的全部行为 + 该 raw）；按 behaviorId 定位源 raw */
    suspend fun deleteMessage(behaviorId: String) {
        database.withTransaction {
            val b = behaviorDao.getById(behaviorId) ?: return@withTransaction
            val rawId = b.batchId // = 源 raw.id
            behaviorDao.deleteByBatchId(rawId)
            rawReplyDao.deleteById(rawId)
        }
    }

    /** 删除一个批次（一条 raw 下的全部行为 + 该 raw）；按 behaviorId 定位源 raw */
    suspend fun deleteBatch(behaviorId: String) {
        database.withTransaction {
            val b = behaviorDao.getById(behaviorId) ?: return@withTransaction
            val rawId = b.batchId // = 源 raw.id
            behaviorDao.deleteByBatchId(rawId)
            rawReplyDao.deleteById(rawId)
        }
    }

    /** 打开会话清未读：把该对话下所有「已执行未读」行为翻为已读（status 1 → 2） */
    suspend fun markRead(conversationId: Long) =
        behaviorDao.markAllReadByConversation(conversationId.toString())

    /** 未读红点：按对话统计「已执行未读」行为（status=1），返回 Map<对话id, 数量> */
    fun observeUnread(): Flow<Map<Long, Int>> =
        behaviorDao.observeUnread().map { list ->
            list.associate { (it.conversationId.toLongOrNull() ?: -1L) to it.cnt }
        }

    // ─── 行为（真实对话分时推送） ─────────────────
    private fun BehaviorEntity.toBehavior() = Behavior(
        behaviorId = id,
        rawId = rawId,
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
}
