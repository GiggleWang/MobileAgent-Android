package com.mobileagent.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    }

    data class Settings(
        val provider: String = "openai",
        val endpoint: String = "",
        val apiKey: String = "",
        val model: String = "",
        val coordType: String = "absolute",
        val maxSteps: Int = 25,
        val enableNotetaker: Boolean = true
    )

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            provider = prefs[KEY_PROVIDER] ?: "openai",
            endpoint = prefs[KEY_ENDPOINT] ?: "",
            apiKey = prefs[KEY_API_KEY] ?: "",
            model = prefs[KEY_MODEL] ?: "",
            coordType = prefs[KEY_COORD_TYPE] ?: "absolute",
            maxSteps = prefs[KEY_MAX_STEPS] ?: 25,
            enableNotetaker = prefs[KEY_ENABLE_NOTETAKER] ?: true
        )
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
        }
    }
}
