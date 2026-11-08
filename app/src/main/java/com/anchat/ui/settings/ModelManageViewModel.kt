package com.anchat.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.anchat.AnChatApplication
import com.anchat.data.config.ModelProviders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 模型管理：选提供方 + 填 Key + 填模型名（原样作为 API 的 model 值）→ 保存为 1 条模型。
 * 聊天地址由 [ModelProviders] 内置，不再由用户输入。
 */
class ModelManageViewModel(app: Application) : AndroidViewModel(app) {

    private val anchatApp = app as AnChatApplication
    val repo = anchatApp.settingsRepository

    /** 已添加模型（来自配置文件，响应式） */
    val models = repo.observeModels()

    /** 默认模型 id（响应式） */
    val defaultModelId = repo.observeDefaultModelId()

    private val _provider = MutableStateFlow(ModelProviders.DEEPSEEK)
    val provider: StateFlow<String> = _provider.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _modelName = MutableStateFlow("")
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onProviderChange(id: String) { _provider.value = id }
    fun onKeyChange(text: String) { _apiKey.value = text }
    fun onModelNameChange(text: String) { _modelName.value = text }

    /**
     * 保存一条模型。唯一性 = 服务方 + 模型名：同一组合更新 Key；不同服务方同名可共存两条。
     */
    fun save() {
        val key = _apiKey.value.trim()
        val model = _modelName.value.trim()
        if (key.isBlank()) {
            _message.value = "请填写 API Key"
            return
        }
        if (model.isBlank()) {
            _message.value = "请填写模型名称"
            return
        }

        val p = _provider.value
        val existing = repo.getModels().firstOrNull { ModelProviders.sameModel(it, p, model) }
        repo.addModel(p, key, model, existing?.id)
        // 保留 Key，方便同一个 Key 连续添加多个模型
        _modelName.value = ""
        val label = ModelProviders.label(p)
        _message.value = if (existing != null) "已更新模型：$model [$label]"
        else "已保存模型：$model [$label]"
    }

    fun removeModel(id: String) {
        repo.removeModel(id)
        _message.value = "已删除模型：$id"
    }

    fun setDefault(id: String) {
        repo.setDefaultModel(id)
        _message.value = "已设为默认"
    }

    /** 设置全局「聊天 AI 模型」（下拉框一） */
    fun setChatModel(id: String?) {
        repo.setChatModelId(id)
        _message.value = if (id != null) "已设置聊天 AI 模型" else "已清除聊天 AI 模型"
    }

    /** 设置全局「真实对话管理 AI」（下拉框二）；设置后角色卡才可开启真实对话 */
    fun setRealConversationModel(id: String?) {
        repo.setRealConversationModelId(id)
        _message.value = if (id != null) "已设置真实对话管理 AI" else "已清除真实对话管理 AI"
    }

    fun clearMessage() { _message.value = null }
}
