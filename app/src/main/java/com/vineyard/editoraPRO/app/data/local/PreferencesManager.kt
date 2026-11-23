package com.vineyard.editoraPRO.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vineyard.editoraPRO.app.data.model.ApiKeyEntry
import com.vineyard.editoraPRO.app.data.model.GeminiModelInfo
import com.vineyard.editoraPRO.app.data.model.ProviderMode
import com.vineyard.editoraPRO.app.data.model.ThinkingLevel
import kotlinx.serialization.json.Json

class PreferencesManager(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs: SharedPreferences = createEncryptedOrFallbackPrefs(context.applicationContext)

    @Suppress("DEPRECATION")
    private fun createEncryptedOrFallbackPrefs(appContext: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE_ENCRYPTED,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Throwable) {
            appContext.getSharedPreferences(PREFS_FILE_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    fun getProviderMode(): ProviderMode =
        ProviderMode.fromStorage(prefs.getString(KEY_PROVIDER, ProviderMode.VERTEX_AI.storageId))

    fun setProviderMode(mode: ProviderMode) {
        prefs.edit().putString(KEY_PROVIDER, mode.storageId).apply()
    }

    fun getThinkingLevel(): ThinkingLevel =
        ThinkingLevel.fromWire(prefs.getString(KEY_THINKING_LEVEL, ThinkingLevel.DEFAULT.wireValue))

    fun setThinkingLevel(level: ThinkingLevel) {
        prefs.edit().putString(KEY_THINKING_LEVEL, level.wireValue).apply()
    }

    fun getExportFps(): Int = prefs.getInt(KEY_EXPORT_FPS, 30).let { if (it == 24) 24 else 30 }

    fun setExportFps(fps: Int) {
        prefs.edit().putInt(KEY_EXPORT_FPS, if (fps == 24) 24 else 30).apply()
    }

    fun getMaxCanvasEdge(): Int = prefs.getInt(KEY_MAX_CANVAS_EDGE, 1080).let { if (it == 720) 720 else 1080 }

    fun setMaxCanvasEdge(edge: Int) {
        prefs.edit().putInt(KEY_MAX_CANVAS_EDGE, if (edge == 720) 720 else 1080).apply()
    }

    fun getApiKeys(mode: ProviderMode): List<ApiKeyEntry> {
        val raw = prefs.getString(apiKeysKey(mode), null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ApiKeyEntry>>(raw) }.getOrDefault(emptyList())
    }

    fun saveApiKeys(mode: ProviderMode, keys: List<ApiKeyEntry>) {
        prefs.edit().putString(apiKeysKey(mode), json.encodeToString(keys)).apply()
    }

    fun getCycleIndex(mode: ProviderMode): Int =
        prefs.getInt(cycleIdxKey(mode), 0).coerceAtLeast(0)

    fun saveCycleIndex(mode: ProviderMode, index: Int) {
        prefs.edit().putInt(cycleIdxKey(mode), index.coerceAtLeast(0)).apply()
    }

    fun getModels(mode: ProviderMode): List<GeminiModelInfo> {
        val raw = prefs.getString(modelsKey(mode), null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<GeminiModelInfo>>(raw) }.getOrDefault(emptyList())
    }

    fun saveModels(mode: ProviderMode, models: List<GeminiModelInfo>) {
        prefs.edit().putString(modelsKey(mode), json.encodeToString(models)).apply()
    }

    fun getSelectedModel(mode: ProviderMode): String =
        prefs.getString(selectedModelKey(mode), "") ?: ""

    fun saveSelectedModel(mode: ProviderMode, modelName: String) {
        prefs.edit().putString(selectedModelKey(mode), modelName).apply()
    }

    private fun apiKeysKey(mode: ProviderMode) =
        if (mode == ProviderMode.AI_STUDIO) "avd_aistudio_api_keys_v1" else "avd_api_keys_v1"

    private fun cycleIdxKey(mode: ProviderMode) =
        if (mode == ProviderMode.AI_STUDIO) "avd_aistudio_cycle_idx_v1" else "avd_cycle_idx_v1"

    private fun modelsKey(mode: ProviderMode) =
        if (mode == ProviderMode.AI_STUDIO) "avd_aistudio_live_models_v1" else "avd_live_models_v1"

    private fun selectedModelKey(mode: ProviderMode) =
        if (mode == ProviderMode.AI_STUDIO) "avd_aistudio_selected_model_v1" else "avd_selected_model_v1"

    companion object {
        private const val PREFS_FILE_ENCRYPTED = "editora_pro_secure_prefs"
        private const val PREFS_FILE_FALLBACK = "editora_pro_prefs"
        private const val KEY_PROVIDER = "avd_api_provider_v1"
        private const val KEY_THINKING_LEVEL = "avd_aistudio_thinking_level_v1"
        private const val KEY_EXPORT_FPS = "avd_export_fps_v1"
        private const val KEY_MAX_CANVAS_EDGE = "avd_max_canvas_edge_v1"
    }
}
