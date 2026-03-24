package com.pockettavern.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsVoiceStorage @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("tts_voice_prefs", Context.MODE_PRIVATE)

    fun getVoiceId(characterFile: String): String? =
        prefs.getString("voice_$characterFile", null)

    fun setVoiceId(characterFile: String, voiceId: String) {
        prefs.edit().putString("voice_$characterFile", voiceId).apply()
    }

    fun getProviderOverride(characterFile: String): String? =
        prefs.getString("provider_$characterFile", null)

    fun setProviderOverride(characterFile: String, provider: String?) {
        if (provider != null) {
            prefs.edit().putString("provider_$characterFile", provider).apply()
        } else {
            prefs.edit().remove("provider_$characterFile").apply()
        }
    }

    fun clearVoice(characterFile: String) {
        prefs.edit()
            .remove("voice_$characterFile")
            .remove("provider_$characterFile")
            .apply()
    }
}
