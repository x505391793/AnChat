package com.anchat.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the DeepSeek API key in Android's encrypted preferences so it never
 * lives in plaintext on disk. Replaces the old "environment variable" approach.
 */
class ApiKeyStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "anchat_secure",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getKey(): String? {
        val value = prefs.getString(KEY_API, null)
        return if (value.isNullOrBlank()) null else value
    }

    fun saveKey(key: String) {
        prefs.edit().putString(KEY_API, key.trim()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_API).apply()
    }

    companion object {
        private const val KEY_API = "deepseek_api_key"
    }
}
