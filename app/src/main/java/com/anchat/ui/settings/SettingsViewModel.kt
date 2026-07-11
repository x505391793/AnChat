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

    // ─── 通知推送开关 ───────────────────────
    private val _notificationsEnabled = MutableStateFlow(configManager.getNotificationsEnabled())
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        configManager.setNotificationsEnabled(enabled)
        _notificationsEnabled.value = enabled
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

    fun clearMessage() { _message.value = null }
}
