package com.anchat.data.repository

import android.net.Uri
import com.anchat.data.config.ConfigManager
import com.anchat.data.config.ModelConfig
import com.anchat.data.config.ModelProviders
import com.anchat.data.remote.DeepSeekApi
import com.anchat.data.remote.ModelInfo
import kotlinx.coroutines.flow.Flow

class SettingsRepository(
    private val api: DeepSeekApi,
    private val configManager: ConfigManager
) {
    fun getConfigDisplayPath(): String = configManager.displayPath
    fun isSafMode(): Boolean = configManager.isSafMode
    fun setSafTreeUri(uri: Uri) { configManager.setSafTreeUri(uri); configManager.resync() }
    fun resetToDefault() { configManager.resetToDefault(); configManager.resync() }

    // ─── 模型（存于配置文件，含各自的 apiKey / apiUrl） ───

    fun observeModels(): Flow<List<ModelConfig>> = configManager.modelsFlow
    fun observeDefaultModelId(): Flow<String?> = configManager.defaultModelIdFlow
    fun getDefaultModelId(): String? = configManager.defaultModelIdFlow.value
    fun observeChatModelId(): Flow<String?> = configManager.chatModelIdFlow
    fun observeRealConversationModelId(): Flow<String?> = configManager.realConversationModelIdFlow
    fun getChatModelId(): String? = configManager.getChatModelId()
    fun getRealConversationModelId(): String? = configManager.getRealConversationModelId()
    fun setChatModelId(id: String?) = configManager.setChatModelId(id)
    fun setRealConversationModelId(id: String?) = configManager.setRealConversationModelId(id)
    fun getModelConfig(id: String): ModelConfig? = configManager.getModel(id)
    fun getModels(): List<ModelConfig> = configManager.getModels()
    fun setDefaultModel(id: String) = configManager.setDefaultModel(id)
    fun removeModel(id: String) = configManager.removeModel(id)

    /**
     * 保存一条模型。唯一性 = 服务方 + 模型名：同一组合传 existingId 走更新（沿用旧引用键，
     * 不破坏配置/Room 里已存的选择）；不同服务方同名则生成带前缀的新 id 共存。
     * modelName 单独存真正传给 API 的值，URL 由提供方内置。
     */
    fun addModel(provider: String, apiKey: String, model: String, existingId: String? = null) {
        configManager.addModels(
            listOf(
                ModelConfig(
                    id = existingId ?: ModelProviders.nextModelId(configManager.getModels(), provider, model),
                    name = model,
                    modelName = model,
                    apiKey = apiKey,
                    apiUrl = ModelProviders.baseUrl(provider).orEmpty(),
                    provider = provider
                )
            )
        )
    }

    /** 旧版「拉取模型列表」入口，UI 已不再调用，保留避免多余改动 */
    fun fetchModels(apiKey: String, baseUrl: String): Flow<List<ModelInfo>> =
        api.getModels(apiKey, baseUrl)
}
