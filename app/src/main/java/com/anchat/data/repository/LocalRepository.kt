package com.anchat.data.repository

import com.anchat.data.local.dao.CharacterDao
import com.anchat.data.local.dao.ConversationDao
import com.anchat.data.local.dao.MessageDao
import com.anchat.data.local.entity.CharacterEntity
import com.anchat.data.local.entity.Conversation
import com.anchat.data.local.entity.Message
import kotlinx.coroutines.flow.Flow

class LocalRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val characterDao: CharacterDao
) {
    // ─── 对话 ────────────────────────────────────────
    fun observeConversations(): Flow<List<Conversation>> = conversationDao.observeAll()
    fun observeMessages(conversationId: Long): Flow<List<Message>> =
        messageDao.observeByConversation(conversationId)

    suspend fun getMessages(conversationId: Long): List<Message> =
        messageDao.getByConversation(conversationId)

    suspend fun getConversation(id: Long): Conversation? = conversationDao.getById(id)

    suspend fun createConversation(
        title: String = "新对话",
        modelId: String? = null,
        characterId: Long = -1L
    ): Long = conversationDao.insert(
        Conversation(title = title, modelId = modelId, characterId = characterId)
    )

    suspend fun insertMessage(message: Message): Long = messageDao.insert(message)
    suspend fun renameConversation(id: Long, title: String) = conversationDao.rename(id, title)
    suspend fun updatePreview(id: Long, preview: String) = conversationDao.updatePreview(id, preview)
    suspend fun setStar(id: Long, isStar: Boolean) = conversationDao.setStar(id, isStar)
    suspend fun setPinned(id: Long, pinned: Boolean) = conversationDao.setPinned(id, pinned)
    suspend fun deleteConversation(id: Long) {
        messageDao.deleteByConversation(id)
        conversationDao.deleteById(id)
    }

    // ─── 角色 ────────────────────────────────────────
    fun observeCharacters(): Flow<List<CharacterEntity>> = characterDao.observeAll()
    suspend fun getAllCharacters(): List<CharacterEntity> = characterDao.getAll()
    suspend fun getCharacter(id: Long): CharacterEntity? = characterDao.getById(id)
    suspend fun insertCharacter(character: CharacterEntity): Long = characterDao.insert(character)
    suspend fun updateCharacter(character: CharacterEntity) = characterDao.update(character)
    suspend fun deleteCharacter(character: CharacterEntity) = characterDao.delete(character)
}
