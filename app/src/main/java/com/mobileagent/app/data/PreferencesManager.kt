package com.mobileagent.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_PROVIDER = stringPreferencesKey("provider")
        val KEY_ENDPOINT = stringPreferencesKey("endpoint")
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_MODEL = stringPreferencesKey("model")
        val KEY_COORD_TYPE = stringPreferencesKey("coord_type")
        val KEY_MAX_STEPS = intPreferencesKey("max_steps")
        val KEY_ENABLE_NOTETAKER = booleanPreferencesKey("enable_notetaker")
        val KEY_AGENT_MODE = intPreferencesKey("agent_mode")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_LOCAL_MODEL_ID = stringPreferencesKey("local_model_id")
    }

    data class Settings(
        val provider: String = "openai",
        val endpoint: String = "",
        val apiKey: String = "",
        val model: String = "",
        val coordType: String = "absolute",
        val maxSteps: Int = 25,
        val enableNotetaker: Boolean = true,
        val agentMode: Int = 1,
        val language: String = "system",
        val localModelId: String = "qwen3-vl-2b"
    )

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            provider = prefs[KEY_PROVIDER] ?: "openai",
            endpoint = prefs[KEY_ENDPOINT] ?: "",
            apiKey = prefs[KEY_API_KEY] ?: "",
            model = prefs[KEY_MODEL] ?: "",
            coordType = prefs[KEY_COORD_TYPE] ?: "absolute",
            maxSteps = prefs[KEY_MAX_STEPS] ?: 25,
            enableNotetaker = prefs[KEY_ENABLE_NOTETAKER] ?: true,
            agentMode = prefs[KEY_AGENT_MODE] ?: 1,
            language = prefs[KEY_LANGUAGE] ?: "system",
            localModelId = prefs[KEY_LOCAL_MODEL_ID] ?: "qwen3-vl-2b"
        )
    }

    fun getLanguageSync(): String {
        val sp = context.getSharedPreferences("language_prefs", Context.MODE_PRIVATE)
        return sp.getString("language", "system") ?: "system"
    }

    fun saveLanguageSync(language: String) {
        context.getSharedPreferences("language_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("language", language)
            .commit()
    }

    suspend fun saveSettings(settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PROVIDER] = settings.provider
            prefs[KEY_ENDPOINT] = settings.endpoint
            prefs[KEY_API_KEY] = settings.apiKey
            prefs[KEY_MODEL] = settings.model
            prefs[KEY_COORD_TYPE] = settings.coordType
            prefs[KEY_MAX_STEPS] = settings.maxSteps
            prefs[KEY_ENABLE_NOTETAKER] = settings.enableNotetaker
            prefs[KEY_AGENT_MODE] = settings.agentMode
            prefs[KEY_LANGUAGE] = settings.language
            prefs[KEY_LOCAL_MODEL_ID] = settings.localModelId
        }
    }
}
