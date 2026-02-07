package com.stark.sillytavern.ui.screens.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.BuildConfig
import com.stark.sillytavern.data.remote.api.GitHubApi
import com.stark.sillytavern.data.remote.api.GitHubRelease
import com.stark.sillytavern.data.repository.SettingsRepository
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.Character
import com.stark.sillytavern.domain.model.Result
import com.stark.sillytavern.domain.model.ServerSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val releaseUrl: String,
    val releaseNotes: String? = null
)

data class MainUiState(
    val settings: ServerSettings = ServerSettings(),
    val isConnected: Boolean = false,
    val statusText: String = "Not connected",
    val characters: List<Character> = emptyList(),
    val characterAvatarUrls: Map<String, String?> = emptyMap(),
    val isLoadingCharacters: Boolean = false,
    val selectedCharacter: Character? = null,
    val showDeleteDialog: Boolean = false,
    val characterToDelete: Character? = null,
    val showActionMenu: Boolean = false,
    val actionMenuCharacter: Character? = null,
    val error: String? = null,
    val updateAvailable: UpdateInfo? = null,
    val showUpdateDialog: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val stRepository: SillyTavernRepository,
    private val settingsRepository: SettingsRepository,
    private val gitHubApi: GitHubApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Check for updates on startup
        checkForUpdates()

        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }

                if (settings.serverUrl.isNotBlank() && !_uiState.value.isConnected) {
                    connect()
                }
            }
        }
    }

    private fun checkForUpdates() {
        viewModelScope.launch {
            try {
                val release = gitHubApi.getLatestRelease()
                val latestVersion = release.tagName.removePrefix("v")
                val currentVersion = BuildConfig.VERSION_NAME

                Log.d("MainViewModel", "Current version: $currentVersion, Latest: $latestVersion")

                if (isNewerVersion(latestVersion, currentVersion)) {
                    _uiState.update {
                        it.copy(
                            updateAvailable = UpdateInfo(
                                latestVersion = latestVersion,
                                currentVersion = currentVersion,
                                releaseUrl = release.htmlUrl,
                                releaseNotes = release.body
                            ),
                            showUpdateDialog = true
                        )
                    }
                }
            } catch (e: Exception) {
                // Silently fail - don't bother user if we can't check for updates
                Log.w("MainViewModel", "Failed to check for updates: ${e.message}")
            }
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

            for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
                val latestPart = latestParts.getOrElse(i) { 0 }
                val currentPart = currentParts.getOrElse(i) { 0 }

                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }

    fun connect() {
        viewModelScope.launch {
            _uiState.update { it.copy(statusText = "Connecting...") }

            when (val result = stRepository.connect()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isConnected = true,
                            statusText = "Connected"
                        )
                    }
                    loadCharacters()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isConnected = false,
                            statusText = "Connection failed",
                            error = result.exception.message
                        )
                    }
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            stRepository.disconnect()
            _uiState.update {
                it.copy(
                    isConnected = false,
                    statusText = "Disconnected",
                    characters = emptyList()
                )
            }
        }
    }

    fun loadCharacters() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCharacters = true) }

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
                            isLoadingCharacters = false
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCharacters = false,
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

    fun clearSelection() {
        _uiState.update { it.copy(selectedCharacter = null) }
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
}
