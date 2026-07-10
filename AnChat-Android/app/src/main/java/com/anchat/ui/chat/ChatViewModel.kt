package com.anchat.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anchat.AnChatApplication
import com.anchat.data.local.entity.Message
import com.anchat.data.remote.DeepSeekConstants
import com.anchat.data.remote.model.ChatMessageDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val role: String,
    val content: String
)

class ChatViewModel(app: Application, convId: Long = -1L) : AndroidViewModel(app) {

    private val app = app as AnChatApplication
    private val chatRepo = app.chatRepository
    private val localRepo = app.localRepository
    private val settingsRepo = app.settingsRepository

    private var conversationId: Long? = if (convId >= 0) convId else null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _defaultModel = MutableStateFlow(DeepSeekConstants.MODELS.first().id)
    val defaultModel: StateFlow<String> = _defaultModel.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.observeModels().collect { models ->
                _defaultModel.value = models.firstOrNull { it.isDefault }?.id
                    ?: models.firstOrNull()?.id
                    ?: DeepSeekConstants.MODELS.first().id
            }
        }
        if (convId >= 0) {
            viewModelScope.launch {
                val stored = localRepo.getMessages(convId).map { ChatMessage(it.role, it.content) }
                _messages.value = stored
            }
        }
    }

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun send() {
        val text = _input.value.trim()
        if (text.isBlank() || _isStreaming.value) return

        val apiKey = settingsRepo.getApiKey()
        if (apiKey == null) {
            _error.value = "请先在「设置」中填写 API Key"
            return
        }
        val model = _defaultModel.value

        _isStreaming.value = true
        _error.value = null

        val userMsg = ChatMessage("user", text)
        val assistantMsg = ChatMessage("assistant", "")
        _messages.value = _messages.value + userMsg + assistantMsg
        _input.value = ""

        viewModelScope.launch {
            try {
                val convId = conversationId ?: localRepo.createConversation(title = text.take(20))
                    .also { conversationId = it }

                localRepo.insertMessage(
                    Message(conversationId = convId, role = "user", content = text)
                )

                val history = _messages.value
                    .dropLast(1)
                    .map { ChatMessageDto(role = it.role, content = it.content) }

                val sb = StringBuilder()
                chatRepo.streamChat(apiKey, model, history).collect { token ->
                    if (token.startsWith("<error>")) {
                        _error.value = token.removePrefix("<error>").removeSuffix("</error>")
                    } else {
                        sb.append(token)
                        _messages.value = _messages.value.dropLast(1) +
                            ChatMessage("assistant", sb.toString())
                    }
                }

                localRepo.insertMessage(
                    Message(conversationId = convId, role = "assistant", content = sb.toString(), model = model)
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "发送失败"
            } finally {
                _isStreaming.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun startNewConversation() {
        conversationId = null
        _messages.value = emptyList()
    }

    companion object {
        fun Factory(app: Application, convId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(app, convId) as T
            }
        }
    }
}
