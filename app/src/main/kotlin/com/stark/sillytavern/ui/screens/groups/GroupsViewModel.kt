package com.stark.sillytavern.ui.screens.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.Character
import com.stark.sillytavern.domain.model.Group
import com.stark.sillytavern.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupsUiState(
    val groups: List<Group> = emptyList(),
    val groupAvatarUrls: Map<String, List<String?>> = emptyMap(),  // Group ID -> member avatar URLs
    val isLoading: Boolean = true,
    val error: String? = null,
    // Create group dialog
    val showCreateDialog: Boolean = false,
    val availableCharacters: List<Character> = emptyList(),
    val characterAvatarUrls: Map<String, String?> = emptyMap(),
    val newGroupName: String = "",
    val selectedMembers: Set<String> = emptySet(),  // Avatar URLs of selected characters
    val isCreating: Boolean = false
)

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val repository: SillyTavernRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    init {
        loadGroups()
    }

    fun loadGroups() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val result = repository.getAllGroups()) {
                is Result.Success -> {
                    val groups = result.data

                    // Build avatar URLs for group members
                    val avatarUrls = mutableMapOf<String, List<String?>>()
                    groups.forEach { group ->
                        avatarUrls[group.id] = group.members.map { memberAvatar ->
                            repository.buildGroupMemberAvatarUrl(memberAvatar)
                        }
                    }

                    _uiState.update {
                        it.copy(
                            groups = groups,
                            groupAvatarUrls = avatarUrls,
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

    fun showCreateDialog() {
        viewModelScope.launch {
            // Load characters for selection
            when (val result = repository.getCharacters()) {
                is Result.Success -> {
                    val characters = result.data
                    val avatarUrls = characters.associate { char ->
                        (char.avatar ?: char.name) to repository.buildAvatarUrl(char.avatar)
                    }

                    _uiState.update {
                        it.copy(
                            showCreateDialog = true,
                            availableCharacters = characters,
                            characterAvatarUrls = avatarUrls,
                            newGroupName = "",
                            selectedMembers = emptySet()
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(error = "Failed to load characters: ${result.exception.message}")
                    }
                }
            }
        }
    }

    fun dismissCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = false,
                newGroupName = "",
                selectedMembers = emptySet()
            )
        }
    }

    fun updateNewGroupName(name: String) {
        _uiState.update { it.copy(newGroupName = name) }
    }

    fun toggleMemberSelection(avatarUrl: String) {
        _uiState.update { state ->
            val newSelection = if (avatarUrl in state.selectedMembers) {
                state.selectedMembers - avatarUrl
            } else {
                state.selectedMembers + avatarUrl
            }
            state.copy(selectedMembers = newSelection)
        }
    }

    fun createGroup() {
        val state = _uiState.value
        if (state.newGroupName.isBlank() || state.selectedMembers.size < 2) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true) }

            when (val result = repository.createGroup(state.newGroupName, state.selectedMembers.toList())) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            showCreateDialog = false,
                            newGroupName = "",
                            selectedMembers = emptySet()
                        )
                    }
                    loadGroups()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            error = result.exception.message
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
