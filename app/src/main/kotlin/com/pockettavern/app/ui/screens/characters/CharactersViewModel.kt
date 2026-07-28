package com.pockettavern.app.ui.screens.characters

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.data.repository.CharaVaultRepository
import com.pockettavern.app.data.repository.LlmRepository
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.Group
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.domain.usecase.TranslateCardUseCase
import com.pockettavern.app.util.PngCharacterCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "CharactersViewModel"

enum class CharactersTab { CHARACTERS, GROUPS }

data class CharactersUiState(
    val activeTab: CharactersTab = CharactersTab.CHARACTERS,
    val characters: List<Character> = emptyList(),
    val characterAvatarUrls: Map<String, String?> = emptyMap(),
    val isLoading: Boolean = false,
    val selectedCharacter: Character? = null,
    val showDeleteDialog: Boolean = false,
    val characterToDelete: Character? = null,
    val showActionMenu: Boolean = false,
    val actionMenuCharacter: Character? = null,
    val error: String? = null,
    val isUploading: Boolean = false,
    val uploadSuccess: String? = null,
    // Local file import + translation
    val isImportingLocal: Boolean = false,
    val showTranslateDialog: Boolean = false,
    val pendingTranslateFileName: String? = null,
    val pendingTranslateFields: Set<TranslateCardUseCase.Field> = emptySet(),
    val isTranslating: Boolean = false,
    val translateError: String? = null,
    val translateSuccess: Boolean = false,
    // Groups
    val groups: List<Group> = emptyList(),
    val groupAvatarUrls: Map<String, List<String?>> = emptyMap(),
    val isLoadingGroups: Boolean = false,
    val showCreateGroupDialog: Boolean = false,
    val newGroupName: String = "",
    val selectedGroupMembers: Set<String> = emptySet(),
    val isCreatingGroup: Boolean = false
)

@HiltViewModel
class CharactersViewModel @Inject constructor(
    private val localRepository: LocalRepository,
    private val charaVaultRepository: CharaVaultRepository,
    private val llmRepository: LlmRepository,
    private val settingsDataStore: SettingsDataStore,
    private val translateCardUseCase: TranslateCardUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharactersUiState())
    val uiState: StateFlow<CharactersUiState> = _uiState.asStateFlow()

    init {
        loadCharacters()
        // Groups are stubbed — no server needed, no local storage yet
        _uiState.update { it.copy(isLoadingGroups = false, groups = emptyList()) }
    }

    fun setActiveTab(tab: CharactersTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun loadCharacters() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val result = localRepository.getCharacters()) {
                is Result.Success -> {
                    val characters = result.data
                    val avatarUrls = mutableMapOf<String, String?>()
                    characters.forEach { char ->
                        val key = char.avatar ?: char.name
                        avatarUrls[key] = localRepository.getAvatarUri(
                            char.avatar ?: "${char.name}.png"
                        ).toString()
                    }
                    _uiState.update {
                        it.copy(
                            characters = characters,
                            characterAvatarUrls = avatarUrls,
                            isLoading = false
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.exception.message)
                    }
                }
            }
        }
    }

    fun selectCharacter(character: Character) {
        _uiState.update { it.copy(selectedCharacter = character) }
    }

    fun showActionMenu(character: Character) {
        _uiState.update { it.copy(showActionMenu = true, actionMenuCharacter = character) }
    }

    fun dismissActionMenu() {
        _uiState.update { it.copy(showActionMenu = false, actionMenuCharacter = null) }
    }

    fun showDeleteConfirmation(character: Character) {
        _uiState.update {
            it.copy(
                showDeleteDialog = true,
                characterToDelete = character,
                showActionMenu = false,
                actionMenuCharacter = null
            )
        }
    }

    fun deleteCharacter() {
        val character = _uiState.value.characterToDelete ?: return
        viewModelScope.launch {
            when (val result = localRepository.deleteCharacter(character.avatar ?: "${character.name}.png")) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            showDeleteDialog = false,
                            characterToDelete = null,
                            selectedCharacter = if (it.selectedCharacter?.name == character.name) null else it.selectedCharacter
                        )
                    }
                    loadCharacters()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            showDeleteDialog = false,
                            characterToDelete = null,
                            error = result.exception.message
                        )
                    }
                }
            }
        }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false, characterToDelete = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearUploadSuccess() {
        _uiState.update { it.copy(uploadSuccess = null) }
    }

    // ===== Groups (stubbed — local group storage not yet implemented) =====

    fun loadGroups() {
        // Groups require local storage implementation — stubbed for now
        _uiState.update { it.copy(isLoadingGroups = false, groups = emptyList()) }
    }

    fun showCreateGroupDialog() {
        viewModelScope.launch {
            when (val result = localRepository.getCharacters()) {
                is Result.Success -> {
                    val characters = result.data
                    val avatarUrls = characters.associate { char ->
                        (char.avatar ?: char.name) to localRepository.getAvatarUri(
                            char.avatar ?: "${char.name}.png"
                        ).toString()
                    }
                    _uiState.update {
                        it.copy(
                            showCreateGroupDialog = true,
                            characters = if (it.characters.isEmpty()) characters else it.characters,
                            characterAvatarUrls = if (it.characterAvatarUrls.isEmpty()) avatarUrls else it.characterAvatarUrls,
                            newGroupName = "",
                            selectedGroupMembers = emptySet()
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(error = "Failed to load characters: ${result.exception.message}") }
                }
            }
        }
    }

    fun dismissCreateGroupDialog() {
        _uiState.update {
            it.copy(showCreateGroupDialog = false, newGroupName = "", selectedGroupMembers = emptySet())
        }
    }

    fun updateNewGroupName(name: String) {
        _uiState.update { it.copy(newGroupName = name) }
    }

    fun toggleGroupMemberSelection(avatarUrl: String) {
        _uiState.update { state ->
            val newSelection = if (avatarUrl in state.selectedGroupMembers) {
                state.selectedGroupMembers - avatarUrl
            } else {
                state.selectedGroupMembers + avatarUrl
            }
            state.copy(selectedGroupMembers = newSelection)
        }
    }

    fun createGroup() {
        // Local group storage not yet implemented
        _uiState.update {
            it.copy(
                isCreatingGroup = false,
                showCreateGroupDialog = false,
                error = "Group chats are not yet supported in standalone mode"
            )
        }
    }

    // ===== Local file import (PNG or charx) =====

    fun importLocalCharacter(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingLocal = true, error = null) }
            when (val result = localRepository.importCharacterCard(uri)) {
                is Result.Success -> {
                    val fileName = result.data
                    _uiState.update { it.copy(isImportingLocal = false) }
                    loadCharacters()
                    // V8: detect non-English fields, offer translation dialog
                    val nonEnglishFields = detectNonEnglishFields(fileName)
                    if (nonEnglishFields.isNotEmpty()) {
                        _uiState.update {
                            it.copy(
                                showTranslateDialog = true,
                                pendingTranslateFileName = fileName,
                                pendingTranslateFields = nonEnglishFields
                            )
                        }
                    }
                }
                is Result.Error -> _uiState.update {
                    it.copy(isImportingLocal = false, error = "Import failed: ${result.exception.message}")
                }
            }
        }
    }

    fun dismissTranslateDialog() {
        _uiState.update {
            it.copy(
                showTranslateDialog = false,
                pendingTranslateFileName = null,
                pendingTranslateFields = emptySet(),
                translateError = null
            )
        }
    }

    fun translateImportedCard(fields: List<TranslateCardUseCase.Field>) {
        val fileName = _uiState.value.pendingTranslateFileName ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, translateError = null) }
            try {
                val config = settingsDataStore.getLlmConfig()
                val charResult = localRepository.getCharacter(fileName)
                if (charResult !is Result.Success) {
                    _uiState.update { it.copy(isTranslating = false, translateError = "Character not found") }
                    return@launch
                }
                val character = charResult.data

                // Build card data from character
                val cardData = com.pockettavern.app.util.CharacterCardData(
                    name = character.name,
                    description = character.description,
                    personality = character.personality,
                    scenario = character.scenario,
                    firstMes = character.firstMessage,
                    mesExample = character.messageExample,
                    creatorNotes = character.creatorNotes,
                    systemPrompt = character.systemPrompt,
                    postHistoryInstructions = character.postHistoryInstructions,
                    alternateGreetings = character.alternateGreetings,
                    tags = character.tags
                )

                // V9: translate all fields first, then save
                val translated = translateCardUseCase.translate(cardData, fields, config)
                val updatedChar = character.copy(
                    description = translated.description,
                    personality = translated.personality,
                    scenario = translated.scenario,
                    firstMessage = translated.firstMes,
                    messageExample = translated.mesExample,
                    creatorNotes = translated.creatorNotes,
                    alternateGreetings = translated.alternateGreetings
                )
                localRepository.editCharacter(fileName, updatedChar)
                loadCharacters()
                _uiState.update {
                    it.copy(
                        isTranslating = false,
                        showTranslateDialog = false,
                        pendingTranslateFileName = null,
                        translateSuccess = true
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Translation failed", e)
                _uiState.update { it.copy(isTranslating = false, translateError = e.message ?: "Translation failed") }
            }
        }
    }

    fun clearTranslateSuccess() {
        _uiState.update { it.copy(translateSuccess = false) }
    }

    // Trigger translate dialog for an already-imported character (from long-press menu)
    fun requestTranslateExisting(character: Character) {
        val fileName = character.avatar ?: "${character.name}.png"
        viewModelScope.launch {
            val fields = detectNonEnglishFields(fileName)
            _uiState.update {
                it.copy(
                    showActionMenu = false,
                    actionMenuCharacter = null,
                    pendingTranslateFileName = fileName,
                    pendingTranslateFields = fields,
                    showTranslateDialog = true
                )
            }
        }
    }

    // V8: detect which fields have > 0.15 non-ASCII ratio
    private suspend fun detectNonEnglishFields(fileName: String): Set<TranslateCardUseCase.Field> {
        val charResult = localRepository.getCharacter(fileName)
        if (charResult !is Result.Success) return emptySet()
        val c = charResult.data
        val fieldMap = mapOf(
            TranslateCardUseCase.Field.FIRST_MES to c.firstMessage,
            TranslateCardUseCase.Field.DESCRIPTION to c.description,
            TranslateCardUseCase.Field.PERSONALITY to c.personality,
            TranslateCardUseCase.Field.SCENARIO to c.scenario,
            TranslateCardUseCase.Field.MES_EXAMPLE to c.messageExample,
            TranslateCardUseCase.Field.CREATOR_NOTES to c.creatorNotes,
            TranslateCardUseCase.Field.ALTERNATE_GREETINGS to c.alternateGreetings.joinToString("\n")
        )
        return fieldMap.filterValues { translateCardUseCase.needsTranslation(it) }.keys
    }

    fun uploadToCharaVault(character: Character) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isUploading = true, showActionMenu = false, actionMenuCharacter = null)
            }

            try {
                val avatarKey = character.avatar ?: "${character.name}.png"
                Log.d(TAG, "Exporting character card for upload: $avatarKey")

                when (val exportResult = localRepository.exportCharacterCard(avatarKey)) {
                    is Result.Success -> {
                        val imageBytes = exportResult.data
                        val filename = "${character.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.png"
                        Log.d(TAG, "Uploading to CharaVault: $filename (${imageBytes.size} bytes)")

                        when (val uploadResult = charaVaultRepository.uploadCard(imageBytes, filename)) {
                            is Result.Success -> {
                                Log.d(TAG, "Upload successful: ${uploadResult.data}")
                                _uiState.update {
                                    it.copy(
                                        isUploading = false,
                                        uploadSuccess = "Uploaded \"${character.name}\" to CharaVault"
                                    )
                                }
                            }
                            is Result.Error -> {
                                Log.e(TAG, "Upload failed", uploadResult.exception)
                                _uiState.update {
                                    it.copy(
                                        isUploading = false,
                                        error = "Upload failed: ${uploadResult.exception.message}"
                                    )
                                }
                            }
                        }
                    }
                    is Result.Error -> {
                        Log.e(TAG, "Failed to export character card", exportResult.exception)
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                error = "Failed to export character: ${exportResult.exception.message}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload error", e)
                _uiState.update { it.copy(isUploading = false, error = "Upload error: ${e.message}") }
            }
        }
    }
}
