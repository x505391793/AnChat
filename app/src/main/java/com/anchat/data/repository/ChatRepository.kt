package com.anchat.data.repository

import com.anchat.data.local.dao.ConversationDao
import com.anchat.data.local.dao.MessageDao
import com.anchat.data.remote.DeepSeekApi
import com.anchat.data.remote.model.ChatCompletionResponse
import com.anchat.data.remote.model.ChatMessageDto
import com.anchat.data.remote.model.ChatRequest

/**
 * Handles sending a chat turn to DeepSeek and returning the full response.
 */
class ChatRepository(
    private val api: DeepSeekApi,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    suspend fun sendChat(apiKey: String, model: String, messages: List<ChatMessageDto>): ChatCompletionResponse {
        return api.sendChat(apiKey, ChatRequest(model = model, messages = messages, stream = false))
    }
}
