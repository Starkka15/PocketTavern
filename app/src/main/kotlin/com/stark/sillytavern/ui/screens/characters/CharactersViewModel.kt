package com.stark.sillytavern.ui.screens.characters

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.repository.CardVaultRepository
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.Character
import com.stark.sillytavern.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "CharactersViewModel"

data class CharactersUiState(
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
    val uploadSuccess: String? = null
)

@HiltViewModel
class CharactersViewModel @Inject constructor(
    private val stRepository: SillyTavernRepository,
    private val cardVaultRepository: CardVaultRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharactersUiState())
    val uiState: StateFlow<CharactersUiState> = _uiState.asStateFlow()

    init {
        loadCharacters()
    }

    fun loadCharacters() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val result = stRepository.getCharacters()) {
                is Result.Success -> {
                    val characters = result.data

                    // Build avatar URLs for all characters
                    val avatarUrls = mutableMapOf<String, String?>()
                    characters.forEach { char ->
                        val key = char.avatar ?: char.name
                        avatarUrls[key] = stRepository.buildAvatarUrl(char.avatar)
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
                        it.copy(
                            isLoading = false,
                            error = result.exception.message
                        )
                    }
                }
            }
        }
    }

    fun selectCharacter(character: Character) {
        _uiState.update { it.copy(selectedCharacter = character) }
    }

    fun showActionMenu(character: Character) {
        _uiState.update {
            it.copy(
                showActionMenu = true,
                actionMenuCharacter = character
            )
        }
    }

    fun dismissActionMenu() {
        _uiState.update {
            it.copy(
                showActionMenu = false,
                actionMenuCharacter = null
            )
        }
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
            when (val result = stRepository.deleteCharacter(character.avatar ?: character.name)) {
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
        _uiState.update {
            it.copy(
                showDeleteDialog = false,
                characterToDelete = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearUploadSuccess() {
        _uiState.update { it.copy(uploadSuccess = null) }
    }

    fun uploadToCardVault(character: Character) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    showActionMenu = false,
                    actionMenuCharacter = null
                )
            }

            try {
                // Export the full character card PNG with embedded metadata
                val avatarKey = character.avatar ?: character.name
                Log.d(TAG, "Exporting character card for upload: $avatarKey")

                when (val exportResult = stRepository.exportCharacterCard(avatarKey)) {
                    is Result.Success -> {
                        val imageBytes = exportResult.data
                        val filename = "${character.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.png"

                        Log.d(TAG, "Uploading to CardVault: $filename (${imageBytes.size} bytes)")

                        when (val uploadResult = cardVaultRepository.uploadCard(imageBytes, filename)) {
                            is Result.Success -> {
                                Log.d(TAG, "Upload successful: ${uploadResult.data}")
                                _uiState.update {
                                    it.copy(
                                        isUploading = false,
                                        uploadSuccess = "Uploaded \"${character.name}\" to CardVault"
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
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        error = "Upload error: ${e.message}"
                    )
                }
            }
        }
    }
}
