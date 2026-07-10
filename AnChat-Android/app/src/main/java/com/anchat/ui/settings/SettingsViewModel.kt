package com.anchat.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anchat.AnChatApplication
import com.anchat.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: SettingsRepository = (app as AnChatApplication).settingsRepository

    private val _apiKey = MutableStateFlow(repo.getApiKey() ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    val models = repo.observeModels()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onKeyChange(text: String) {
        _apiKey.value = text
    }

    fun saveKey() {
        val key = _apiKey.value.trim()
        if (key.isBlank()) {
            repo.clearApiKey()
            _message.value = "已清除 API Key"
        } else {
            repo.saveApiKey(key)
            _message.value = "API Key 已加密保存"
        }
    }

    fun refresh() {
        val key = repo.getApiKey()
        if (key == null) {
            _message.value = "请先填写 API Key"
            return
        }
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repo.refreshModels(key).collect { }
                _message.value = "模型列表已刷新"
            } catch (e: Exception) {
                _message.value = "刷新失败：${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun setDefault(id: String) {
        viewModelScope.launch { repo.setDefaultModel(id) }
    }

    fun clearMessage() {
        _message.value = null
    }
}
