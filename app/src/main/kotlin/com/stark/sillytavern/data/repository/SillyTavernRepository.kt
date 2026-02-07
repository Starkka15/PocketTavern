package com.stark.sillytavern.data.repository

import android.util.Base64
import android.util.Log
import com.stark.sillytavern.data.remote.api.SillyTavernApi
import com.stark.sillytavern.data.remote.dto.st.*
import com.stark.sillytavern.data.remote.interceptor.CsrfInterceptor
import com.stark.sillytavern.domain.model.*
import com.stark.sillytavern.domain.model.Character
import com.stark.sillytavern.domain.model.Persona
import com.stark.sillytavern.domain.model.PersonaPosition
import com.stark.sillytavern.domain.model.PersonaRole
import com.stark.sillytavern.domain.prompt.PromptBuilder
import com.stark.sillytavern.util.DebugLogger
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.stark.sillytavern.util.PngCharacterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SillyTavernRepository @Inject constructor(
    private val apiProvider: () -> SillyTavernApi,
    private val csrfInterceptor: CsrfInterceptor,
    private val settingsRepository: SettingsRepository,
    private val settingsDataStore: com.stark.sillytavern.data.local.SettingsDataStore,
    @Named("SillyTavern") private val okHttpClient: OkHttpClient
) {
    private var apiType: String = "koboldcpp"
    private var apiServer: String = "http://127.0.0.1:5001"
    private var mainApi: String = "textgenerationwebui"  // Main API type (textgenerationwebui, openai, kobold, etc.)
    private var chatCompletionSource: String = "openai"  // Chat completion source for OpenAI-style APIs
    private var openaiModel: String = ""  // Selected OpenAI model
    private var customOpenaiUrl: String = ""  // Custom OpenAI-compatible endpoint URL
    private var textGenSettings: TextGenSettings? = null
    private var currentPreset: TextGenPreset? = null

    // Formatting settings
    private var currentInstructTemplate: InstructTemplate? = null
    private var currentSystemPrompt: String = ""

    // OAI/Chat Completion settings (for NanoGPT, OpenAI, etc.)
    // Prompt storage - maps identifier to content
    private var oaiPrompts: MutableMap<String, String> = mutableMapOf()
    // Prompt order - list of (identifier, enabled) pairs
    private var oaiPromptOrder: List<Pair<String, Boolean>> = emptyList()
    // Sampler settings
    private var oaiTemperature: Float = 1.0f
    private var oaiTopP: Float = 1.0f
    private var oaiTopK: Int = 0
    private var oaiTopA: Float = 0f
    private var oaiMinP: Float = 0f
    private var oaiRepetitionPenalty: Float = 1f
    private var oaiFrequencyPenalty: Float = 0f
    private var oaiPresencePenalty: Float = 0f
    private var oaiMaxTokens: Int = 300
    private var oaiMaxContext: Int = 4095
    private var oaiStream: Boolean = true

    // Main APIs that use chat completions format (check main_api against this)
    private val chatCompletionMainApis = setOf("openai", "claude", "windowai", "openrouter", "ai21", "mistralai", "cohere", "perplexity", "groq", "makersuite", "01ai")

    // Text completion APIs (local LLMs) - always use text-completions endpoint
    private val textCompletionApis = setOf("textgenerationwebui", "kobold", "koboldhorde", "novel", "ooba")

    // Chat completion sources that definitely require chat-completions endpoint
    private val chatCompletionSources = setOf("openai", "nanogpt", "openrouter", "claude", "mistralai", "cohere", "perplexity", "groq", "makersuite", "ai21", "custom", "deepseek", "xai", "fireworks", "pollinations", "chutes", "electronhub")

    // Check if we should use chat completions based on main_api AND chat_completion_source
    // Text completion APIs (local LLMs) ALWAYS use text-completions, regardless of chat_completion_source
    private fun usesChatCompletions(): Boolean {
        // If main_api is explicitly a text completion API, always use text completions
        // This takes priority over any chat_completion_source setting
        if (mainApi.lowercase() in textCompletionApis) {
            return false
        }
        // For OpenAI-type APIs, check main_api or the specific chat_completion_source
        return mainApi.lowercase() in chatCompletionMainApis ||
               chatCompletionSource.lowercase() in chatCompletionSources
    }

    private val api: SillyTavernApi
        get() = apiProvider()

    suspend fun connect(): Result<Unit> {
        return try {
            // Clear any stale CSRF token and cookies before starting fresh connection
            csrfInterceptor.clearToken()
            com.stark.sillytavern.di.NetworkModule.clearCookies()

            // Get user credentials for potential MultiUserMode login
            val userSettings = settingsRepository.getSettings()

            // Fetch CSRF token first - needed for all requests including login
            val tokenResponse = api.getCsrfToken()
            csrfInterceptor.updateToken(tokenResponse.token)
            Log.d("STRepo", "Got CSRF token: ${tokenResponse.token?.take(10)}...")

            // Try MultiUserMode login if credentials are provided
            if (userSettings.username.isNotBlank() && userSettings.password.isNotBlank()) {
                try {
                    val loginRequest = com.stark.sillytavern.data.remote.dto.st.LoginRequest(
                        handle = userSettings.username,
                        password = userSettings.password
                    )
                    Log.d("STRepo", "Attempting MultiUserMode login for user: ${userSettings.username}")
                    val loginResponse = api.login(loginRequest)
                    val code = loginResponse.code()
                    Log.d("STRepo", "Login response code: $code")

                    if (!loginResponse.isSuccessful) {
                        val errorBody = loginResponse.errorBody()?.string()
                        Log.e("STRepo", "Login failed with code $code: $errorBody")

                        // 404 means MultiUserMode not enabled - continue with Basic Auth
                        // 401/403 means bad credentials in MultiUserMode
                        if (code == 401 || code == 403) {
                            return Result.Error(Exception("Login failed: Invalid username or password (HTTP $code)"))
                        } else if (code != 404) {
                            Log.w("STRepo", "Login returned $code, continuing anyway")
                        }
                    } else {
                        Log.d("STRepo", "MultiUserMode login successful")
                        // Refresh CSRF token after login - session may have new token
                        val newTokenResponse = api.getCsrfToken()
                        csrfInterceptor.updateToken(newTokenResponse.token)
                    }
                } catch (e: Exception) {
                    // Login endpoint might not exist (404) - that's fine, use Basic Auth
                    Log.d("STRepo", "Login attempt failed: ${e.message}, falling back to Basic Auth")
                }
            }

            // Fetch settings for API configuration
            val settings = api.getSettings()
            settings.textGenSettings?.let { tgSettings ->
                textGenSettings = tgSettings
                tgSettings.type?.let { apiType = it }
                tgSettings.serverUrls?.get(apiType)?.let { apiServer = it }
            }
            // Fallback to top-level settings
            settings.api_type?.let { apiType = it }
            settings.api_server?.let { apiServer = it }

            // Get main_api and chat_completion_source from full settings to determine endpoint type
            try {
                val fullSettings = api.getFullSettings(emptyMap())
                cachedFullSettings = fullSettings
                val settingsJson = parseSettingsString(fullSettings.settings)
                mainApi = getStringFromSettings(settingsJson, "main_api").ifBlank { "textgenerationwebui" }
                // chat_completion_source is nested inside oai_settings
                chatCompletionSource = getNestedString(settingsJson, "oai_settings", "chat_completion_source").ifBlank { "openai" }

                // Get model based on chat_completion_source - each source has its own model field
                openaiModel = when (chatCompletionSource.lowercase()) {
                    "nanogpt" -> getNestedString(settingsJson, "oai_settings", "nanogpt_model")
                    "openrouter" -> getNestedString(settingsJson, "oai_settings", "openrouter_model")
                    "mistralai" -> getNestedString(settingsJson, "oai_settings", "mistralai_model")
                    "cohere" -> getNestedString(settingsJson, "oai_settings", "cohere_model")
                    "perplexity" -> getNestedString(settingsJson, "oai_settings", "perplexity_model")
                    "groq" -> getNestedString(settingsJson, "oai_settings", "groq_model")
                    "deepseek" -> getNestedString(settingsJson, "oai_settings", "deepseek_model")
                    "claude" -> getNestedString(settingsJson, "oai_settings", "claude_model")
                    else -> getNestedString(settingsJson, "oai_settings", "openai_model").ifBlank {
                        getNestedString(settingsJson, "oai_settings", "model_openai_select")
                    }
                }

                customOpenaiUrl = getNestedString(settingsJson, "oai_settings", "custom_openai_url").ifBlank {
                    getNestedString(settingsJson, "oai_settings", "openai_custom_url")
                }
                Log.d("STRepo", "Main API: $mainApi, Chat Source: $chatCompletionSource, Model: $openaiModel, Custom URL: $customOpenaiUrl")
                Log.d("STRepo", "Using chat completions: ${usesChatCompletions()} (mainApi in textCompletionApis: ${mainApi.lowercase() in textCompletionApis})")
            } catch (e: Exception) {
                Log.w("STRepo", "Failed to get main_api: ${e.message}")
            }

            // Load current formatting settings (instruct template, system prompt)
            try {
                loadCurrentFormattingSettings()
            } catch (e: Exception) {
                Log.w("STRepo", "Failed to load formatting settings: ${e.message}")
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            csrfInterceptor.clearToken()
            Result.Error(Exception("Connection failed: ${e.message}", e))
        }
    }

    suspend fun disconnect() {
        csrfInterceptor.clearToken()
    }

    suspend fun getCharacters(): Result<List<Character>> {
        return try {
            val dtos = api.getAllCharacters()
            val characters = dtos.mapNotNull { dto ->
                val name = dto.name ?: dto.data?.name ?: return@mapNotNull null
                val extensions = dto.data?.extensions
                val characterBook = dto.data?.characterBook
                val depthPrompt = extensions?.depthPrompt
                Character(
                    name = name,
                    avatar = dto.avatar,
                    description = dto.description ?: dto.data?.description ?: "",
                    personality = dto.personality ?: dto.data?.personality ?: "",
                    scenario = dto.scenario ?: dto.data?.scenario ?: "",
                    firstMessage = dto.firstMes ?: dto.greeting ?: dto.data?.firstMes ?: "",
                    messageExample = dto.mesExample ?: dto.data?.mesExample ?: "",
                    creatorNotes = dto.creatorNotes ?: dto.data?.creatorNotes ?: "",
                    systemPrompt = dto.systemPrompt ?: dto.data?.systemPrompt ?: "",
                    tags = dto.tags ?: dto.data?.tags ?: emptyList(),
                    attachedWorldInfo = extensions?.world,
                    hasCharacterBook = characterBook != null,
                    characterBookEntryCount = characterBook?.entries?.size ?: 0,
                    depthPrompt = depthPrompt?.prompt ?: "",
                    depthPromptDepth = depthPrompt?.depth ?: 4,
                    talkativeness = extensions?.talkativeness?.toFloatOrNull() ?: 0.5f,
                    isFavorite = extensions?.fav ?: false
                )
            }
            Result.Success(characters)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to load characters: ${e.message}", e))
        }
    }

    suspend fun getCharacter(avatarUrl: String): Result<Character> {
        return try {
            val dto = api.getCharacter(GetCharacterRequest(avatarUrl))
            val name = dto.name ?: dto.data?.name ?: throw Exception("Character has no name")
            val extensions = dto.data?.extensions
            val characterBook = dto.data?.characterBook
            val depthPrompt = extensions?.depthPrompt
            Result.Success(
                Character(
                    name = name,
                    avatar = dto.avatar,
                    description = dto.description ?: dto.data?.description ?: "",
                    personality = dto.personality ?: dto.data?.personality ?: "",
                    scenario = dto.scenario ?: dto.data?.scenario ?: "",
                    firstMessage = dto.firstMes ?: dto.greeting ?: dto.data?.firstMes ?: "",
                    messageExample = dto.mesExample ?: dto.data?.mesExample ?: "",
                    creatorNotes = dto.creatorNotes ?: dto.data?.creatorNotes ?: "",
                    systemPrompt = dto.systemPrompt ?: dto.data?.systemPrompt ?: "",
                    tags = dto.tags ?: dto.data?.tags ?: emptyList(),
                    alternateGreetings = dto.data?.alternateGreetings ?: emptyList(),
                    attachedWorldInfo = extensions?.world,
                    hasCharacterBook = characterBook != null,
                    characterBookEntryCount = characterBook?.entries?.size ?: 0,
                    depthPrompt = depthPrompt?.prompt ?: "",
                    depthPromptDepth = depthPrompt?.depth ?: 4,
                    talkativeness = extensions?.talkativeness?.toFloatOrNull() ?: 0.5f,
                    isFavorite = extensions?.fav ?: false
                )
            )
        } catch (e: Exception) {
            Result.Error(Exception("Failed to get character: ${e.message}", e))
        }
    }

    suspend fun createCharacter(
        name: String,
        description: String = "",
        personality: String = "",
        scenario: String = "",
        firstMessage: String = "",
        messageExample: String = "",
        avatarBase64: String? = null
    ): Result<Unit> {
        return try {
            if (avatarBase64.isNullOrBlank()) {
                // No avatar - use simple create endpoint
                val request = CreateCharacterRequest(
                    chName = name,  // SillyTavern expects ch_name
                    description = description,
                    personality = personality,
                    scenario = scenario,
                    firstMes = firstMessage,
                    mesExample = messageExample
                )
                api.createCharacter(request)
            } else {
                // Has avatar - create PNG card and import
                val pngBytes = createCharacterCardPng(
                    avatarBase64 = avatarBase64,
                    name = name,
                    description = description,
                    personality = personality,
                    scenario = scenario,
                    firstMessage = firstMessage,
                    messageExample = messageExample
                )

                val fileName = name.replace(Regex("[^a-zA-Z0-9]"), "_") + ".png"
                val requestBody = pngBytes.toRequestBody("image/png".toMediaType())
                val filePart = MultipartBody.Part.createFormData("avatar", fileName, requestBody)
                val fileTypePart = "png".toRequestBody("text/plain".toMediaType())

                val response = api.importCharacter(filePart, fileTypePart)
                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    return Result.Error(Exception("Import failed (${response.code()}): $errorBody"))
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to create character: ${e.message}", e))
        }
    }

    private fun createCharacterCardPng(
        avatarBase64: String,
        name: String,
        description: String,
        personality: String,
        scenario: String,
        firstMessage: String,
        messageExample: String = ""
    ): ByteArray {
        // Decode avatar from base64
        val avatarBytes = Base64.decode(avatarBase64, Base64.DEFAULT)

        // Create character card data
        val cardData = PngCharacterCard.createCard(
            name = name,
            description = description,
            personality = personality,
            scenario = scenario,
            firstMessage = firstMessage,
            messageExample = messageExample
        )

        // Embed character data into PNG
        return PngCharacterCard.embedCharacterData(avatarBytes, cardData)
    }

    /**
     * Import a character card PNG directly to SillyTavern.
     * This preserves all embedded data including lorebooks, system prompts, etc.
     *
     * @param pngBytes The raw PNG bytes (must be a valid character card PNG)
     * @param fileName The filename to use for the import
     */
    suspend fun importCharacterCard(pngBytes: ByteArray, fileName: String): Result<Unit> {
        return try {
            val requestBody = pngBytes.toRequestBody("image/png".toMediaType())
            val filePart = MultipartBody.Part.createFormData("avatar", fileName, requestBody)
            val fileTypePart = "png".toRequestBody("text/plain".toMediaType())

            val response = api.importCharacter(filePart, fileTypePart)
            if (response.isSuccessful) {
                Log.d("STRepo", "Character card imported successfully: $fileName")
                Result.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.Error(Exception("Import failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to import character card", e)
            Result.Error(Exception("Failed to import character: ${e.message}", e))
        }
    }

    suspend fun deleteCharacter(avatarUrl: String): Result<Unit> {
        return try {
            val response = api.deleteCharacter(DeleteCharacterRequest(avatarUrl))
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.Error(Exception("Delete failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Failed to delete character: ${e.message}", e))
        }
    }

    suspend fun editCharacter(
        avatarUrl: String,
        name: String,
        description: String = "",
        personality: String = "",
        scenario: String = "",
        firstMessage: String = "",
        messageExample: String = ""
    ): Result<Unit> {
        return try {
            val request = EditCharacterRequest(
                avatarUrl = avatarUrl,
                chName = name,  // SillyTavern expects ch_name, not name
                description = description,
                personality = personality,
                scenario = scenario,
                firstMes = firstMessage,
                mesExample = messageExample
            )
            val response = api.editCharacter(request)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.Error(Exception("Edit failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Failed to edit character: ${e.message}", e))
        }
    }

    /**
     * Update character-specific settings (World Info, System Prompt, Author's Note, etc.)
     */
    suspend fun updateCharacterSettings(
        avatarUrl: String,
        systemPrompt: String? = null,
        postHistoryInstructions: String? = null,
        attachedWorldInfo: String? = null,
        depthPrompt: String? = null,
        depthPromptDepth: Int? = null,
        depthPromptRole: String? = null,
        talkativeness: Float? = null,
        isFavorite: Boolean? = null
    ): Result<Unit> {
        return try {
            // First get the current character data to preserve existing fields
            val currentChar = api.getCharacter(GetCharacterRequest(avatarUrl))
            val name = currentChar.name ?: currentChar.data?.name
                ?: throw Exception("Character has no name")

            // Get existing depth prompt values from character
            val existingDepthPrompt = currentChar.data?.extensions?.depthPrompt
            val currentDepthPromptText = existingDepthPrompt?.prompt ?: ""
            val currentDepthPromptDepth = existingDepthPrompt?.depth ?: 4
            val currentDepthPromptRole = existingDepthPrompt?.role ?: "system"

            // Get current system prompt and post history instructions
            val currentSystemPromptValue = currentChar.systemPrompt ?: currentChar.data?.systemPrompt ?: ""
            val currentPostHistoryValue = currentChar.data?.postHistoryInstructions ?: ""
            val currentWorldInfo = currentChar.data?.extensions?.world ?: ""

            // Determine world info value to save
            val worldInfoToSave = attachedWorldInfo ?: currentWorldInfo

            DebugLogger.logSection("Character Settings Save")
            DebugLogger.logKeyValue("World info current", currentWorldInfo.ifBlank { "(none)" })
            DebugLogger.logKeyValue("World info new", attachedWorldInfo ?: "(not changed)")
            DebugLogger.logKeyValue("World info saving", worldInfoToSave.ifBlank { "(none)" })

            // SillyTavern edit endpoint expects ch_name and depth prompt fields at top level
            val request = EditCharacterRequest(
                avatarUrl = avatarUrl,
                chName = name,  // SillyTavern expects ch_name, not name
                description = currentChar.description ?: currentChar.data?.description ?: "",
                personality = currentChar.personality ?: currentChar.data?.personality ?: "",
                scenario = currentChar.scenario ?: currentChar.data?.scenario ?: "",
                firstMes = currentChar.firstMes ?: currentChar.greeting ?: currentChar.data?.firstMes ?: "",
                mesExample = currentChar.mesExample ?: currentChar.data?.mesExample ?: "",
                creatorNotes = currentChar.creatorNotes ?: currentChar.data?.creatorNotes ?: "",
                talkativeness = talkativeness ?: currentChar.talkativeness ?: 0.5f,
                fav = if (isFavorite ?: currentChar.fav ?: false) "true" else "false",
                tags = currentChar.tags ?: currentChar.data?.tags ?: emptyList(),
                creator = currentChar.creator ?: currentChar.data?.creator ?: "",
                // System prompt and post history instructions
                systemPrompt = systemPrompt ?: currentSystemPromptValue,
                postHistoryInstructions = postHistoryInstructions ?: currentPostHistoryValue,
                // Depth prompt fields at top level (SillyTavern's expected format)
                depthPromptPrompt = depthPrompt ?: currentDepthPromptText,
                depthPromptDepth = depthPromptDepth ?: currentDepthPromptDepth,
                depthPromptRole = depthPromptRole ?: currentDepthPromptRole,
                // World info attachment
                world = worldInfoToSave,
                // Preserve chat and create_date
                chat = currentChar.chat,
                createDate = currentChar.createDate
            )

            val response = api.editCharacter(request)
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                return Result.Error(Exception("Update failed (${response.code()}): $errorBody"))
            }

            // Update world info attachment via edit-attribute endpoint
            // (world info is stored in extensions.world which needs special handling)
            // Always update if a value was provided (even empty string to clear it)
            if (attachedWorldInfo != null) {
                Log.d("STRepo", "Updating character world info to: '$attachedWorldInfo'")
                updateCharacterExtension(
                    avatarUrl = avatarUrl,
                    chName = name,
                    field = "extensions.world",
                    value = attachedWorldInfo
                )
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to update character settings: ${e.message}", e))
        }
    }

    /**
     * Update a single character extension field via the edit-attribute endpoint
     */
    private suspend fun updateCharacterExtension(
        avatarUrl: String,
        chName: String,
        field: String,
        value: String
    ) {
        try {
            api.editCharacterAttribute(
                EditCharacterAttributeRequest(
                    avatarUrl = avatarUrl,
                    chName = chName,
                    field = field,
                    value = value
                )
            )
        } catch (e: Exception) {
            Log.w("STRepo", "Failed to update character extension $field: ${e.message}")
        }
    }

    suspend fun getCharacterChats(avatarUrl: String): Result<List<ChatInfo>> {
        return try {
            val dtos = api.getCharacterChats(GetChatsRequest(avatarUrl))
            val chats = dtos.mapNotNull { dto ->
                dto.fileName?.let { fileName ->
                    ChatInfo(
                        fileName = fileName.replace(".jsonl", ""),
                        lastMessage = dto.lastMes
                    )
                }
            }
            Result.Success(chats)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to load chats: ${e.message}", e))
        }
    }

    suspend fun getChat(characterName: String, avatarUrl: String, fileName: String): Result<List<ChatMessage>> {
        return try {
            val dtos = api.getChat(
                GetChatRequest(
                    chName = characterName,
                    fileName = fileName,
                    avatarUrl = avatarUrl
                )
            )
            val messages = dtos.mapIndexedNotNull { index, dto ->
                val content = dto.mes ?: dto.content ?: return@mapIndexedNotNull null
                ChatMessage(
                    content = content,
                    isUser = dto.isUser,
                    // Preserve integrity slug from first message
                    integritySlug = if (index == 0) dto.chatMetadata?.integrity else null,
                    // Preserve full chat metadata (author's note) from first message
                    chatMetadata = if (index == 0 && dto.chatMetadata != null) {
                        ChatMessageMetadata(
                            notePrompt = dto.chatMetadata.notePrompt,
                            noteInterval = dto.chatMetadata.noteInterval,
                            noteDepth = dto.chatMetadata.noteDepth,
                            notePosition = dto.chatMetadata.notePosition,
                            noteRole = dto.chatMetadata.noteRole
                        )
                    } else null
                )
            }
            Result.Success(messages)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to load chat: ${e.message}", e))
        }
    }

    /**
     * Get chat messages along with the timestamp of the last message.
     * Returns Pair(messages, lastMessageTimestamp)
     */
    suspend fun getChatWithTimestamp(characterName: String, avatarUrl: String, fileName: String): Result<Pair<List<ChatMessage>, Long>> {
        return try {
            val dtos = api.getChat(
                GetChatRequest(
                    chName = characterName,
                    fileName = fileName,
                    avatarUrl = avatarUrl
                )
            )

            var lastTimestamp = 0L

            val messages = dtos.mapIndexedNotNull { index, dto ->
                val content = dto.mes ?: dto.content ?: return@mapIndexedNotNull null

                // Parse send_date to get timestamp
                dto.sendDate?.let { dateStr ->
                    val parsed = parseSendDate(dateStr)
                    if (parsed > lastTimestamp) {
                        lastTimestamp = parsed
                    }
                }

                ChatMessage(
                    content = content,
                    isUser = dto.isUser,
                    integritySlug = if (index == 0) dto.chatMetadata?.integrity else null,
                    // Preserve full chat metadata (author's note) from first message
                    chatMetadata = if (index == 0 && dto.chatMetadata != null) {
                        ChatMessageMetadata(
                            notePrompt = dto.chatMetadata.notePrompt,
                            noteInterval = dto.chatMetadata.noteInterval,
                            noteDepth = dto.chatMetadata.noteDepth,
                            notePosition = dto.chatMetadata.notePosition,
                            noteRole = dto.chatMetadata.noteRole
                        )
                    } else null
                )
            }

            Result.Success(Pair(messages, lastTimestamp))
        } catch (e: Exception) {
            Result.Error(Exception("Failed to load chat: ${e.message}", e))
        }
    }

    /**
     * Parse SillyTavern send_date format to timestamp.
     * Common formats: "November 15, 2024 2:30pm", epoch millis as string, ISO format
     */
    private fun parseSendDate(dateStr: String): Long {
        return try {
            // Try parsing as epoch millis first
            dateStr.toLongOrNull()?.let { return it }

            // Try common date formats
            val formats = listOf(
                java.text.SimpleDateFormat("MMMM d, yyyy h:mma", java.util.Locale.ENGLISH),
                java.text.SimpleDateFormat("MMMM d, yyyy h:mm a", java.util.Locale.ENGLISH),
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ENGLISH),
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ENGLISH)
            )

            for (format in formats) {
                try {
                    format.parse(dateStr)?.time?.let { return it }
                } catch (_: Exception) { }
            }

            0L
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun saveChat(
        characterName: String,
        avatarUrl: String,
        fileName: String,
        messages: List<ChatMessage>
    ): Result<Unit> {
        return try {
            val saveMsgs = messages.mapIndexed { index, msg ->
                SaveChatMessageDto(
                    name = if (msg.isUser) "User" else characterName,
                    isUser = msg.isUser,
                    mes = msg.content,
                    sendDate = msg.timestamp.toString(),
                    // Include full chat_metadata on first message (integrity + author's note)
                    chatMetadata = if (index == 0) {
                        ChatMetadata(
                            integrity = msg.integritySlug,
                            notePrompt = msg.chatMetadata?.notePrompt,
                            noteInterval = msg.chatMetadata?.noteInterval,
                            noteDepth = msg.chatMetadata?.noteDepth,
                            notePosition = msg.chatMetadata?.notePosition,
                            noteRole = msg.chatMetadata?.noteRole
                        )
                    } else null
                )
            }
            api.saveChat(
                SaveChatRequest(
                    chName = characterName,
                    fileName = fileName,
                    avatarUrl = avatarUrl,
                    chat = saveMsgs
                )
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to save chat: ${e.message}", e))
        }
    }

    suspend fun deleteChat(
        characterName: String,
        avatarUrl: String,
        fileName: String
    ): Result<Unit> {
        return try {
            // Add .jsonl extension if not present
            val fullFileName = if (fileName.endsWith(".jsonl")) fileName else "$fileName.jsonl"
            val response = api.deleteChat(
                DeleteChatRequest(
                    chName = characterName,
                    fileName = fullFileName,
                    avatarUrl = avatarUrl,
                    chatfile = fullFileName
                )
            )
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.Error(Exception("Delete failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Failed to delete chat: ${e.message}", e))
        }
    }

    private fun buildTextCompletionRequest(
        prompt: String,
        characterName: String,
        userName: String = "User",
        stream: Boolean = false
    ): TextCompletionRequest {
        val preset = currentPreset
        val template = currentInstructTemplate

        // Build stopping strings based on template, character name, and user name
        val stoppingStrings = buildList {
            // Add template stop sequence if available
            template?.stopSequence?.takeIf { it.isNotBlank() }?.let { add(it) }
            // Add input sequence as stop (to stop before next user turn)
            template?.inputSequence?.takeIf { it.isNotBlank() }?.let { add(it) }
            // Fallback stopping strings for character
            add("\n$characterName:")
            add("\n\n$characterName:")
            // IMPORTANT: Add user name to prevent AI from impersonating user
            add("\n$userName:")
            add("\n\n$userName:")
            add("$userName:")
            // Common impersonation patterns
            add("\nUser:")
            add("\n\nUser:")
            add("\n\n\n")
        }.distinct()

        return TextCompletionRequest(
            apiServer = apiServer,
            apiType = apiType,
            prompt = prompt,
            // Token limits (null = let server decide)
            maxNewTokens = preset?.maxNewTokens,
            maxTokens = preset?.maxNewTokens,
            nPredict = preset?.maxNewTokens,
            numPredict = preset?.maxNewTokens,
            minTokens = preset?.minTokens ?: 0,
            truncationLength = preset?.truncationLength ?: 2048,
            numCtx = preset?.truncationLength ?: 2048,
            // Temperature and sampling
            temperature = preset?.temperature ?: 0.7f,
            topP = preset?.topP ?: 0.5f,
            topK = preset?.topK ?: 40,
            topA = preset?.topA ?: 0f,
            minP = preset?.minP ?: 0f,
            typicalP = preset?.typicalP ?: 1.0f,
            typical = preset?.typicalP ?: 1.0f,
            tfs = preset?.tfs ?: 1.0f,
            // Repetition penalty
            repPen = preset?.repPen ?: 1.2f,
            repetitionPenalty = preset?.repPen ?: 1.2f,
            repeatPenalty = preset?.repPen ?: 1.2f,
            repPenRange = preset?.repPenRange ?: 0,
            repetitionPenaltyRange = preset?.repPenRange ?: 0,
            repeatLastN = preset?.repPenRange ?: 0,
            repPenSlope = preset?.repPenSlope ?: 1f,
            frequencyPenalty = preset?.frequencyPenalty ?: 0f,
            presencePenalty = preset?.presencePenalty ?: 0f,
            // DRY sampler
            dryMultiplier = preset?.dryMultiplier ?: 0f,
            dryBase = preset?.dryBase ?: 1.75f,
            dryAllowedLength = preset?.dryAllowedLength ?: 2,
            dryPenaltyLastN = preset?.dryPenaltyLastN ?: 0,
            // Mirostat
            mirostatMode = preset?.mirostatMode ?: 0,
            mirostat = preset?.mirostatMode ?: 0,
            mirostatTau = preset?.mirostatTau ?: 5f,
            mirostatEta = preset?.mirostatEta ?: 0.1f,
            // XTC
            xtcThreshold = preset?.xtcThreshold ?: 0.1f,
            xtcProbability = preset?.xtcProbability ?: 0f,
            // Other sampling
            skew = preset?.skew ?: 0f,
            smoothingFactor = preset?.smoothingFactor ?: 0f,
            smoothingCurve = preset?.smoothingCurve ?: 1f,
            // Guidance
            guidanceScale = preset?.guidanceScale ?: 1f,
            // Token handling
            addBosToken = preset?.addBosToken ?: true,
            banEosToken = preset?.banEosToken ?: false,
            skipSpecialTokens = preset?.skipSpecialTokens ?: true,
            // Stopping
            stoppingStrings = stoppingStrings,
            stop = stoppingStrings,
            // Stream
            stream = stream
        )
    }

    private fun buildChatCompletionRequest(
        chatHistory: List<ChatMessage>,
        newMessage: String,
        character: Character,
        chatContext: ChatContext,
        stream: Boolean = false
    ): ChatCompletionRequest {
        val template = currentInstructTemplate
        val userName = chatContext.userPersona.name.ifBlank { "User" }

        // Substitute template variables
        fun substituteVars(text: String): String {
            return text
                .replace("{{char}}", character.name)
                .replace("{{charIfNotGroup}}", character.name)
                .replace("{{user}}", userName)
                .replace("{{description}}", character.description)
                .replace("{{personality}}", character.personality)
                .replace("{{scenario}}", character.scenario)
                .replace("{{mesExamples}}", character.messageExample)
                .replace("{{mesExamples}}", character.messageExample)
                .replace("{{system}}", character.systemPrompt)
                .replace("{{persona}}", chatContext.userPersona.description)
        }

        // Scan for triggered World Info entries
        fun scanWorldInfo(): List<WorldInfoEntry> {
            val entries = chatContext.worldInfoEntries.filter { it.enabled }
            if (entries.isEmpty()) return emptyList()

            val scanDepth = chatContext.worldInfoSettings.depth
            val textToScan = buildString {
                append(newMessage)
                append(" ")
                chatHistory.takeLast(scanDepth).forEach { msg ->
                    append(msg.content)
                    append(" ")
                }
                append(character.description)
                append(" ")
                append(character.scenario)
            }.lowercase()

            return entries.filter { entry ->
                if (entry.constant) return@filter true
                entry.key.any { key ->
                    if (key.isBlank()) return@any false
                    val pattern = if (entry.matchWholeWords) "\\b${Regex.escape(key)}\\b" else Regex.escape(key)
                    val regex = if (entry.caseSensitive) Regex(pattern) else Regex(pattern, RegexOption.IGNORE_CASE)
                    regex.containsMatchIn(textToScan)
                }
            }
        }

        // Get ALL triggered World Info content (no filtering by position or depth)
        // For Chat Completions, we include everything - depth/position are Text Completion concepts
        var worldInfoAlreadyAdded = false
        fun getAllWorldInfo(): String {
            if (worldInfoAlreadyAdded) return ""

            val triggered = scanWorldInfo()

            // Log triggered entries for debugging
            if (triggered.isNotEmpty()) {
                DebugLogger.log("Chat Completions: Triggered ${triggered.size} World Info entries (all positions/depths):")
                triggered.forEach { entry ->
                    DebugLogger.log("  - ${entry.comment ?: "unnamed"} (pos=${entry.position}, depth=${entry.depth}, keys: ${entry.key.take(2).joinToString(", ")})")
                }
                worldInfoAlreadyAdded = true
            }

            return triggered.sortedBy { it.order }
                .joinToString("\n") { substituteVars(it.content) }
        }

        // Log World Info summary
        DebugLogger.logSection("Chat Completions - World Info Scan")
        DebugLogger.logKeyValue("Total WI entries available", chatContext.worldInfoEntries.size)
        DebugLogger.logKeyValue("Enabled entries", chatContext.worldInfoEntries.count { it.enabled })

        // Log OAI prompts configuration
        DebugLogger.logSection("Chat Completions - OAI Prompts")
        DebugLogger.logKeyValue("Prompt order count", oaiPromptOrder.size)
        DebugLogger.logKeyValue("Main prompt", (oaiPrompts["main"]?.take(100) ?: "not set"))
        DebugLogger.logKeyValue("NSFW prompt", (oaiPrompts["nsfw"]?.take(100) ?: "not set"))
        DebugLogger.logKeyValue("Jailbreak prompt", (oaiPrompts["jailbreak"]?.take(100) ?: "not set"))
        DebugLogger.logKeyValue("Prompt order", oaiPromptOrder.map { "${it.first}=${it.second}" }.joinToString(", "))

        // Build messages following prompt_order
        val messages = buildList {
            // Track where chat history should be inserted
            var chatHistoryInserted = false

            // If we have a prompt order, follow it
            if (oaiPromptOrder.isNotEmpty()) {
                for ((identifier, enabled) in oaiPromptOrder) {
                    if (!enabled) continue

                    when (identifier) {
                        // Prompts with content from preset
                        "main", "nsfw", "jailbreak", "enhanceDefinitions" -> {
                            val content = oaiPrompts[identifier]
                            if (!content.isNullOrBlank()) {
                                add(ChatCompletionMessage(role = "system", content = substituteVars(content)))
                            }
                        }
                        // Character data markers
                        "charDescription" -> {
                            if (character.description.isNotBlank()) {
                                add(ChatCompletionMessage(role = "system", content = "[Character Description: ${character.description}]"))
                            }
                        }
                        "charPersonality" -> {
                            if (character.personality.isNotBlank()) {
                                add(ChatCompletionMessage(role = "system", content = "[Character Personality: ${character.personality}]"))
                            }
                        }
                        "scenario" -> {
                            if (character.scenario.isNotBlank()) {
                                add(ChatCompletionMessage(role = "system", content = "[Scenario: ${character.scenario}]"))
                            }
                        }
                        "dialogueExamples" -> {
                            if (character.messageExample.isNotBlank()) {
                                add(ChatCompletionMessage(role = "system", content = "[Example Dialogue:\n${character.messageExample}]"))
                            }
                        }
                        "personaDescription" -> {
                            val persona = chatContext.userPersona
                            if (persona.description.isNotBlank()) {
                                add(ChatCompletionMessage(role = "system", content = "[${persona.name}'s persona: ${substituteVars(persona.description)}]"))
                            }
                        }
                        "worldInfoBefore", "worldInfoAfter" -> {
                            // Add ALL World Info at whichever position comes first (no filtering)
                            val worldInfo = getAllWorldInfo()
                            if (worldInfo.isNotBlank()) {
                                DebugLogger.log("Chat Completions: Adding World Info at '$identifier' (${worldInfo.length} chars)")
                                add(ChatCompletionMessage(role = "system", content = worldInfo))
                            }
                        }
                        "chatHistory" -> {
                            // Insert chat history at this position
                            chatHistory.forEach { msg ->
                                val role = if (msg.isUser) "user" else "assistant"
                                add(ChatCompletionMessage(role = role, content = msg.content))
                            }
                            chatHistoryInserted = true
                        }
                        else -> {
                            // Handle custom prompts (UUIDs or any other custom identifier)
                            val customContent = oaiPrompts[identifier]
                            if (!customContent.isNullOrBlank()) {
                                DebugLogger.log("Chat Completions: Adding custom prompt '$identifier' (${customContent.length} chars)")
                                add(ChatCompletionMessage(role = "system", content = substituteVars(customContent)))
                            }
                        }
                    }
                }
            } else {
                // Fallback: simple order if no prompt_order defined
                val mainPrompt = oaiPrompts["main"] ?: currentSystemPrompt.ifBlank { template?.systemPrompt ?: "" }
                if (mainPrompt.isNotBlank()) {
                    add(ChatCompletionMessage(role = "system", content = substituteVars(mainPrompt)))
                }

                // Add character info
                if (character.description.isNotBlank()) {
                    add(ChatCompletionMessage(role = "system", content = "[Character: ${character.description}]"))
                }
                if (character.personality.isNotBlank()) {
                    add(ChatCompletionMessage(role = "system", content = "[Personality: ${character.personality}]"))
                }
                if (character.scenario.isNotBlank()) {
                    add(ChatCompletionMessage(role = "system", content = "[Scenario: ${character.scenario}]"))
                }

                // NSFW/Auxiliary
                val nsfwPrompt = oaiPrompts["nsfw"]
                if (!nsfwPrompt.isNullOrBlank()) {
                    add(ChatCompletionMessage(role = "system", content = substituteVars(nsfwPrompt)))
                }
            }

            // If chat history wasn't inserted via order, add it now
            if (!chatHistoryInserted) {
                chatHistory.forEach { msg ->
                    val role = if (msg.isUser) "user" else "assistant"
                    add(ChatCompletionMessage(role = role, content = msg.content))
                }
            }

            // Add jailbreak after history if not already added via order
            if (oaiPromptOrder.isEmpty()) {
                val jailbreakPrompt = oaiPrompts["jailbreak"]
                if (!jailbreakPrompt.isNullOrBlank()) {
                    add(ChatCompletionMessage(role = "system", content = substituteVars(jailbreakPrompt)))
                }
            }

            // Add new user message
            add(ChatCompletionMessage(role = "user", content = newMessage))
        }

        // Build stopping strings - include user name to prevent impersonation
        val stopStrings = buildList {
            template?.stopSequence?.takeIf { it.isNotBlank() }?.let { add(it) }
            // Add user name patterns to prevent AI from impersonating user
            add("\n$userName:")
            add("\n\n$userName:")
            add("$userName:")
            // Also add generic "User:" in case persona name differs
            if (userName != "User") {
                add("\nUser:")
                add("\n\nUser:")
            }
        }.distinct().takeIf { it.isNotEmpty() }

        // Use OAI preset settings for chat completion APIs
        return ChatCompletionRequest(
            chatCompletionSource = chatCompletionSource,
            customUrl = customOpenaiUrl.ifBlank { null },
            messages = messages,
            model = openaiModel,
            maxTokens = oaiMaxTokens,
            temperature = oaiTemperature,
            topP = oaiTopP,
            topK = oaiTopK.takeIf { it > 0 },
            topA = oaiTopA.takeIf { it > 0f },
            minP = oaiMinP.takeIf { it > 0f },
            typicalP = null, // Not typically used in OAI APIs
            frequencyPenalty = oaiFrequencyPenalty,
            presencePenalty = oaiPresencePenalty,
            repetitionPenalty = oaiRepetitionPenalty.takeIf { it > 1f },
            stop = stopStrings,
            stream = stream
        )
    }

    suspend fun sendMessage(
        message: String,
        character: Character,
        chatHistory: List<ChatMessage>
    ): Result<String> {
        return try {
            // Load full chat context for proper prompt building
            val contextResult = loadChatContext(character, chatHistory)
            val chatContext = when (contextResult) {
                is Result.Success -> contextResult.data
                is Result.Error -> {
                    Log.w("STRepo", "Failed to load context, using defaults: ${contextResult.exception.message}")
                    ChatContext(
                        characterName = character.name,
                        characterDescription = character.description,
                        instructTemplate = currentInstructTemplate,
                        systemPromptPreset = currentSystemPrompt
                    )
                }
            }

            // Build prompt using PromptBuilder with all context data
            val promptBuilder = PromptBuilder(character, chatContext)
            val prompt = promptBuilder.buildPrompt(chatHistory, message)

            val request = buildTextCompletionRequest(prompt, character.name, chatContext.userPersona.name, stream = false)
            val response = api.generateTextCompletion(request)
            val assistantMessage = parseCompletionResponse(response)
            Result.Success(assistantMessage)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to generate response: ${e.message}", e))
        }
    }

    private val jsonSerializer = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun sendMessageStreaming(
        message: String,
        character: Character,
        chatHistory: List<ChatMessage>
    ): Flow<StreamEvent> = flow {
        // Load full chat context for proper prompt building
        val contextResult = loadChatContext(character, chatHistory)
        val chatContext = when (contextResult) {
            is Result.Success -> contextResult.data
            is Result.Error -> {
                Log.w("STRepo", "Failed to load context, using defaults: ${contextResult.exception.message}")
                ChatContext(
                    characterName = character.name,
                    characterDescription = character.description,
                    instructTemplate = currentInstructTemplate,
                    systemPromptPreset = currentSystemPrompt
                )
            }
        }

        // Build prompt using PromptBuilder with all context data
        val promptBuilder = PromptBuilder(character, chatContext)
        val prompt = promptBuilder.buildPrompt(chatHistory, message)

        // Debug logging for prompt building diagnostics
        DebugLogger.logSection("sendMessageStreaming")
        DebugLogger.logKeyValue("Character", character.name)
        DebugLogger.logKeyValue("Chat history length", chatHistory.size)
        DebugLogger.logKeyValue("New message", message)
        DebugLogger.logSection("Chat Context")
        DebugLogger.logKeyValue("Instruct template", chatContext.instructTemplate?.name ?: "none")
        DebugLogger.logKeyValue("System prompt preset", chatContext.systemPromptPreset.take(100))
        DebugLogger.logKeyValue("User persona", chatContext.userPersona.name)
        DebugLogger.logKeyValue("Authors note depth", chatContext.authorsNote.depth)
        DebugLogger.logKeyValue("Authors note content", chatContext.authorsNote.content.take(100))
        DebugLogger.logSection("World Info")
        DebugLogger.logKeyValue("World Info entries count", chatContext.worldInfoEntries.size)
        chatContext.worldInfoEntries.take(10).forEachIndexed { index, entry ->
            DebugLogger.logKeyValue("  Entry $index", "${entry.comment ?: "unnamed"} - keys: ${entry.key.take(3)} - enabled: ${entry.enabled}")
        }
        DebugLogger.logKeyValue("World Info scan depth", chatContext.worldInfoSettings.depth)
        DebugLogger.logPrompt("Built Prompt (text completion)", prompt)

        val settings = settingsRepository.getSettings()
        val serverUrl = settings.normalizedServerUrl

        // Choose endpoint based on API type
        val useChatCompletions = usesChatCompletions()
        val endpoint = if (useChatCompletions) {
            "$serverUrl/api/backends/chat-completions/generate"
        } else {
            "$serverUrl/api/backends/text-completions/generate"
        }

        try {
            // Serialize request based on API type
            val jsonBody = if (useChatCompletions) {
                val chatRequest = buildChatCompletionRequest(chatHistory, message, character, chatContext, stream = true)
                Log.d("STRepo", "Chat completion request - source: ${chatRequest.chatCompletionSource}, model: ${chatRequest.model}")
                jsonSerializer.encodeToString(chatRequest)
            } else {
                val textRequest = buildTextCompletionRequest(prompt, character.name, chatContext.userPersona.name, stream = true)
                jsonSerializer.encodeToString(textRequest)
            }
            Log.d("STRepo", "Request endpoint: $endpoint, useChatCompletions: $useChatCompletions, mainApi: $mainApi")

            // Debug log the full API request
            DebugLogger.logSection("API Request Details")
            DebugLogger.logKeyValue("Endpoint", endpoint)
            DebugLogger.logKeyValue("API Type", if (useChatCompletions) "Chat Completions" else "Text Completions")
            DebugLogger.logKeyValue("Main API", mainApi)
            DebugLogger.logApiRequest(endpoint, jsonBody)

            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

            // Build Basic Auth header
            val authHeader = settings.let {
                if (it.username.isNotBlank() && it.password.isNotBlank()) {
                    val credentials = "${it.username}:${it.password}"
                    val encoded = android.util.Base64.encodeToString(
                        credentials.toByteArray(Charsets.UTF_8),
                        android.util.Base64.NO_WRAP
                    )
                    "Basic $encoded"
                } else null
            }

            val httpRequest = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .apply {
                    csrfInterceptor.csrfToken?.let {
                        header("X-CSRF-Token", it)
                    }
                    authHeader?.let {
                        header("Authorization", it)
                    }
                }
                .build()

            Log.d("STRepo", "Streaming request to: $endpoint (mainApi: $mainApi, chatCompletions: $useChatCompletions)")

            val response = okHttpClient.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            Log.d("STRepo", "Response content-type: ${response.header("Content-Type")}")
            Log.d("STRepo", "Transfer-Encoding: ${response.header("Transfer-Encoding")}")

            val inputStream = response.body?.byteStream() ?: throw Exception("Empty response body")
            // No buffering - read directly from stream
            val reader = java.io.InputStreamReader(inputStream, Charsets.UTF_8)
            val fullText = StringBuilder()
            val eventBuffer = StringBuilder()

            // Read character by character for true streaming - no buffering
            var char: Int
            val lineBuffer = StringBuilder()

            while (reader.read().also { char = it } != -1) {
                val c = char.toChar()

                if (c == '\n') {
                    val line = lineBuffer.toString().trimEnd('\r')
                    lineBuffer.clear()

                    if (line.isEmpty()) {
                        // Empty line = end of SSE event
                        if (eventBuffer.isNotEmpty()) {
                            val token = parseSseEvent(eventBuffer.toString())
                            if (token != null) {
                                fullText.append(token)
                                emit(StreamEvent.Token(token, fullText.toString()))
                            }
                            eventBuffer.clear()
                        }
                    } else if (line == "data: [DONE]") {
                        break
                    } else {
                        eventBuffer.append(line).append("\n")
                    }
                } else {
                    lineBuffer.append(c)
                }
            }

            reader.close()
            response.close()

            Log.d("STRepo", "Stream finished. Full text length: ${fullText.length}")

            // Clean up and emit final result
            val finalText = cleanupResponse(fullText.toString())
            emit(StreamEvent.Complete(finalText))
        } catch (e: Exception) {
            Log.e("STRepo", "Streaming error: ${e.message}", e)
            emit(StreamEvent.Error(e.message ?: "Streaming failed"))
        }
    }.flowOn(Dispatchers.IO)

    private val streamJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parseSseEvent(event: String): String? {
        val lines = event.split(Regex("\\r?\\n"))
        val dataLines = StringBuilder()

        for (line in lines) {
            when {
                line.startsWith("data: ") -> dataLines.append(line.removePrefix("data: ")).append("\n")
                line.startsWith("data:") -> dataLines.append(line.removePrefix("data:")).append("\n")
            }
        }

        val data = dataLines.toString().trimEnd('\n')
        if (data.isBlank() || data == "[DONE]") return null

        return try {
            val chunk = streamJson.decodeFromString<StreamChunk>(data)
            // Try different response formats
            chunk.choices?.firstOrNull()?.delta?.content
                ?: chunk.choices?.firstOrNull()?.text
                ?: chunk.text
                ?: chunk.token
                ?: chunk.content
        } catch (e: Exception) {
            // If JSON parsing fails, maybe it's raw text
            null
        }
    }

    private fun cleanupResponse(text: String): String {
        var result = text.trim()
        val userIdx = result.indexOf("\nUser:")
        if (userIdx > 0) {
            result = result.substring(0, userIdx)
        }
        return result
    }

    private fun buildPrompt(
        history: List<ChatMessage>,
        newMessage: String,
        characterName: String
    ): String {
        val template = currentInstructTemplate
        val systemPrompt = currentSystemPrompt.ifBlank { template?.systemPrompt ?: "" }

        return if (template != null && template.inputSequence.isNotBlank()) {
            // Use instruct template formatting
            buildInstructPrompt(history, newMessage, characterName, template, systemPrompt)
        } else {
            // Fallback to simple format
            buildSimplePrompt(history, newMessage, characterName)
        }
    }

    private fun buildInstructPrompt(
        history: List<ChatMessage>,
        newMessage: String,
        characterName: String,
        template: InstructTemplate,
        systemPrompt: String
    ): String {
        val sb = StringBuilder()

        // Add system prompt if present
        if (systemPrompt.isNotBlank()) {
            if (template.systemSequence.isNotBlank()) {
                sb.append(template.systemSequence)
            }
            sb.append(systemPrompt)
            if (template.stopSequence.isNotBlank()) {
                sb.append(template.stopSequence)
            }
            sb.append("\n")
        }

        // Add chat history
        var isFirst = true
        history.forEach { msg ->
            if (msg.isUser) {
                sb.append(template.inputSequence)
                sb.append(msg.content)
                if (template.stopSequence.isNotBlank()) {
                    sb.append(template.stopSequence)
                }
                sb.append("\n")
            } else {
                // Use first_output_sequence for first assistant message if available
                val outputSeq = if (isFirst && template.firstOutputSequence.isNotBlank()) {
                    template.firstOutputSequence
                } else {
                    template.outputSequence
                }
                sb.append(outputSeq)
                sb.append(msg.content)
                if (template.stopSequence.isNotBlank()) {
                    sb.append(template.stopSequence)
                }
                sb.append("\n")
                isFirst = false
            }
        }

        // Add new user message
        sb.append(template.inputSequence)
        sb.append(newMessage)
        if (template.stopSequence.isNotBlank()) {
            sb.append(template.stopSequence)
        }
        sb.append("\n")

        // Start assistant response
        sb.append(template.outputSequence)

        return sb.toString()
    }

    private fun buildSimplePrompt(
        history: List<ChatMessage>,
        newMessage: String,
        characterName: String
    ): String {
        val sb = StringBuilder()
        history.forEach { msg ->
            if (msg.isUser) {
                sb.append("User: ${msg.content}\n")
            } else {
                sb.append("$characterName: ${msg.content}\n")
            }
        }
        sb.append("User: $newMessage\n")
        sb.append("$characterName:")
        return sb.toString()
    }

    private fun parseCompletionResponse(response: TextCompletionResponse): String {
        var text = when {
            response.text != null -> response.text
            response.content != null -> response.content
            response.results?.firstOrNull()?.text != null -> response.results.first().text!!
            response.choices?.firstOrNull()?.text != null -> response.choices.first().text!!
            response.choices?.firstOrNull()?.message?.content != null ->
                response.choices.first().message!!.content!!
            else -> ""
        }

        // Clean up response
        text = text.trim()
        val userIdx = text.indexOf("\nUser:")
        if (userIdx > 0) {
            text = text.substring(0, userIdx)
        }

        return text
    }

    suspend fun abortGeneration() {
        try {
            // Try SillyTavern's abort endpoint first
            api.abortTextCompletion()
            Log.d("STRepo", "Abort request sent to SillyTavern")
        } catch (e: Exception) {
            Log.w("STRepo", "SillyTavern abort failed: ${e.message}")
        }

        // Also try to abort directly on the backend (KoboldCpp, etc.)
        try {
            val abortUrl = "$apiServer/api/extra/abort"
            val request = okhttp3.Request.Builder()
                .url(abortUrl)
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
            okHttpClient.newCall(request).execute().close()
            Log.d("STRepo", "Abort request sent directly to backend: $abortUrl")
        } catch (e: Exception) {
            Log.w("STRepo", "Direct backend abort failed: ${e.message}")
        }
    }

    fun generateChatFileName(characterName: String): String {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'@'HH'h'mm'm'ss's'SSS'ms'")
        return "$characterName - ${now.format(formatter)}"
    }

    suspend fun buildAvatarUrl(avatar: String?): String? {
        if (avatar.isNullOrBlank()) return null
        val settings = settingsRepository.getSettings()
        val serverUrl = settings.normalizedServerUrl
        if (serverUrl.isBlank()) return null

        // Build simple URL - Coil's OkHttpClient handles authentication
        return "$serverUrl/thumbnail?type=avatar&file=${java.net.URLEncoder.encode(avatar, "UTF-8")}"
    }

    /**
     * Export a character card as PNG with embedded metadata.
     * Used for uploading characters to external services like CardVault.
     */
    suspend fun exportCharacterCard(avatar: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val request = ExportCharacterRequest(avatarUrl = avatar, format = "png")
                val responseBody = api.exportCharacter(request)
                val bytes = responseBody.bytes()
                if (bytes.isEmpty()) {
                    Result.Error(Exception("Exported character card is empty"))
                } else {
                    Result.Success(bytes)
                }
            } catch (e: Exception) {
                Result.Error(Exception("Failed to export character: ${e.message}", e))
            }
        }

    // ========== User Management ==========

    suspend fun getCurrentUser(): Result<UserInfo> {
        return try {
            val response = api.getCurrentUser()
            Result.Success(
                UserInfo(
                    handle = response.handle ?: "",
                    name = response.name ?: response.handle ?: "",
                    avatar = response.avatar,
                    isAdmin = response.admin,
                    hasPassword = response.password != null,
                    created = response.created
                )
            )
        } catch (e: Exception) {
            Result.Error(Exception("Failed to get user info: ${e.message}", e))
        }
    }

    suspend fun changePassword(oldPassword: String?, newPassword: String?): Result<Unit> {
        return try {
            val userSettings = settingsRepository.getSettings()
            val request = ChangePasswordRequest(
                handle = userSettings.username,
                oldPassword = oldPassword,
                newPassword = newPassword
            )
            val response = api.changePassword(request)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.Error(Exception("Password change failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Failed to change password: ${e.message}", e))
        }
    }

    suspend fun logout(): Result<Unit> {
        return try {
            val response = api.logout()
            // Clear local auth state and cached settings
            csrfInterceptor.clearToken()
            com.stark.sillytavern.di.NetworkModule.clearCookies()
            clearLocalSettings()
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                // Still clear local state even if server logout fails
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            // Still clear local state even if server logout fails
            csrfInterceptor.clearToken()
            com.stark.sillytavern.di.NetworkModule.clearCookies()
            clearLocalSettings()
            Result.Success(Unit)
        }
    }

    // ========== Full Settings & Presets ==========

    private var cachedFullSettings: FullSettingsResponse? = null
    private var settingsInitialized = false

    // Local overrides for settings changed in the app (persists even if cache refreshes)
    private var localSelectedTextGenPreset: String? = null
    private var localSelectedInstructPreset: String? = null
    private var localSelectedSyspromptPreset: String? = null

    /**
     * Clear all local setting overrides (call on logout/disconnect)
     */
    suspend fun clearLocalSettings() {
        cachedFullSettings = null
        settingsInitialized = false
        localSelectedTextGenPreset = null
        localSelectedInstructPreset = null
        localSelectedSyspromptPreset = null
        // Also clear persisted selections
        settingsDataStore.clearPresetSelections()
    }

    /**
     * Sync persisted local settings to the server on startup/login.
     * This ensures the server knows about our preset selections.
     */
    suspend fun syncLocalSettingsToServer(): Result<Unit> {
        return try {
            // Load persisted selections
            val textGenPreset = settingsDataStore.getSelectedTextGenPreset()
            val instructPreset = settingsDataStore.getSelectedInstructPreset()
            val syspromptPreset = settingsDataStore.getSelectedSyspromptPreset()

            // Also set local overrides so they're used immediately
            textGenPreset?.let { localSelectedTextGenPreset = it }
            instructPreset?.let { localSelectedInstructPreset = it }
            syspromptPreset?.let { localSelectedSyspromptPreset = it }

            // Build settings to send to server
            val settingsToSync = buildJsonObject {
                textGenPreset?.let { put("textgenerationwebui_preset", it) }
                instructPreset?.let { put("instruct_preset", it) }
                syspromptPreset?.let { put("sysprompt_preset", it) }
            }

            // Only send if we have something to sync
            if (settingsToSync.isNotEmpty()) {
                Log.d("STRepo", "Syncing local settings to server: textgen=$textGenPreset, instruct=$instructPreset, sysprompt=$syspromptPreset")
                val response = api.saveServerSettings(settingsToSync)
                if (!response.isSuccessful) {
                    Log.w("STRepo", "Failed to sync settings to server: ${response.code()}")
                }
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to sync local settings to server", e)
            Result.Error(Exception("Failed to sync settings: ${e.message}", e))
        }
    }

    suspend fun getFullSettings(): Result<FullSettingsResponse> {
        return try {
            val response = api.getFullSettings(emptyMap())
            Log.d("STRepo", "Full settings loaded - instruct: ${response.instruct?.size ?: 0}, context: ${response.context?.size ?: 0}, sysprompt: ${response.sysprompt?.size ?: 0}")
            cachedFullSettings = response
            Result.Success(response)
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to get full settings", e)
            Result.Error(Exception("Failed to get settings: ${e.message}", e))
        }
    }

    private fun parsePresetString(presetString: String?): JsonObject? {
        if (presetString.isNullOrBlank()) return null
        return try {
            settingsParser.decodeFromString<JsonObject>(presetString)
        } catch (e: Exception) {
            Log.w("STRepo", "Failed to parse preset string: ${e.message}")
            null
        }
    }

    private fun getFloatFromJson(json: JsonObject?, vararg keys: String): Float? {
        if (json == null) return null
        for (key in keys) {
            try {
                val value = json[key]?.jsonPrimitive?.content?.toFloatOrNull()
                if (value != null) return value
            } catch (e: Exception) {
                // Continue to next key
            }
        }
        return null
    }

    private fun getIntFromJson(json: JsonObject?, vararg keys: String): Int? {
        if (json == null) return null
        for (key in keys) {
            try {
                val value = json[key]?.jsonPrimitive?.content?.toIntOrNull()
                if (value != null) return value
            } catch (e: Exception) {
                // Continue to next key
            }
        }
        return null
    }

    private fun getBoolFromJson(json: JsonObject?, key: String, default: Boolean = false): Boolean {
        if (json == null) return default
        return try {
            val value = json[key]?.jsonPrimitive?.content
            when (value?.lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> default
            }
        } catch (e: Exception) {
            default
        }
    }

    data class TextGenPresetsResult(
        val presets: List<TextGenPreset>,
        val selectedPresetName: String
    )

    suspend fun getTextGenPresets(): Result<TextGenPresetsResult> {
        return try {
            val fullSettings = cachedFullSettings ?: api.getFullSettings(emptyMap()).also {
                cachedFullSettings = it
                settingsInitialized = true
            }
            val presetNames = fullSettings.textGenPresetNames ?: emptyList()
            val presetStrings = fullSettings.textGenPresets ?: emptyList()

            // Priority: local memory override > persisted DataStore > server settings
            val selectedPreset = localSelectedTextGenPreset
                ?: settingsDataStore.getSelectedTextGenPreset()?.also { localSelectedTextGenPreset = it }
                ?: run {
                    val settingsJson = parseSettingsString(fullSettings.settings)
                    getStringFromSettings(settingsJson, "textgenerationwebui_preset").ifBlank {
                        getStringFromSettings(settingsJson, "preset").ifBlank {
                            getStringFromSettings(settingsJson, "textgen_preset").ifBlank {
                                getStringFromSettings(settingsJson, "api_textgenerationwebui_preset")
                            }
                        }
                    }
                }
            Log.d("STRepo", "TextGen presets: ${presetNames.size}, selected='$selectedPreset' (local override: ${localSelectedTextGenPreset != null})")

            val result = presetNames.mapIndexed { index, name ->
                val json = parsePresetString(presetStrings.getOrNull(index))
                TextGenPreset(
                    name = name,
                    // Token limits
                    maxNewTokens = getIntFromJson(json, "max_new_tokens", "max_tokens", "n_predict") ?: 350,
                    minTokens = getIntFromJson(json, "min_tokens") ?: 0,
                    truncationLength = getIntFromJson(json, "truncation_length", "num_ctx") ?: 8192,
                    // Temperature and sampling
                    temperature = getFloatFromJson(json, "temp", "temperature") ?: 1.0f,
                    topP = getFloatFromJson(json, "top_p") ?: 1.0f,
                    topK = getIntFromJson(json, "top_k") ?: 0,
                    topA = getFloatFromJson(json, "top_a") ?: 0f,
                    minP = getFloatFromJson(json, "min_p") ?: 0.1f,
                    typicalP = getFloatFromJson(json, "typical_p", "typical") ?: 1.0f,
                    tfs = getFloatFromJson(json, "tfs") ?: 1.0f,
                    // Repetition penalty
                    repPen = getFloatFromJson(json, "rep_pen", "repetition_penalty", "repeat_penalty") ?: 1.18f,
                    repPenRange = getIntFromJson(json, "rep_pen_range", "repetition_penalty_range", "repeat_last_n") ?: 2048,
                    repPenSlope = getFloatFromJson(json, "rep_pen_slope") ?: 0f,
                    frequencyPenalty = getFloatFromJson(json, "frequency_penalty") ?: 0f,
                    presencePenalty = getFloatFromJson(json, "presence_penalty") ?: 0f,
                    // DRY sampler
                    dryMultiplier = getFloatFromJson(json, "dry_multiplier") ?: 0f,
                    dryBase = getFloatFromJson(json, "dry_base") ?: 1.75f,
                    dryAllowedLength = getIntFromJson(json, "dry_allowed_length") ?: 2,
                    dryPenaltyLastN = getIntFromJson(json, "dry_penalty_last_n") ?: 0,
                    // Mirostat
                    mirostatMode = getIntFromJson(json, "mirostat_mode", "mirostat") ?: 0,
                    mirostatTau = getFloatFromJson(json, "mirostat_tau") ?: 5f,
                    mirostatEta = getFloatFromJson(json, "mirostat_eta") ?: 0.1f,
                    // XTC
                    xtcThreshold = getFloatFromJson(json, "xtc_threshold") ?: 0.1f,
                    xtcProbability = getFloatFromJson(json, "xtc_probability") ?: 0f,
                    // Other sampling
                    skew = getFloatFromJson(json, "skew") ?: 0f,
                    smoothingFactor = getFloatFromJson(json, "smoothing_factor") ?: 0f,
                    smoothingCurve = getFloatFromJson(json, "smoothing_curve") ?: 1f,
                    // Guidance
                    guidanceScale = getFloatFromJson(json, "guidance_scale") ?: 1f,
                    // Token handling
                    addBosToken = getBoolFromJson(json, "add_bos_token", true),
                    banEosToken = getBoolFromJson(json, "ban_eos_token", false),
                    skipSpecialTokens = getBoolFromJson(json, "skip_special_tokens", true)
                )
            }
            Result.Success(TextGenPresetsResult(result, selectedPreset))
        } catch (e: Exception) {
            Result.Error(Exception("Failed to get presets: ${e.message}", e))
        }
    }

    suspend fun saveTextGenPreset(preset: TextGenPreset): Result<Unit> {
        return try {
            val presetJson = buildJsonObject {
                // Token limits
                put("max_new_tokens", preset.maxNewTokens)
                put("max_tokens", preset.maxNewTokens)
                put("n_predict", preset.maxNewTokens)
                put("min_tokens", preset.minTokens)
                put("truncation_length", preset.truncationLength)
                put("num_ctx", preset.truncationLength)
                // Temperature and sampling
                put("temp", preset.temperature)
                put("temperature", preset.temperature)
                put("top_p", preset.topP)
                put("top_k", preset.topK)
                put("top_a", preset.topA)
                put("min_p", preset.minP)
                put("typical_p", preset.typicalP)
                put("typical", preset.typicalP)
                put("tfs", preset.tfs)
                // Repetition penalty
                put("rep_pen", preset.repPen)
                put("repetition_penalty", preset.repPen)
                put("repeat_penalty", preset.repPen)
                put("rep_pen_range", preset.repPenRange)
                put("repetition_penalty_range", preset.repPenRange)
                put("repeat_last_n", preset.repPenRange)
                put("rep_pen_slope", preset.repPenSlope)
                put("frequency_penalty", preset.frequencyPenalty)
                put("presence_penalty", preset.presencePenalty)
                // DRY sampler
                put("dry_multiplier", preset.dryMultiplier)
                put("dry_base", preset.dryBase)
                put("dry_allowed_length", preset.dryAllowedLength)
                put("dry_penalty_last_n", preset.dryPenaltyLastN)
                // Mirostat
                put("mirostat_mode", preset.mirostatMode)
                put("mirostat", preset.mirostatMode)
                put("mirostat_tau", preset.mirostatTau)
                put("mirostat_eta", preset.mirostatEta)
                // XTC
                put("xtc_threshold", preset.xtcThreshold)
                put("xtc_probability", preset.xtcProbability)
                // Other sampling
                put("skew", preset.skew)
                put("smoothing_factor", preset.smoothingFactor)
                put("smoothing_curve", preset.smoothingCurve)
                // Guidance
                put("guidance_scale", preset.guidanceScale)
                // Token handling
                put("add_bos_token", preset.addBosToken)
                put("ban_eos_token", preset.banEosToken)
                put("skip_special_tokens", preset.skipSpecialTokens)
            }
            val request = SavePresetRequest(
                name = preset.name,
                preset = presetJson,
                apiId = "textgenerationwebui"
            )
            val response = api.savePreset(request)
            if (response.isSuccessful) {
                cachedFullSettings = null // Invalidate cache
                Result.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.Error(Exception("Failed to save preset (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Failed to save preset: ${e.message}", e))
        }
    }

    suspend fun deleteTextGenPreset(name: String): Result<Unit> {
        return try {
            val request = DeletePresetRequest(name = name, apiId = "textgenerationwebui")
            val response = api.deletePreset(request)
            if (response.isSuccessful) {
                cachedFullSettings = null // Invalidate cache
                Result.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.Error(Exception("Failed to delete preset (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Failed to delete preset: ${e.message}", e))
        }
    }

    /**
     * Select a text generation preset (save the selection to server settings)
     */
    suspend fun selectTextGenPreset(presetName: String): Result<Unit> {
        // Save locally first so it persists even if server save fails or cache refreshes
        localSelectedTextGenPreset = presetName
        // Persist to DataStore so it survives app restarts
        settingsDataStore.setSelectedTextGenPreset(presetName)

        return try {
            val settingsJson = buildJsonObject {
                put("textgenerationwebui_preset", presetName)
            }

            val response = api.saveServerSettings(settingsJson)
            if (response.isSuccessful) {
                // Don't invalidate cache - we have the local override
                Log.d("STRepo", "Selected TextGen preset: $presetName (saved to server)")
                Result.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.w("STRepo", "Failed to save preset to server, but local selection preserved")
                Result.Error(Exception("Failed to select preset (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to select TextGen preset on server, local selection preserved", e)
            Result.Error(Exception("Failed to select preset: ${e.message}", e))
        }
    }

    // ========== Formatting Settings ==========

    private val settingsParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun parseSettingsString(settingsString: String?): JsonObject? {
        if (settingsString.isNullOrBlank()) return null
        return try {
            settingsParser.decodeFromString<JsonObject>(settingsString)
        } catch (e: Exception) {
            Log.w("STRepo", "Failed to parse settings string: ${e.message}")
            null
        }
    }

    private fun getStringFromSettings(json: JsonObject?, key: String): String {
        return try {
            json?.get(key)?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // Get string from nested object (e.g., oai_settings.chat_completion_source)
    private fun getNestedString(json: JsonObject?, parentKey: String, key: String): String {
        return try {
            val parent = json?.get(parentKey)?.jsonObject
            parent?.get(key)?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            Log.w("STRepo", "Failed to get nested string $parentKey.$key: ${e.message}")
            ""
        }
    }

    suspend fun getFormattingSettings(): Result<FormattingSettings> {
        return try {
            // Use cached settings if available, only fetch on first load
            val fullSettings = cachedFullSettings ?: api.getFullSettings(emptyMap()).also {
                cachedFullSettings = it
                settingsInitialized = true
            }

            // Try to get names from dedicated array first, otherwise extract from objects
            val instructPresets = fullSettings.instructPresetNames?.takeIf { it.isNotEmpty() }
                ?: fullSettings.instruct?.mapNotNull { it["name"]?.jsonPrimitive?.content }
                ?: emptyList()

            val contextPresets = fullSettings.contextPresetNames?.takeIf { it.isNotEmpty() }
                ?: fullSettings.context?.mapNotNull { it["name"]?.jsonPrimitive?.content }
                ?: emptyList()

            val syspromptPresets = fullSettings.syspromptPresetNames?.takeIf { it.isNotEmpty() }
                ?: fullSettings.sysprompt?.mapNotNull { it["name"]?.jsonPrimitive?.content }
                ?: emptyList()

            Log.d("STRepo", "Formatting presets - instruct: ${instructPresets.size}, context: ${contextPresets.size}, sysprompt: ${syspromptPresets.size}")

            // Parse the settings string into a JsonObject
            val settingsJson = parseSettingsString(fullSettings.settings)

            // Priority: local memory override > persisted DataStore > server settings
            val selectedInstruct = localSelectedInstructPreset
                ?: settingsDataStore.getSelectedInstructPreset()?.also { localSelectedInstructPreset = it }
                ?: getStringFromSettings(settingsJson, "instruct_preset")
            val selectedContext = getStringFromSettings(settingsJson, "context_preset")
            val selectedSysprompt = localSelectedSyspromptPreset
                ?: settingsDataStore.getSelectedSyspromptPreset()?.also { localSelectedSyspromptPreset = it }
                ?: getStringFromSettings(settingsJson, "sysprompt_preset")
            val customPrompt = getStringFromSettings(settingsJson, "custom_system_prompt")

            Log.d("STRepo", "Formatting selected - instruct: $selectedInstruct (local: ${localSelectedInstructPreset != null}), sysprompt: $selectedSysprompt (local: ${localSelectedSyspromptPreset != null})")

            Result.Success(
                FormattingSettings(
                    instructPresets = instructPresets,
                    selectedInstructPreset = selectedInstruct,
                    contextPresets = contextPresets,
                    selectedContextPreset = selectedContext,
                    systemPromptPresets = syspromptPresets,
                    selectedSystemPromptPreset = selectedSysprompt,
                    customSystemPrompt = customPrompt
                )
            )
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to get formatting settings", e)
            Result.Error(Exception("Failed to get formatting settings: ${e.message}", e))
        }
    }

    suspend fun saveFormattingSettings(
        instructPreset: String?,
        contextPreset: String?,
        syspromptPreset: String?,
        customSystemPrompt: String?
    ): Result<Unit> {
        // Save local overrides first so they persist
        instructPreset?.let {
            localSelectedInstructPreset = it
            settingsDataStore.setSelectedInstructPreset(it)
        }
        syspromptPreset?.let {
            localSelectedSyspromptPreset = it
            settingsDataStore.setSelectedSyspromptPreset(it)
        }

        return try {
            val settingsJson = buildJsonObject {
                instructPreset?.let { put("instruct_preset", it) }
                contextPreset?.let { put("context_preset", it) }
                syspromptPreset?.let { put("sysprompt_preset", it) }
                customSystemPrompt?.let { put("custom_system_prompt", it) }
            }
            val response = api.saveServerSettings(settingsJson)
            if (response.isSuccessful) {
                // Apply the formatting settings locally
                applyFormattingSettings(instructPreset, syspromptPreset, customSystemPrompt)
                // Don't invalidate cache - we have local overrides
                Log.d("STRepo", "Formatting settings saved to server")
                Result.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.w("STRepo", "Failed to save formatting to server, local selection preserved")
                Result.Error(Exception("Failed to save settings (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to save formatting settings to server, local selection preserved", e)
            Result.Error(Exception("Failed to save formatting settings: ${e.message}", e))
        }
    }

    private suspend fun applyFormattingSettings(
        instructPresetName: String?,
        syspromptPresetName: String?,
        customSystemPrompt: String?
    ) {
        try {
            val fullSettings = cachedFullSettings ?: api.getFullSettings(emptyMap()).also { cachedFullSettings = it }

            // Load system prompt - custom takes priority, then preset, then template default
            currentSystemPrompt = when {
                !customSystemPrompt.isNullOrBlank() -> customSystemPrompt
                !syspromptPresetName.isNullOrBlank() -> {
                    // Load content from sysprompt preset
                    val syspromptObj = fullSettings.sysprompt?.find { obj ->
                        obj["name"]?.jsonPrimitive?.content == syspromptPresetName
                    }
                    syspromptObj?.get("content")?.jsonPrimitive?.content ?: ""
                }
                else -> ""
            }
            Log.d("STRepo", "Applied system prompt: ${currentSystemPrompt.take(100)}...")

            // Load and store the instruct template
            if (instructPresetName.isNullOrBlank()) {
                currentInstructTemplate = null
                return
            }

            val instructObj = fullSettings.instruct?.find { obj ->
                obj["name"]?.jsonPrimitive?.content == instructPresetName
            }

            if (instructObj != null) {
                currentInstructTemplate = InstructTemplate(
                    name = instructPresetName,
                    systemPrompt = instructObj["system_prompt"]?.jsonPrimitive?.content ?: "",
                    inputSequence = instructObj["input_sequence"]?.jsonPrimitive?.content ?: "",
                    inputSuffix = instructObj["input_suffix"]?.jsonPrimitive?.content ?: "",
                    outputSequence = instructObj["output_sequence"]?.jsonPrimitive?.content ?: "",
                    outputSuffix = instructObj["output_suffix"]?.jsonPrimitive?.content ?: "",
                    firstOutputSequence = instructObj["first_output_sequence"]?.jsonPrimitive?.content ?: "",
                    lastOutputSequence = instructObj["last_output_sequence"]?.jsonPrimitive?.content ?: "",
                    systemSequence = instructObj["system_sequence"]?.jsonPrimitive?.content ?: "",
                    systemSuffix = instructObj["system_suffix"]?.jsonPrimitive?.content ?: "",
                    stopSequence = instructObj["stop_sequence"]?.jsonPrimitive?.content ?: "",
                    separatorSequence = instructObj["separator_sequence"]?.jsonPrimitive?.content ?: "",
                    wrap = instructObj["wrap"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                )
                Log.d("STRepo", "Applied instruct template: $instructPresetName")
            } else {
                Log.w("STRepo", "Instruct template not found: $instructPresetName")
                currentInstructTemplate = null
            }
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to apply formatting settings: ${e.message}")
            currentInstructTemplate = null
        }
    }

    // Load current formatting settings from server
    private suspend fun loadCurrentFormattingSettings() {
        val fullSettings = api.getFullSettings(emptyMap()).also { cachedFullSettings = it }
        val settingsJson = parseSettingsString(fullSettings.settings)

        val selectedInstruct = getStringFromSettings(settingsJson, "instruct_preset")
        val selectedSysprompt = getStringFromSettings(settingsJson, "sysprompt_preset")
        val customSystemPrompt = getStringFromSettings(settingsJson, "custom_system_prompt")

        Log.d("STRepo", "Loading formatting - instruct: $selectedInstruct, sysprompt: $selectedSysprompt")

        // Load TextGen preset for text completion APIs
        if (!usesChatCompletions()) {
            try {
                // Get selected preset name
                val selectedPreset = getStringFromSettings(settingsJson, "textgenerationwebui_preset").ifBlank {
                    getStringFromSettings(settingsJson, "preset").ifBlank {
                        getStringFromSettings(settingsJson, "textgen_preset").ifBlank {
                            getStringFromSettings(settingsJson, "api_textgenerationwebui_preset")
                        }
                    }
                }
                Log.d("STRepo", "Selected TextGen preset: $selectedPreset")

                if (selectedPreset.isNotBlank()) {
                    val presetNames = fullSettings.textGenPresetNames ?: emptyList()
                    val presetStrings = fullSettings.textGenPresets ?: emptyList()
                    val presetIndex = presetNames.indexOf(selectedPreset)

                    if (presetIndex >= 0) {
                        val presetJson = parsePresetString(presetStrings.getOrNull(presetIndex))
                        if (presetJson != null) {
                            currentPreset = TextGenPreset(
                                name = selectedPreset,
                                maxNewTokens = getIntFromJson(presetJson, "max_new_tokens", "max_tokens", "n_predict"),
                                minTokens = getIntFromJson(presetJson, "min_tokens") ?: 0,
                                truncationLength = getIntFromJson(presetJson, "truncation_length", "num_ctx") ?: 2048,
                                temperature = getFloatFromJson(presetJson, "temp", "temperature") ?: 0.7f,
                                topP = getFloatFromJson(presetJson, "top_p") ?: 0.5f,
                                topK = getIntFromJson(presetJson, "top_k") ?: 40,
                                topA = getFloatFromJson(presetJson, "top_a") ?: 0f,
                                minP = getFloatFromJson(presetJson, "min_p") ?: 0f,
                                typicalP = getFloatFromJson(presetJson, "typical_p", "typical") ?: 1.0f,
                                tfs = getFloatFromJson(presetJson, "tfs") ?: 1.0f,
                                repPen = getFloatFromJson(presetJson, "rep_pen", "repetition_penalty", "repeat_penalty") ?: 1.2f,
                                repPenRange = getIntFromJson(presetJson, "rep_pen_range", "repetition_penalty_range", "repeat_last_n") ?: 0,
                                repPenSlope = getFloatFromJson(presetJson, "rep_pen_slope") ?: 1f,
                                frequencyPenalty = getFloatFromJson(presetJson, "frequency_penalty") ?: 0f,
                                presencePenalty = getFloatFromJson(presetJson, "presence_penalty") ?: 0f,
                                dryMultiplier = getFloatFromJson(presetJson, "dry_multiplier") ?: 0f,
                                dryBase = getFloatFromJson(presetJson, "dry_base") ?: 1.75f,
                                dryAllowedLength = getIntFromJson(presetJson, "dry_allowed_length") ?: 2,
                                dryPenaltyLastN = getIntFromJson(presetJson, "dry_penalty_last_n") ?: 0,
                                mirostatMode = getIntFromJson(presetJson, "mirostat_mode", "mirostat") ?: 0,
                                mirostatTau = getFloatFromJson(presetJson, "mirostat_tau") ?: 5f,
                                mirostatEta = getFloatFromJson(presetJson, "mirostat_eta") ?: 0.1f,
                                xtcThreshold = getFloatFromJson(presetJson, "xtc_threshold") ?: 0.1f,
                                xtcProbability = getFloatFromJson(presetJson, "xtc_probability") ?: 0f,
                                skew = getFloatFromJson(presetJson, "skew") ?: 0f,
                                smoothingFactor = getFloatFromJson(presetJson, "smoothing_factor") ?: 0f,
                                smoothingCurve = getFloatFromJson(presetJson, "smoothing_curve") ?: 1f,
                                guidanceScale = getFloatFromJson(presetJson, "guidance_scale") ?: 1f,
                                addBosToken = getBoolFromJson(presetJson, "add_bos_token", true),
                                banEosToken = getBoolFromJson(presetJson, "ban_eos_token", false),
                                skipSpecialTokens = getBoolFromJson(presetJson, "skip_special_tokens", true)
                            )
                            Log.d("STRepo", "Loaded TextGen preset: ${currentPreset?.name} - temp: ${currentPreset?.temperature}, topP: ${currentPreset?.topP}, topK: ${currentPreset?.topK}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("STRepo", "Failed to load TextGen preset: ${e.message}")
            }
        }

        // Load OAI/Chat Completion preset when using chat completion APIs
        if (usesChatCompletions()) {
            try {
                val oaiSettings = settingsJson?.get("oai_settings")?.jsonObject

                // Get the selected preset name
                val selectedOaiPreset = oaiSettings?.get("preset_settings_openai")?.jsonPrimitive?.content ?: "Default"

                DebugLogger.logSection("Chat Completion Preset Loading")
                DebugLogger.logKeyValue("Selected preset name", selectedOaiPreset)
                DebugLogger.logKeyValue("Available preset names", fullSettings.openaiSettingNames?.joinToString(", ") ?: "none")
                DebugLogger.logKeyValue("Available presets count", fullSettings.openaiSettings?.size ?: 0)

                // Find and parse the preset (openai_settings comes as JSON strings)
                val presetIndex = fullSettings.openaiSettingNames?.indexOf(selectedOaiPreset) ?: -1
                DebugLogger.logKeyValue("Preset index", presetIndex)
                val presetString = if (presetIndex >= 0) fullSettings.openaiSettings?.getOrNull(presetIndex) else null
                DebugLogger.logKeyValue("Preset string found", presetString != null)
                val preset = presetString?.let {
                    try {
                        settingsParser.decodeFromString<JsonObject>(it)
                    } catch (e: Exception) {
                        Log.w("STRepo", "Failed to parse OAI preset: ${e.message}")
                        null
                    }
                }

                // Helper to load prompts and order from a JsonObject
                fun loadPromptsFromJson(source: JsonObject) {
                    // Clear existing prompts
                    oaiPrompts.clear()

                    // Load ALL prompts (not just specific ones)
                    val prompts = source["prompts"]?.jsonArray
                    DebugLogger.logKeyValue("Prompts array size", prompts?.size ?: 0)

                    prompts?.forEach { promptElement ->
                        val prompt = promptElement.jsonObject
                        val identifier = prompt["identifier"]?.jsonPrimitive?.content ?: return@forEach
                        val content = prompt["content"]?.jsonPrimitive?.content ?: ""
                        val isMarker = prompt["marker"]?.jsonPrimitive?.booleanOrNull ?: false
                        // Store prompts that have content (not just markers)
                        if (content.isNotBlank() || !isMarker) {
                            oaiPrompts[identifier] = content
                            DebugLogger.log("  Loaded prompt '$identifier': ${content.take(50)}...")
                        }
                    }

                    // Load prompt order (use character_id 100001 for default, or first available)
                    val promptOrderArray = source["prompt_order"]?.jsonArray
                    DebugLogger.logKeyValue("Prompt order array size", promptOrderArray?.size ?: 0)

                    val orderEntry = promptOrderArray?.firstOrNull { entry ->
                        entry.jsonObject["character_id"]?.jsonPrimitive?.intOrNull == 100001
                    } ?: promptOrderArray?.firstOrNull()

                    oaiPromptOrder = orderEntry?.jsonObject?.get("order")?.jsonArray?.mapNotNull { orderItem ->
                        val obj = orderItem.jsonObject
                        val id = obj["identifier"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
                        id to enabled
                    } ?: emptyList()

                    DebugLogger.logKeyValue("Loaded OAI prompts", oaiPrompts.size)
                    DebugLogger.logKeyValue("Loaded prompt order items", oaiPromptOrder.size)
                    DebugLogger.log("Prompt order: ${oaiPromptOrder.map { "${it.first}=${it.second}" }.joinToString(", ")}")
                    Log.d("STRepo", "Loaded ${oaiPrompts.size} OAI prompts, order: ${oaiPromptOrder.size} items")
                }

                if (preset != null) {
                    // Load sampler settings from preset
                    oaiTemperature = preset["temperature"]?.jsonPrimitive?.floatOrNull ?: 1.0f
                    oaiTopP = preset["top_p"]?.jsonPrimitive?.floatOrNull ?: 1.0f
                    oaiTopK = preset["top_k"]?.jsonPrimitive?.intOrNull ?: 0
                    oaiTopA = preset["top_a"]?.jsonPrimitive?.floatOrNull ?: 0f
                    oaiMinP = preset["min_p"]?.jsonPrimitive?.floatOrNull ?: 0f
                    oaiRepetitionPenalty = preset["repetition_penalty"]?.jsonPrimitive?.floatOrNull ?: 1f
                    oaiFrequencyPenalty = preset["frequency_penalty"]?.jsonPrimitive?.floatOrNull ?: 0f
                    oaiPresencePenalty = preset["presence_penalty"]?.jsonPrimitive?.floatOrNull ?: 0f
                    oaiMaxTokens = preset["openai_max_tokens"]?.jsonPrimitive?.intOrNull ?: 300
                    oaiMaxContext = preset["openai_max_context"]?.jsonPrimitive?.intOrNull ?: 4095
                    oaiStream = preset["stream_openai"]?.jsonPrimitive?.booleanOrNull ?: true

                    // Load prompts and order from preset
                    loadPromptsFromJson(preset)
                    Log.d("STRepo", "Loaded OAI preset - temp: $oaiTemperature, topP: $oaiTopP, maxTokens: $oaiMaxTokens")
                } else if (oaiSettings != null) {
                    // Fallback: load from oai_settings in main settings
                    oaiTemperature = oaiSettings.get("temp_openai")?.jsonPrimitive?.floatOrNull ?: 1.0f
                    oaiTopP = oaiSettings.get("top_p_openai")?.jsonPrimitive?.floatOrNull ?: 1.0f
                    oaiMaxTokens = oaiSettings.get("openai_max_tokens")?.jsonPrimitive?.intOrNull ?: 300

                    loadPromptsFromJson(oaiSettings)
                    Log.d("STRepo", "Loaded OAI settings from oai_settings (preset not found)")
                }
            } catch (e: Exception) {
                Log.w("STRepo", "Failed to load OAI settings: ${e.message}")
            }
        }

        // Apply the settings
        applyFormattingSettings(
            selectedInstruct.ifBlank { null },
            selectedSysprompt.ifBlank { null },
            customSystemPrompt.ifBlank { null }
        )
    }

    // Apply current text gen settings
    fun applyTextGenSettings(preset: TextGenPreset) {
        currentPreset = preset
        // Also update textGenSettings for backwards compatibility
        textGenSettings = TextGenSettings(
            temp = preset.temperature,
            topP = preset.topP,
            repPen = preset.repPen
        )
    }

    // ========== API Configuration ==========

    /**
     * Get current API configuration from SillyTavern settings
     */
    suspend fun getApiConfiguration(): Result<ApiConfiguration> {
        return try {
            val fullSettings = cachedFullSettings ?: api.getFullSettings(emptyMap()).also { cachedFullSettings = it }
            val settingsJson = parseSettingsString(fullSettings.settings)

            val currentMainApi = getStringFromSettings(settingsJson, "main_api").ifBlank { "textgenerationwebui" }
            val currentTextGenType = getStringFromSettings(settingsJson, "api_type_textgenerationwebui").ifBlank {
                apiType // fallback to what we detected
            }
            val currentApiServer = getStringFromSettings(settingsJson, "api_server_textgenerationwebui").ifBlank {
                apiServer // fallback
            }
            val currentChatSource = getNestedString(settingsJson, "oai_settings", "chat_completion_source").ifBlank { "openai" }
            val currentCustomUrl = getNestedString(settingsJson, "oai_settings", "custom_openai_url").ifBlank { null }

            // Get current model based on API type
            val currentModel = if (currentMainApi.lowercase() == "openai") {
                when (currentChatSource.lowercase()) {
                    "nanogpt" -> getNestedString(settingsJson, "oai_settings", "nanogpt_model")
                    "openrouter" -> getNestedString(settingsJson, "oai_settings", "openrouter_model")
                    "mistralai" -> getNestedString(settingsJson, "oai_settings", "mistralai_model")
                    "cohere" -> getNestedString(settingsJson, "oai_settings", "cohere_model")
                    "perplexity" -> getNestedString(settingsJson, "oai_settings", "perplexity_model")
                    "groq" -> getNestedString(settingsJson, "oai_settings", "groq_model")
                    "deepseek" -> getNestedString(settingsJson, "oai_settings", "deepseek_model")
                    "claude" -> getNestedString(settingsJson, "oai_settings", "claude_model")
                    else -> getNestedString(settingsJson, "oai_settings", "openai_model")
                }
            } else {
                // For text completion, model is usually auto-detected from backend
                ""
            }

            Result.Success(
                ApiConfiguration(
                    mainApi = currentMainApi,
                    textGenType = currentTextGenType,
                    apiServer = currentApiServer,
                    chatCompletionSource = currentChatSource,
                    customUrl = currentCustomUrl,
                    currentModel = currentModel,
                    isConnected = true
                )
            )
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to get API configuration", e)
            Result.Error(Exception("Failed to get API configuration: ${e.message}", e))
        }
    }

    /**
     * Fetch available models from the appropriate backend status endpoint
     */
    suspend fun getAvailableModels(config: ApiConfiguration): Result<List<AvailableModel>> {
        return try {
            val models = if (config.usesChatCompletions) {
                // Chat completion - query chat-completions status endpoint
                val request = ChatCompletionStatusRequest(
                    chatCompletionSource = config.chatCompletionSource,
                    customUrl = config.customUrl
                )
                val response = api.getChatCompletionStatus(request)
                if (response.isSuccessful) {
                    response.body()?.data?.map { modelInfo ->
                        AvailableModel(
                            id = modelInfo.id,
                            name = modelInfo.name ?: modelInfo.id,
                            contextLength = modelInfo.context_length
                        )
                    } ?: emptyList()
                } else {
                    Log.w("STRepo", "Chat completion status failed: ${response.code()}")
                    emptyList()
                }
            } else {
                // Text completion - query text-completions status endpoint
                val request = TextCompletionStatusRequest(
                    apiServer = config.apiServer,
                    apiType = config.textGenType
                )
                val response = api.getTextCompletionStatus(request)
                if (response.isSuccessful) {
                    response.body()?.data?.map { modelInfo ->
                        AvailableModel(
                            id = modelInfo.id,
                            name = modelInfo.name ?: modelInfo.id,
                            contextLength = modelInfo.context_length
                        )
                    } ?: emptyList()
                } else {
                    Log.w("STRepo", "Text completion status failed: ${response.code()}")
                    emptyList()
                }
            }

            Log.d("STRepo", "Found ${models.size} available models")
            Result.Success(models)
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to get available models", e)
            Result.Error(Exception("Failed to get models: ${e.message}", e))
        }
    }

    /**
     * Save API configuration changes to SillyTavern
     */
    suspend fun saveApiConfiguration(config: ApiConfiguration): Result<Unit> {
        return try {
            val settingsJson = buildJsonObject {
                put("main_api", config.mainApi)

                if (config.usesChatCompletions) {
                    // For chat completion APIs, we need to update oai_settings
                    // This is tricky because oai_settings is nested - we may need a different approach
                    // For now, update the top-level settings that affect generation
                } else {
                    // For text completion APIs
                    put("api_type_textgenerationwebui", config.textGenType)
                    put("api_server_textgenerationwebui", config.apiServer)
                }
            }

            val response = api.saveServerSettings(settingsJson)
            if (response.isSuccessful) {
                // Update local state
                mainApi = config.mainApi
                if (!config.usesChatCompletions) {
                    apiType = config.textGenType
                    apiServer = config.apiServer
                } else {
                    chatCompletionSource = config.chatCompletionSource
                    openaiModel = config.currentModel
                    customOpenaiUrl = config.customUrl ?: ""
                }

                cachedFullSettings = null  // Invalidate cache
                Log.d("STRepo", "API configuration saved: ${config.displayName}")
                Result.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.Error(Exception("Failed to save settings (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to save API configuration", e)
            Result.Error(Exception("Failed to save API configuration: ${e.message}", e))
        }
    }

    /**
     * Get current API display info for UI status bar
     */
    fun getCurrentApiInfo(): Pair<String, String> {
        return if (usesChatCompletions()) {
            val sourceName = ApiConfiguration.chatCompletionSourceDisplayName(chatCompletionSource)
            sourceName to openaiModel
        } else {
            val typeName = ApiConfiguration.textGenTypeDisplayName(apiType)
            typeName to ""  // Model is auto-detected for text completion
        }
    }

    // ========== Context Loading (Pull from SillyTavern) ==========

    /**
     * Load complete chat context from SillyTavern.
     * This pulls all data needed for context building: character, world info, persona, author's note.
     */
    suspend fun loadChatContext(character: Character, chatMessages: List<ChatMessage>): Result<ChatContext> {
        return try {
            val fullSettings = cachedFullSettings ?: api.getFullSettings(emptyMap()).also { cachedFullSettings = it }
            val settingsJson = parseSettingsString(fullSettings.settings) ?: buildJsonObject {}

            // Load user persona from settings
            val userPersona = loadUserPersonaFromSettings(settingsJson)

            // Load author's note from chat (first message might have chat_metadata)
            val authorsNote = extractAuthorsNoteFromChat(chatMessages)

            // Load world info entries
            val worldInfoEntries = loadWorldInfoEntries(settingsJson, character)
            val worldInfoSettings = loadWorldInfoSettings(settingsJson)

            // Get instruct template
            val instructTemplate = currentInstructTemplate

            Result.Success(
                ChatContext(
                    characterName = character.name,
                    characterDescription = character.description,
                    characterPersonality = character.personality,
                    characterScenario = character.scenario,
                    characterFirstMessage = character.firstMessage,
                    characterMessageExamples = character.messageExample,
                    characterSystemPrompt = character.systemPrompt,
                    characterPostHistoryInstructions = "", // Load if available
                    userPersona = userPersona,
                    authorsNote = authorsNote,
                    worldInfoEntries = worldInfoEntries,
                    worldInfoSettings = worldInfoSettings,
                    instructTemplate = instructTemplate,
                    contextTemplate = null, // Loaded separately via formatting settings
                    systemPromptPreset = currentSystemPrompt,
                    oaiPrompts = oaiPrompts.toMap(),
                    oaiPromptOrder = oaiPromptOrder.map { PromptOrderEntry(it.first, it.second) },
                    isLoaded = true,
                    lastModified = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to load chat context", e)
            Result.Error(Exception("Failed to load context: ${e.message}", e))
        }
    }

    private fun loadUserPersonaFromSettings(settingsJson: JsonObject): UserPersona {
        return try {
            val personaDesc = getStringFromSettings(settingsJson, "persona_description")
            val personaPosition = settingsJson["persona_description_position"]?.jsonPrimitive?.intOrNull ?: 0
            val personaDepth = settingsJson["persona_description_depth"]?.jsonPrimitive?.intOrNull ?: 2
            val personaRole = settingsJson["persona_description_role"]?.jsonPrimitive?.intOrNull ?: 0

            // Get persona name from selected avatar (filename without extension)
            val userAvatar = getStringFromSettings(settingsJson, "user_avatar")
            val personaName = if (userAvatar.isNotBlank()) {
                userAvatar.substringBeforeLast(".").ifBlank { "User" }
            } else {
                getStringFromSettings(settingsJson, "name1").ifBlank { "User" }
            }

            UserPersona(
                name = personaName,
                description = personaDesc,
                position = personaPosition,
                depth = personaDepth,
                role = personaRole
            )
        } catch (e: Exception) {
            Log.w("STRepo", "Failed to load user persona: ${e.message}")
            UserPersona()
        }
    }

    private fun extractAuthorsNoteFromChat(messages: List<ChatMessage>): AuthorsNote {
        // Extract author's note from first message's chat metadata
        val firstMessage = messages.firstOrNull() ?: return AuthorsNote()
        val metadata = firstMessage.chatMetadata ?: return AuthorsNote()

        return AuthorsNote(
            content = metadata.notePrompt ?: "",
            interval = metadata.noteInterval ?: 1,
            depth = metadata.noteDepth ?: 4,
            position = metadata.notePosition ?: 0,
            role = metadata.noteRole ?: 0
        )
    }

    private suspend fun loadWorldInfoEntries(settingsJson: JsonObject, character: Character): List<WorldInfoEntry> {
        val entries = mutableListOf<WorldInfoEntry>()

        try {
            // Get world info settings to find selected world
            val worldInfoSettings = settingsJson["world_info_settings"]?.jsonObject
            val worldInfoObj = worldInfoSettings?.get("world_info")?.jsonObject

            // The selected global world is in world_info.globalSelect array
            // e.g., {"globalSelect": ["Eldoria"]}
            val selectedWorld: String? = try {
                worldInfoObj?.get("globalSelect")?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            } catch (e: Exception) {
                // Fallback: try as direct string (older format?)
                try {
                    worldInfoSettings?.get("world_info")?.jsonPrimitive?.content
                } catch (e2: Exception) { null }
            }

            DebugLogger.logSection("World Info Loading")
            DebugLogger.logKeyValue("world_info.globalSelect", selectedWorld ?: "null")
            DebugLogger.logKeyValue("Character attached world", character.attachedWorldInfo ?: "null")

            // Use global world if selected
            val globalWorld: String? = selectedWorld?.takeIf { it.isNotBlank() }

            // Load global world info if selected
            if (!globalWorld.isNullOrBlank()) {
                DebugLogger.log("Loading global world info: $globalWorld")
                val worldInfo = api.getWorldInfo(GetWorldInfoRequest(name = globalWorld))
                worldInfo.entries?.forEach { (uid, entry) ->
                    entries.add(
                        WorldInfoEntry(
                            uid = uid,
                            key = entry.key,
                            keysecondary = entry.keysecondary,
                            content = entry.content,
                            comment = entry.comment,
                            constant = entry.constant,
                            selective = entry.selective,
                            order = entry.order,
                            position = entry.position,
                            depth = entry.depth,
                            probability = entry.probability,
                            enabled = entry.enabled,
                            group = entry.group,
                            scanDepth = entry.scanDepth,
                            caseSensitive = entry.caseSensitive,
                            matchWholeWords = entry.matchWholeWords
                        )
                    )
                }
                DebugLogger.log("Loaded ${worldInfo.entries?.size ?: 0} entries from global world: $globalWorld")
            } else {
                DebugLogger.log("No global world info selected")
            }

            // Load character-attached world info if present
            val attachedWorld = character.attachedWorldInfo
            if (!attachedWorld.isNullOrBlank() && attachedWorld != globalWorld) {
                DebugLogger.log("Loading character-attached world info: $attachedWorld")
                try {
                    val charWorldInfo = api.getWorldInfo(GetWorldInfoRequest(name = attachedWorld))
                    charWorldInfo.entries?.forEach { (uid, entry) ->
                        entries.add(
                            WorldInfoEntry(
                                uid = "char_$uid",  // Prefix to distinguish from global
                                key = entry.key,
                                keysecondary = entry.keysecondary,
                                content = entry.content,
                                comment = entry.comment,
                                constant = entry.constant,
                                selective = entry.selective,
                                order = entry.order,
                                position = entry.position,
                                depth = entry.depth,
                                probability = entry.probability,
                                enabled = entry.enabled,
                                group = entry.group,
                                scanDepth = entry.scanDepth,
                                caseSensitive = entry.caseSensitive,
                                matchWholeWords = entry.matchWholeWords
                            )
                        )
                    }
                    Log.d("STRepo", "Loaded ${charWorldInfo.entries?.size ?: 0} entries from character world info: $attachedWorld")
                } catch (e: Exception) {
                    Log.w("STRepo", "Failed to load character world info '$attachedWorld': ${e.message}")
                }
            }
            DebugLogger.log("Total World Info entries loaded: ${entries.size}")
            Log.d("STRepo", "Loaded ${entries.size} total world info entries")
        } catch (e: Exception) {
            DebugLogger.log("Failed to load world info: ${e.message}")
            Log.w("STRepo", "Failed to load world info: ${e.message}")
        }

        return entries
    }

    private fun loadWorldInfoSettings(settingsJson: JsonObject): WorldInfoSettings {
        return try {
            val wiSettings = settingsJson["world_info_settings"]?.jsonObject
            WorldInfoSettings(
                depth = wiSettings?.get("world_info_depth")?.jsonPrimitive?.intOrNull ?: 2,
                budget = wiSettings?.get("world_info_budget")?.jsonPrimitive?.intOrNull ?: 25,
                budgetCap = wiSettings?.get("world_info_budget_cap")?.jsonPrimitive?.intOrNull ?: 0,
                minActivations = wiSettings?.get("world_info_min_activations")?.jsonPrimitive?.intOrNull ?: 0,
                recursive = wiSettings?.get("world_info_recursive")?.jsonPrimitive?.booleanOrNull ?: false,
                caseSensitive = wiSettings?.get("world_info_case_sensitive")?.jsonPrimitive?.booleanOrNull ?: false,
                matchWholeWords = wiSettings?.get("world_info_match_whole_words")?.jsonPrimitive?.booleanOrNull ?: false
            )
        } catch (e: Exception) {
            Log.w("STRepo", "Failed to load world info settings: ${e.message}")
            WorldInfoSettings()
        }
    }

    /**
     * Get list of available world info files (lorebooks)
     */
    suspend fun getWorldInfoList(): Result<List<com.stark.sillytavern.domain.model.WorldInfoListItem>> {
        return try {
            val list = api.getWorldInfoList()
            Result.Success(list.map {
                com.stark.sillytavern.domain.model.WorldInfoListItem(
                    fileId = it.fileId,
                    name = it.name
                )
            })
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to get world info list", e)
            Result.Error(Exception("Failed to get lorebooks: ${e.message}", e))
        }
    }

    /**
     * Get world info entries from a specific lorebook
     */
    suspend fun getWorldInfo(name: String): Result<List<WorldInfoEntry>> {
        return try {
            val worldInfo = api.getWorldInfo(GetWorldInfoRequest(name = name))
            val entries = worldInfo.entries?.map { (uid, entry) ->
                WorldInfoEntry(
                    uid = uid,
                    key = entry.key,
                    keysecondary = entry.keysecondary,
                    content = entry.content,
                    comment = entry.comment,
                    constant = entry.constant,
                    selective = entry.selective,
                    order = entry.order,
                    position = entry.position,
                    depth = entry.depth,
                    probability = entry.probability,
                    enabled = entry.enabled,
                    group = entry.group,
                    scanDepth = entry.scanDepth,
                    caseSensitive = entry.caseSensitive,
                    matchWholeWords = entry.matchWholeWords
                )
            } ?: emptyList()
            Result.Success(entries)
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to get world info", e)
            Result.Error(Exception("Failed to get lorebook: ${e.message}", e))
        }
    }

    // ========== User Persona ==========

    /**
     * Get user persona from SillyTavern settings
     */
    suspend fun getUserPersona(): Result<UserPersona> {
        return try {
            val fullSettings = cachedFullSettings ?: api.getFullSettings(emptyMap()).also { cachedFullSettings = it }
            val settingsJson = parseSettingsString(fullSettings.settings) ?: buildJsonObject {}

            val persona = loadUserPersonaFromSettings(settingsJson)
            Result.Success(persona)
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to get user persona", e)
            Result.Error(Exception("Failed to get user persona: ${e.message}", e))
        }
    }

    /**
     * Save user persona to SillyTavern settings
     */
    suspend fun saveUserPersona(persona: UserPersona): Result<Unit> {
        return try {
            val settingsJson = buildJsonObject {
                put("persona_description", persona.description)
                put("persona_description_position", persona.position)
                put("persona_description_depth", persona.depth)
                put("persona_description_role", persona.role)
            }

            val response = api.saveServerSettings(settingsJson)
            if (response.isSuccessful) {
                cachedFullSettings = null  // Invalidate cache
                Log.d("STRepo", "User persona saved successfully")
                Result.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.Error(Exception("Failed to save persona (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to save user persona", e)
            Result.Error(Exception("Failed to save user persona: ${e.message}", e))
        }
    }

    // ========== Groups (for Group Chats) ==========

    /**
     * Get all groups
     */
    suspend fun getAllGroups(): Result<List<Group>> {
        return try {
            val groups = api.getAllGroups()
            Result.Success(groups.map { dto ->
                Group(
                    id = dto.id,
                    name = dto.name,
                    members = dto.members,
                    chatId = dto.chatId,
                    avatar = dto.avatar,
                    description = dto.description,
                    favorite = dto.favorite
                )
            })
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to get groups", e)
            Result.Error(Exception("Failed to get groups: ${e.message}", e))
        }
    }

    /**
     * Get group chat messages
     */
    suspend fun getGroupChat(groupId: String): Result<List<GroupChatMessage>> {
        return try {
            val request = com.stark.sillytavern.data.remote.dto.st.GetGroupChatRequest(id = groupId)
            val messages = api.getGroupChat(request)
            Result.Success(messages.map { dto ->
                GroupChatMessage(
                    content = dto.mes ?: "",
                    isUser = dto.isUser,
                    isSystem = dto.isSystem,
                    senderName = dto.name,
                    senderAvatar = dto.originalAvatar ?: dto.forceAvatar
                )
            })
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to get group chat", e)
            Result.Error(Exception("Failed to get group chat: ${e.message}", e))
        }
    }

    /**
     * Save group chat messages
     */
    suspend fun saveGroupChat(groupId: String, messages: List<GroupChatMessage>): Result<Unit> {
        return try {
            val dtos = messages.map { msg ->
                com.stark.sillytavern.data.remote.dto.st.GroupChatMessageDto(
                    name = msg.senderName,
                    isUser = msg.isUser,
                    isSystem = msg.isSystem,
                    mes = msg.content,
                    originalAvatar = msg.senderAvatar
                )
            }
            val request = com.stark.sillytavern.data.remote.dto.st.SaveGroupChatRequest(
                id = groupId,
                chat = dtos
            )
            val response = api.saveGroupChat(request)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Failed to save group chat: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to save group chat", e)
            Result.Error(Exception("Failed to save group chat: ${e.message}", e))
        }
    }

    /**
     * Create a new group
     */
    suspend fun createGroup(name: String, members: List<String>): Result<Group> {
        return try {
            val request = com.stark.sillytavern.data.remote.dto.st.CreateGroupRequest(
                name = name,
                members = members
            )
            val response = api.createGroup(request)
            if (response.isSuccessful) {
                val dto = response.body()!!
                Result.Success(Group(
                    id = dto.id,
                    name = dto.name,
                    members = dto.members,
                    chatId = dto.chatId,
                    avatar = dto.avatar,
                    description = dto.description,
                    favorite = dto.favorite
                ))
            } else {
                Result.Error(Exception("Failed to create group: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to create group", e)
            Result.Error(Exception("Failed to create group: ${e.message}", e))
        }
    }

    /**
     * Build avatar URL for a group member
     */
    suspend fun buildGroupMemberAvatarUrl(memberAvatar: String?): String? {
        return buildAvatarUrl(memberAvatar)
    }

    // ========== Personas (User Avatars) ==========

    /**
     * Get all available personas (user avatars)
     */
    suspend fun getPersonas(): Result<List<Persona>> {
        return try {
            val avatars = api.getUserAvatars()
            val fullSettings = cachedFullSettings ?: api.getFullSettings(emptyMap()).also { cachedFullSettings = it }
            val settingsJson = parseSettingsString(fullSettings.settings) ?: buildJsonObject {}

            // Get current selection and descriptions
            val selectedAvatar = getStringFromSettings(settingsJson, "user_avatar")
            val personaDescriptions = settingsJson["persona_descriptions"]?.jsonObject

            val personas = avatars.map { avatarId ->
                val descObj = personaDescriptions?.get(avatarId)?.jsonObject
                Persona(
                    avatarId = avatarId,
                    name = avatarId.substringBeforeLast("."),
                    description = descObj?.get("description")?.jsonPrimitive?.content ?: "",
                    position = PersonaPosition.fromInt(
                        descObj?.get("position")?.jsonPrimitive?.intOrNull ?: 0
                    ),
                    role = PersonaRole.fromInt(
                        descObj?.get("role")?.jsonPrimitive?.intOrNull ?: 0
                    ),
                    depth = descObj?.get("depth")?.jsonPrimitive?.intOrNull ?: 2,
                    lorebook = descObj?.get("lorebook")?.jsonPrimitive?.content ?: "",
                    isSelected = avatarId == selectedAvatar
                )
            }

            Result.Success(personas)
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to get personas", e)
            Result.Error(Exception("Failed to get personas: ${e.message}", e))
        }
    }

    /**
     * Select a persona as active
     */
    suspend fun selectPersona(avatarId: String): Result<Unit> {
        return try {
            val fullSettings = cachedFullSettings ?: api.getFullSettings(emptyMap()).also { cachedFullSettings = it }
            val settingsJson = parseSettingsString(fullSettings.settings)?.toMutableMap() ?: mutableMapOf()

            // Update user_avatar
            settingsJson["user_avatar"] = JsonPrimitive(avatarId)

            // Get persona description to update current persona fields
            val personaDescriptions = (settingsJson["persona_descriptions"] as? JsonObject)
            val descObj = personaDescriptions?.get(avatarId)?.jsonObject

            if (descObj != null) {
                settingsJson["persona_description"] = JsonPrimitive(
                    descObj["description"]?.jsonPrimitive?.content ?: ""
                )
                settingsJson["persona_description_position"] = JsonPrimitive(
                    descObj["position"]?.jsonPrimitive?.intOrNull ?: 0
                )
                settingsJson["persona_description_role"] = JsonPrimitive(
                    descObj["role"]?.jsonPrimitive?.intOrNull ?: 0
                )
                settingsJson["persona_description_depth"] = JsonPrimitive(
                    descObj["depth"]?.jsonPrimitive?.intOrNull ?: 2
                )
            }

            // Save settings
            val response = api.saveServerSettings(JsonObject(settingsJson))
            if (response.isSuccessful) {
                cachedFullSettings = null // Invalidate cache
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Failed to save: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to select persona", e)
            Result.Error(Exception("Failed to select persona: ${e.message}", e))
        }
    }

    /**
     * Update persona description and settings
     */
    suspend fun updatePersona(
        avatarId: String,
        description: String,
        position: PersonaPosition,
        role: PersonaRole,
        depth: Int
    ): Result<Unit> {
        return try {
            val fullSettings = cachedFullSettings ?: api.getFullSettings(emptyMap()).also { cachedFullSettings = it }
            val settingsJson = parseSettingsString(fullSettings.settings)?.toMutableMap() ?: mutableMapOf()

            // Get or create persona_descriptions
            val personaDescriptions = (settingsJson["persona_descriptions"] as? JsonObject)
                ?.toMutableMap() ?: mutableMapOf()

            // Update this persona's description
            personaDescriptions[avatarId] = buildJsonObject {
                put("description", description)
                put("position", position.value)
                put("role", role.value)
                put("depth", depth)
            }

            settingsJson["persona_descriptions"] = JsonObject(personaDescriptions)

            // If this is the selected persona, also update the current fields
            val selectedAvatar = getStringFromSettings(JsonObject(settingsJson), "user_avatar")
            if (avatarId == selectedAvatar) {
                settingsJson["persona_description"] = JsonPrimitive(description)
                settingsJson["persona_description_position"] = JsonPrimitive(position.value)
                settingsJson["persona_description_role"] = JsonPrimitive(role.value)
                settingsJson["persona_description_depth"] = JsonPrimitive(depth)
            }

            // Save settings
            val response = api.saveServerSettings(JsonObject(settingsJson))
            if (response.isSuccessful) {
                cachedFullSettings = null // Invalidate cache
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Failed to save: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to update persona", e)
            Result.Error(Exception("Failed to update persona: ${e.message}", e))
        }
    }

    /**
     * Create a new persona by uploading an avatar image
     */
    suspend fun createPersona(
        imageBytes: ByteArray,
        fileName: String,
        mimeType: String,
        description: String = "",
        position: PersonaPosition = PersonaPosition.IN_PROMPT,
        role: PersonaRole = PersonaRole.SYSTEM,
        depth: Int = 2
    ): Result<String> {
        return try {
            // Upload the avatar with the specified name
            val requestBody = imageBytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val avatarPart = MultipartBody.Part.createFormData("avatar", fileName, requestBody)

            // Use overwrite_name to force the persona name
            val overwriteNamePart = fileName.toRequestBody("text/plain".toMediaType())

            val uploadResponse = api.uploadUserAvatar(avatarPart, overwriteNamePart)
            if (!uploadResponse.isSuccessful) {
                return Result.Error(Exception("Failed to upload avatar: ${uploadResponse.code()}"))
            }

            val avatarId = uploadResponse.body()?.path ?: return Result.Error(Exception("No avatar path returned"))

            // If we have a description, save it
            if (description.isNotBlank()) {
                updatePersona(avatarId, description, position, role, depth)
            }

            Result.Success(avatarId)
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to create persona", e)
            Result.Error(Exception("Failed to create persona: ${e.message}", e))
        }
    }

    /**
     * Delete a persona (avatar)
     */
    suspend fun deletePersona(avatarId: String): Result<Unit> {
        return try {
            val response = api.deleteUserAvatar(DeleteAvatarRequest(avatar = avatarId))
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Failed to delete: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("STRepo", "Failed to delete persona", e)
            Result.Error(Exception("Failed to delete persona: ${e.message}", e))
        }
    }

    /**
     * Get the currently selected persona's avatar ID
     */
    suspend fun getSelectedPersonaId(): String? {
        return try {
            val fullSettings = cachedFullSettings ?: api.getFullSettings(emptyMap()).also { cachedFullSettings = it }
            val settingsJson = parseSettingsString(fullSettings.settings) ?: return null
            getStringFromSettings(settingsJson, "user_avatar").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w("STRepo", "Failed to get selected persona: ${e.message}")
            null
        }
    }
}
