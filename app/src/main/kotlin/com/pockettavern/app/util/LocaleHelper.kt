package com.pockettavern.app.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * App-level language override. Empty tag = follow system locale.
 * Read synchronously in [MainActivity.attachBaseContext], so this uses
 * SharedPreferences rather than DataStore.
 */
object LocaleHelper {
    private const val PREFS = "app_locale"
    private const val KEY_LANGUAGE = "language"

    /** BCP-47 tags the app ships resources for. "" = system default. */
    val AVAILABLE = listOf("", "en")
    // Add "zh-CN" here once values-zh-rCN/strings.xml lands.

    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "") ?: ""

    fun setLanguage(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, tag).apply()
    }

    /** Wrap a base context with the persisted locale override, if any. */
    fun wrap(base: Context): Context {
        val tag = getLanguage(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
