package com.anchat.data.repository

import com.anchat.data.local.dao.ConversationDao
import com.anchat.data.local.dao.MessageDao
import com.anchat.data.remote.DeepSeekApi
import com.anchat.data.remote.model.ChatMessageDto
import com.anchat.data.remote.model.ChatRequest
import kotlinx.coroutines.flow.Flow

/**
 * Handles sending a chat turn to DeepSeek and streaming the reply back token by token.
 * Persistence of messages is done by the ViewModel (so it can manage the conversation row).
 */
class ChatRepository(
    private val api: DeepSeekApi,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    fun streamChat(apiKey: String, model: String, messages: List<ChatMessageDto>): Flow<String> {
        return api.streamChat(apiKey, ChatRequest(model = model, messages = messages, stream = true))
    }
}
