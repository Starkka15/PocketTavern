package com.pockettavern.app.ui.screens.characters

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.repository.CharaVaultRepository
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.Group
import com.pockettavern.app.domain.model.Result
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
    private val charaVaultRepository: CharaVaultRepository
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
