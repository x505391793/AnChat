package com.anchat.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.anchat.AnChatApplication
import com.anchat.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val anchatApp = app as AnChatApplication
    private val repo: SettingsRepository = anchatApp.settingsRepository
    private val configManager = anchatApp.configManager

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // ─── 外观（深色模式） ───────────────────────
    private val _themeMode = MutableStateFlow(configManager.getThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        configManager.setThemeMode(mode)
        _themeMode.value = mode
    }

    // ─── Config path ───────────────────────────────────
    private val _configDisplayPath = MutableStateFlow(repo.getConfigDisplayPath())
    val configDisplayPath: StateFlow<String> = _configDisplayPath.asStateFlow()

    val isSafMode: Boolean = repo.isSafMode()

    fun setSafUri(uri: Uri) {
        repo.setSafTreeUri(uri)
        _configDisplayPath.value = repo.getConfigDisplayPath()
        _message.value = "配置路径已更改"
    }

    fun resetConfigPath() {
        repo.resetToDefault()
        _configDisplayPath.value = repo.getConfigDisplayPath()
        _message.value = "已恢复默认路径"
    }

    // ─── 主身份 ───────────────────────────────────────
    private val _defaultUserName = MutableStateFlow(configManager.getDefaultUserName())
    val defaultUserName: StateFlow<String> = _defaultUserName.asStateFlow()

    private val _defaultUserDescription = MutableStateFlow(configManager.getDefaultUserDescription())
    val defaultUserDescription: StateFlow<String> = _defaultUserDescription.asStateFlow()

    fun onUserNameChange(text: String) { _defaultUserName.value = text }
    fun onUserDescChange(text: String) { _defaultUserDescription.value = text }

    fun saveIdentity() {
        configManager.saveDefaultIdentity(
            userName = _defaultUserName.value,
            description = _defaultUserDescription.value,
            avatar = configManager.getDefaultUserAvatar()  // 头像暂不处理
        )
        _message.value = "主身份已保存"
    }

    fun clearMessage() { _message.value = null }
}
