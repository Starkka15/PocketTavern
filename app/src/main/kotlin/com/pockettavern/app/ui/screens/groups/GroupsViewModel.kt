package com.pockettavern.app.ui.screens.groups

import androidx.lifecycle.ViewModel
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.Group
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class GroupsUiState(
    val groups: List<Group> = emptyList(),
    val groupAvatarUrls: Map<String, List<String?>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    // Create group dialog
    val showCreateDialog: Boolean = false,
    val availableCharacters: List<Character> = emptyList(),
    val characterAvatarUrls: Map<String, String?> = emptyMap(),
    val newGroupName: String = "",
    val selectedMembers: Set<String> = emptySet(),
    val isCreating: Boolean = false,
    // Delete group
    val showDeleteDialog: Boolean = false,
    val groupToDelete: Group? = null
)

@HiltViewModel
class GroupsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    init {
        loadGroups()
    }

    fun loadGroups() {
        // Groups are not yet supported in standalone mode
        _uiState.update { it.copy(isLoading = false, groups = emptyList()) }
    }

    fun showCreateDialog() {
        _uiState.update { it.copy(error = "Group chats are not yet supported in standalone mode") }
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
        _uiState.update {
            it.copy(
                isCreating = false,
                showCreateDialog = false,
                error = "Group chats are not yet supported in standalone mode"
            )
        }
    }

    fun showDeleteConfirmation(group: Group) {
        _uiState.update { it.copy(showDeleteDialog = true, groupToDelete = group) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false, groupToDelete = null) }
    }

    fun deleteGroup() {
        _uiState.update { it.copy(showDeleteDialog = false, groupToDelete = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
