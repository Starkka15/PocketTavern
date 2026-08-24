package com.pockettavern.app.data.local

import android.content.Context
import com.pockettavern.app.domain.model.ContextTemplate
import com.pockettavern.app.domain.model.InstructTemplate
import com.pockettavern.app.domain.model.OaiPreset
import com.pockettavern.app.domain.model.OaiPromptOrderItem
import com.pockettavern.app.domain.model.SystemPromptPreset
import com.pockettavern.app.domain.model.TextGenPreset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// Loads presets from bundled assets (assets/presets/{type}/name.json, read-only)
// and user overrides in files/presets/{type}/name.json (read/write).
// User files shadow bundled files with the same name.
@Singleton
class PresetStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val presetsDir: File
        get() = File(context.filesDir, "presets").also { it.mkdirs() }

    // --- Instruct Templates ---

    suspend fun listInstructTemplates(): List<String> = withContext(Dispatchers.IO) {
        mergedNames("instruct")
    }

    suspend fun loadInstructTemplate(name: String): InstructTemplate? = withContext(Dispatchers.IO) {
        readPreset("instruct", name)?.let { obj -> parseInstructTemplate(name, obj) }
    }

    suspend fun saveInstructTemplate(name: String, template: InstructTemplate) = withContext(Dispatchers.IO) {
        val data = mapOf(
            "name" to name,
            "input_sequence" to template.inputSequence,
            "input_suffix" to template.inputSuffix,
            "output_sequence" to template.outputSequence,
            "output_suffix" to template.outputSuffix,
            "first_output_sequence" to template.firstOutputSequence,
            "last_output_sequence" to template.lastOutputSequence,
            "system_sequence" to template.systemSequence,
            "system_suffix" to template.systemSuffix,
            "stop_sequence" to template.stopSequence,
            "system_prompt" to template.systemPrompt
        )
        writeUserPreset("instruct", name, data)
    }

    // --- Context Templates ---

    suspend fun listContextTemplates(): List<String> = withContext(Dispatchers.IO) {
        mergedNames("context")
    }

    suspend fun loadContextTemplate(name: String): ContextTemplate? = withContext(Dispatchers.IO) {
        readPreset("context", name)?.let { obj -> parseContextTemplate(name, obj) }
    }

    // --- System Prompts ---

    suspend fun listSystemPrompts(): List<String> = withContext(Dispatchers.IO) {
        mergedNames("sysprompt")
    }

    suspend fun loadSystemPrompt(name: String): SystemPromptPreset? = withContext(Dispatchers.IO) {
        readPreset("sysprompt", name)?.let { obj ->
            SystemPromptPreset(
                name = name,
                content = obj["content"]?.jsonPrimitive?.contentOrNull ?: ""
            )
        }
    }

    suspend fun saveSystemPrompt(name: String, content: String) = withContext(Dispatchers.IO) {
        writeUserPreset("sysprompt", name, mapOf("name" to name, "content" to content))
    }

    // --- TextGen Presets ---

    suspend fun listTextGenPresets(): List<String> = withContext(Dispatchers.IO) {
        mergedNames("textgen")
    }

    suspend fun loadTextGenPreset(name: String): TextGenPreset? = withContext(Dispatchers.IO) {
        readPreset("textgen", name)?.let { obj -> parseTextGenPreset(name, obj) }
    }

    suspend fun saveTextGenPreset(name: String, preset: TextGenPreset) = withContext(Dispatchers.IO) {
        val dir = File(presetsDir, "textgen").also { it.mkdirs() }
        val map: Map<String, JsonElement> = buildMap {
            put("name", JsonPrimitive(name))
            put("max_new_tokens", JsonPrimitive(preset.maxNewTokens ?: 200))
            put("min_tokens", JsonPrimitive(preset.minTokens))
            put("truncation_length", JsonPrimitive(preset.truncationLength))
            put("temp", JsonPrimitive(preset.temperature))
            put("top_p", JsonPrimitive(preset.topP))
            put("top_k", JsonPrimitive(preset.topK))
            put("top_a", JsonPrimitive(preset.topA))
            put("min_p", JsonPrimitive(preset.minP))
            put("typical", JsonPrimitive(preset.typicalP))
            put("tfs", JsonPrimitive(preset.tfs))
            put("rep_pen", JsonPrimitive(preset.repPen))
            put("rep_pen_range", JsonPrimitive(preset.repPenRange))
            put("rep_pen_slope", JsonPrimitive(preset.repPenSlope))
            put("frequency_penalty", JsonPrimitive(preset.frequencyPenalty))
            put("presence_penalty", JsonPrimitive(preset.presencePenalty))
            put("dry_multiplier", JsonPrimitive(preset.dryMultiplier))
            put("dry_base", JsonPrimitive(preset.dryBase))
            put("dry_allowed_length", JsonPrimitive(preset.dryAllowedLength))
            put("dry_penalty_last_n", JsonPrimitive(preset.dryPenaltyLastN))
            put("mirostat", JsonPrimitive(preset.mirostatMode))
            put("mirostat_tau", JsonPrimitive(preset.mirostatTau))
            put("mirostat_eta", JsonPrimitive(preset.mirostatEta))
            put("xtc_threshold", JsonPrimitive(preset.xtcThreshold))
            put("xtc_probability", JsonPrimitive(preset.xtcProbability))
            put("skew", JsonPrimitive(preset.skew))
            put("smoothing_factor", JsonPrimitive(preset.smoothingFactor))
            put("smoothing_curve", JsonPrimitive(preset.smoothingCurve))
            put("guidance_scale", JsonPrimitive(preset.guidanceScale))
            put("add_bos_token", JsonPrimitive(preset.addBosToken))
            put("ban_eos_token", JsonPrimitive(preset.banEosToken))
            put("skip_special_tokens", JsonPrimitive(preset.skipSpecialTokens))
        }
        File(dir, "$name.json").writeText(json.encodeToString(JsonObject(map)))
    }

    suspend fun deleteTextGenPreset(name: String) = withContext(Dispatchers.IO) {
        deleteUserPreset("textgen", name)
    }

    // --- OAI Presets (chat completion) ---

    suspend fun listOaiPresets(): List<String> = withContext(Dispatchers.IO) {
        mergedNames("oai")
    }

    suspend fun loadOaiPreset(name: String): OaiPreset? = withContext(Dispatchers.IO) {
        readPreset("oai", name)?.let { obj -> parseOaiPreset(name, obj) }
    }

    suspend fun saveOaiPreset(name: String, preset: OaiPreset) = withContext(Dispatchers.IO) {
        val dir = File(presetsDir, "oai").also { it.mkdirs() }
        val orderArray = JsonArray(preset.promptOrder.map { item ->
            val map = buildMap<String, JsonElement> {
                put("id", JsonPrimitive(item.id))
                put("enabled", JsonPrimitive(item.enabled))
                if (item.customLabel != null) put("label", JsonPrimitive(item.customLabel))
                if (item.content != null) put("content", JsonPrimitive(item.content))
                if (item.role != "system") put("role", JsonPrimitive(item.role))
                if (item.injectionPosition != 0) put("injection_position", JsonPrimitive(item.injectionPosition))
                if (item.injectionDepth != 4) put("injection_depth", JsonPrimitive(item.injectionDepth))
            }
            JsonObject(map)
        })
        val map: Map<String, JsonElement> = buildMap {
            put("name", JsonPrimitive(name))
            put("temperature", JsonPrimitive(preset.temperature))
            put("temperature_enabled", JsonPrimitive(preset.temperatureEnabled))
            put("top_p", JsonPrimitive(preset.topP))
            put("top_p_enabled", JsonPrimitive(preset.topPEnabled))
            put("top_k", JsonPrimitive(preset.topK))
            put("top_k_enabled", JsonPrimitive(preset.topKEnabled))
            put("max_tokens", JsonPrimitive(preset.maxTokens))
            put("max_tokens_enabled", JsonPrimitive(preset.maxTokensEnabled))
            put("frequency_penalty", JsonPrimitive(preset.frequencyPenalty))
            put("frequency_penalty_enabled", JsonPrimitive(preset.frequencyPenaltyEnabled))
            put("presence_penalty", JsonPrimitive(preset.presencePenalty))
            put("presence_penalty_enabled", JsonPrimitive(preset.presencePenaltyEnabled))
            put("repetition_penalty", JsonPrimitive(preset.repetitionPenalty))
            put("repetition_penalty_enabled", JsonPrimitive(preset.repetitionPenaltyEnabled))
            put("min_p", JsonPrimitive(preset.minP))
            put("min_p_enabled", JsonPrimitive(preset.minPEnabled))
            put("top_a", JsonPrimitive(preset.topA))
            put("top_a_enabled", JsonPrimitive(preset.topAEnabled))
            put("context_size", JsonPrimitive(preset.contextSize))
            put("context_size_enabled", JsonPrimitive(preset.contextSizeEnabled))
            put("seed", JsonPrimitive(preset.seed))
            put("seed_enabled", JsonPrimitive(preset.seedEnabled))
            put("prompt_order", orderArray)
        }
        File(dir, "$name.json").writeText(json.encodeToString(JsonObject(map)))
    }

    /**
     * Parse a SillyTavern OAI preset JSON (the Default.json format with `chat_completion_source`,
     * `prompts` array, `prompt_order` array) into a PocketTavern OaiPreset.
     * Sampling params are mapped directly; prompt content is mapped from the prompts array.
     */
    suspend fun importStOaiPreset(name: String, jsonText: String): OaiPreset = withContext(Dispatchers.IO) {
        val obj = json.decodeFromString<JsonObject>(jsonText)
        fun float(key: String, default: Float = 0f) = obj[key]?.jsonPrimitive?.floatOrNull ?: default
        fun int(key: String, default: Int = 0) = obj[key]?.jsonPrimitive?.intOrNull ?: default

        // Step 1: Build a lookup map from ST identifier → prompt data.
        // This covers both built-in (e.g. "main", "jailbreak") and UUID custom prompts.
        data class StPromptData(
            val name: String,
            val content: String,
            val isMarker: Boolean,
            val role: String,
            val injectionPosition: Int,
            val injectionDepth: Int
        )
        val stPromptMap = mutableMapOf<String, StPromptData>()
        val stPromptsArray = obj["prompts"]?.jsonArray ?: JsonArray(emptyList())
        stPromptsArray.forEach { el ->
            val p = el.jsonObject
            val stId = p["identifier"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val content = p["content"]?.jsonPrimitive?.contentOrNull ?: ""
            val isMarker = p["marker"]?.jsonPrimitive?.booleanOrNull ?: false
            val pName = p["name"]?.jsonPrimitive?.contentOrNull ?: stId
            val role = p["role"]?.jsonPrimitive?.contentOrNull ?: "system"
            val injectionPosition = p["injection_position"]?.jsonPrimitive?.intOrNull ?: 0
            val injectionDepth = p["injection_depth"]?.jsonPrimitive?.intOrNull ?: 4
            stPromptMap[stId] = StPromptData(pName, content, isMarker, role, injectionPosition, injectionDepth)
        }

        // Step 2: Resolve the best prompt_order to use.
        // prompt_order can be:
        //   A) Array of {character_id, order:[...]} objects  → pick the entry with the most items
        //   B) Flat array of {identifier, enabled} objects   → use directly (exported prompt list)
        val promptOrderArray = obj["prompt_order"]?.jsonArray
        val orderArray: kotlinx.serialization.json.JsonArray? = if (promptOrderArray != null) {
            val first = promptOrderArray.firstOrNull()?.jsonObject
            if (first?.containsKey("character_id") == true || first?.containsKey("order") == true) {
                // Format A: per-character orders — use the one with the most entries
                promptOrderArray
                    .maxByOrNull { it.jsonObject["order"]?.jsonArray?.size ?: 0 }
                    ?.jsonObject?.get("order")?.jsonArray
            } else {
                // Format B: flat list
                promptOrderArray
            }
        } else null

        val orderedItems = mutableListOf<OaiPromptOrderItem>()
        val seenOurIds = mutableSetOf<String>()
        val usedStIds = mutableSetOf<String>() // track which stIds were placed via the order

        if (orderArray != null) {
            for (el in orderArray) {
                val o = el.jsonObject
                val stId = o["identifier"]?.jsonPrimitive?.contentOrNull ?: continue
                val orderEnabled = o["enabled"]?.jsonPrimitive?.booleanOrNull ?: true

                val ourId = OaiPromptOrderItem.stIdentifierToId(stId)
                val stData = stPromptMap[stId]
                val isMarkerBlock = stData?.isMarker ?: (ourId in OaiPromptOrderItem.MARKER_IDS)
                val role = stData?.role ?: "system"
                val injPos = stData?.injectionPosition ?: 0
                val injDepth = stData?.injectionDepth ?: 4

                val item: OaiPromptOrderItem = when {
                    ourId in OaiPromptOrderItem.MARKER_IDS ->
                        OaiPromptOrderItem(ourId, orderEnabled)
                    OaiPromptOrderItem.defaultOrder().any { it.id == ourId } ->
                        OaiPromptOrderItem(ourId, orderEnabled, content = stData?.content ?: "",
                            role = role, injectionPosition = injPos, injectionDepth = injDepth)
                    stData != null && !isMarkerBlock ->
                        OaiPromptOrderItem("custom_$stId", orderEnabled, stData.name, stData.content,
                            role, injPos, injDepth)
                    else -> continue
                }

                if (seenOurIds.add(item.id)) {
                    orderedItems.add(item)
                    usedStIds.add(stId)
                }
            }
        }

        // Step 3: Add any prompts from the prompts array that weren't placed by the order.
        // These get appended as disabled blocks so the user can see and enable them.
        stPromptMap.forEach { (stId, stData) ->
            if (stId in usedStIds) return@forEach
            if (stData.isMarker) return@forEach
            val ourId = OaiPromptOrderItem.stIdentifierToId(stId)
            if (ourId in OaiPromptOrderItem.MARKER_IDS) return@forEach

            val item = if (OaiPromptOrderItem.defaultOrder().any { it.id == ourId }) {
                OaiPromptOrderItem(ourId, enabled = false, content = stData.content,
                    role = stData.role, injectionPosition = stData.injectionPosition, injectionDepth = stData.injectionDepth)
            } else {
                OaiPromptOrderItem("custom_$stId", enabled = false, stData.name, stData.content,
                    stData.role, stData.injectionPosition, stData.injectionDepth)
            }
            if (seenOurIds.add(item.id)) orderedItems.add(item)
        }

        // Step 4: Append any built-in defaults not yet present (ensures all built-ins always exist)
        OaiPromptOrderItem.defaultOrder().forEach { defaultItem ->
            if (defaultItem.id !in seenOurIds) {
                orderedItems.add(defaultItem)
                seenOurIds.add(defaultItem.id)
            }
        }

        OaiPreset(
            name = name,
            temperature = float("temperature", 1.0f),
            temperatureEnabled = true,
            topP = float("top_p", 1.0f),
            topPEnabled = false,
            topK = int("top_k", 0),
            topKEnabled = false,
            maxTokens = int("openai_max_tokens", 300).coerceIn(1, 32768),
            maxTokensEnabled = true,
            frequencyPenalty = float("frequency_penalty", 0f),
            frequencyPenaltyEnabled = false,
            presencePenalty = float("presence_penalty", 0f),
            presencePenaltyEnabled = false,
            repetitionPenalty = float("repetition_penalty", 1.0f),
            repetitionPenaltyEnabled = false,
            minP = float("min_p", 0f),
            minPEnabled = false,
            topA = float("top_a", 0f),
            topAEnabled = false,
            contextSize = int("openai_max_context", 4095).coerceIn(512, 131072),
            // ST always applies openai_max_context — imported presets get trimming on
            contextSizeEnabled = true,
            seed = int("seed", -1),
            seedEnabled = false,
            promptOrder = orderedItems
        )
    }

    suspend fun deleteOaiPreset(name: String) = withContext(Dispatchers.IO) {
        deleteUserPreset("oai", name)
    }

    // --- Delete ---

    suspend fun deleteUserPreset(type: String, name: String) = withContext(Dispatchers.IO) {
        File(File(presetsDir, type), "$name.json").delete()
    }

    /** Returns true if a user-created file exists for the given preset (i.e., it can be deleted). */
    fun isUserPreset(type: String, name: String): Boolean =
        File(File(presetsDir, type), "$name.json").exists()

    // --- Internals ---

    private fun mergedNames(type: String): List<String> {
        val bundled = try {
            context.assets.list("presets/$type")
                ?.filter { it.endsWith(".json") }
                ?.map { it.removeSuffix(".json") }
                ?: emptyList()
        } catch (e: Exception) { emptyList() }

        val user = File(presetsDir, type)
            .takeIf { it.exists() }
            ?.listFiles { f -> f.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()

        return (user + bundled).distinct().sorted()
    }

    private fun readPreset(type: String, name: String): JsonObject? {
        val userFile = File(File(presetsDir, type), "$name.json")
        if (userFile.exists()) {
            return try { json.decodeFromString<JsonObject>(userFile.readText()) } catch (e: Exception) { null }
        }
        return try {
            context.assets.open("presets/$type/$name.json").use { stream ->
                json.decodeFromString<JsonObject>(stream.bufferedReader().readText())
            }
        } catch (e: Exception) { null }
    }

    private fun writeUserPreset(type: String, name: String, data: Map<String, String>) {
        val dir = File(presetsDir, type).also { it.mkdirs() }
        val jsonElements: Map<String, JsonElement> = data.mapValues { entry -> JsonPrimitive(entry.value) }
        File(dir, "$name.json").writeText(json.encodeToString(JsonObject(jsonElements)))
    }

    private fun parseInstructTemplate(name: String, obj: JsonObject): InstructTemplate {
        fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull ?: ""
        fun bool(key: String) = obj[key]?.jsonPrimitive?.booleanOrNull ?: false
        return InstructTemplate(
            name = str("name").ifBlank { name },
            systemPrompt = str("system_prompt"),
            inputSequence = str("input_sequence"),
            inputSuffix = str("input_suffix"),
            outputSequence = str("output_sequence"),
            outputSuffix = str("output_suffix"),
            firstOutputSequence = str("first_output_sequence"),
            lastOutputSequence = str("last_output_sequence"),
            systemSequence = str("system_sequence"),
            systemSuffix = str("system_suffix"),
            stopSequence = str("stop_sequence"),
            separatorSequence = str("separator_sequence"),
            wrap = bool("wrap")
        )
    }

    private fun parseContextTemplate(name: String, obj: JsonObject): ContextTemplate {
        fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull ?: ""
        return ContextTemplate(
            name = str("name").ifBlank { name },
            storyString = str("story_string"),
            chatStart = str("chat_start"),
            exampleSeparator = str("example_separator")
        )
    }

    private fun parseOaiPreset(name: String, obj: JsonObject): OaiPreset {
        fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull ?: ""
        fun float(key: String, default: Float = 0f) = obj[key]?.jsonPrimitive?.floatOrNull ?: default
        fun int(key: String, default: Int = 0) = obj[key]?.jsonPrimitive?.intOrNull ?: default
        fun bool(key: String, default: Boolean = false) = obj[key]?.jsonPrimitive?.booleanOrNull ?: default

        // Migration: old format stored mainPrompt as top-level "main_prompt" string
        val legacyMainPrompt = obj["main_prompt"]?.jsonPrimitive?.contentOrNull
        val legacyMainEnabled = obj["main_prompt_enabled"]?.jsonPrimitive?.booleanOrNull ?: true

        val storedOrder = obj["prompt_order"]?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val enabled = o["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
            val customLabel = o["label"]?.jsonPrimitive?.contentOrNull
            val content = o["content"]?.jsonPrimitive?.contentOrNull
            val role = o["role"]?.jsonPrimitive?.contentOrNull ?: "system"
            val injectionPosition = o["injection_position"]?.jsonPrimitive?.intOrNull ?: 0
            val injectionDepth = o["injection_depth"]?.jsonPrimitive?.intOrNull ?: 4
            OaiPromptOrderItem(id, enabled, customLabel, content, role, injectionPosition, injectionDepth)
        } ?: emptyList()

        // Apply legacy migration: if main_prompt existed at top level and the stored order
        // item has no content yet, inject the legacy value
        val migratedOrder = if (legacyMainPrompt != null) {
            storedOrder.map { item ->
                if (item.id == "main_prompt" && item.content.isNullOrEmpty())
                    item.copy(content = legacyMainPrompt, enabled = legacyMainEnabled)
                else item
            }
        } else storedOrder

        val promptOrder = OaiPromptOrderItem.mergeWithDefault(migratedOrder)

        return OaiPreset(
            name = str("name").ifBlank { name },
            temperature = float("temperature", 1.0f),
            temperatureEnabled = bool("temperature_enabled", true),
            topP = float("top_p", 1.0f),
            topPEnabled = bool("top_p_enabled", false),
            topK = int("top_k", 0),
            topKEnabled = bool("top_k_enabled", false),
            maxTokens = int("max_tokens", 500),
            maxTokensEnabled = bool("max_tokens_enabled", true),
            frequencyPenalty = float("frequency_penalty", 0f),
            frequencyPenaltyEnabled = bool("frequency_penalty_enabled", false),
            presencePenalty = float("presence_penalty", 0f),
            presencePenaltyEnabled = bool("presence_penalty_enabled", false),
            repetitionPenalty = float("repetition_penalty", 1.0f),
            repetitionPenaltyEnabled = bool("repetition_penalty_enabled", false),
            minP = float("min_p", 0f),
            minPEnabled = bool("min_p_enabled", false),
            topA = float("top_a", 0f),
            topAEnabled = bool("top_a_enabled", false),
            contextSize = int("context_size", 4096),
            contextSizeEnabled = bool("context_size_enabled", false),
            seed = int("seed", -1),
            seedEnabled = bool("seed_enabled", false),
            promptOrder = promptOrder
        )
    }

    private fun parseTextGenPreset(name: String, obj: JsonObject): TextGenPreset {
        fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull ?: ""
        fun float(key: String, default: Float = 0f) = obj[key]?.jsonPrimitive?.floatOrNull ?: default
        fun int(key: String, default: Int = 0) = obj[key]?.jsonPrimitive?.intOrNull ?: default
        fun bool(key: String, default: Boolean = false) = obj[key]?.jsonPrimitive?.booleanOrNull ?: default
        return TextGenPreset(
            name = str("name").ifBlank { name },
            maxNewTokens = int("max_new_tokens", 300),
            minTokens = int("min_tokens", 0),
            truncationLength = int("truncation_length", 4096),
            temperature = float("temp", 0.7f),
            topP = float("top_p", 0.5f),
            topK = int("top_k", 40),
            topA = float("top_a", 0f),
            minP = float("min_p", 0f),
            typicalP = float("typical", 1.0f),
            tfs = float("tfs", 1.0f),
            repPen = float("rep_pen", 1.0f),
            repPenRange = int("rep_pen_range", 0),
            repPenSlope = float("rep_pen_slope", 1f),
            frequencyPenalty = float("frequency_penalty", 0f),
            presencePenalty = float("presence_penalty", 0f),
            dryMultiplier = float("dry_multiplier", 0f),
            dryBase = float("dry_base", 1.75f),
            dryAllowedLength = int("dry_allowed_length", 2),
            dryPenaltyLastN = int("dry_penalty_last_n", 0),
            mirostatMode = int("mirostat", 0),
            mirostatTau = float("mirostat_tau", 5.0f),
            mirostatEta = float("mirostat_eta", 0.1f),
            xtcThreshold = float("xtc_threshold", 0.1f),
            xtcProbability = float("xtc_probability", 0f),
            skew = float("skew", 0f),
            smoothingFactor = float("smoothing_factor", 0f),
            smoothingCurve = float("smoothing_curve", 1f),
            guidanceScale = float("guidance_scale", 1f),
            addBosToken = bool("add_bos_token", true),
            banEosToken = bool("ban_eos_token", false),
            skipSpecialTokens = bool("skip_special_tokens", true)
        )
    }
}
