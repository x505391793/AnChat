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
import com.anchat.data.local.entity.Conversation
import com.anchat.data.local.entity.Message
import com.anchat.engine.core.ConversationEngine
import com.anchat.engine.core.contract.ConversationContext
import com.anchat.engine.core.contract.EngineEvent
import com.anchat.engine.core.contract.TurnInput
import com.anchat.push.ActiveConversation
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
    private val localRepo = anchatApp.localRepository
    private val settingsRepo = anchatApp.settingsRepository
    private val configManager = anchatApp.configManager
    private val engine = anchatApp.engine
    private val engineEvents = anchatApp.engineEvents

    /** 当前对话的真实 id（新建对话创建后才非空，供头像点击进编辑页） */
    private var conversationId: Long? = if (convId >= 0) convId else null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 聊天页顶部标题：优先用对话级角色名（可被对话内编辑覆盖） */
    private val _title = MutableStateFlow("AnChat")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _defaultModel = MutableStateFlow("")
    val defaultModel: StateFlow<String> = _defaultModel.asStateFlow()

    /** 是否展示思考（推理）过程 */
    private val _thinkingEnabled = MutableStateFlow(false)
    val thinkingEnabled: StateFlow<Boolean> = _thinkingEnabled.asStateFlow()

    /**
     * 对话级身份快照（从主角色卡继承，可二次编辑，不影响主角色卡）。
     * 聊天运行时以它为准：system 提示、用户身份、模型、思考开关、标题。
     */
    private val _profile = MutableStateFlow<ConversationProfile?>(null)
    val profile: StateFlow<ConversationProfile?> = _profile.asStateFlow()

    /** 当前对话真实 id 的响应式版本，供 UI 判断头像是否可点击进编辑 */
    private val _conversationId = MutableStateFlow<Long?>(if (convId >= 0) convId else null)
    val conversationIdFlow: StateFlow<Long?> = _conversationId.asStateFlow()

    /** 当前选中的主角色卡（仅作快照回退来源），null = 无角色（用主身份） */
    private var currentCharacter: CharacterEntity? = null

    init {
        viewModelScope.launch {
            settingsRepo.observeModels().collect { models ->
                val def = settingsRepo.getDefaultModelId()
                _defaultModel.value = (if (def != null && models.any { it.id == def }) def else null)
                    ?: models.firstOrNull()?.id ?: ""
            }
        }
        // 统一响应式：对话 id 一旦确定（既有或新建），观察其快照推导 profile / 标题 / 思考。
        // 这样「对话内二次编辑」保存后能即时回灌当前聊天页。
        viewModelScope.launch {
            _conversationId.collect { id ->
                if (id != null) {
                    localRepo.observeConversation(id).collect { conv ->
                        val p = profileOf(conv, currentCharacter)
                        _profile.value = p
                        _title.value = p.charRemark ?: p.charName
                        _thinkingEnabled.value = p.thinkingEnabled
                    }
                }
            }
        }
        // 收集引擎渲染事件（助手消息 / 错误）更新 UI
        viewModelScope.launch {
            engineEvents.collect { event ->
                when (event) {
                    is EngineEvent.AssistantMessage -> {
                        _messages.value = _messages.value +
                            ChatMessage("assistant", event.record.content, event.record.reasoningContent)
                        _isLoading.value = false
                    }
                    is EngineEvent.Error -> {
                        _messages.value = _messages.value +
                            ChatMessage("assistant", "❌ ${event.message}")
                        _error.value = event.message
                        _isLoading.value = false
                    }
                }
            }
        }
        if (convId >= 0) {
            viewModelScope.launch {
                val stored = localRepo.getMessages(convId).map {
                    ChatMessage(it.role, it.content, it.reasoningContent)
                }
                _messages.value = stored
                // 角色卡对话：按 characterId 恢复主角色卡（仅作快照回退）
                val charId = localRepo.getConversation(convId)?.characterId ?: -1L
                val character = if (charId >= 0) localRepo.getCharacter(charId) else null
                setCharacter(character)
                _title.value = character?.name ?: "AnChat"
                // 打开既有会话：标记当前会话 + 清未读（红点消失）
                ActiveConversation.set(convId)
                localRepo.markRead(convId)
                // _conversationId 已是 convId，上面的 observe 会接管 profile
            }
        }
        // 从角色名片新建对话：加载角色卡，有开场白时建对话并快照身份
        if (characterId >= 0) {
            viewModelScope.launch {
                val character = localRepo.getCharacter(characterId)
                setCharacter(character)
                _title.value = character?.name ?: "AnChat"
                if (character != null) {
                    val greeting = character.greeting
                    if (!greeting.isNullOrBlank() && convId < 0) {
                        val conv = snapshotFrom(character, character.name, character.id)
                        val newId = localRepo.createConversation(conv)
                        conversationId = newId
                        _conversationId.value = newId
                        ActiveConversation.set(newId)
                        localRepo.insertMessage(
                            Message(
                                conversationId = newId,
                                role = "assistant",
                                content = greeting
                            )
                        )
                        localRepo.markRead(newId) // 开场白立即展示，视为已读，避免列表误显红点
                        _messages.value = listOf(ChatMessage("assistant", greeting))
                        applyProfile(conv, character)
                    }
                }
            }
        }
    }

    fun setCharacter(character: CharacterEntity?) {
        currentCharacter = character
        _thinkingEnabled.value = character?.thinkingEnabled ?: false
    }

    /**
     * 由 conversation 快照 + 主角色卡 推导对话级身份。
     * 优先级：快照列优先；为空时回退主角色卡 / 全局配置主身份。
     */
    private fun profileOf(conv: Conversation?, character: CharacterEntity?): ConversationProfile {
        val charRemark = conv?.charRemark?.ifBlank { null }
            ?: character?.remark?.ifBlank { null }
        val charName = conv?.charName?.ifBlank { null }
            ?: character?.name
            ?: conv?.title
            ?: "AI"
        val systemPrompt = conv?.systemPrompt?.ifBlank { null }
            ?: character?.systemPrompt
            ?: ""
        val userName = conv?.userName?.ifBlank { null }
            ?: character?.userName?.ifBlank { null }
            ?: configManager.getDefaultUserName().ifBlank { null }
        val userDescription = conv?.userDescription?.ifBlank { null }
            ?: character?.userDescription?.ifBlank { null }
            ?: configManager.getDefaultUserDescription().ifBlank { null }
        // 性别 / 微信号来自全局主身份（角色卡 / 对话快照均无单独字段）
        val userGender = configManager.getDefaultUserGender().ifBlank { null }
        val userWechatId = configManager.getDefaultUserWechatId().ifBlank { null }
        return ConversationProfile(
            charRemark = charRemark,
            charName = charName,
            charAvatar = conv?.charAvatar ?: character?.avatar,
            charDescription = conv?.charDescription ?: character?.description,
            systemPrompt = systemPrompt,
            greeting = conv?.charGreeting ?: character?.greeting,
            userName = userName,
            userAvatar = conv?.userAvatar ?: character?.userAvatar ?: configManager.getDefaultUserAvatar().ifBlank { null },
            userDescription = userDescription,
            userGender = userGender,
            userWechatId = userWechatId,
            modelId = conv?.modelId ?: character?.modelId,
            thinkingEnabled = conv?.charThinkingEnabled ?: character?.thinkingEnabled ?: false
        )
    }

    /** 由主角色卡生成一份对话级身份快照（写入 conversation，之后可独立编辑） */
    private fun snapshotFrom(
        character: CharacterEntity?,
        title: String,
        charId: Long
    ): Conversation = Conversation(
        title = title,
        characterId = charId,
        charRemark = character?.remark,
        modelId = character?.modelId,
        systemPrompt = character?.systemPrompt,
        charName = character?.name,
        charAvatar = character?.avatar,
        charDescription = character?.description,
        charGreeting = character?.greeting,
        charThinkingEnabled = character?.thinkingEnabled ?: false,
        userName = character?.userName,
        userAvatar = character?.userAvatar,
        userDescription = character?.userDescription
    )

    /** 同步建立 profile（创建对话时调用，保证首条请求的 system 提示立即生效） */
    private fun applyProfile(conv: Conversation?, character: CharacterEntity?) {
        if (conv == null) return
        val p = profileOf(conv, character)
        _profile.value = p
        _title.value = p.charRemark ?: p.charName
        _thinkingEnabled.value = p.thinkingEnabled
    }

    /**
     * 构造最终的 system 消息：
     * 对话级角色 systemPrompt + 对话级用户身份（回退到主角色卡 / 全局主身份）
     */
    private fun buildSystemPrompt(): String? {
        val profile = _profile.value ?: return null
        val parts = mutableListOf<String>()

        if (profile.systemPrompt.isNotBlank()) {
            parts.add(profile.systemPrompt)
        }

        val userName = profile.userName?.ifBlank { null }
        val userGender = profile.userGender?.ifBlank { null }
        val userWechatId = profile.userWechatId?.ifBlank { null }
        val userDesc = profile.userDescription?.ifBlank { null }
        if (userName != null || userGender != null || userWechatId != null || userDesc != null) {
            val identityParts = mutableListOf<String>()
            if (userName != null) identityParts.add("你的对话对象名叫${userName}。")
            if (userGender != null) identityParts.add("其性别为${userGender}。")
            if (userWechatId != null) identityParts.add("其微信号是${userWechatId}。")
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

        // 解析模型与对应凭证（key / url 绑定在模型上）
        val modelId = _profile.value?.modelId ?: currentCharacter?.modelId
            ?: settingsRepo.getDefaultModelId() ?: _defaultModel.value
        if (modelId.isBlank()) {
            Toast.makeText(getApplication(), "请先在「模型管理」中添加模型", Toast.LENGTH_LONG).show()
            _messages.value = _messages.value + ChatMessage("system", "请先在「模型管理」中添加模型")
            _error.value = "请先在「模型管理」中添加模型"
            return
        }
        val modelCfg = settingsRepo.getModelConfig(modelId)
        val apiKey = modelCfg?.apiKey
        val apiUrl = modelCfg?.apiUrl
        if (apiKey.isNullOrBlank()) {
            val label = modelCfg?.name ?: modelId
            Toast.makeText(getApplication(), "模型「$label」未配置 API Key，请到模型管理添加", Toast.LENGTH_LONG).show()
            _messages.value = _messages.value + ChatMessage("system", "模型「$label」未配置 API Key，请到模型管理添加")
            _error.value = "模型「$label」未配置 API Key"
            return
        }

        // 进入异步前先锁住 loading，防止快速连点导致重复发送
        _isLoading.value = true
        _error.value = null

        // 创建/获取对话（VM 持有 conversationId 供导航），并把用户消息落库
        viewModelScope.launch {
            val convId = conversationId ?: run {
                val conv = snapshotFrom(
                    currentCharacter,
                    currentCharacter?.name ?: "新对话",
                    currentCharacter?.id ?: -1L
                )
                val newId = localRepo.createConversation(conv)
                conversationId = newId
                _conversationId.value = newId
                ActiveConversation.set(newId)
                newId
            }

            // 对话（可能刚创建）已落库：据其建立 profile，
            // 确保 system 提示 / 模型凭证在「首条消息」即生效
            // （旧写法在 applyProfile 之前取提示词会拿到 null，导致首条消息漏提示词）
            val conv = localRepo.getConversation(convId)
            applyProfile(conv, currentCharacter)
            val systemPrompt = buildSystemPrompt()

            try {
                localRepo.insertMessage(
                    Message(conversationId = convId, role = "user", content = text)
                )
            } catch (e: Exception) {
                Log.w(TAG, "保存用户消息失败: ${e.message}")
            }
            _messages.value = _messages.value + ChatMessage("user", text)

            // 交给对话处理机（fire-and-forget：内部在自有 scope 执行，不阻塞此处）
            val context = ConversationContext(
                conversationId = convId.toString(),
                systemPrompt = systemPrompt,
                modelId = modelId,
                apiKey = apiKey,
                apiUrl = apiUrl ?: ""
            )
            engine.send(TurnInput(text), context)
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun startNewConversation() {
        conversationId = null
        _conversationId.value = null
        ActiveConversation.set(null)
        _messages.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        // 离开聊天页：清空「当前打开的会话」，使后续消息能正常弹通知
        ActiveConversation.set(null)
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
