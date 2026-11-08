package com.anchat.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anchat.AnChatApplication
import com.anchat.data.backup.ImportPreview
import com.anchat.data.backup.ImportResult
import com.anchat.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val anchatApp = app as AnChatApplication
    private val repo: SettingsRepository = anchatApp.settingsRepository
    private val configManager = anchatApp.configManager
    private val backupRepository = anchatApp.backupRepository

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

    // ─── 开发者模式 ───────────────────────
    private val _developerMode = MutableStateFlow(configManager.getDeveloperMode())
    val developerMode: StateFlow<Boolean> = _developerMode.asStateFlow()

    fun setDeveloperMode(enabled: Boolean) {
        configManager.setDeveloperMode(enabled)
        _developerMode.value = enabled
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

    // ─── 数据备份与恢复 ─────────────────────────────
    /** 导出 / 导入进行中：UI 置灰并提示，避免重复点击 */
    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    /** 导入预检结果：非空时弹确认对话框 */
    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _backupBusy.value = true
            val result = runCatching { withContext(Dispatchers.IO) { backupRepository.exportTo(uri) } }
            _backupBusy.value = false
            _message.value = result.getOrNull()?.let { c ->
                "已导出：角色卡 ${c.characters} · 对话 ${c.conversations} · 消息 ${c.behaviors} · 头像 ${c.avatars}"
            } ?: "导出失败：${result.exceptionOrNull()?.message}"
        }
    }

    /** 选完备份文件先预检：全量比对，只报数不写库 */
    fun previewImport(uri: Uri) {
        viewModelScope.launch {
            _backupBusy.value = true
            val result = runCatching { withContext(Dispatchers.IO) { backupRepository.previewImport(uri) } }
            _backupBusy.value = false
            result.getOrNull()?.let { _importPreview.value = it }
                ?: run { _message.value = "导入失败：${result.exceptionOrNull()?.message}" }
        }
    }

    fun confirmImport(overwriteConfig: Boolean = false) {
        val preview = _importPreview.value ?: return
        _importPreview.value = null
        viewModelScope.launch {
            _backupBusy.value = true
            val result = runCatching { withContext(Dispatchers.IO) { backupRepository.applyImport(overwriteConfig) } }
            _backupBusy.value = false
            result.getOrNull()?.let { r ->
                // 设置项可能已被覆盖 → 重新同步本页开关的显示值
                _themeMode.value = configManager.getThemeMode()
                _notificationsEnabled.value = configManager.getNotificationsEnabled()
                _developerMode.value = configManager.getDeveloperMode()
                _message.value = buildImportMessage(r)
            } ?: run { _message.value = "导入失败：${result.exceptionOrNull()?.message}" }
        }
    }

    /** 把导入结果拼成一句提示：数据变更 + 设置是否覆盖 + 兜底快照提示 */
    private fun buildImportMessage(r: ImportResult): String {
        val dataPart = if (r.added.total == 0) {
            "数据已存在，无需导入（跳过 ${r.skipped.total} 条）"
        } else {
            "已导入：角色卡 ${r.added.characters} · 对话 ${r.added.conversations} · 消息 ${r.added.behaviors}（跳过重复 ${r.skipped.total} 条）"
        }
        // configChanged=true 表示用户显式勾选覆盖且备份设置确实不同；否则本地设置保持不动
        val cfgPart = if (r.configChanged) "；设置项已按备份覆盖" else ""
        // 兜底快照：导入前已自动保存当前全量数据，万一异常可再导入该文件恢复
        val safetyPart = if (r.safetyBackupPath != null) "；导入前已自动备份当前数据" else ""
        return dataPart + cfgPart + safetyPart
    }

    fun dismissImportPreview() {
        _importPreview.value = null
        backupRepository.clearPending()
    }
}
