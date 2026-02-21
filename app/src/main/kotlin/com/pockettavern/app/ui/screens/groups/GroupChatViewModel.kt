package com.pockettavern.app.ui.screens.groups

import androidx.lifecycle.ViewModel
import com.pockettavern.app.domain.model.Group
import com.pockettavern.app.domain.model.GroupChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class GroupChatUiState(
    val group: Group? = null,
    val messages: List<GroupChatMessage> = emptyList(),
    val memberAvatarUrls: Map<String, String?> = emptyMap(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isGenerating: Boolean = false,
    val streamingContent: String = "",
    val streamingCharacterName: String = "",
    val streamingCharacterAvatar: String = "",
    val error: String? = null,
    val currentApiName: String = "",
    val currentModelName: String = ""
)

@HiltViewModel
class GroupChatViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(GroupChatUiState())
    val uiState: StateFlow<GroupChatUiState> = _uiState.asStateFlow()

    fun loadGroup(groupId: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = "Group chats are not yet supported in standalone mode"
            )
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        _uiState.update { it.copy(error = "Group chats are not yet supported in standalone mode") }
    }

    fun stopGeneration() {
        _uiState.update { it.copy(isGenerating = false) }
    }

    fun setActivationStrategy(strategy: Int) {
        // No-op in standalone mode
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
