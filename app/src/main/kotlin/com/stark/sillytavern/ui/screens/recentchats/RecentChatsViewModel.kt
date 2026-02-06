package com.stark.sillytavern.ui.screens.recentchats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.Character
import com.stark.sillytavern.domain.model.Result
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
    val timestamp: Long = 0L // For sorting
)

data class RecentChatsUiState(
    val recentChats: List<RecentChat> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RecentChatsViewModel @Inject constructor(
    private val stRepository: SillyTavernRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecentChatsUiState())
    val uiState: StateFlow<RecentChatsUiState> = _uiState.asStateFlow()

    init {
        loadRecentChats()
    }

    fun loadRecentChats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val result = stRepository.getCharacters()) {
                is Result.Success -> {
                    val characters = result.data

                    // Load chat info for each character in parallel
                    val recentChatsDeferred = characters.map { char ->
                        async {
                            val avatarUrl = char.avatar ?: char.name
                            val chatsResult = stRepository.getCharacterChats(avatarUrl)

                            when (chatsResult) {
                                is Result.Success -> {
                                    val chats = chatsResult.data
                                    if (chats.isNotEmpty()) {
                                        // Find the chat with the most recent message
                                        var bestChat = chats.first()
                                        var bestTimestamp = 0L
                                        var bestLastMsg = bestChat.lastMessage?.take(100) ?: "Tap to continue chatting"

                                        // Check each chat to find the one with the latest message
                                        for (chat in chats) {
                                            val chatResult = stRepository.getChatWithTimestamp(
                                                char.name, avatarUrl, chat.fileName
                                            )
                                            if (chatResult is Result.Success) {
                                                val (_, lastTimestamp) = chatResult.data
                                                if (lastTimestamp > bestTimestamp) {
                                                    bestTimestamp = lastTimestamp
                                                    bestChat = chat
                                                    bestLastMsg = chat.lastMessage?.take(100) ?: "Tap to continue chatting"
                                                }
                                            }
                                        }

                                        // If no timestamp found, fall back to filename parsing
                                        if (bestTimestamp == 0L) {
                                            bestTimestamp = parseTimestampFromFileName(bestChat.fileName)
                                        }

                                        RecentChat(
                                            character = char,
                                            avatarUrl = stRepository.buildAvatarUrl(char.avatar),
                                            lastMessage = bestLastMsg,
                                            timestamp = bestTimestamp
                                        )
                                    } else {
                                        // No chats for this character
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

    /**
     * Parse timestamp from chat filename.
     * Format: "CharName - 2024-01-15@14h30m45s123ms"
     */
    private fun parseTimestampFromFileName(fileName: String): Long {
        return try {
            val regex = Regex("(\\d{4})-(\\d{2})-(\\d{2})@(\\d{2})h(\\d{2})m(\\d{2})s(\\d+)ms")
            val match = regex.find(fileName)

            if (match != null) {
                val (year, month, day, hour, minute, second, ms) = match.destructured
                // Create a simple timestamp for sorting (not exact, but good enough for ordering)
                val yearVal = year.toLongOrNull() ?: 0
                val monthVal = month.toLongOrNull() ?: 0
                val dayVal = day.toLongOrNull() ?: 0
                val hourVal = hour.toLongOrNull() ?: 0
                val minuteVal = minute.toLongOrNull() ?: 0
                val secondVal = second.toLongOrNull() ?: 0
                val msVal = ms.toLongOrNull() ?: 0

                // Convert to a sortable long value
                yearVal * 10000000000000L +
                monthVal * 100000000000L +
                dayVal * 1000000000L +
                hourVal * 10000000L +
                minuteVal * 100000L +
                secondVal * 1000L +
                msVal
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
