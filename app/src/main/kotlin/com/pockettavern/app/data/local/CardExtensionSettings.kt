package com.pockettavern.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists enable/disable state for card-embedded extension scripts. */
@Singleton
class CardExtensionSettings @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("card_ext_settings", Context.MODE_PRIVATE)

    fun isEnabled(characterFile: String): Boolean =
        prefs.getBoolean("enabled_$characterFile", false)

    fun setEnabled(characterFile: String, enabled: Boolean) {
        prefs.edit().putBoolean("enabled_$characterFile", enabled).apply()
    }

    fun disable(characterFile: String) =
        prefs.edit().remove("enabled_$characterFile").apply()

    /** Call on app start — ensures no card extension is enabled unless a card is actively open. */
    fun disableAll() {
        prefs.edit().clear().apply()
    }
}
