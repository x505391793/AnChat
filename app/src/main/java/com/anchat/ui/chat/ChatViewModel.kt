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
import com.anchat.engine.core.contract.Behavior
import com.anchat.engine.core.contract.ConversationContext
import com.anchat.engine.core.contract.DecompositionSpec
import com.anchat.engine.core.contract.EngineEvent
import com.anchat.engine.core.contract.TurnInput
import com.anchat.push.ActiveConversation
import com.anchat.service.RequestForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val role: String,
    val content: String,
    val reasoningContent: String? = null,
    val batchId: String? = null,
    /** 落库后的自增主键，供单条删除精准定位（未落库乐观消息为 -1） */
    val id: Long = -1L,
    /** 真实对话行为类型：speech / emotion / movement（为 null 表示普通消息） */
    val behaviorType: String? = null,
    /** movement 的离开时长文本 */
    val duration: String? = null,
    /** 行为表主键，用于去重与整批删除 */
    val behaviorId: String? = null
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
                        if (event.record.hidden) {
                            // 真实对话：原始整段回复仅入库供上下文，不进展示列表，仅清 loading
                            _isLoading.value = false
                        } else {
                            _messages.value = _messages.value +
                                ChatMessage(
                                    "assistant",
                                    event.record.content,
                                    event.record.reasoningContent,
                                    event.record.batchId,
                                    id = event.record.id
                                )
                            _isLoading.value = false
                        }
                    }

                    is EngineEvent.BehaviorDue -> {
                        // 行为到点实时推送：去重 + 按对话隔离后追加（调度器直接发事件，
                        // 不依赖 Room Flow 失效重查，修复「退出重进才看得到」的断链）。
                        // conversationId 隔离：engineEvents 是全局通道，避免把别的对话的行为串到本对话。
                        val b = event.behavior
                        val cid = conversationId
                        if (cid != null && b.conversationId == cid.toString() && b.behaviorId !in shownBehaviorIds) {
                            shownBehaviorIds += b.behaviorId
                            _messages.value = _messages.value + behaviorToChatMessage(b)
                            // 实时推到当前激活对话并立即可见 → 翻为已读（status 1 → 2）
                            viewModelScope.launch(Dispatchers.IO) { localRepo.markBehaviorRead(b.behaviorId) }
                        }
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
                val stored = localRepo.getMessages(convId)
                // 角色卡对话：按 characterId 恢复主角色卡（仅作快照回退）
                val charId = localRepo.getConversation(convId)?.characterId ?: -1L
                val character = if (charId >= 0) localRepo.getCharacter(charId) else null
                setCharacter(character)
                _title.value = character?.name ?: "AnChat"
                // 打开既有会话：标记当前会话 + 清未读（红点消失）
                ActiveConversation.set(convId)
                localRepo.markRead(convId)
                // 该对话下历史「已执行未读」行为翻为已读（status 1 → 2），避免重进后还停在 unread
                localRepo.markBehaviorsRead(convId)
                _conversationId.value = convId
                // _conversationId 已是 convId，上面的 observe 会接管 profile

                // 真实对话：初始列表由「可见消息 + 已完成行为」按时序合并；
                // 后续行为由调度器经 engineEvents(BehaviorDue) 实时推送，无需在此挂观察
                val realConv = isRealConvConversation(convId)
                _messages.value = buildInitialDisplay(convId, realConv, stored)
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
                        val greetingBatch = UUID.randomUUID().toString()
                        localRepo.insertMessage(
                            Message(
                                conversationId = newId,
                                role = "assistant",
                                content = greeting,
                                batchId = greetingBatch
                            )
                        )
                        localRepo.markRead(newId) // 开场白立即展示，视为已读，避免列表误显红点
                        _messages.value = listOf(ChatMessage("assistant", greeting, batchId = greetingBatch))
                        applyProfile(conv, character)
                        // 真实对话：开场白之后的回复由行为层（调度器经 engineEvents）驱动，无需挂观察
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
        // 用户身份默认以全局配置（我→个人信息）为准；仅当本对话被「对话内」主动改过
        // （userIdentityOverridden=true）才使用对话级快照，避免旧名被烤进快照后跨重装残留。
        val userOverridden = conv?.userIdentityOverridden == true
        val userName = if (userOverridden) conv?.userName?.ifBlank { null } else null
            ?: configManager.getDefaultUserName().ifBlank { null }
            ?: character?.userName?.ifBlank { null }
        val userDescription = if (userOverridden) conv?.userDescription?.ifBlank { null } else null
            ?: configManager.getDefaultUserDescription().ifBlank { null }
            ?: character?.userDescription?.ifBlank { null }
        val userAvatar = if (userOverridden) conv?.userAvatar?.ifBlank { null } else null
            ?: character?.userAvatar?.ifBlank { null }
            ?: configManager.getDefaultUserAvatar().ifBlank { null }
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
            userAvatar = userAvatar,
            userDescription = userDescription,
            userGender = userGender,
            userWechatId = userWechatId,
            modelId = conv?.modelId ?: character?.modelId,
            thinkingEnabled = conv?.charThinkingEnabled ?: character?.thinkingEnabled ?: false,
            realConversation = conv?.charRealConversation ?: character?.realConversation ?: false
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
        charRealConversation = character?.realConversation ?: false,
        // 用户身份默认跟随全局配置，不在创建时把旧名烤进快照
        userIdentityOverridden = false,
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
        val userDesc = profile.userDescription?.ifBlank { null }
        if (userName != null || userGender != null || userDesc != null) {
            val identityParts = mutableListOf<String>()
            if (userName != null) identityParts.add("你的对话对象名叫${userName}。")
            if (userGender != null) identityParts.add("其性别为${userGender}。")
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

        // 趁 app 还在前台（用户刚点击发送）立即启动前台保活 Service。
        // 不能放在下面的异步块里——等 suspend 函数恢复时 app 可能已进后台，
        // Android 12+ 会拒绝后台启动前台 Service，导致请求在后台被杀报网络错误。
        RequestForegroundService.begin(getApplication())

        // 捕获当前快照（供 engineScope 使用，避免跨协程读到中间态）
        val character = currentCharacter
        val existingConvId = conversationId
        val existingSystemPrompt = buildSystemPrompt()
        val batchId = UUID.randomUUID().toString()

        // 引擎编排（建对话→存用户消息→engine.send）放到 Application 级 engineScope，
        // 不随 ViewModel 生命周期绑定——用户退出页面或退到桌面后请求仍能完成。
        anchatApp.engineScope.launch {
            val convId = existingConvId ?: run {
                val conv = snapshotFrom(
                    character,
                    character?.name ?: "新对话",
                    character?.id ?: -1L
                )
                val newId = localRepo.createConversation(conv)
                conversationId = newId
                _conversationId.value = newId
                ActiveConversation.set(newId)
                newId
            }

            val conv = localRepo.getConversation(convId)
            applyProfile(conv, character)
            val systemPrompt = existingSystemPrompt ?: buildSystemPrompt()

            // 先落库拿自增 id，再带 id 上屏——保证长按删除能精准定位这一句
            val userMsgId = try {
                localRepo.insertMessage(
                    Message(conversationId = convId, role = "user", content = text, batchId = batchId)
                )
            } catch (e: Exception) {
                Log.w(TAG, "保存用户消息失败: ${e.message}")
                -1L
            }
            _messages.value = _messages.value + ChatMessage("user", text, batchId = batchId, id = userMsgId)

            // 真实对话：开启且已配置管理 AI 时，构建第二模型（拆解）规格
            val decompSpec = buildDecompSpec()
            val realConv = _profile.value?.realConversation == true && decompSpec != null

            val context = ConversationContext(
                conversationId = convId.toString(),
                systemPrompt = systemPrompt,
                modelId = modelId,
                apiKey = apiKey,
                apiUrl = apiUrl ?: "",
                batchId = batchId,
                realConversation = realConv,
                decompSpec = decompSpec
            )
            engine.send(TurnInput(text), context)
        }
    }

    /** 只删当前长按的那一条气泡（按 DB 主键），不波及同回合其它消息，也不取消正在进行的回合 */
    fun deleteMessage(messageId: Long) {
        if (messageId < 0) return
        viewModelScope.launch(Dispatchers.IO) {
            localRepo.deleteMessage(messageId)
        }
        _messages.value = _messages.value.filter { it.id != messageId }
    }

    /** 删除一个回合的全部数据：消息（用户提问+AI回复）+ 原始回复 + 行为 */
    fun deleteBatch(batchId: String?) {
        if (batchId.isNullOrBlank()) return
        engine.cancel(batchId)
        RequestForegroundService.finish(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            localRepo.deleteBatch(batchId)
        }
        _messages.value = _messages.value.filter { it.batchId != batchId }
    }

    fun clearError() {
        _error.value = null
    }

    // ─── 真实对话：行为层驱动 UI 的辅助 ───────────────

    /** 该对话是否处于真实对话模式（角色/对话开关开启且已配置管理 AI） */
    private suspend fun isRealConvConversation(convId: Long): Boolean {
        val conv = localRepo.getConversation(convId) ?: return false
        val character = if (conv.characterId >= 0) localRepo.getCharacter(conv.characterId) else null
        val flag = conv.charRealConversation ?: character?.realConversation ?: false
        return flag && configManager.getRealConversationModelId() != null
    }

    /** 构建真实对话管理 AI 规格（第二模型）；未配置则返回 null */
    private fun buildDecompSpec(): DecompositionSpec? {
        val id = configManager.getRealConversationModelId() ?: return null
        val m = settingsRepo.getModelConfig(id) ?: return null
        if (m.apiKey.isBlank()) return null
        return DecompositionSpec(modelId = m.id, apiKey = m.apiKey, apiUrl = m.apiUrl)
    }

    /** 行为 → 聊天展示消息（speech/emotion/movement 由 ChatScreen 按 behaviorType 区分渲染） */
    private fun behaviorToChatMessage(b: Behavior): ChatMessage = ChatMessage(
        role = "behavior",
        content = b.content,
        batchId = b.rawId,
        behaviorId = b.behaviorId,
        behaviorType = b.type.value,
        duration = b.duration
    )

    /**
     * 初始展示列表：可见消息（用户 + 非隐藏助手，如开场白）+ 已完成行为，按时间序合并。
     * 真实对话模式下隐藏「原始整段回复」（已入库供上下文），改由行为层数据呈现。
     */
    private suspend fun buildInitialDisplay(
        convId: Long,
        realConv: Boolean,
        messages: List<Message>
    ): List<ChatMessage> {
        val items = mutableListOf<Pair<Long, ChatMessage>>()
        messages
            .filter { it.role == "user" || (it.role == "assistant" && !it.hidden) }
            .forEach { m ->
                items += Pair(
                    m.createdAt,
                    ChatMessage(m.role, m.content, m.reasoningContent, m.batchId, id = m.id)
                )
            }
        if (realConv) {
            val behaviors = localRepo.getCompletedBehaviors(convId)
            shownBehaviorIds += behaviors.map { it.behaviorId }
            behaviors.forEach { b ->
                items += Pair(b.excuTime, behaviorToChatMessage(b))
            }
        }
        return items.sortedBy { it.first }.map { it.second }
    }

    private val shownBehaviorIds = mutableSetOf<String>()

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
