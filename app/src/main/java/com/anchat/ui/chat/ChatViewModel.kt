package com.anchat.ui.chat

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anchat.AnChatApplication
import com.anchat.data.local.entity.Message
import com.anchat.data.remote.DeepSeekConstants
import com.anchat.data.remote.model.ChatCompletionResponse
import com.anchat.data.remote.model.ChatMessageDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val role: String,
    val content: String,
    val reasoningContent: String? = null
)

class ChatViewModel(app: Application, convId: Long = -1L) : AndroidViewModel(app) {

    private val anchatApp = app as AnChatApplication
    private val chatRepo = anchatApp.chatRepository
    private val localRepo = anchatApp.localRepository
    private val settingsRepo = anchatApp.settingsRepository

    private var conversationId: Long? = if (convId >= 0) convId else null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
                Log.d(TAG, "默认模型: ${_defaultModel.value}")
            }
        }
        if (convId >= 0) {
            viewModelScope.launch {
                val stored = localRepo.getMessages(convId).map {
                    ChatMessage(it.role, it.content, it.reasoningContent)
                }
                _messages.value = stored
                Log.d(TAG, "加载对话 $convId: ${stored.size} 条消息")
            }
        }
    }

    fun onInputChange(text: String) {
        _input.value = text
    }

    fun send() {
        val text = _input.value.trim()
        Log.d(TAG, "send() 被调用, 输入: '${text.take(30)}', isLoading=${_isLoading.value}")

        if (text.isBlank()) {
            Log.w(TAG, "输入为空, 忽略")
            Toast.makeText(getApplication(), "输入为空", Toast.LENGTH_SHORT).show()
            return
        }
        if (_isLoading.value) {
            Log.w(TAG, "正在等待回复中, 忽略")
            Toast.makeText(getApplication(), "正在回复中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. 始终先显示用户消息
        _input.value = ""
        val userMsg = ChatMessage("user", text)
        _messages.value = _messages.value + userMsg
        Log.d(TAG, "用户消息已添加, 当前消息数=${_messages.value.size}")

        // 2. 检查 API Key
        val apiKey = settingsRepo.getApiKey()
        if (apiKey == null) {
            Log.e(TAG, "API Key 未设置!")
            Toast.makeText(getApplication(), "请先在「设置」中填写 API Key", Toast.LENGTH_LONG).show()
            _messages.value = _messages.value + ChatMessage("system", "请先在「设置」中填写 API Key")
            _error.value = "请先在「设置」中填写 API Key"
            return
        }
        Log.d(TAG, "API Key 已获取 (长度=${apiKey.length}), 模型=${_defaultModel.value}")

        // 3. 开始请求
        val model = _defaultModel.value
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                // DB: 创建/获取对话
                val convId = conversationId ?: localRepo.createConversation(title = text.take(20))
                    .also { conversationId = it }
                Log.d(TAG, "对话ID: $convId")

                // DB: 保存用户消息
                try {
                    localRepo.insertMessage(
                        Message(conversationId = convId, role = "user", content = text)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "保存用户消息失败(不影响流程): ${e.message}")
                }

                // 构造请求历史（去掉系统提示，这些由模型配置的 systemPrompt 处理）
                val history = _messages.value
                    .filter { it.role == "user" || it.role == "assistant" }
                    .map { ChatMessageDto(role = it.role, content = it.content) }
                Log.d(TAG, "发送 ${history.size} 条消息到 DeepSeek API, model=$model")

                // 调用 API（非流式）
                val response: ChatCompletionResponse = chatRepo.sendChat(apiKey, model, history)
                Log.d(TAG, "API 响应成功: choices=${response.choices.size}")

                // 解析响应
                val choice = response.choices.firstOrNull()
                val content = choice?.message?.content ?: ""
                val reasoningContent = choice?.message?.reasoningContent
                val finishReason = choice?.finishReason
                val usage = response.usage

                Log.d(TAG, "回复内容长度=${content.length}, 思考长度=${reasoningContent?.length ?: 0}, finishReason=$finishReason")
                Log.d(TAG, "Token: prompt=${usage?.promptTokens}, completion=${usage?.completionTokens}, total=${usage?.totalTokens}, reasoning=${usage?.completionTokensDetails?.reasoningTokens}")
                Log.d(TAG, "缓存: hit=${usage?.promptCacheHitTokens}, miss=${usage?.promptCacheMissTokens}")

                // 在聊天区域显示
                _messages.value = _messages.value + ChatMessage(
                    role = "assistant",
                    content = content,
                    reasoningContent = reasoningContent
                )

                // DB: 保存 assistant 消息（含全部新字段）
                try {
                    localRepo.insertMessage(
                        Message(
                            conversationId = convId,
                            role = "assistant",
                            content = content,
                            reasoningContent = reasoningContent,
                            model = model,
                            finishReason = finishReason,
                            promptTokens = usage?.promptTokens,
                            completionTokens = usage?.completionTokens,
                            totalTokens = usage?.totalTokens,
                            reasoningTokens = usage?.completionTokensDetails?.reasoningTokens,
                            promptCacheHitTokens = usage?.promptCacheHitTokens,
                            promptCacheMissTokens = usage?.promptCacheMissTokens
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "保存回复消息失败(不影响流程): ${e.message}")
                }

                // DB: 更新对话的 preview
                try {
                    localRepo.updatePreview(convId, content.take(50))
                } catch (e: Exception) {
                    Log.w(TAG, "更新 preview 失败: ${e.message}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "发送异常: ${e.message}", e)
                _error.value = e.message ?: "发送失败"
                _messages.value = _messages.value + ChatMessage(
                    role = "assistant",
                    content = "❌ ${e.message ?: "发送失败"}"
                )
            } finally {
                _isLoading.value = false
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
        private const val TAG = "AnChatVM"

        fun Factory(app: Application, convId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(app, convId) as T
            }
        }
    }
}
