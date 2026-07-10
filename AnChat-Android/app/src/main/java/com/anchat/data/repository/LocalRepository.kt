package com.anchat.data.repository

import com.anchat.data.local.dao.ConversationDao
import com.anchat.data.local.dao.MessageDao
import com.anchat.data.local.entity.Conversation
import com.anchat.data.local.entity.Message
import kotlinx.coroutines.flow.Flow

/**
 * Local-only access to conversations and their messages (Room).
 */
class LocalRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    fun observeConversations(): Flow<List<Conversation>> = conversationDao.observeAll()
    fun observeMessages(conversationId: Long): Flow<List<Message>> =
        messageDao.observeByConversation(conversationId)

    suspend fun getMessages(conversationId: Long): List<Message> =
        messageDao.getByConversation(conversationId)

    suspend fun createConversation(title: String = "新对话"): Long =
        conversationDao.insert(Conversation(title = title))

    suspend fun insertMessage(message: Message): Long = messageDao.insert(message)
    suspend fun renameConversation(id: Long, title: String) = conversationDao.rename(id, title)
    suspend fun deleteConversation(id: Long) {
        messageDao.deleteByConversation(id)
        conversationDao.deleteById(id)
    }
}
