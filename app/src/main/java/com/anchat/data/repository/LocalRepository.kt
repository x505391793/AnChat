package com.anchat.data.repository

import com.anchat.data.engine.BehaviorDao
import com.anchat.data.engine.BehaviorEntity
import com.anchat.data.engine.RawReplyEntity
import com.anchat.data.local.dao.CharacterDao
import com.anchat.data.local.dao.ConversationDao
import com.anchat.data.local.dao.MessageDao
import com.anchat.data.local.AppDatabase
import com.anchat.data.engine.RawReplyDao
import com.anchat.data.engine.RawReplyTotals
import com.anchat.data.local.entity.CharacterEntity
import com.anchat.data.local.entity.Conversation
import com.anchat.data.local.entity.Message
import com.anchat.engine.core.contract.Behavior
import com.anchat.engine.core.contract.BehaviorType
import com.anchat.engine.core.contract.RawReply
import com.anchat.engine.core.contract.TokenUsage
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalRepository(
    private val database: AppDatabase,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val characterDao: CharacterDao,
    private val rawReplyDao: RawReplyDao,
    private val behaviorDao: BehaviorDao
) {
    // ─── 对话 ────────────────────────────────────────
    fun observeConversations(): Flow<List<Conversation>> = conversationDao.observeAll()
    fun observeMessages(conversationId: Long): Flow<List<Message>> =
        messageDao.observeByConversation(conversationId)

    suspend fun getMessages(conversationId: Long): List<Message> =
        messageDao.getByConversation(conversationId)

    suspend fun getConversation(id: Long): Conversation? = conversationDao.getById(id)

    fun observeConversation(id: Long): Flow<Conversation?> = conversationDao.observeById(id)

    suspend fun createConversation(conv: Conversation): Long = conversationDao.insert(conv)

    suspend fun updateConversation(conv: Conversation) = conversationDao.update(conv)

    suspend fun insertMessage(message: Message): Long = messageDao.insert(message)
    suspend fun markRead(conversationId: Long) = messageDao.markReadByConversation(conversationId)
    fun observeUnread(): Flow<Map<Long, Int>> =
        messageDao.unreadByConversation().map { list -> list.associate { it.conversationId to it.count } }
    suspend fun renameConversation(id: Long, title: String) = conversationDao.rename(id, title)
    suspend fun updatePreview(id: Long, preview: String) = conversationDao.updatePreview(id, preview)
    suspend fun setStar(id: Long, isStar: Boolean) = conversationDao.setStar(id, isStar)
    suspend fun setPinned(id: Long, pinned: Boolean) = conversationDao.setPinned(id, pinned)
    suspend fun deleteConversation(id: Long) {
        messageDao.deleteByConversation(id)
        conversationDao.deleteById(id)
    }

    /** 删除单个消息（长按气泡删除那一整句，不波及同回合其他消息） */
    suspend fun deleteMessage(id: Long) = messageDao.deleteById(id)

    /** 删除一个回合的全部数据：消息（用户+AI）+ 原始回复 + 行为 */
    suspend fun deleteBatch(batchId: String) {
        // 一个回合的 UI 消息、原始回复和定时行为必须同生共死，避免留下孤儿数据。
        database.withTransaction {
            messageDao.deleteByBatchId(batchId)
            behaviorDao.deleteByRawId(batchId)
            rawReplyDao.deleteById(batchId)
        }
    }

    // ─── 行为（真实对话分时推送） ─────────────────
    private fun BehaviorEntity.toBehavior() = Behavior(
        behaviorId = id,
        rawId = rawId,
        order = order,
        type = enumValues<BehaviorType>().firstOrNull { it.value == type } ?: BehaviorType.SPEECH,
        content = content,
        duration = duration,
        excuTime = excuTime,
        status = status,
        conversationId = conversationId
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
