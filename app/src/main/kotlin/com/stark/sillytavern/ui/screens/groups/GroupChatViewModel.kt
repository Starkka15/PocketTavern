package com.stark.sillytavern.ui.screens.groups

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.Group
import com.stark.sillytavern.domain.model.GroupChatMessage
import com.stark.sillytavern.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupChatUiState(
    val group: Group? = null,
    val messages: List<GroupChatMessage> = emptyList(),
    val memberAvatarUrls: Map<String, String?> = emptyMap(),  // Member avatar -> full URL
    val inputText: String = "",
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val error: String? = null,
    // API info
    val currentApiName: String = "",
    val currentModelName: String = ""
)

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    private val repository: SillyTavernRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupChatUiState())
    val uiState: StateFlow<GroupChatUiState> = _uiState.asStateFlow()

    private var currentGroupId: String = ""

    fun loadGroup(groupId: String) {
        currentGroupId = groupId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load API info
            loadApiInfo()

            // Load groups to find ours
            when (val groupsResult = repository.getAllGroups()) {
                is Result.Success -> {
                    val group = groupsResult.data.find { it.id == groupId }
                    if (group == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Group not found"
                            )
                        }
                        return@launch
                    }

                    // Build avatar URLs for members
                    val avatarUrls = group.members.associateWith { memberAvatar ->
                        repository.buildGroupMemberAvatarUrl(memberAvatar)
                    }

                    _uiState.update {
                        it.copy(
                            group = group,
                            memberAvatarUrls = avatarUrls
                        )
                    }

                    // Load chat messages
                    loadMessages(groupId)
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = groupsResult.exception.message
                        )
                    }
                }
            }
        }
    }

    private fun loadApiInfo() {
        val (apiName, modelName) = repository.getCurrentApiInfo()
        _uiState.update {
            it.copy(
                currentApiName = apiName,
                currentModelName = modelName
            )
        }
    }

    private suspend fun loadMessages(groupId: String) {
        when (val result = repository.getGroupChat(groupId)) {
            is Result.Success -> {
                _uiState.update {
                    it.copy(
                        messages = result.data,
                        isLoading = false
                    )
                }
            }
            is Result.Error -> {
                // No messages yet is OK - might be a new group
                Log.d("GroupChatVM", "No messages or error: ${result.exception.message}")
                _uiState.update {
                    it.copy(
                        messages = emptyList(),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }

            // Add user message locally
            val userMessage = GroupChatMessage(
                content = text,
                isUser = true,
                senderName = "You"
            )

            val updatedMessages = _uiState.value.messages + userMessage
            _uiState.update {
                it.copy(
                    messages = updatedMessages,
                    inputText = ""
                )
            }

            // Save to server
            when (val result = repository.saveGroupChat(currentGroupId, updatedMessages)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSending = false) }
                    // Note: In a full implementation, we'd trigger AI generation here
                    // For now, we just save the user message
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSending = false,
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
