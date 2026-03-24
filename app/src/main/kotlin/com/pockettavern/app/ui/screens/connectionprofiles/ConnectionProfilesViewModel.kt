package com.pockettavern.app.ui.screens.connectionprofiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.ConnectionProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionProfilesUiState(
    val profiles: List<ConnectionProfile> = emptyList(),
    val activeProfileId: String? = null,   // persisted last-activated ID
    val showNameDialog: Boolean = false,
    val nameField: String = "",
    val justActivatedId: String? = null,   // transient — for snackbar
    val error: String? = null
)

@HiltViewModel
class ConnectionProfilesViewModel @Inject constructor(
    private val localRepository: LocalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionProfilesUiState())
    val uiState: StateFlow<ConnectionProfilesUiState> = _uiState.asStateFlow()

    init {
        load()
        // Observe persisted active profile ID
        viewModelScope.launch {
            localRepository.lastActivatedProfileIdFlow.collect { id ->
                _uiState.update { it.copy(activeProfileId = id) }
            }
        }
    }

    fun load() {
        val profiles = localRepository.getConnectionProfiles()
        _uiState.update { it.copy(profiles = profiles) }
    }

    fun showSaveDialog() {
        _uiState.update { it.copy(showNameDialog = true, nameField = "") }
    }

    fun hideDialog() {
        _uiState.update { it.copy(showNameDialog = false, nameField = "") }
    }

    fun updateNameField(name: String) = _uiState.update { it.copy(nameField = name) }

    fun saveCurrentAsProfile() {
        val name = _uiState.value.nameField.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Name cannot be empty") }
            return
        }
        viewModelScope.launch {
            val profile = localRepository.getCurrentAsProfile(name)
            val profiles = _uiState.value.profiles + profile
            localRepository.saveConnectionProfiles(profiles)
            _uiState.update { it.copy(profiles = profiles, showNameDialog = false, nameField = "") }
        }
    }

    fun activateProfile(profile: ConnectionProfile) {
        viewModelScope.launch {
            localRepository.activateConnectionProfile(profile)
            // activeProfileId updates via the flow observer above
            _uiState.update { it.copy(justActivatedId = profile.id) }
        }
    }

    fun clearJustActivated() {
        _uiState.update { it.copy(justActivatedId = null) }
    }

    fun deleteProfile(profileId: String) {
        val profiles = _uiState.value.profiles.filter { it.id != profileId }
        localRepository.saveConnectionProfiles(profiles)
        _uiState.update { it.copy(profiles = profiles) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
