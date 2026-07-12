package com.anchat.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anchat.AnChatApplication
import com.anchat.data.config.ModelConfig
import com.anchat.data.remote.DeepSeekConstants
import com.anchat.data.remote.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 模型管理：输入 apiKey + apiUrl → 拉取模型 → 多选 → 添加。
 * 添加后的模型（含 key / url）写入配置文件。
 */
class ModelManageViewModel(app: Application) : AndroidViewModel(app) {

    private val anchatApp = app as AnChatApplication
    val repo = anchatApp.settingsRepository

    /** 已添加模型（来自配置文件，响应式） */
    val models = repo.observeModels()

    /** 默认模型 id（响应式） */
    val defaultModelId = repo.observeDefaultModelId()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _apiUrl = MutableStateFlow(DeepSeekConstants.DEFAULT_BASE_URL)
    val apiUrl: StateFlow<String> = _apiUrl.asStateFlow()

    /** 拉取到的待选模型 */
    private val _fetched = MutableStateFlow<List<ModelInfo>>(emptyList())
    val fetched: StateFlow<List<ModelInfo>> = _fetched.asStateFlow()

    private val _isFetching = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = _isFetching.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onKeyChange(text: String) { _apiKey.value = text }
    fun onUrlChange(text: String) { _apiUrl.value = text }

    /** 步骤①：按 apiKey + apiUrl 拉取远端模型列表 */
    fun fetch() {
        val key = _apiKey.value.trim()
        val url = _apiUrl.value.trim()
        if (url.isBlank()) {
            _message.value = "请填写 API 地址"
            return
        }
        viewModelScope.launch {
            _isFetching.value = true
            try {
                val list = repo.fetchModels(key, url).first()
                _fetched.value = list
                _message.value = if (list.isEmpty()) {
                    "未获取到模型，请检查地址与 Key"
                } else {
                    "获取到 ${list.size} 个模型，请勾选后添加"
                }
            } catch (e: Exception) {
                _fetched.value = emptyList()
                _message.value = "拉取失败：${e.message}"
            } finally {
                _isFetching.value = false
            }
        }
    }

    /** 步骤②+③+④：把勾选的模型连同 key / url 写入配置文件 */
    fun addSelected(selectedIds: Set<String>) {
        val list = _fetched.value.filter { it.id in selectedIds }
        if (list.isEmpty()) {
            _message.value = "请先勾选模型"
            return
        }
        val key = _apiKey.value.trim()
        val url = _apiUrl.value.trim().removeSuffix("/")
        repo.addModels(
            list.map { m: ModelInfo ->
                ModelConfig(
                    id = m.id,
                    name = m.name,
                    description = m.description,
                    apiKey = key,
                    apiUrl = url
                )
            }
        )
        _fetched.value = emptyList()
        _message.value = "已添加 ${list.size} 个模型"
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
