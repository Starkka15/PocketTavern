package com.pockettavern.app.ui.screens.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.local.CharacterStorage
import com.pockettavern.app.data.local.GroupStorage
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.Group
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

data class GroupsUiState(
    val groups: List<Group> = emptyList(),
    val groupAvatarUrls: Map<String, List<String?>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val availableCharacters: List<Character> = emptyList(),
    val characterAvatarUrls: Map<String, String?> = emptyMap(),
    val newGroupName: String = "",
    val selectedMembers: Set<String> = emptySet(),
    val isCreating: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val groupToDelete: Group? = null
)

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groupStorage: GroupStorage,
    private val characterStorage: CharacterStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    init {
        loadGroups()
    }

    fun loadGroups() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val groups = groupStorage.loadGroups()
            val avatarUrls = groups.associate { group ->
                group.id to group.members.map { member ->
                    characterStorage.getAvatarUri(member).toString()
                }
            }
            _uiState.update { it.copy(groups = groups, groupAvatarUrls = avatarUrls, isLoading = false) }
        }
    }

    fun showCreateDialog() {
        viewModelScope.launch {
            val characters = characterStorage.listCharacters()
            val urls = characters.associate { char ->
                (char.avatar ?: "${char.name}.png") to
                    characterStorage.getAvatarUri(char.avatar ?: "${char.name}.png").toString()
            }
            _uiState.update {
                it.copy(
                    showCreateDialog = true,
                    availableCharacters = characters,
                    characterAvatarUrls = urls,
                    newGroupName = "",
                    selectedMembers = emptySet()
                )
            }
        }
    }

    fun dismissCreateDialog() {
        _uiState.update {
            it.copy(showCreateDialog = false, newGroupName = "", selectedMembers = emptySet())
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
        if (state.newGroupName.isBlank()) {
            _uiState.update { it.copy(error = "Group name cannot be empty") }
            return
        }
        if (state.selectedMembers.size < 2) {
            _uiState.update { it.copy(error = "Select at least 2 characters") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true) }
            val group = Group(
                id = UUID.randomUUID().toString(),
                name = state.newGroupName.trim(),
                members = state.selectedMembers.toList()
            )
            groupStorage.saveGroup(group)
            _uiState.update { it.copy(isCreating = false, showCreateDialog = false) }
            loadGroups()
        }
    }

    fun showDeleteConfirmation(group: Group) {
        _uiState.update { it.copy(showDeleteDialog = true, groupToDelete = group) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false, groupToDelete = null) }
    }

    fun deleteGroup() {
        val group = _uiState.value.groupToDelete ?: return
        viewModelScope.launch {
            groupStorage.deleteGroup(group.id)
            _uiState.update { it.copy(showDeleteDialog = false, groupToDelete = null) }
            loadGroups()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
