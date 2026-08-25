package com.pockettavern.app.data.local

import android.content.Context
import com.pockettavern.app.R
import com.pockettavern.app.domain.model.QuickReplyButton
import com.pockettavern.app.domain.model.QuickReplyPreset
import com.pockettavern.app.domain.model.RegexRule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class ExtensionStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val extensionsDir: File
        get() = File(context.filesDir, "extensions").also { it.mkdirs() }

    private val quickReplyFile get() = File(extensionsDir, "quick_reply.json")
    private val regexFile get() = File(extensionsDir, "regex_rules.json")
    private val enabledFile get() = File(extensionsDir, "enabled.json")

    // ── Quick Reply ───────────────────────────────────────────────────────────

    fun loadQuickReplyPresets(): List<QuickReplyPreset> {
        if (!quickReplyFile.exists()) return listOf(defaultQuickReplyPreset())
        return try {
            val arr = json.parseToJsonElement(quickReplyFile.readText()).jsonArray
            arr.map { parseQuickReplyPreset(it.jsonObject) }
        } catch (_: Exception) {
            listOf(defaultQuickReplyPreset())
        }
    }

    fun saveQuickReplyPresets(presets: List<QuickReplyPreset>) {
        val arr = buildJsonArray {
            presets.forEach { preset ->
                add(buildJsonObject {
                    put("id", preset.id)
                    put("name", preset.name)
                    put("enabled", preset.enabled)
                    put("buttons", buildJsonArray {
                        preset.buttons.forEach { btn ->
                            add(buildJsonObject {
                                put("id", btn.id)
                                put("label", btn.label)
                                put("message", btn.message)
                                put("autoTriggers", buildJsonArray {
                                    btn.autoTriggers.forEach { add(JsonPrimitive(it)) }
                                })
                            })
                        }
                    })
                })
            }
        }
        quickReplyFile.writeText(arr.toString())
    }

    private fun parseQuickReplyPreset(obj: JsonObject): QuickReplyPreset {
        val buttons = obj["buttons"]?.jsonArray?.map { btnEl ->
            val btn = btnEl.jsonObject
            QuickReplyButton(
                id = btn["id"]?.jsonPrimitive?.content ?: java.util.UUID.randomUUID().toString(),
                label = btn["label"]?.jsonPrimitive?.content ?: "",
                message = btn["message"]?.jsonPrimitive?.content ?: "",
                autoTriggers = btn["autoTriggers"]?.jsonArray
                    ?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
            )
        } ?: emptyList()
        return QuickReplyPreset(
            id = obj["id"]?.jsonPrimitive?.content ?: java.util.UUID.randomUUID().toString(),
            name = obj["name"]?.jsonPrimitive?.content ?: "Preset",
            enabled = obj["enabled"]?.jsonPrimitive?.boolean ?: true,
            buttons = buttons
        )
    }

    private fun defaultQuickReplyPreset() = QuickReplyPreset(
        name = "Default",
        enabled = true,
        buttons = listOf(
            QuickReplyButton(label = context.getString(R.string.action_continue), message = context.getString(R.string.quick_reply_msg_continue)),
            QuickReplyButton(label = context.getString(R.string.quick_reply_label_go_on), message = context.getString(R.string.quick_reply_msg_go_on)),
            QuickReplyButton(label = context.getString(R.string.quick_reply_label_summarize), message = context.getString(R.string.quick_reply_msg_summarize))
        )
    )

    // ── Regex Rules ───────────────────────────────────────────────────────────

    fun loadRegexRules(): List<RegexRule> {
        if (!regexFile.exists()) return emptyList()
        return try {
            val arr = json.parseToJsonElement(regexFile.readText()).jsonArray
            arr.map { parseRegexRule(it.jsonObject) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRegexRules(rules: List<RegexRule>) {
        val arr = buildJsonArray {
            rules.forEach { rule ->
                add(buildJsonObject {
                    put("id", rule.id)
                    put("name", rule.name)
                    put("enabled", rule.enabled)
                    put("pattern", rule.pattern)
                    put("isRegex", rule.isRegex)
                    put("replacement", rule.replacement)
                    put("applyToOutput", rule.applyToOutput)
                    put("applyToInput", rule.applyToInput)
                    put("caseInsensitive", rule.caseInsensitive)
                })
            }
        }
        regexFile.writeText(arr.toString())
    }

    private fun parseRegexRule(obj: JsonObject): RegexRule = RegexRule(
        id = obj["id"]?.jsonPrimitive?.content ?: java.util.UUID.randomUUID().toString(),
        name = obj["name"]?.jsonPrimitive?.content ?: "",
        enabled = obj["enabled"]?.jsonPrimitive?.boolean ?: true,
        pattern = obj["pattern"]?.jsonPrimitive?.content ?: "",
        isRegex = obj["isRegex"]?.jsonPrimitive?.boolean ?: false,
        replacement = obj["replacement"]?.jsonPrimitive?.content ?: "",
        applyToOutput = obj["applyToOutput"]?.jsonPrimitive?.boolean ?: true,
        applyToInput = obj["applyToInput"]?.jsonPrimitive?.boolean ?: false,
        caseInsensitive = obj["caseInsensitive"]?.jsonPrimitive?.boolean ?: false
    )

    // ── Extension Enabled Flags ───────────────────────────────────────────────

    fun loadEnabledMap(): Map<String, Boolean> {
        if (!enabledFile.exists()) return emptyMap()
        return try {
            val obj = json.parseToJsonElement(enabledFile.readText()).jsonObject
            obj.entries.associate { (k, v) -> k to v.jsonPrimitive.boolean }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveEnabledMap(map: Map<String, Boolean>) {
        val obj = buildJsonObject { map.forEach { (k, v) -> put(k, v) } }
        enabledFile.writeText(obj.toString())
    }
}
