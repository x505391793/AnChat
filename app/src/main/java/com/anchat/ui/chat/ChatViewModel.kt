package com.anchat.ui.chat

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anchat.AnChatApplication
import com.anchat.data.local.entity.CharacterEntity
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

class ChatViewModel(
    app: Application,
    convId: Long = -1L,
    characterId: Long = -1L
) : AndroidViewModel(app) {

    private val anchatApp = app as AnChatApplication
    private val chatRepo = anchatApp.chatRepository
    private val localRepo = anchatApp.localRepository
    private val settingsRepo = anchatApp.settingsRepository
    private val configManager = anchatApp.configManager

    private var conversationId: Long? = if (convId >= 0) convId else null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 聊天页顶部标题：角色卡对话显示角色卡名字，普通对话兜底 "AnChat" */
    private val _title = MutableStateFlow("AnChat")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _defaultModel = MutableStateFlow(DeepSeekConstants.MODELS.first().id)
    val defaultModel: StateFlow<String> = _defaultModel.asStateFlow()

    /** 是否展示思考（推理）过程，跟随角色卡设置，普通对话默认开启 */
    private val _thinkingEnabled = MutableStateFlow(true)
    val thinkingEnabled: StateFlow<Boolean> = _thinkingEnabled.asStateFlow()

    /** 当前选中的角色卡，null = 无角色（用主身份） */
    private var currentCharacter: CharacterEntity? = null

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
                val stored = localRepo.getMessages(convId).map {
                    ChatMessage(it.role, it.content, it.reasoningContent)
                }
                _messages.value = stored
                // 角色卡对话：按 characterId 恢复角色名与 system 设定
                val charId = localRepo.getConversation(convId)?.characterId ?: -1L
                if (charId >= 0) {
                    val character = localRepo.getCharacter(charId)
                    setCharacter(character)
                    _title.value = character?.name ?: "AnChat"
                }
            }
        }
        // 角色卡：加载并设置（角色卡优先于配置文件主身份）
        if (characterId >= 0) {
            viewModelScope.launch {
                val character = localRepo.getCharacter(characterId)
                setCharacter(character)
                _title.value = character?.name ?: "AnChat"
                // 有开场白且是新对话 → 自动建对话并写入开场白
                if (character != null) {
                    val greeting = character.greeting
                    if (!greeting.isNullOrBlank() && convId < 0) {
                        val newId = localRepo.createConversation(
                            title = character.name,
                            modelId = null,
                            characterId = character.id
                        )
                        conversationId = newId
                        localRepo.insertMessage(
                            Message(
                                conversationId = newId,
                                role = "assistant",
                                content = greeting
                            )
                        )
                        _messages.value = listOf(
                            ChatMessage("assistant", greeting)
                        )
                    }
                }
            }
        }
    }

    fun setCharacter(character: CharacterEntity?) {
        currentCharacter = character
        _thinkingEnabled.value = character?.thinkingEnabled ?: true
    }

    /**
     * 构造最终的 system 消息：
     * 角色的 systemPrompt + 用户身份（角色卡定义的 or 配置文件主身份）
     */
    private fun buildSystemPrompt(): String? {
        val parts = mutableListOf<String>()

        // 角色的 systemPrompt
        val charPrompt = currentCharacter?.systemPrompt
        if (charPrompt != null && charPrompt.isNotBlank()) {
            parts.add(charPrompt)
        }

        // 用户身份：角色卡优先，否则用配置文件主身份
        val userName = currentCharacter?.userName?.ifBlank { null }
            ?: configManager.getDefaultUserName().ifBlank { null }
        val userDesc = currentCharacter?.userDescription?.ifBlank { null }
            ?: configManager.getDefaultUserDescription().ifBlank { null }

        if (userName != null || userDesc != null) {
            val identityParts = mutableListOf<String>()
            if (userName != null) identityParts.add("你的对话对象名叫${userName}。")
            if (userDesc != null) identityParts.add(userDesc)
            parts.add(identityParts.joinToString("\n"))
        }

        return if (parts.isEmpty()) null else parts.joinToString("\n\n")
    }

    fun send(input: String) {
        val text = input.trim()

        if (text.isBlank()) {
            Toast.makeText(getApplication(), "输入为空", Toast.LENGTH_SHORT).show()
            return
        }
        if (_isLoading.value) {
            Toast.makeText(getApplication(), "正在回复中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. 显示用户消息
        _messages.value = _messages.value + ChatMessage("user", text)

        // 2. 检查 API Key
        val apiKey = settingsRepo.getApiKey()
        if (apiKey == null) {
            Toast.makeText(getApplication(), "请先在「设置」中填写 API Key", Toast.LENGTH_LONG).show()
            _messages.value = _messages.value + ChatMessage("system", "请先在「设置」中填写 API Key")
            _error.value = "请先在「设置」中填写 API Key"
            return
        }

        // 3. 开始请求
        val model = currentCharacter?.modelId ?: _defaultModel.value
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                // DB: 创建/获取对话
                val convId = conversationId ?: localRepo.createConversation(
                    title = text.take(20),
                    characterId = currentCharacter?.id ?: -1L
                ).also { conversationId = it }

                // DB: 保存用户消息
                try {
                    localRepo.insertMessage(
                        Message(conversationId = convId, role = "user", content = text)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "保存用户消息失败: ${e.message}")
                }

                // 构造请求消息列表
                val systemPrompt = buildSystemPrompt()
                val chatMessages = mutableListOf<ChatMessageDto>()

                // system 消息放在最前面
                if (systemPrompt != null) {
                    chatMessages.add(ChatMessageDto(role = "system", content = systemPrompt))
                }

                // 历史对话（只取 user + assistant）
                _messages.value
                    .filter { it.role == "user" || it.role == "assistant" }
                    .forEach { chatMessages.add(ChatMessageDto(role = it.role, content = it.content)) }

                // 调用 API
                val response: ChatCompletionResponse = chatRepo.sendChat(apiKey, model, chatMessages)

                // 解析响应
                val choice = response.choices.firstOrNull()
                val content = choice?.message?.content ?: ""
                val reasoningContent = choice?.message?.reasoningContent
                val finishReason = choice?.finishReason
                val usage = response.usage

                // 显示
                _messages.value = _messages.value + ChatMessage(
                    role = "assistant",
                    content = content,
                    reasoningContent = reasoningContent
                )

                // DB: 保存 assistant 消息
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
                    Log.w(TAG, "保存回复消息失败: ${e.message}")
                }

                // DB: 更新 preview
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

        fun Factory(app: Application, convId: Long, characterId: Long = -1L) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(app, convId, characterId) as T
                }
            }
    }
}
