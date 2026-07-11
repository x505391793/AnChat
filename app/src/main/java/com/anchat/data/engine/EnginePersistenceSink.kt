package com.anchat.data.engine

import com.anchat.data.engine.BehaviorDao
import com.anchat.data.local.dao.ConversationDao
import com.anchat.data.local.dao.MessageDao
import com.anchat.data.engine.RawReplyDao
import com.anchat.data.local.entity.Message
import com.anchat.engine.core.contract.Behavior
import com.anchat.engine.core.contract.BehaviorType
import com.anchat.engine.core.contract.ChatMessageRecord
import com.anchat.engine.core.contract.RawReply
import com.anchat.engine.core.contract.TokenUsage
import com.anchat.engine.spi.PersistenceSink

/** 真实 PersistenceSink：把引擎的数据契约映射到 Room 实体 */
class EnginePersistenceSink(
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val rawDao: RawReplyDao,
    private val behaviorDao: BehaviorDao
) : PersistenceSink {

    override suspend fun getHistory(conversationId: String): List<ChatMessageRecord> {
        val cid = conversationId.toLongOrNull() ?: 0L
        return messageDao.getByConversation(cid)
            .filter { it.role == "user" || it.role == "assistant" }
            .map {
                ChatMessageRecord(
                    conversationId = conversationId,
                    role = it.role,
                    content = it.content,
                    reasoningContent = it.reasoningContent
                )
            }
    }

    override suspend fun persistRaw(raw: RawReply, conversationId: String) {
        rawDao.insert(
            RawReplyEntity(
                id = raw.id,
                conversationId = conversationId,
                content = raw.content,
                reasoningContent = raw.reasoningContent,
                promptTokens = raw.usage?.promptTokens,
                completionTokens = raw.usage?.completionTokens,
                totalTokens = raw.usage?.totalTokens,
                reasoningTokens = raw.usage?.reasoningTokens,
                promptCacheHitTokens = raw.usage?.promptCacheHitTokens,
                promptCacheMissTokens = raw.usage?.promptCacheMissTokens,
                isError = raw.isError,
                createdAt = raw.createdAt
            )
        )
    }

    override suspend fun persistBehaviors(behaviors: List<Behavior>) {
        behaviorDao.insertAll(
            behaviors.map {
                BehaviorEntity(
                    id = it.behaviorId,
                    rawId = it.rawId,
                    order = it.order,
                    type = it.type.value,
                    content = it.content,
                    excuTime = it.excuTime,
                    completed = it.completed,
                    isRead = it.isRead
                )
            }
        )
    }

    override suspend fun persistAssistant(record: ChatMessageRecord) {
        messageDao.insert(
            Message(
                conversationId = record.conversationId.toLongOrNull() ?: 0L,
                role = record.role,
                content = record.content,
                reasoningContent = record.reasoningContent,
                promptTokens = record.usage?.promptTokens,
                completionTokens = record.usage?.completionTokens,
                totalTokens = record.usage?.totalTokens,
                reasoningTokens = record.usage?.reasoningTokens,
                promptCacheHitTokens = record.usage?.promptCacheHitTokens,
                promptCacheMissTokens = record.usage?.promptCacheMissTokens
            )
        )
    }

    override suspend fun updatePreview(conversationId: String, preview: String) {
        conversationDao.updatePreview(conversationId.toLongOrNull() ?: 0L, preview)
    }

    override suspend fun markCompleted(behaviorId: String) {
        behaviorDao.markCompleted(behaviorId)
    }

    override suspend fun markRead(behaviorId: String) {
        behaviorDao.markRead(behaviorId)
    }

    override suspend fun getDueBehaviors(): List<Behavior> {
        return behaviorDao.getDue().map {
            Behavior(
                behaviorId = it.id,
                rawId = it.rawId,
                order = it.order,
                type = enumValues<BehaviorType>().firstOrNull { t -> t.value == it.type }
                    ?: BehaviorType.SPEECH,
                content = it.content,
                excuTime = it.excuTime,
                completed = it.completed,
                isRead = it.isRead
            )
        }
    }
}
