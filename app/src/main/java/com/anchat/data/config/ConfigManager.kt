package com.anchat.data.config

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manages the AnChat configuration file.
 *
 * Two storage modes:
 * 1. **File mode** (default): config lives in `context.filesDir/anchat_config.json`.
 *    Android auto-deletes this on uninstall — zero residue.
 * 2. **SAF mode** (user-picked): config lives in a directory the user chose via
 *    the system directory picker. Uses DocumentFile + ContentResolver for I/O.
 *    Persistent URI permission is taken so the app can access it after restart.
 */
class ConfigManager(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** Bootstrap prefs — stores ONLY the SAF tree URI (or nothing = File mode). */
    private val bootstrapPrefs: SharedPreferences =
        context.getSharedPreferences("anchat_paths", Context.MODE_PRIVATE)

    /** Default config file: internal app storage. */
    private val defaultConfigFile: File = File(context.filesDir, CONFIG_FILENAME)

    val defaultConfigPath: String = defaultConfigFile.absolutePath

    /** SAF tree URI if the user picked a custom directory. Null = File mode. */
    val safTreeUri: Uri?
        get() = bootstrapPrefs.getString(KEY_SAF_TREE_URI, null)?.let { Uri.parse(it) }

    /** Whether we're using SAF mode. */
    val isSafMode: Boolean
        get() = safTreeUri != null

    /** Human-readable display path for the settings UI. */
    val displayPath: String
        get() {
            val uri = safTreeUri
            if (uri != null) {
                try {
                    val doc = DocumentFile.fromTreeUri(context, uri)
                    if (doc?.name != null) {
                        return "${doc.name}/$CONFIG_FILENAME"
                    }
                } catch (_: Exception) { }
                return uri.lastPathSegment?.replace(":", "/")
                    ?.plus("/$CONFIG_FILENAME") ?: uri.toString()
            }
            return defaultConfigPath
        }

    // ─── Load / Save ───────────────────────────────────────

    fun load(): AppConfig {
        return if (isSafMode) loadFromSaf() else loadFromFile()
    }

    fun save(config: AppConfig) {
        if (isSafMode) saveToSaf(config) else saveToFile(config)
    }

    // ─── File mode (default) ───────────────────────────────

    private fun loadFromFile(): AppConfig {
        if (!defaultConfigFile.exists()) {
            val default = AppConfig()
            saveToFile(default)
            return default
        }
        return try {
            json.decodeFromString<AppConfig>(defaultConfigFile.readText())
        } catch (_: Exception) {
            AppConfig()
        }
    }

    private fun saveToFile(config: AppConfig) {
        defaultConfigFile.parentFile?.mkdirs()
        defaultConfigFile.writeText(json.encodeToString(AppConfig.serializer(), config))
    }

    // ─── SAF mode (user-picked directory) ──────────────────

    private fun loadFromSaf(): AppConfig {
        val uri = safTreeUri ?: return AppConfig()
        try {
            val pickedDir = DocumentFile.fromTreeUri(context, uri)
            val configFileDoc = pickedDir?.findFile(CONFIG_FILENAME)

            if (configFileDoc == null || !configFileDoc.exists()) {
                val default = AppConfig()
                saveToSaf(default)
                return default
            }

            context.contentResolver.openInputStream(configFileDoc.uri)?.use { stream ->
                val text = stream.bufferedReader().readText()
                return json.decodeFromString<AppConfig>(text)
            }
        } catch (_: Exception) { /* SAF URI invalid / directory deleted → fallback */ }
        return AppConfig()
    }

    private fun saveToSaf(config: AppConfig) {
        val uri = safTreeUri ?: return saveToFile(config)
        try {
            val pickedDir = DocumentFile.fromTreeUri(context, uri)
            val existingFile = pickedDir?.findFile(CONFIG_FILENAME)
            val configFileDoc = existingFile
                ?: pickedDir?.createFile("application/json", CONFIG_FILENAME)

            if (configFileDoc != null) {
                context.contentResolver.openOutputStream(configFileDoc.uri)?.use { stream ->
                    stream.write(json.encodeToString(AppConfig.serializer(), config).toByteArray())
                }
            }
        } catch (_: Exception) { /* save failed silently */ }
    }

    // ─── API Key ───────────────────────────────────────────

    fun getApiKey(): String? {
        val key = load().apiKey
        return if (key.isBlank()) null else key
    }

    fun saveApiKey(key: String) {
        save(load().copy(apiKey = key.trim()))
    }

    fun clearApiKey() {
        save(load().copy(apiKey = ""))
    }

    // ─── Path management ───────────────────────────────────

    /**
     * Switch to a user-picked directory (SAF URI from directory picker).
     * Migrates existing config to the new location.
     */
    fun setSafTreeUri(newUri: Uri) {
        val oldConfig = load()

        // Take persistent permission so we can read/write after restart
        try {
            context.contentResolver.takePersistableUriPermission(
                newUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) { }

        // Release old SAF permission if there was one
        safTreeUri?.let { oldUri ->
            try {
                context.contentResolver.releasePersistableUriPermission(
                    oldUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
        }

        // Delete config from the old SAF location (if we were in SAF mode)
        if (isSafMode) {
            try {
                val oldDir = DocumentFile.fromTreeUri(context, safTreeUri!!)
                oldDir?.findFile(CONFIG_FILENAME)?.delete()
            } catch (_: Exception) { }
        }

        // Store new URI → now isSafMode becomes true
        bootstrapPrefs.edit()
            .putString(KEY_SAF_TREE_URI, newUri.toString())
            .apply()

        // Write config to the new SAF location
        saveToSaf(oldConfig)

        // Delete old internal-storage file if it existed
        if (defaultConfigFile.exists()) {
            defaultConfigFile.delete()
        }
    }

    /**
     * Reset to default internal-storage path.
     * Migrates config back, releases SAF permission.
     */
    fun resetToDefault() {
        val oldConfig = load()

        // Delete config from SAF location
        if (isSafMode) {
            try {
                val oldDir = DocumentFile.fromTreeUri(context, safTreeUri!!)
                oldDir?.findFile(CONFIG_FILENAME)?.delete()
            } catch (_: Exception) { }
        }

        // Release SAF permission
        safTreeUri?.let { uri ->
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
        }

        // Clear SAF URI from prefs → now isSafMode becomes false
        bootstrapPrefs.edit()
            .remove(KEY_SAF_TREE_URI)
            .apply()

        // Write config back to internal storage
        saveToFile(oldConfig)
    }

    companion object {
        private const val KEY_SAF_TREE_URI = "saf_tree_uri"
        private const val CONFIG_FILENAME = "anchat_config.json"
    }
}
