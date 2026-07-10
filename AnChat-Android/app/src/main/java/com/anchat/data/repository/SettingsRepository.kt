package com.anchat.data.repository

import com.anchat.data.local.dao.ModelDao
import com.anchat.data.local.entity.ModelEntity
import com.anchat.data.remote.DeepSeekApi
import kotlinx.coroutines.flow.Flow

/**
 * Settings: API key storage + model list management.
 */
class SettingsRepository(
    private val api: DeepSeekApi,
    private val apiKeyStore: ApiKeyStore,
    private val modelDao: ModelDao
) {
    fun getApiKey(): String? = apiKeyStore.getKey()
    fun saveApiKey(key: String) = apiKeyStore.saveKey(key)
    fun clearApiKey() = apiKeyStore.clear()

    fun observeModels(): Flow<List<ModelEntity>> = modelDao.observeAll()
    suspend fun getDefaultModel(): ModelEntity? = modelDao.getDefault()
    suspend fun setDefaultModel(id: String) {
        modelDao.clearDefault()
        modelDao.setDefault(id)
    }

    /** Refresh the model list from the API; falls back to the bundled list on failure. */
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
