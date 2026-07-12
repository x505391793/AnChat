package com.anchat.data.repository

import android.net.Uri
import com.anchat.data.config.ConfigManager
import com.anchat.data.config.ModelConfig
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
    fun setDefaultModel(id: String) = configManager.setDefaultModel(id)
    fun addModels(list: List<ModelConfig>) = configManager.addModels(list)
    fun removeModel(id: String) = configManager.removeModel(id)

    /** 按 baseUrl + apiKey 拉取远端模型列表 */
    fun fetchModels(apiKey: String, baseUrl: String): Flow<List<ModelInfo>> =
        api.getModels(apiKey, baseUrl)
}
