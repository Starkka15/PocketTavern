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
    val AVAILABLE = listOf("", "en", "zh-CN")

    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "") ?: ""

    fun setLanguage(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, tag).apply()
    }

    /** The locale the user actually experiences: override if set, else system. */
    fun effectiveLocale(context: Context): Locale {
        val tag = getLanguage(context)
        return if (tag.isEmpty()) Locale.getDefault() else Locale.forLanguageTag(tag)
    }

    /** English name of the effective language, for use inside LLM prompts. */
    fun targetLanguageName(context: Context): String {
        val loc = effectiveLocale(context)
        return when {
            loc.language == "zh" && loc.country != "TW" && loc.country != "HK" -> "Simplified Chinese"
            loc.language == "zh" -> "Traditional Chinese"
            else -> loc.getDisplayLanguage(Locale.ENGLISH).ifBlank { loc.toLanguageTag() }
        }
    }

    /** Localized name of the effective language, for showing in the UI. */
    fun displayLanguageName(context: Context): String {
        val loc = effectiveLocale(context)
        return loc.getDisplayName(loc).replaceFirstChar { it.uppercase(loc) }
            .ifBlank { loc.toLanguageTag() }
    }

    /**
     * Prompt directive telling the model which language to respond in,
     * or null when the effective language is English (models default to it).
     */
    fun responseLanguageDirective(context: Context): String? {
        val loc = effectiveLocale(context)
        if (loc.language == "en" || loc.language.isEmpty()) return null
        val name = targetLanguageName(context)
        return "[IMPORTANT]: Write ALL narration and dialogue in $name. " +
            "Respond only in $name, regardless of the language used elsewhere in this prompt."
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
