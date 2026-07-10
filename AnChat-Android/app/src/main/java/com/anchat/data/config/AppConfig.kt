package com.anchat.data.config

import kotlinx.serialization.Serializable

/**
 * All AnChat settings in one place.
 * Stored as a JSON file on disk (see [ConfigManager]).
 *
 * Add new fields here as needed — they'll be picked up automatically
 * thanks to `ignoreUnknownKeys = true` in the JSON config, so old
 * config files won't break when you add fields.
 */
@Serializable
data class AppConfig(
    /** DeepSeek API key. Blank = not set. */
    val apiKey: String = "",
    /**
     * Override for the config file location.
     * Blank = use the default path inside the app's internal storage
     * (auto-cleaned on uninstall).
     * Set to an absolute path to use a custom location (e.g. external storage).
     */
    val configFilePath: String = "",
)
