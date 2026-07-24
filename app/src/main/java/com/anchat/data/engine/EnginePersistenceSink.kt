package com.anchat.data.engine

import com.anchat.data.engine.BehaviorDao
import com.anchat.data.local.dao.ConversationDao
import com.anchat.data.engine.RawReplyDao
import com.anchat.engine.core.contract.Behavior
import com.anchat.engine.core.contract.BehaviorType
import com.anchat.engine.core.contract.ChatMessageRecord
import com.anchat.engine.core.contract.RawReply
import com.anchat.engine.core.contract.TokenUsage
import com.anchat.engine.spi.PersistenceSink

/** 真实 PersistenceSink：把引擎的数据契约映射到 Room 实体 */
class EnginePersistenceSink(
    private val conversationDao: ConversationDao,
    private val rawDao: RawReplyDao,
    private val behaviorDao: BehaviorDao
) : PersistenceSink {

    override suspend fun getHistory(conversationId: String): List<ChatMessageRecord> {
        return rawDao.getHistory(conversationId)
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
                role = raw.role,
                content = raw.content,
                reasoningContent = raw.reasoningContent,
                promptTokens = raw.usage?.promptTokens,
                completionTokens = raw.usage?.completionTokens,
                totalTokens = raw.usage?.totalTokens,
                reasoningTokens = raw.usage?.reasoningTokens,
                promptCacheHitTokens = raw.usage?.promptCacheHitTokens,
                promptCacheMissTokens = raw.usage?.promptCacheMissTokens,
                isError = raw.isError,
                kind = raw.kind,
                createdAt = raw.createdAt
            )
        )
    }

    override suspend fun persistBehaviors(behaviors: List<Behavior>) {
        behaviorDao.insertAll(
            behaviors.map {
                BehaviorEntity(
                    id = it.behaviorId,
                    order = it.order,
                    type = it.type.value,
                    role = it.role,
                    content = it.content,
                    duration = it.duration,
                    excuTime = it.excuTime,
                    status = it.status,
                    conversationId = it.conversationId,
                    batchId = it.batchId
                )
            }
        )
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
                order = it.order,
                type = enumValues<BehaviorType>().firstOrNull { t -> t.value == it.type }
                    ?: BehaviorType.SPEECH,
                role = it.role,
                content = it.content,
                duration = it.duration,
                excuTime = it.excuTime,
                status = it.status,
                conversationId = it.conversationId,
                batchId = it.batchId
            )
        }
    }
}
