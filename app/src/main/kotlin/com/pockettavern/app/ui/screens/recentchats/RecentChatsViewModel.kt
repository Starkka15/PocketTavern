package com.pockettavern.app.ui.screens.recentchats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.Character
import com.pockettavern.app.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentChat(
    val character: Character,
    val avatarUrl: String?,
    val lastMessage: String = "Tap to continue chatting",
    val timestamp: Long = 0L
)

data class RecentChatsUiState(
    val recentChats: List<RecentChat> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RecentChatsViewModel @Inject constructor(
    private val localRepository: LocalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecentChatsUiState())
    val uiState: StateFlow<RecentChatsUiState> = _uiState.asStateFlow()

    init {
        loadRecentChats()
    }

    fun loadRecentChats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val result = localRepository.getCharacters()) {
                is Result.Success -> {
                    val characters = result.data

                    // Load chat info for each character in parallel
                    val recentChatsDeferred = characters.map { char ->
                        async {
                            val chatsResult = localRepository.getCharacterChats(char.name)

                            when (chatsResult) {
                                is Result.Success -> {
                                    val chats = chatsResult.data
                                    if (chats.isNotEmpty()) {
                                        // listChats() already returns sorted by lastModified desc
                                        val best = chats.first()
                                        val avatarKey = char.avatar ?: "${char.name}.png"

                                        RecentChat(
                                            character = char,
                                            avatarUrl = localRepository.getAvatarUri(avatarKey).toString(),
                                            lastMessage = best.lastMessage?.take(100) ?: "Tap to continue chatting",
                                            timestamp = best.lastModified
                                        )
                                    } else {
                                        null
                                    }
                                }
                                is Result.Error -> null
                            }
                        }
                    }

                    val recentChats = recentChatsDeferred.awaitAll()
                        .filterNotNull()
                        .sortedByDescending { it.timestamp }

                    _uiState.update {
                        it.copy(
                            recentChats = recentChats,
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
