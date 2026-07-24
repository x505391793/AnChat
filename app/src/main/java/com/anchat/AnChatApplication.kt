package com.anchat

import android.app.Application
import com.anchat.data.config.ConfigManager
import com.anchat.data.config.ModelConfig
import com.anchat.data.engine.EngineEngineSink
import com.anchat.data.engine.EnginePersistenceSink
import com.anchat.data.engine.EngineRequestSink
import com.anchat.data.local.AppDatabase
import com.anchat.data.remote.DeepSeekApi
import com.anchat.data.remote.DeepSeekConstants
import com.anchat.data.repository.LocalRepository
import com.anchat.data.repository.SettingsRepository
import com.anchat.push.PushNotifier
import com.anchat.service.RequestForegroundService
import com.anchat.engine.scheduler.BehaviorScheduler
import com.anchat.engine.analyzer.ReplyAnalyzer
import com.anchat.engine.core.ConversationEngine
import com.anchat.engine.core.contract.BehaviorType
import com.anchat.engine.core.contract.EngineEvent
import com.anchat.engine.sender.RequestBuilder
import com.anchat.engine.spi.EngineSink
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Application-level container that wires up the local database, the DeepSeek
 * API client, the config manager and the repositories. Screens reach it
 * through [com.anchat.LocalApp].
 */
class AnChatApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }

    private val deepSeekApi: DeepSeekApi by lazy { DeepSeekApi() }
    val configManager: ConfigManager by lazy { ConfigManager(this) }

    val localRepository: LocalRepository by lazy {
        LocalRepository(
            database,
            database.conversationDao(),
            database.characterDao(),
            database.rawReplyDao(),
            database.behaviorDao()
        )
    }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(deepSeekApi, configManager)
    }

    // ─── 对话处理机（Conversation Engine）装配 ───
    // 引擎为纯 Kotlin 模块；以下为对话模块侧的真实 spi 实现 + 门面。
    // 引擎协程异常必须就地吞掉并记录，绝不能冒泡到 Android 默认未捕获处理器杀进程。
    private val engineExceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.e("AnChatEngine", "引擎协程异常（已隔离，不影响主流程）", e)
    }
    val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + engineExceptionHandler)
    private val engineSinkImpl = EngineEngineSink()
    val engineSink: EngineSink = engineSinkImpl
    val engineEvents: Flow<EngineEvent> = engineSinkImpl.events
    val engine: ConversationEngine by lazy {
        val persistence = EnginePersistenceSink(
            database.conversationDao(),
            database.rawReplyDao(),
            database.behaviorDao()
        )
        val scheduler = BehaviorScheduler(engineScope, persistence, engineSinkImpl)
        ConversationEngine(
            scope = engineScope,
            requestSink = EngineRequestSink(deepSeekApi),
            persistenceSink = persistence,
            engineSink = engineSinkImpl,
            requestBuilder = RequestBuilder(),
            analyzer = ReplyAnalyzer(),
            scheduler = scheduler
        )
    }

    /** 系统通知推送器（依赖引擎事件流；受「通知推送」开关控制） */
    private val pushNotifier by lazy {
        PushNotifier(this, localRepository) { configManager.getNotificationsEnabled() }
    }

    override fun onCreate() {
        super.onCreate()
        // 把内置模型种子写入配置文件（用户可在模型管理中补充 Key / 删除）。
        // 注意：不再用「一次性标记」永久阻断播种——若配置文件被清空/重置
        // （换 SAF 路径、卸载重装残留旧标记等），模型列表会变空，
        // 导致角色卡「对话模型」下拉只剩「默认跟随全局」。
        // 改为「配置里没有任何模型时才补种」，保证下拉永远至少有内置模型可选。
        if (configManager.getModels().isEmpty()) {
            configManager.addModels(
                DeepSeekConstants.MODELS.map { m ->
                    ModelConfig(
                        id = m.id,
                        name = m.name,
                        description = m.description,
                        apiKey = "",
                        apiUrl = DeepSeekConstants.DEFAULT_BASE_URL
                    )
                }
            )
        }
        // 清理旧版遗留的一次性播种标记，避免歧义（已无任何副作用）。
        getSharedPreferences("anchat_bootstrap", MODE_PRIVATE)
            .edit().remove("models_seeded").apply()
        // 启动补播未完成行为（中断重启续播）
        engine.catchUp()
        // 系统通知通道 + 引擎事件 → 推送
        // 通知推送下沉到「行为层」（EngineEvent.BehaviorDue），使用拆解后的回复气泡文本，
        // 而非 EngineEvent.AssistantMessage 的原始 API 返回（真实对话下 raw.content 是 JSON）。
        // 这样不论是否开启真实对话，弹出的通知内容都是正确可读的回复。
        // AssistantMessage 仅作「网络层响应已到达」信号，用来结束前台服务。
        pushNotifier.ensureChannel()
        engineScope.launch {
            engineEvents.collect { event ->
                when (event) {
                    is EngineEvent.AssistantMessage -> {
                        // 响应已到达（网络层）：结束前台服务。通知推送交给行为层。
                        RequestForegroundService.finish(this@AnChatApplication)
                    }
                    is EngineEvent.BehaviorDue -> {
                        // 行为层：拆解后的回复气泡到达。仅对「助手发言(speech)」弹通知，
                        // emotion/leave 等是动作描述，不应作为通知预览文本。
                        val b = event.behavior
                        if (b.role == "assistant" && b.type == BehaviorType.SPEECH) {
                            val convId = b.conversationId.toLongOrNull() ?: return@collect
                            val conv = localRepository.getConversation(convId)
                            val title = conv?.charRemark?.takeIf { it.isNotBlank() }
                                ?: conv?.charName?.takeIf { it.isNotBlank() }
                                ?: conv?.title ?: "AnChat"
                            // 预览用拆解后的发言文本，真实对话下为可读回复而非原始 JSON。
                            val preview = b.content.take(50)
                            pushNotifier.onReply(convId, title, preview)
                        }
                    }
                    is EngineEvent.Error -> {
                        RequestForegroundService.finish(this@AnChatApplication)
                    }
                }
            }
        }
    }
}
