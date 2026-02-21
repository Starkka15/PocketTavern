package com.pockettavern.app.data.local

import android.content.Context
import com.pockettavern.app.domain.model.ConnectionProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionProfileStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val file: File get() = File(context.filesDir, "connection_profiles.json")

    fun loadProfiles(): List<ConnectionProfile> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = json.parseToJsonElement(file.readText()).jsonArray
            arr.map { parseProfile(it.jsonObject) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveProfiles(profiles: List<ConnectionProfile>) {
        val arr: JsonArray = buildJsonArray {
            profiles.forEach { p ->
                add(buildJsonObject {
                    put("id", p.id)
                    put("name", p.name)
                    put("mainApi", p.mainApi)
                    put("textGenType", p.textGenType)
                    put("apiServer", p.apiServer)
                    put("chatCompletionSource", p.chatCompletionSource)
                    if (p.customUrl != null) put("customUrl", p.customUrl)
                    put("apiKey", p.apiKey)
                    put("model", p.model)
                    put("textGenPreset", p.textGenPreset)
                    put("instructPreset", p.instructPreset)
                    put("contextPreset", p.contextPreset)
                    put("syspromptPreset", p.syspromptPreset)
                })
            }
        }
        file.writeText(arr.toString())
    }

    private fun parseProfile(obj: kotlinx.serialization.json.JsonObject): ConnectionProfile {
        fun str(key: String, default: String = "") = obj[key]?.jsonPrimitive?.content ?: default
        return ConnectionProfile(
            id = str("id").ifBlank { UUID.randomUUID().toString() },
            name = str("name"),
            mainApi = str("mainApi", "textgenerationwebui"),
            textGenType = str("textGenType", "koboldcpp"),
            apiServer = str("apiServer", "http://127.0.0.1:5001"),
            chatCompletionSource = str("chatCompletionSource", "openai"),
            customUrl = obj["customUrl"]?.jsonPrimitive?.content,
            apiKey = str("apiKey"),
            model = str("model"),
            textGenPreset = str("textGenPreset"),
            instructPreset = str("instructPreset"),
            contextPreset = str("contextPreset"),
            syspromptPreset = str("syspromptPreset")
        )
    }
}
