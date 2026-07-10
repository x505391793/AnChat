package com.anchat.data.repository

import android.net.Uri
import com.anchat.data.config.ConfigManager
import com.anchat.data.local.dao.ModelDao
import com.anchat.data.local.entity.ModelEntity
import com.anchat.data.remote.DeepSeekApi
import kotlinx.coroutines.flow.Flow

class SettingsRepository(
    private val api: DeepSeekApi,
    private val configManager: ConfigManager,
    private val modelDao: ModelDao
) {
    fun getApiKey(): String? = configManager.getApiKey()
    fun saveApiKey(key: String) = configManager.saveApiKey(key)
    fun clearApiKey() = configManager.clearApiKey()

    fun getConfigDisplayPath(): String = configManager.displayPath
    fun isSafMode(): Boolean = configManager.isSafMode
    fun setSafTreeUri(uri: Uri) = configManager.setSafTreeUri(uri)
    fun resetToDefault() = configManager.resetToDefault()

    fun observeModels(): Flow<List<ModelEntity>> = modelDao.observeAll()
    suspend fun getDefaultModel(): ModelEntity? = modelDao.getDefault()
    suspend fun setDefaultModel(id: String) {
        modelDao.clearDefault()
        modelDao.setDefault(id)
    }

    fun refreshModels(apiKey: String): Flow<List<ModelEntity>> {
        return kotlinx.coroutines.flow.flow {
            val remote = api.getModels(apiKey)
            remote.collect { list ->
                if (list.isNotEmpty()) {
                    val hadDefault = modelDao.getDefault()?.id
                    modelDao.clear()
                    modelDao.insertAll(
                        list.map { m ->
                            ModelEntity(
                                id = m.id,
                                name = m.name,
                                description = "",
                                isDefault = m.id == (hadDefault ?: list.first().id)
                            )
                        }
                    )
                }
                emit(modelDao.getAll())
            }
        }
    }
}
