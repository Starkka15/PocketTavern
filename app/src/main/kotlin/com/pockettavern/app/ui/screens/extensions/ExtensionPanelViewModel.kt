package com.pockettavern.app.ui.screens.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.extensions.ExtensionManager
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class ExtensionPanelViewModel @Inject constructor(
    val extensionManager: ExtensionManager,
    val localRepository: LocalRepository
) : ViewModel() {

    /** Full ST V2-formatted JSON array of local characters. */
    private val _localCharactersJson = MutableStateFlow("[]")
    val localCharactersJson: StateFlow<String> = _localCharactersJson.asStateFlow()

    /** ST-formatted worldinfo list: [{name:"..."},...] */
    private val _worldInfoListJson = MutableStateFlow("[]")
    val worldInfoListJson: StateFlow<String> = _worldInfoListJson.asStateFlow()

    /** Unique tag list collected from all characters. */
    private val _tagsJson = MutableStateFlow("[]")
    val tagsJson: StateFlow<String> = _tagsJson.asStateFlow()

    init {
        loadLocalData()
    }

    private fun loadLocalData() {
        viewModelScope.launch { refreshLocalData() }
    }

    /** Callable from both coroutine scope and runBlocking (bridge threads). */
    private suspend fun refreshLocalData() {
        loadLocalCharactersJson()
        loadWorldInfoList()
    }

    private suspend fun loadLocalCharactersJson() {
        try {
            when (val result = localRepository.getCharacters()) {
                is Result.Success -> {
                    val arr = JSONArray()
                    val allTags = mutableSetOf<String>()
                    result.data.forEach { char ->
                        arr.put(buildCharacterJson(char))
                        allTags.addAll(char.tags)
                    }
                    _localCharactersJson.value = arr.toString()
                    _tagsJson.value = JSONArray(allTags.toList()).toString()
                    DebugLogger.log("[ExtensionPanel] Local library: ${result.data.size} characters")
                }
                else -> {}
            }
        } catch (e: Exception) {
            DebugLogger.log("[ExtensionPanel] Failed to load local library: ${e.message}")
        }
    }

    private suspend fun loadWorldInfoList() {
        try {
            when (val result = localRepository.getWorldInfoList()) {
                is Result.Success -> {
                    val arr = JSONArray()
                    result.data.forEach { name ->
                        arr.put(JSONObject().apply {
                            put("name", name)
                            put("file_id", name)
                        })
                    }
                    _worldInfoListJson.value = arr.toString()
                }
                else -> {}
            }
        } catch (e: Exception) {
            DebugLogger.log("[ExtensionPanel] Failed to load worldinfo list: ${e.message}")
        }
    }

    /** Builds a full ST V2 character card JSON object from a PT Character. */
    private fun buildCharacterJson(char: Character): JSONObject {
        val avatarFile = char.avatar ?: "${char.name}.png"
        return JSONObject().apply {
            put("name", char.name)
            put("avatar", avatarFile)
            put("chat", "${char.name} - default.jsonl")
            put("date_added", 0L)
            put("date_last_chat", 0L)
            put("chat_size", 0)
            put("fav", char.isFavorite)
            put("tags", JSONArray(char.tags))
            put("data", JSONObject().apply {
                put("name", char.name)
                put("description", char.description)
                put("personality", char.personality)
                put("scenario", char.scenario)
                put("first_mes", char.firstMessage)
                put("mes_example", char.messageExample)
                put("creator_notes", char.creatorNotes)
                put("system_prompt", char.systemPrompt)
                put("post_history_instructions", char.postHistoryInstructions)
                put("tags", JSONArray(char.tags))
                put("alternate_greetings", JSONArray(char.alternateGreetings))
                put("character_version", "")
                put("extensions", JSONObject().apply {
                    put("talkativeness", char.talkativeness.toString())
                    put("fav", char.isFavorite)
                    put("world", char.attachedWorldInfo ?: "")
                    put("depth_prompt", JSONObject().apply {
                        put("prompt", char.depthPrompt)
                        put("depth", char.depthPromptDepth)
                        put("role", char.depthPromptRole)
                    })
                })
            })
        }
    }

    /**
     * Find a single character by avatar filename and return its ST V2 JSON.
     * Called from PanelBridge on a background thread (safe to call blocking).
     * Returns null if not found.
     */
    fun getCharacterJson(avatarUrl: String): String? {
        val charsJson = _localCharactersJson.value
        return try {
            val arr = JSONArray(charsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val av = obj.optString("avatar", "")
                val name = obj.optString("name", "")
                if (av == avatarUrl || "$name.png" == avatarUrl || name == avatarUrl) {
                    return obj.toString()
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the chat list for a character by avatar filename.
     * Returns a JSON array string in ST format.
     * Safe to call blocking from a background thread.
     */
    fun getCharacterChatsJson(avatarUrl: String): String {
        val name = avatarUrl.removeSuffix(".png").removeSuffix(".card").removeSuffix(".json")
        return try {
            when (val result = runBlocking { localRepository.getCharacterChats(name) }) {
                is Result.Success -> {
                    val arr = JSONArray()
                    result.data.forEach { info ->
                        arr.put(JSONObject().apply {
                            put("file_name", info.fileName)
                            put("chat_metadata", JSONObject().apply {
                                put("create_date", info.lastModified)
                                put("last_mes", info.lastMessage ?: "")
                                put("chat_items", info.messageCount)
                            })
                        })
                    }
                    arr.toString()
                }
                else -> "[]"
            }
        } catch (e: Exception) {
            "[]"
        }
    }

    /**
     * Apply an ST-format edit request body JSON to a PT character.
     * Called blocking from PanelBridge background thread.
     * Returns true on success.
     */
    fun editCharacterFromJson(avatarUrl: String, bodyJson: String): Boolean {
        return try {
            val obj = org.json.JSONObject(bodyJson)
            // ST uses ch_name for the (possibly new) name; fall back to existing name from avatar
            val newName = obj.optString("ch_name", "").ifEmpty {
                avatarUrl.removeSuffix(".png")
            }
            val updated = com.pockettavern.app.domain.model.Character(
                name                   = newName,
                avatar                 = avatarUrl,
                description            = obj.optString("description", ""),
                personality            = obj.optString("personality", ""),
                scenario               = obj.optString("scenario", ""),
                firstMessage           = obj.optString("first_mes", ""),
                messageExample         = obj.optString("mes_example", ""),
                creatorNotes           = obj.optString("creator_notes", ""),
                systemPrompt           = obj.optString("system_prompt", ""),
                postHistoryInstructions = obj.optString("post_history_instructions", ""),
                tags                   = run {
                    val arr = obj.optJSONArray("tags")
                    if (arr != null) (0 until arr.length()).map { arr.getString(it) } else emptyList()
                },
                alternateGreetings     = run {
                    val arr = obj.optJSONArray("alternate_greetings")
                    if (arr != null) (0 until arr.length()).map { arr.getString(it) } else emptyList()
                }
            )
            val result = runBlocking { localRepository.editCharacter(avatarUrl, updated) }
            if (result is Result.Success) {
                runBlocking { refreshLocalData() }
                DebugLogger.log("[ExtensionPanel] Edited character: $avatarUrl")
                true
            } else false
        } catch (e: Exception) {
            DebugLogger.log("[ExtensionPanel] editCharacterFromJson failed: ${e.message}")
            false
        }
    }

    fun importCharacterFromBase64(
        base64: String,
        filename: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                localRepository.importCharacterCardBytes(bytes, filename)
                DebugLogger.log("[ExtensionPanel] Imported character: $filename")
                refreshLocalData()
                onResult(true)
            } catch (e: Exception) {
                DebugLogger.log("[ExtensionPanel] Import failed for $filename: ${e.message}")
                onResult(false)
            }
        }
    }
}
