package com.stark.sillytavern.ui.screens.chub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.local.ChubSession
import com.stark.sillytavern.data.local.SettingsDataStore
import com.stark.sillytavern.data.repository.ChubRepository
import com.stark.sillytavern.domain.model.ChubCharacter
import com.stark.sillytavern.domain.model.ChubSortOption
import com.stark.sillytavern.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChubBrowserUiState(
    val searchQuery: String = "",
    val results: List<ChubCharacter> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val nsfw: Boolean = false,
    val minStars: Int = 0,
    val sortBy: ChubSortOption = ChubSortOption.DOWNLOADS,
    val selectedCharacter: ChubCharacter? = null,
    val isLoadingDetails: Boolean = false,
    val selectedFirstMessage: String? = null,
    val isImporting: Boolean = false,
    val importSuccess: Boolean = false,
    val error: String? = null,
    // Chub.ai login state
    val isLoggedIn: Boolean = false,
    val chubUsername: String? = null
)

@HiltViewModel
class ChubBrowserViewModel @Inject constructor(
    private val chubRepository: ChubRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChubBrowserUiState())
    val uiState: StateFlow<ChubBrowserUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Observe login state
            settingsDataStore.chubSessionFlow.collect { session ->
                _uiState.update {
                    it.copy(
                        isLoggedIn = session != null,
                        chubUsername = session?.username
                    )
                }
            }
        }
        viewModelScope.launch {
            // Load initial results
            search()
        }
    }

    fun logout() {
        viewModelScope.launch {
            settingsDataStore.clearChubSession()
            // Refresh search after logout
            search()
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setNsfw(enabled: Boolean) {
        _uiState.update { it.copy(nsfw = enabled, currentPage = 1) }
        search()
    }

    fun setMinStars(stars: Int) {
        _uiState.update { it.copy(minStars = stars, currentPage = 1) }
        search()
    }

    fun setSortBy(option: ChubSortOption) {
        _uiState.update { it.copy(sortBy = option, currentPage = 1) }
        search()
    }

    fun search() {
        viewModelScope.launch {
            // Reset to page 1 and clear results for new search
            _uiState.update { it.copy(isLoading = true, currentPage = 1, results = emptyList()) }

            when (val result = chubRepository.search(
                query = _uiState.value.searchQuery,
                page = 1,
                sort = _uiState.value.sortBy,
                nsfw = _uiState.value.nsfw,
                minStars = _uiState.value.minStars
            )) {
                is Result.Success -> {
                    val searchResult = result.data
                    _uiState.update {
                        it.copy(
                            results = searchResult.characters,
                            currentPage = searchResult.currentPage,
                            totalPages = searchResult.totalPages,
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

    fun loadMore() {
        if (_uiState.value.isLoadingMore || _uiState.value.currentPage >= _uiState.value.totalPages) {
            return
        }

        viewModelScope.launch {
            val nextPage = _uiState.value.currentPage + 1
            _uiState.update { it.copy(isLoadingMore = true) }

            when (val result = chubRepository.search(
                query = _uiState.value.searchQuery,
                page = nextPage,
                sort = _uiState.value.sortBy,
                nsfw = _uiState.value.nsfw,
                minStars = _uiState.value.minStars
            )) {
                is Result.Success -> {
                    val searchResult = result.data
                    _uiState.update {
                        it.copy(
                            // Append new results to existing
                            results = it.results + searchResult.characters,
                            currentPage = searchResult.currentPage,
                            totalPages = searchResult.totalPages,
                            isLoadingMore = false
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            error = result.exception.message
                        )
                    }
                }
            }
        }
    }

    fun nextPage() {
        loadMore()
    }

    fun previousPage() {
        // With infinite scroll, going back doesn't make sense
        // Keep for compatibility but it's a no-op now
    }

    fun selectCharacter(character: ChubCharacter) {
        _uiState.update {
            it.copy(
                selectedCharacter = character,
                isLoadingDetails = true,
                selectedFirstMessage = character.firstMessage
            )
        }

        // Load full details if we don't have the first message
        if (character.firstMessage == null) {
            viewModelScope.launch {
                when (val result = chubRepository.getCharacterDetails(character.fullPath)) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                selectedCharacter = result.data,
                                selectedFirstMessage = result.data.firstMessage,
                                isLoadingDetails = false
                            )
                        }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(isLoadingDetails = false)
                        }
                    }
                }
            }
        } else {
            _uiState.update { it.copy(isLoadingDetails = false) }
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedCharacter = null,
                selectedFirstMessage = null
            )
        }
    }

    fun importCharacter(onSuccess: () -> Unit) {
        val character = _uiState.value.selectedCharacter ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }

            when (val result = chubRepository.importCharacter(character.fullPath)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            importSuccess = true,
                            selectedCharacter = null
                        )
                    }
                    onSuccess()
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
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

    /**
     * Returns the avatar URL directly - no proxy needed on Android.
     */
    fun buildAvatarUrl(originalUrl: String?): String? {
        return originalUrl
    }
}
