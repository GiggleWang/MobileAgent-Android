package com.mobileagent.app.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

val LocalLocale = staticCompositionLocalOf { "system" }

object LocaleManager {
    var current by mutableStateOf("system")

    fun init(context: Context) {
        val sp = context.getSharedPreferences("language_prefs", Context.MODE_PRIVATE)
        current = sp.getString("language", "system") ?: "system"
    }

    fun setLocale(context: Context, language: String) {
        context.getSharedPreferences("language_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("language", language)
            .commit()
        current = language
    }
}

@Composable
fun LocaleProvider(content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val language = LocaleManager.current

    val localizedContext = remember(language) {
        if (language == "system") {
            baseContext
        } else {
            val locale = when (language) {
                "zh-CN" -> Locale.SIMPLIFIED_CHINESE
                "en" -> Locale.ENGLISH
                else -> Locale(language)
            }
            val config = Configuration(baseContext.resources.configuration)
            config.setLocale(locale)
            baseContext.createConfigurationContext(config)
        }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalLocale provides language
    ) {
        content()
    }
}
