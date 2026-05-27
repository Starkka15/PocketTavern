package com.pockettavern.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.pockettavern.app.data.local.CardExtensionMeta
import com.pockettavern.app.data.local.CharacterStorage
import com.pockettavern.app.data.local.ChatStorage
import com.pockettavern.app.data.local.LoreBookStorage
import com.pockettavern.app.data.local.PresetStorage
import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.domain.model.*
import com.pockettavern.app.util.DebugLogger
import com.pockettavern.app.util.PngCharacterCard
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LocalRepository replaces SillyTavernRepository for all character/chat/preset data.
 * All data is stored locally on the device in ST-compatible formats.
 */
@Singleton
class LocalRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val characterStorage: CharacterStorage,
    private val chatStorage: ChatStorage,
    private val loreBookStorage: LoreBookStorage,
    private val presetStorage: PresetStorage,
    private val settingsDataStore: SettingsDataStore,
    private val connectionProfileStorage: com.pockettavern.app.data.local.ConnectionProfileStorage
) {

    // ── Characters ───────────────────────────────────────────────────────────

    suspend fun getCharacters(): Result<List<Character>> = withResult {
        characterStorage.listCharacters()
    }

    suspend fun getCharacter(fileName: String): Result<Character> = withResult {
        characterStorage.getCharacter(fileName)
            ?: throw Exception("Character not found: $fileName")
    }

    suspend fun createCharacter(character: Character): Result<String> = withResult {
        characterStorage.saveCharacter(character, null)
    }

    suspend fun createCharacterWithBitmap(character: Character, bitmap: Bitmap?): Result<String> = withResult {
        characterStorage.saveCharacter(character, bitmap)
    }

    suspend fun editCharacter(fileName: String, character: Character): Result<Unit> = withResult {
        characterStorage.saveCharacter(character, null, fileName)
        Unit
    }

    suspend fun deleteCharacter(fileName: String): Result<Unit> = withResult {
        // Resolve display name before deleting the entity so we can clean up chats
        val displayName = characterStorage.getCharacter(fileName)?.name
            ?: fileName.removeSuffix(".png")
        characterStorage.deleteCharacter(fileName)
        chatStorage.deleteAllForCharacter(displayName)
    }

    /** Import a character card (PNG or .charx) from a content URI. */
    suspend fun importCharacterCard(uri: Uri): Result<String> = withResult {
        characterStorage.importCharacterCard(uri)
    }

    suspend fun importCharacterCardBytes(bytes: ByteArray): Result<String> = withResult {
        characterStorage.importCharacterCardBytes(bytes)
    }

    suspend fun importCharacterCardFile(file: java.io.File): Result<String> = withResult {
        characterStorage.importCharacterCardFile(file)
    }

    /** Get raw PNG bytes for a character (for export). */
    suspend fun exportCharacterCard(fileName: String): Result<ByteArray> = withResult {
        characterStorage.getCharacterBytes(fileName)
            ?: throw Exception("Character file not found: $fileName")
    }

    /** Scan all character PNGs and return metadata for those with embedded PT scripts. */
    suspend fun listCardExtensions(): List<CardExtensionMeta> =
        characterStorage.listCardExtensions()

    /** Get local URI for a character's avatar PNG. */
    fun getAvatarUri(fileName: String): Uri = characterStorage.getAvatarUri(fileName)

    /** Rebuild Room index from disk (call on first launch). */
    suspend fun rebuildCharacterIndex() = characterStorage.rebuildIndex()

    /** List all character entries cross-referenced with disk files. */
    suspend fun listCharacterFileInfo(): Result<List<CharacterStorage.CharacterFileInfo>> = withResult {
        characterStorage.listCharacterFileInfo()
    }

    // ── Chats ────────────────────────────────────────────────────────────────

    suspend fun getCharacterChats(characterName: String): Result<List<ChatInfo>> = withResult {
        chatStorage.listChats(characterName)
    }

    suspend fun getChat(characterName: String, fileName: String): Result<Chat> = withResult {
        chatStorage.loadChat(characterName, fileName)
            ?: throw Exception("Chat not found: $fileName")
    }

    suspend fun saveChat(chat: Chat): Result<String> = withResult {
        val userName = settingsDataStore.getUserPersonaName()
        chatStorage.saveChat(chat, userName)
    }

    suspend fun deleteChat(characterName: String, fileName: String): Result<Unit> = withResult {
        chatStorage.deleteChat(characterName, fileName)
    }

    fun generateChatFileName(characterName: String): String =
        chatStorage.generateFileName(characterName)

    // ── World Info / Lorebooks ───────────────────────────────────────────────

    suspend fun getWorldInfoList(): Result<List<String>> = withResult {
        loreBookStorage.listLorebooks()
    }

    suspend fun getWorldInfo(name: String): Result<List<WorldInfoEntry>> = withResult {
        loreBookStorage.loadLorebook(name)
    }

    suspend fun saveWorldInfo(name: String, entries: List<WorldInfoEntry>): Result<Unit> = withResult {
        loreBookStorage.saveLorebook(name, entries)
    }

    suspend fun deleteWorldInfo(name: String): Result<Unit> = withResult {
        loreBookStorage.deleteLorebook(name)
    }

    suspend fun importWorldInfoJson(name: String, bytes: ByteArray): Result<Unit> = withResult {
        loreBookStorage.saveRawLorebook(name, bytes)
    }

    // ── Formatting / Presets ─────────────────────────────────────────────────

    suspend fun getFormattingSettings(): Result<FormattingSettings> = withResult {
        val instructNames = presetStorage.listInstructTemplates()
        val contextNames = presetStorage.listContextTemplates()
        val syspromptNames = presetStorage.listSystemPrompts()
        val selectedInstruct = settingsDataStore.getSelectedInstructPreset() ?: ""
        val selectedContext = settingsDataStore.getSelectedContextPreset() ?: ""
        val selectedSysprompt = settingsDataStore.getSelectedSyspromptPreset() ?: ""
        val customSysPrompt = settingsDataStore.getCustomSystemPrompt()

        FormattingSettings(
            instructPresets = instructNames,
            selectedInstructPreset = selectedInstruct,
            contextPresets = contextNames,
            selectedContextPreset = selectedContext,
            systemPromptPresets = syspromptNames,
            selectedSystemPromptPreset = selectedSysprompt,
            customSystemPrompt = customSysPrompt
        )
    }

    suspend fun saveFormattingSettings(settings: FormattingSettings) {
        settingsDataStore.setSelectedInstructPreset(settings.selectedInstructPreset.ifBlank { null })
        settingsDataStore.setSelectedContextPreset(settings.selectedContextPreset.ifBlank { null })
        settingsDataStore.setSelectedSyspromptPreset(settings.selectedSystemPromptPreset.ifBlank { null })
        settingsDataStore.saveCustomSystemPrompt(settings.customSystemPrompt)
    }

    /** Load the currently selected TextGen preset (name from DataStore, content from PresetStorage). */
    suspend fun getCurrentTextGenPreset(): TextGenPreset? {
        val name = settingsDataStore.getSelectedTextGenPreset() ?: return null
        return presetStorage.loadTextGenPreset(name)
    }

    suspend fun loadInstructTemplate(name: String): InstructTemplate? =
        presetStorage.loadInstructTemplate(name)

    suspend fun loadContextTemplate(name: String): ContextTemplate? =
        presetStorage.loadContextTemplate(name)

    suspend fun loadSystemPrompt(name: String): SystemPromptPreset? =
        presetStorage.loadSystemPrompt(name)

    suspend fun listSystemPrompts(): List<String> =
        presetStorage.listSystemPrompts()

    suspend fun saveSystemPrompt(name: String, content: String) =
        presetStorage.saveSystemPrompt(name, content)

    suspend fun deleteSystemPrompt(name: String) =
        presetStorage.deleteUserPreset("sysprompt", name)

    fun isUserSystemPrompt(name: String): Boolean =
        presetStorage.isUserPreset("sysprompt", name)

    suspend fun getTextGenPresets(): Result<List<String>> = withResult {
        presetStorage.listTextGenPresets()
    }

    /** Load all textgen presets as objects plus the currently selected preset name. */
    suspend fun getTextGenPresetsWithSelected(): Result<Pair<List<TextGenPreset>, String>> = withResult {
        val names = presetStorage.listTextGenPresets()
        val presets = names.mapNotNull { name -> presetStorage.loadTextGenPreset(name) }
        val selectedName = settingsDataStore.getSelectedTextGenPreset() ?: ""
        Pair(presets, selectedName)
    }

    suspend fun selectTextGenPreset(name: String) {
        settingsDataStore.setSelectedTextGenPreset(name.ifBlank { null })
    }

    suspend fun saveTextGenPreset(preset: TextGenPreset): Result<Unit> = withResult {
        presetStorage.saveTextGenPreset(preset.name, preset)
    }

    suspend fun deleteTextGenPreset(name: String): Result<Unit> = withResult {
        presetStorage.deleteTextGenPreset(name)
    }

    suspend fun loadTextGenPreset(name: String): TextGenPreset? =
        presetStorage.loadTextGenPreset(name)

    // ── OAI Presets ──────────────────────────────────────────────────────────

    suspend fun getCurrentOaiPreset(): OaiPreset? {
        val name = settingsDataStore.getSelectedOaiPreset() ?: return null
        return presetStorage.loadOaiPreset(name)
    }

    suspend fun getOaiPresetsWithSelected(): Result<Pair<List<OaiPreset>, String>> = withResult {
        val names = presetStorage.listOaiPresets()
        val presets = names.mapNotNull { name -> presetStorage.loadOaiPreset(name) }
        val selectedName = settingsDataStore.getSelectedOaiPreset() ?: ""
        Pair(presets, selectedName)
    }

    suspend fun selectOaiPreset(name: String) {
        settingsDataStore.setSelectedOaiPreset(name.ifBlank { null })
    }

    suspend fun saveOaiPreset(preset: OaiPreset): Result<Unit> = withResult {
        presetStorage.saveOaiPreset(preset.name, preset)
    }

    suspend fun deleteOaiPreset(name: String): Result<Unit> = withResult {
        presetStorage.deleteOaiPreset(name)
    }

    /** Parse a SillyTavern OAI preset JSON and save it as a PocketTavern preset. */
    suspend fun importStOaiPreset(name: String, jsonText: String): Result<Unit> = withResult {
        val preset = presetStorage.importStOaiPreset(name, jsonText)
        presetStorage.saveOaiPreset(name, preset)
    }

    /** Import a character card from raw PNG bytes (e.g., from CharaVault/Chub download). */
    suspend fun importCharacterCardBytes(bytes: ByteArray, fileName: String): Result<String> = withResult {
        characterStorage.saveRawPng(bytes, fileName)
    }

    // ── User Persona ─────────────────────────────────────────────────────────

    suspend fun getUserPersona(): Result<UserPersona> = withResult {
        UserPersona(
            name = settingsDataStore.getUserPersonaName(),
            description = settingsDataStore.getUserPersonaDesc(),
            position = settingsDataStore.getUserPersonaPosition(),
            depth = settingsDataStore.getUserPersonaDepth(),
            avatarPath = settingsDataStore.getUserPersonaAvatarPath(),
            noSpeakForUser = settingsDataStore.getNoSpeakForUser()
        )
    }

    suspend fun saveUserPersona(persona: UserPersona) {
        settingsDataStore.saveUserPersonaName(persona.name)
        settingsDataStore.saveUserPersonaDesc(persona.description)
        settingsDataStore.saveUserPersonaPosition(persona.position)
        settingsDataStore.saveUserPersonaDepth(persona.depth)
        settingsDataStore.saveUserPersonaAvatarPath(persona.avatarPath)
        settingsDataStore.saveNoSpeakForUser(persona.noSpeakForUser)
    }

    // ── API Configuration ────────────────────────────────────────────────────

    suspend fun getApiConfiguration(): Result<ApiConfiguration> = withResult {
        settingsDataStore.getLlmConfig()
    }

    suspend fun saveApiConfiguration(config: ApiConfiguration) {
        settingsDataStore.saveLlmConfig(config)
        // Clear last-activated profile — user has manually diverged from it
        settingsDataStore.setLastActivatedProfileId(null)
    }

    // ── Chat Context (for prompt building) ───────────────────────────────────

    /**
     * Load the full ChatContext needed for prompt building.
     * Merges character data, persona, world info, and templates.
     */
    suspend fun loadChatContext(
        characterFileName: String,
        chatFileName: String? = null
    ): Result<ChatContext> = withResult {
        val character = characterStorage.getCharacter(characterFileName)
            ?: throw Exception("Character not found: $characterFileName")

        val selectedInstruct = settingsDataStore.getSelectedInstructPreset()
        val selectedContext = settingsDataStore.getSelectedContextPreset()
        val selectedSysprompt = settingsDataStore.getSelectedSyspromptPreset()

        val instructTemplate = selectedInstruct?.let { presetStorage.loadInstructTemplate(it) }
        val contextTemplate = selectedContext?.let { presetStorage.loadContextTemplate(it) }
        val systemPrompt = selectedSysprompt?.let { presetStorage.loadSystemPrompt(it)?.content } ?: ""

        val persona = UserPersona(
            name = settingsDataStore.getUserPersonaName(),
            description = settingsDataStore.getUserPersonaDesc(),
            avatarPath = settingsDataStore.getUserPersonaAvatarPath(),
            noSpeakForUser = settingsDataStore.getNoSpeakForUser()
        )

        // Load global author's note from settings (fallback)
        val globalAuthorsNote = AuthorsNote(
            content = settingsDataStore.getGlobalAuthorsNoteContent(),
            depth = settingsDataStore.getGlobalAuthorsNoteDepth(),
            interval = settingsDataStore.getGlobalAuthorsNoteInterval(),
            position = settingsDataStore.getGlobalAuthorsNotePosition(),
            role = settingsDataStore.getGlobalAuthorsNoteRole()
        )

        // Load author's note: per-chat metadata takes priority, falls back to global
        val authorsNote = if (chatFileName != null) {
            val chat = chatStorage.loadChat(character.name, chatFileName)
            val meta = chat?.messages?.firstOrNull()?.chatMetadata
            val chatContent = meta?.notePrompt ?: ""
            if (chatContent.isNotBlank()) {
                AuthorsNote(
                    content = chatContent,
                    interval = meta?.noteInterval ?: 1,
                    depth = meta?.noteDepth ?: 4,
                    position = meta?.notePosition ?: 0,
                    role = meta?.noteRole ?: 0
                )
            } else {
                globalAuthorsNote
            }
        } else {
            globalAuthorsNote
        }

        // Load world info entries
        val worldInfoEntries = buildList {
            // 1. Attached global lorebook
            character.attachedWorldInfo?.let { lbName ->
                addAll(loreBookStorage.loadLorebook(lbName))
            }
            // 2. Embedded character book (Phase 5 fix — key part of the plan)
            if (character.hasCharacterBook) {
                val pngBytes = characterStorage.getCharacterBytes(characterFileName)
                val card = pngBytes?.let { PngCharacterCard.extractCharacterData(it) }
                card?.data?.characterBook?.entries?.forEach { entry ->
                    add(WorldInfoEntry(
                        uid = (entry.id ?: 0).toString(),
                        key = entry.keys,
                        keysecondary = entry.secondaryKeys,
                        content = entry.content,
                        comment = entry.comment.ifBlank { entry.name },
                        constant = entry.constant,
                        selective = entry.selective,
                        order = entry.insertionOrder,
                        position = if (entry.position == "after_char") 1 else 0,
                        depth = 4,
                        probability = 100,
                        enabled = entry.enabled
                    ))
                }
            }
        }

        ChatContext(
            characterName = character.name,
            characterDescription = character.description,
            characterPersonality = character.personality,
            characterScenario = character.scenario,
            characterFirstMessage = character.firstMessage,
            characterMessageExamples = character.messageExample,
            characterSystemPrompt = character.systemPrompt,
            characterPostHistoryInstructions = character.postHistoryInstructions,
            userPersona = persona,
            authorsNote = authorsNote,
            worldInfoEntries = worldInfoEntries,
            instructTemplate = instructTemplate,
            contextTemplate = contextTemplate,
            systemPromptPreset = systemPrompt,
            isLoaded = true,
            lastModified = System.currentTimeMillis()
        )
    }

    // ── Live config flow (for reactive UI updates) ────────────────────────────

    val apiConfigFlow: kotlinx.coroutines.flow.Flow<ApiConfiguration> =
        settingsDataStore.llmConfigFlow

    // ── Global Author's Note ──────────────────────────────────────────────────

    suspend fun getGlobalAuthorsNote(): AuthorsNote = AuthorsNote(
        content = settingsDataStore.getGlobalAuthorsNoteContent(),
        depth = settingsDataStore.getGlobalAuthorsNoteDepth(),
        interval = settingsDataStore.getGlobalAuthorsNoteInterval(),
        position = settingsDataStore.getGlobalAuthorsNotePosition(),
        role = settingsDataStore.getGlobalAuthorsNoteRole()
    )

    suspend fun saveGlobalAuthorsNote(note: AuthorsNote) =
        settingsDataStore.saveGlobalAuthorsNote(
            content = note.content,
            depth = note.depth,
            interval = note.interval,
            position = note.position,
            role = note.role
        )

    // ── Auto-Continue ─────────────────────────────────────────────────────────

    val autoContinueFlow: kotlinx.coroutines.flow.Flow<Pair<Boolean, Int>> =
        settingsDataStore.autoContinueFlow

    suspend fun saveAutoContinueConfig(enabled: Boolean, minLength: Int) =
        settingsDataStore.saveAutoContinueConfig(enabled, minLength)

    // ── Long-Term Memory ──────────────────────────────────────────────────────

    val memoryEnabledFlow: kotlinx.coroutines.flow.Flow<Boolean> =
        settingsDataStore.memoryEnabledFlow

    suspend fun getMemoryEnabled(): Boolean = settingsDataStore.getMemoryEnabled()
    suspend fun saveMemoryEnabled(enabled: Boolean) = settingsDataStore.saveMemoryEnabled(enabled)
    suspend fun updateChatMemoryBlock(characterName: String, fileName: String, block: String, count: Int) =
        chatStorage.updateMemoryBlock(characterName, fileName, block, count)

    // ── Connection Profiles ───────────────────────────────────────────────────

    fun getConnectionProfiles(): List<com.pockettavern.app.domain.model.ConnectionProfile> =
        connectionProfileStorage.loadProfiles()

    fun saveConnectionProfiles(profiles: List<com.pockettavern.app.domain.model.ConnectionProfile>) =
        connectionProfileStorage.saveProfiles(profiles)

    val lastActivatedProfileIdFlow: kotlinx.coroutines.flow.Flow<String?> =
        settingsDataStore.lastActivatedProfileIdFlow

    suspend fun activateConnectionProfile(profile: com.pockettavern.app.domain.model.ConnectionProfile) {
        settingsDataStore.saveLlmConfig(
            ApiConfiguration(
                mainApi = profile.mainApi,
                textGenType = profile.textGenType,
                apiServer = profile.apiServer,
                chatCompletionSource = profile.chatCompletionSource,
                customUrl = profile.customUrl,
                apiKey = profile.apiKey,
                currentModel = profile.model
            )
        )
        settingsDataStore.setSelectedTextGenPreset(profile.textGenPreset.ifBlank { null })
        settingsDataStore.setSelectedInstructPreset(profile.instructPreset.ifBlank { null })
        settingsDataStore.setSelectedContextPreset(profile.contextPreset.ifBlank { null })
        settingsDataStore.setSelectedSyspromptPreset(profile.syspromptPreset.ifBlank { null })
        settingsDataStore.setLastActivatedProfileId(profile.id)
    }

    suspend fun getCurrentAsProfile(name: String): com.pockettavern.app.domain.model.ConnectionProfile {
        val config = settingsDataStore.getLlmConfig()
        return com.pockettavern.app.domain.model.ConnectionProfile(
            name = name,
            mainApi = config.mainApi,
            textGenType = config.textGenType,
            apiServer = config.apiServer,
            chatCompletionSource = config.chatCompletionSource,
            customUrl = config.customUrl,
            apiKey = config.apiKey,
            model = config.currentModel,
            textGenPreset = settingsDataStore.getSelectedTextGenPreset() ?: "",
            instructPreset = settingsDataStore.getSelectedInstructPreset() ?: "",
            contextPreset = settingsDataStore.getSelectedContextPreset() ?: "",
            syspromptPreset = settingsDataStore.getSelectedSyspromptPreset() ?: ""
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun <T> withResult(block: suspend () -> T): Result<T> {
        return try {
            Result.Success(withContext(Dispatchers.IO) { block() })
        } catch (e: Exception) {
            DebugLogger.logError("LocalRepository", "Operation failed", e)
            Result.Error(e)
        }
    }
}
