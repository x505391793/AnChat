package com.anchat.ui.chat

import android.app.Application
import android.util.Log
import android.widget.Toast
import kotlin.jvm.Volatile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anchat.AnChatApplication
import com.anchat.data.local.entity.CharacterEntity
import com.anchat.engine.core.contract.RealConvVersion
import com.anchat.data.local.entity.Conversation
import com.anchat.engine.core.ConversationEngine
import com.anchat.engine.core.contract.Behavior
import com.anchat.engine.core.contract.BehaviorType
import com.anchat.engine.core.contract.ConversationContext
import com.anchat.engine.core.contract.DecompositionSpec
import com.anchat.engine.core.contract.EngineEvent
import com.anchat.engine.core.contract.TurnInput
import com.anchat.push.ActiveConversation
import com.anchat.service.RequestForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 打字占位时长：每字毫秒（取折中值，后续可按手感调整）；最终再叠 ±10% 随机浮动。 */
private const val TYPING_PER_CHAR_MS = 50L

/** 自动触发计时：输入框失焦且键盘收起后等待 5 秒无变化则触发 */
private const val AUTO_TRIGGER_DELAY_MS = 5000L

data class ChatMessage(
    val role: String,
    val content: String,
    val reasoningContent: String? = null,
    val batchId: String? = null,
    /** 落库后的自增主键，供单条删除精准定位（未落库乐观消息为 -1） */
    val id: Long = -1L,
    /** 真实对话行为类型：speech / emotion / leave（为 null 表示普通消息） */
    val behaviorType: String? = null,
    /** leave 的离开时长文本 */
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

    /** 顶部「对方正在输入中……」标记：仅由行为层 speech 推送延时期间触发，纯前端态 */
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

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

    /** 排队模式：真实对话开启「且」开发者模式开启时，用户消息只进队列，等 trigger 打包发送。
     *  开发者模式关闭则视为普通对话，直接调用 API 发送（测试用的排队/触发/模拟推送全部停用）。 */
    val isQueueMode: StateFlow<Boolean> = combine(_profile, configManager.developerModeFlow) { profile, dev ->
        val v = (profile?.realConversation == true) && dev
        Log.d("ANCHAT_QUEUE", "realConversation=${profile?.realConversation} dev=$dev -> isQueueMode=$v")
        v
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ── 自动触发（排队模式）──
    /** true = 用户正在输入（输入框聚焦或键盘弹出）；false = 空闲 */
    private var inputEverFocused = false
    private var triggerJob: Job? = null
    /** 聊天页是否还活着（未 onCleared）。开发者模式测试气泡等纯 UI 行为依赖此标志，退出后不更新死 StateFlow；
     *  自动触发/flush 不依赖它，仍挂在 engineScope 跑。 */
    private var isScreenActive = true
    /** 内存中已排队（待推送）的用户消息计数，用于校验 DB 是否已落库最新数据 */
    @Volatile private var pendingQueueCount = 0
    /** 数据库未追上时的重试次数，避免无限重试 */
    @Volatile private var staleRetryCount = 0
    private val MAX_STALE_RETRY = 3

    /**
     * 由 UI 上报输入区活跃状态。
     * 活跃（聚焦/键盘弹出）→ 取消计时；空闲且为排队模式 → 5 秒后自动触发。
     */
    fun setInputActive(active: Boolean) {
        if (active) {
            inputEverFocused = true
            triggerJob?.cancel()
            triggerJob = null
            return
        }
        // 初始未聚焦过 / 非真实对话：不计时。
        // 自动触发是「真实对话」下的常驻功能，不绑定开发者模式；开发者模式只决定触发后是模拟还是真发。
        if (!inputEverFocused) return
        if (_profile.value?.realConversation != true) return
        triggerJob?.cancel()
        // 计时挂在应用级 engineScope：退出聊天页后 ViewModel 销毁也不会中断，
        // 保证「发送后立刻退出」仍能在 5 秒后自动打包发送（真实对话常驻功能）。
        triggerJob = anchatApp.engineScope.launch {
            delay(AUTO_TRIGGER_DELAY_MS)
            fireTrigger()
        }
    }

    /**
     * 统一触发入口：手动按钮与自动计时都走这里。
     * 开发者模式开启 = 测试模拟：只弹一句气泡「自动触发了推送」，不发送任何 API；
     * 开发者模式关闭 = 正常流程：打包排队消息直接调用 API 发送（flushQueue）。
     * 双重保障 1：用户没发过气泡（DB 队列为空）则直接进入推送任何东西。
     */
    fun fireTrigger() {
        val convId = conversationId ?: return
        // 挂在 engineScope：退出聊天页后仍能完成「打包→发 API→行为层拆解」，
        // 普通（非真实）对话不进此路径（setInputActive 已按 realConversation 拦截）。
        anchatApp.engineScope.launch(Dispatchers.IO) {
            // 双重保障 1：DB 中无排队气泡就不进入推送（含测试模拟）
            if (localRepo.getQueuedBehaviors(convId).isEmpty()) {
                Log.d("ANCHAT_TRIGGER", "fireTrigger: 无排队气泡，跳过触发")
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (configManager.getDeveloperMode()) {
                    // 测试模拟气泡：仅聊天页还活着时显示，退出后不刷死 StateFlow
                    if (isScreenActive) {
                        _messages.value = _messages.value + ChatMessage("system", "自动触发了推送")
                    }
                    return@withContext
                }
                flushQueue()
            }
        }
    }

    /** 当前对话真实 id 的响应式版本，供 UI 判断头像是否可点击进编辑 */
    private val _conversationId = MutableStateFlow<Long?>(if (convId >= 0) convId else null)
    val conversationIdFlow: StateFlow<Long?> = _conversationId.asStateFlow()

    /** 当前选中的主角色卡（仅作快照回退来源），null = 无角色（用主身份） */
    private var currentCharacter: CharacterEntity? = null

    /** 构造参数里的角色卡 id（构造参数非属性，单独存一份供 send 异步回退取卡） */
    private val characterIdArg: Long = characterId

    /**
     * 确保 currentCharacter 已就绪：init 的异步 setCharacter 可能尚未完成，
     * 此时从构造参数 characterIdArg 重新取出角色卡。避免「从角色卡发消息」时
     * currentCharacter 为 null 导致用户身份/角色信息被全局设置覆盖。
     */
    private suspend fun resolveCharacter(): CharacterEntity? {
        if (currentCharacter == null && characterIdArg >= 0) {
            currentCharacter = localRepo.getCharacter(characterIdArg)
        }
        return currentCharacter
    }

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
                        // 仅作「响应已到达 → 清 loading」信号；
                        // 真实气泡由行为层（BehaviorDue）驱动，不在此进展示列表。
                        _isLoading.value = false
                    }

                    is EngineEvent.BehaviorDue -> {
                        // 行为到点实时推送：去重 + 按对话隔离后追加（调度器直接发事件，
                        // 不依赖 Room Flow 失效重查，修复「退出重进才看得到」的断链）。
                        // conversationId 隔离：engineEvents 是全局通道，避免把别的对话的行为串到本对话。
                        val b = event.behavior
                        val cid = conversationId
                        if (cid != null && b.conversationId == cid.toString() && b.behaviorId !in shownBehaviorIds) {
                            shownBehaviorIds += b.behaviorId
                            when (b.type) {
                                BehaviorType.SPEECH -> {
                                    // speech：顶部显示「对方正在输入中……」，按字数预估 + 随机浮动后直接弹出真实气泡
                                    val real = behaviorToChatMessage(b)
                                    val base = (real.content.length * TYPING_PER_CHAR_MS)
                                    val r = 0.9 + Random.nextDouble() * 0.2
                                    val delayMs = (base * r).toLong()
                                    _isTyping.value = true
                                    viewModelScope.launch {
                                        delay(delayMs)
                                        _isTyping.value = false
                                        _messages.value = _messages.value + real
                                        localRepo.markBehaviorRead(b.behaviorId)
                                    }
                                }
                                else -> {
                                    // leave / emotion 不进打字占位，直接出
                                    _messages.value = _messages.value + behaviorToChatMessage(b)
                                    viewModelScope.launch(Dispatchers.IO) { localRepo.markBehaviorRead(b.behaviorId) }
                                }
                            }
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
                // 角色卡对话：按 characterId 恢复主角色卡（仅作快照回退）
                val conv = localRepo.getConversation(convId)
                val charId = conv?.characterId ?: -1L
                val character = if (charId >= 0) localRepo.getCharacter(charId) else null
                setCharacter(character)
                _title.value = character?.name ?: "AnChat"
                // 进入对话：若用户身份未在对话内主动改过（userIdentityOverridden != true），
                // 且快照里用户字段有空缺，则把「解析后的用户身份」（角色卡优先、否则全局主身份）
                // 烤进对话快照——此后对话框只依赖快照，不再读取全局设置。
                if (conv != null && conv.userIdentityOverridden != true) {
                    val seedName = character?.userName ?: configManager.getDefaultUserName().ifBlank { null }
                    val seedAvatar = character?.userAvatar ?: configManager.getDefaultUserAvatar().ifBlank { null }
                    val seedDesc = character?.userDescription ?: configManager.getDefaultUserDescription().ifBlank { null }
                    val filled = conv.copy(
                        userName = conv.userName?.ifBlank { null } ?: seedName,
                        userAvatar = conv.userAvatar?.ifBlank { null } ?: seedAvatar,
                        userDescription = conv.userDescription?.ifBlank { null } ?: seedDesc
                    )
                    if (filled != conv) localRepo.updateConversation(filled)
                }
                // 打开既有会话：标记当前会话 + 清未读（红点消失）
                ActiveConversation.set(convId)
                localRepo.markRead(convId)
                // 该对话下历史「已执行未读」行为翻为已读（status 1 → 2），避免重进后还停在 unread
                localRepo.markBehaviorsRead(convId)
                _conversationId.value = convId
                // _conversationId 已是 convId，上面的 observe 会接管 profile

                // 初始列表 = 该对话已完成行为（含 user 行 READ，按时间序合并）；
                // 后续行为由调度器经 engineEvents(BehaviorDue) 实时推送，无需在此挂观察
                val realConv = isRealConvConversation(convId)
                _messages.value = buildInitialDisplay(convId, realConv)
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
                        val greetingRawId = UUID.randomUUID().toString()
                        val greetingBehaviorId = localRepo.persistGreeting(newId, greeting, greetingRawId)
                        localRepo.markRead(newId) // 开场白立即展示，视为已读，避免列表误显红点
                        _messages.value = listOf(
                            ChatMessage("assistant", greeting, batchId = greetingRawId, behaviorId = greetingBehaviorId)
                        )
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
     * 用户身份优先级：对话内被改过（userIdentityOverridden）→ 角色卡自带 → 全局主身份兜底。
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
        // 用户身份优先级（未在本对话内主动改过时）：
        //   对话快照（创建时已把角色卡用户字段烤入）→ 角色卡实体 → 全局主身份兜底。
        // 关键：先读对话快照而非依赖 currentCharacter，避免「从角色卡发消息」时 currentCharacter
        // 尚未异步就绪导致用户身份整条回退到全局设置的竞态（首条消息误用全局身份）。
        val userOverridden = conv?.userIdentityOverridden == true
        val userName = if (userOverridden) {
            conv?.userName?.ifBlank { null }
        } else {
            conv?.userName?.ifBlank { null }
                ?: character?.userName?.ifBlank { null }
                ?: configManager.getDefaultUserName().ifBlank { null }
        }
        val userDescription = if (userOverridden) {
            conv?.userDescription?.ifBlank { null }
        } else {
            conv?.userDescription?.ifBlank { null }
                ?: character?.userDescription?.ifBlank { null }
                ?: configManager.getDefaultUserDescription().ifBlank { null }
        }
        val userAvatar = if (userOverridden) {
            conv?.userAvatar?.ifBlank { null }
        } else {
            conv?.userAvatar?.ifBlank { null }
                ?: character?.userAvatar?.ifBlank { null }
                ?: configManager.getDefaultUserAvatar().ifBlank { null }
        }
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
        charRealConvVersion = character?.realConvVersion ?: "v1",
        // 进入对话即把「解析后的用户身份」烤进快照：角色卡用户字段优先，否则全局主身份兜底。
        // 之后对话框只读取对话快照、不再回源全局设置，保证从角色卡发消息带上卡内用户设定，
        // 且普通对话也持有一份独立快照，可在对话内单独编辑而不受全局改动影响。
        userIdentityOverridden = false,
        userName = character?.userName ?: configManager.getDefaultUserName().ifBlank { null },
        userAvatar = character?.userAvatar ?: configManager.getDefaultUserAvatar().ifBlank { null },
        userDescription = character?.userDescription ?: configManager.getDefaultUserDescription().ifBlank { null }
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

        // 角色（AI 自身）名称：备注优先于角色卡名，确保 AI 知道自己并自称该名字
        val charName = profile.charRemark?.ifBlank { null } ?: profile.charName?.ifBlank { null }
        if (!charName.isNullOrBlank()) {
            parts.add("你的名字是${charName}。")
        }

        if (profile.systemPrompt.isNotBlank()) {
            parts.add(profile.systemPrompt)
        }

        val userName = profile.userName?.ifBlank { null }
        val userGender = profile.userGender?.ifBlank { null }
        val userDesc = profile.userDescription?.ifBlank { null }
        if (userName != null || userGender != null || userDesc != null) {
            val identityParts = mutableListOf<String>()
            if (userName != null) identityParts.add("我（用户）的名字叫${userName}。")
            if (userGender != null) identityParts.add("性别为${userGender}。")
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

        // 排队模式（真实对话开启即生效，不绑定开发者模式）：只写 behavior 进队列，不发 API——在此提前返回，不锁 loading。
        // 队列由自动触发（真实对话常驻功能）或开发者模式下的测试按钮打包 flush；
        // 开发者模式关闭时 flush 走真实 API，开启时为模拟推送。
        // 注意：模型与凭证解析已下沉到下方各分支内部（send 异步协程 / flushQueue），
        // 以便取到「角色卡」解析后的用户身份与模型，避免提前用全局默认模型误判。
        val queueMode = _profile.value?.realConversation == true
        if (queueMode) {
            // 内存计数 +1，供 flushQueue 校验 DB 是否已落库最新数据（双重保障 2）
            pendingQueueCount++
            staleRetryCount = 0
            val existingConvId = conversationId
            val msg = ChatMessage("user", text, behaviorId = UUID.randomUUID().toString())
            _messages.value = _messages.value + msg
            anchatApp.engineScope.launch {
                // 确保角色卡已就绪（init 异步 setCharacter 可能未结束），否则快照会烤入 null 用户字段
                val character = resolveCharacter()
                val convId = existingConvId ?: run {
                    val conv = snapshotFrom(character, character?.name ?: "新对话", character?.id ?: -1L)
                    val newId = localRepo.createConversation(conv)
                    conversationId = newId
                    _conversationId.value = newId
                    ActiveConversation.set(newId)
                    newId
                }
                val conv = localRepo.getConversation(convId)
                applyProfile(conv, character)
                localRepo.persistQueuedUserTurn(convId, text, System.currentTimeMillis())
            }
            return
        }

        // 进入异步前先锁住 loading，防止快速连点导致重复发送
        _isLoading.value = true
        _error.value = null
        RequestForegroundService.begin(getApplication())

        val existingConvId = conversationId

        // 引擎编排（建对话→存用户消息→engine.send）放到 Application 级 engineScope，
        // 不随 ViewModel 生命周期绑定——用户退出页面或退到桌面后请求仍能完成。
        anchatApp.engineScope.launch {
            // 确保角色卡已就绪（init 异步 setCharacter 可能未结束），否则快照会烤入 null 用户字段
            val character = resolveCharacter()
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
            val systemPrompt = buildSystemPrompt()

            // 解析模型与对应凭证（优先对话级/角色卡，回退全局）
            val modelId = _profile.value?.modelId ?: character?.modelId
                ?: settingsRepo.getDefaultModelId() ?: _defaultModel.value
            if (modelId.isBlank()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "请先在「模型管理」中添加模型", Toast.LENGTH_LONG).show()
                    _messages.value = _messages.value + ChatMessage("system", "请先在「模型管理」中添加模型")
                    _error.value = "请先在「模型管理」中添加模型"
                    _isLoading.value = false
                }
                return@launch
            }
            val modelCfg = settingsRepo.getModelConfig(modelId)
            val apiKey = modelCfg?.apiKey
            val apiUrl = modelCfg?.apiUrl
            if (apiKey.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    val label = modelCfg?.name ?: modelId
                    Toast.makeText(getApplication(), "模型「$label」未配置 API Key，请到模型管理添加", Toast.LENGTH_LONG).show()
                    _messages.value = _messages.value + ChatMessage("system", "模型「$label」未配置 API Key，请到模型管理添加")
                    _error.value = "模型「$label」未配置 API Key"
                    _isLoading.value = false
                }
                return@launch
            }

            // 用户消息统一写 raw_replies + behaviors（behaviors 即真正消息表），
            // 返回的 behaviorId 供长按删除精准定位这一句。
            val userRawId = UUID.randomUUID().toString()
            val userBehaviorId = try {
                localRepo.persistUserTurn(convId, text, userRawId, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.w(TAG, "保存用户消息失败: ${e.message}")
                null
            }
            _messages.value = _messages.value + ChatMessage("user", text, batchId = userRawId, behaviorId = userBehaviorId)

            // 真实对话：开关纯标志（版本决定是否真需要管理 AI）；v2 主请求直接产出 JSON，无需第二模型
            val decompSpec = buildDecompSpec()
            val realConv = _profile.value?.realConversation == true

            val context = ConversationContext(
                conversationId = convId.toString(),
                systemPrompt = systemPrompt,
                modelId = modelId,
                apiKey = apiKey,
                apiUrl = apiUrl ?: "",
                batchId = userRawId,
                realConversation = realConv,
                realConvVersion = conv?.charRealConvVersion ?: character?.realConvVersion ?: RealConvVersion.DEFAULT,
                decompSpec = decompSpec
            )
            engine.send(TurnInput(text), context)
        }
    }

    /** 打包当前对话全部排队消息，发 API。双重保障：空队列出；DB 未落库最新数据不出。 */
    fun flushQueue() {
        val convId = conversationId ?: return

        // 挂在 engineScope：退出聊天页后排队消息仍能打包发送、API 跑完（前台服务保活）。
        anchatApp.engineScope.launch(Dispatchers.IO) {
            // 确保角色卡已就绪（init 异步 setCharacter 可能未结束），否则用户身份/模型回落全局
            val character = resolveCharacter()
            // 解析模型凭证（同 send 逻辑）
            val modelId = _profile.value?.modelId ?: character?.modelId
                ?: settingsRepo.getDefaultModelId() ?: _defaultModel.value
            if (modelId.isBlank()) return@launch
            val modelCfg = settingsRepo.getModelConfig(modelId)
            val apiKey = modelCfg?.apiKey ?: return@launch
            val apiUrl = modelCfg?.apiUrl ?: return@launch

            // 双重保障 2：确认 DB 已落库最新用户数据再发请求
            val queuedNow = localRepo.getQueuedBehaviors(convId)
            if (queuedNow.isEmpty()) {
                Log.d("ANCHAT_FLUSH", "flushQueue: 队列已空，放弃请求")
                return@launch
            }
            if (pendingQueueCount > 0 && queuedNow.size < pendingQueueCount) {
                Log.w("ANCHAT_FLUSH", "flushQueue: DB 尚未落库最新数据(queued=${queuedNow.size} < pending=$pendingQueueCount)，放弃本次请求")
                if (staleRetryCount < MAX_STALE_RETRY) {
                    staleRetryCount++
                    withContext(Dispatchers.Main) {
                        // 稍后重试，给 DB 一点落库时间（最多 MAX_STALE_RETRY 次）
                        triggerJob?.cancel()
                        triggerJob = anchatApp.engineScope.launch { delay(800); fireTrigger() }
                    }
                } else {
                    Log.e("ANCHAT_FLUSH", "flushQueue: 重试次数耗尽，放弃（消息仍留库，下次触发将重试）")
                    staleRetryCount = 0
                }
                return@launch
            }
            // 通过校验：切回主线程发起 loading / 前台服务，再交 engineScope 真正发请求
            withContext(Dispatchers.Main) {
                _isLoading.value = true
                RequestForegroundService.begin(getApplication())
            }
            anchatApp.engineScope.launch {
                val (rawId, combinedText) = localRepo.flushQueue(convId) ?: run {
                    withContext(Dispatchers.Main) { _isLoading.value = false }
                    return@launch
                }
                // 发送成功：清零内存计数与重试计数
                pendingQueueCount = 0
                staleRetryCount = 0
                Log.d("ANCHAT_FLUSH", "flushQueue -> rawId=$rawId combinedText=[$combinedText]")
                val conv = localRepo.getConversation(convId)
                applyProfile(conv, character)
                val systemPrompt = buildSystemPrompt()
                val decompSpec = buildDecompSpec()
                val realConv = _profile.value?.realConversation == true

                val context = ConversationContext(
                    conversationId = convId.toString(),
                    systemPrompt = systemPrompt,
                    modelId = modelId,
                    apiKey = apiKey,
                    apiUrl = apiUrl,
                    batchId = rawId,
                    realConversation = realConv,
                    realConvVersion = conv?.charRealConvVersion ?: character?.realConvVersion ?: RealConvVersion.DEFAULT,
                    decompSpec = decompSpec
                )
                engine.send(TurnInput(combinedText), context)
            }
        }
    }

    /** 只删当前长按的那一条气泡（按 DB 主键），不波及同回合其它消息，也不取消正在进行的回合 */
    /** 只删当前长按的那一条气泡（按 behaviorId），不波及同回合其它消息，也不取消正在进行的回合 */
    fun deleteMessage(behaviorId: String) {
        if (behaviorId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            localRepo.deleteMessage(behaviorId)
        }
        _messages.value = _messages.value.filter { it.behaviorId != behaviorId }
    }

    fun clearError() {
        _error.value = null
    }

    // ─── 真实对话：行为层驱动 UI 的辅助 ───────────────

    /** 该对话是否处于真实对话模式（角色/对话开关开启） */
    private suspend fun isRealConvConversation(convId: Long): Boolean {
        val conv = localRepo.getConversation(convId) ?: return false
        val character = if (conv.characterId >= 0) localRepo.getCharacter(conv.characterId) else null
        val flag = conv.charRealConversation ?: character?.realConversation ?: false
        if (!flag) return false
        val version = conv.charRealConvVersion ?: character?.realConvVersion ?: RealConvVersion.DEFAULT
        // v1 仍需管理 AI（第二模型）；v2 主请求直接产出 JSON，无需管理 AI
        return if (version == RealConvVersion.V2) true
        else configManager.getRealConversationModelId() != null
    }

    /** 构建真实对话管理 AI 规格（第二模型）；未配置则返回 null */
    private fun buildDecompSpec(): DecompositionSpec? {
        val id = configManager.getRealConversationModelId() ?: return null
        val m = settingsRepo.getModelConfig(id) ?: return null
        if (m.apiKey.isBlank()) return null
        return DecompositionSpec(modelId = m.id, apiKey = m.apiKey, apiUrl = m.apiUrl)
    }

    /** 行为 → 聊天展示消息（speech/emotion/leave 由 ChatScreen 按 behaviorType 区分渲染） */
    private fun behaviorToChatMessage(b: Behavior): ChatMessage = ChatMessage(
        role = b.role,
        content = b.content,
        batchId = b.batchId,
        behaviorId = b.behaviorId,
        behaviorType = b.type.value,
        duration = b.duration
    )

    /**
     * 初始展示列表 = 该对话已完成行为（含 user 行 READ），按执行时间序合并。
     * 全部模式统一由行为层驱动 UI（气泡只来自 behaviors 表；user/assistant 同表）。
     */
    private suspend fun buildInitialDisplay(
        convId: Long,
        realConv: Boolean
    ): List<ChatMessage> {
        val items = mutableListOf<Pair<Long, ChatMessage>>()
        val behaviors = localRepo.getCompletedBehaviors(convId)
        // 真实对话模式：排队消息(status=-1)也要加载展示
        val queued = if (realConv) localRepo.getQueuedBehaviors(convId) else emptyList()
        shownBehaviorIds += behaviors.map { it.behaviorId }
        shownBehaviorIds += queued.map { it.behaviorId }
        behaviors.forEach { b ->
            items += Pair(b.excuTime, behaviorToChatMessage(b))
        }
        queued.forEach { b ->
            items += Pair(b.excuTime, behaviorToChatMessage(b))
        }
        return items.sortedBy { it.first }.map { it.second }
    }

    private val shownBehaviorIds = mutableSetOf<String>()

    fun startNewConversation() {
        conversationId = null
        _conversationId.value = null
        ActiveConversation.set(null)
        _messages.value = emptyList()
        pendingQueueCount = 0
        staleRetryCount = 0
    }

    override fun onCleared() {
        super.onCleared()
        // 注意：不取消 triggerJob —— 自动触发的计时/flush 挂在 engineScope，
        // 退出聊天页后仍需它把排队消息打包发送（真实对话常驻功能）。
        // 仅标记页已销毁，让开发者模式测试气泡等纯 UI 行为不再更新死 StateFlow；
        // 并清空「当前打开的会话」，使回复到达时正常弹系统通知。
        isScreenActive = false
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
