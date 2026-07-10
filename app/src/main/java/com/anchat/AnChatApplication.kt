package com.anchat

import android.app.Application
import com.anchat.data.config.ConfigManager
import com.anchat.data.local.AppDatabase
import com.anchat.data.local.entity.ModelEntity
import com.anchat.data.remote.DeepSeekApi
import com.anchat.data.remote.DeepSeekConstants
import com.anchat.data.repository.ChatRepository
import com.anchat.data.repository.LocalRepository
import com.anchat.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application-level container that wires up the local database, the DeepSeek
 * API client, the config manager and the repositories. Screens reach it
 * through [LocalApp].
 */
class AnChatApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }

    private val deepSeekApi: DeepSeekApi by lazy { DeepSeekApi() }
    val configManager: ConfigManager by lazy { ConfigManager(this) }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(deepSeekApi, database.conversationDao(), database.messageDao())
    }
    val localRepository: LocalRepository by lazy {
        LocalRepository(database.conversationDao(), database.messageDao(), database.characterDao())
    }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(deepSeekApi, configManager, database.modelDao())
    }

    override fun onCreate() {
        super.onCreate()
        // Seed the bundled model list on first launch (user can refresh from API later).
        CoroutineScope(Dispatchers.IO).launch {
            if (database.modelDao().count() == 0) {
                database.modelDao().insertAll(
                    DeepSeekConstants.MODELS.mapIndexed { index, m ->
                        ModelEntity(
                            id = m.id,
                            name = m.name,
                            description = m.description,
                            isDefault = index == 0
                        )
                    }
                )
            }
        }
    }
}
