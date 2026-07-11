package com.anchat

import android.app.Application
import com.anchat.data.config.ConfigManager
import com.anchat.data.config.ModelConfig
import com.anchat.data.local.AppDatabase
import com.anchat.data.remote.DeepSeekApi
import com.anchat.data.remote.DeepSeekConstants
import com.anchat.data.repository.ChatRepository
import com.anchat.data.repository.LocalRepository
import com.anchat.data.repository.SettingsRepository

/**
 * Application-level container that wires up the local database, the DeepSeek
 * API client, the config manager and the repositories. Screens reach it
 * through [com.anchat.LocalApp].
 */
class AnChatApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }

    private val deepSeekApi: DeepSeekApi by lazy { DeepSeekApi() }
    val configManager: ConfigManager by lazy { ConfigManager(this) }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(deepSeekApi, database.conversationDao(), database.messageDao())
    }
    val localRepository: LocalRepository by lazy {
        LocalRepository(database.conversationDao(), database.messageDao(), database.characterDao())
    }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(deepSeekApi, configManager)
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
    }
}
