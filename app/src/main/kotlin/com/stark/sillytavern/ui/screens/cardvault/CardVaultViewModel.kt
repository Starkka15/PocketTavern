package com.stark.sillytavern.ui.screens.cardvault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.local.SettingsDataStore
import com.stark.sillytavern.data.repository.CardVaultRepository
import com.stark.sillytavern.domain.model.CardVaultCharacter
import com.stark.sillytavern.domain.model.CardVaultNsfwFilter
import com.stark.sillytavern.domain.model.CardVaultStats
import com.stark.sillytavern.domain.model.CardVaultLorebook
import com.stark.sillytavern.domain.model.CardVaultLorebookStats
import com.stark.sillytavern.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import com.stark.sillytavern.util.DebugLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CardVaultContentType(val displayName: String) {
    CHARACTERS("Character Cards"),
    LOREBOOKS("Lorebooks")
}

data class CardVaultUiState(
    // Content type switching
    val contentType: CardVaultContentType = CardVaultContentType.CHARACTERS,

    // Common fields
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalCount: Int = 0,
    val nsfwFilter: CardVaultNsfwFilter = CardVaultNsfwFilter.ALL,
    val selectedTags: List<String> = emptyList(),
    val availableTags: List<Pair<String, Int>> = emptyList(),
    val isLoadingTags: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val isImporting: Boolean = false,
    val error: String? = null,
    val importSuccess: Boolean = false,
    val serverUrl: String = "",
    val isServerConfigured: Boolean = false,

    // Character-specific
    val characterResults: List<CardVaultCharacter> = emptyList(),
    val selectedCharacter: CardVaultCharacter? = null,
    val stats: CardVaultStats? = null,

    // Lorebook-specific
    val lorebookResults: List<CardVaultLorebook> = emptyList(),
    val selectedLorebook: CardVaultLorebook? = null,
    val lorebookStats: CardVaultLorebookStats? = null,

)

@HiltViewModel
class CardVaultViewModel @Inject constructor(
    private val repository: CardVaultRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardVaultUiState())
    val uiState: StateFlow<CardVaultUiState> = _uiState.asStateFlow()

    companion object {
        const val PAGE_SIZE = 50
    }

    init {
        loadServerUrl()
    }

    private fun loadServerUrl() {
        viewModelScope.launch {
            val url = settingsDataStore.cardVaultUrlFlow.first()
            _uiState.update {
                it.copy(
                    serverUrl = url,
                    isServerConfigured = url.isNotBlank()
                )
            }
            if (url.isNotBlank()) {
                loadStats()
                loadTags()
                search()
            }
        }
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.saveCardVaultUrl(url)
            _uiState.update {
                it.copy(
                    serverUrl = url,
                    isServerConfigured = url.isNotBlank(),
                    characterResults = emptyList(),
                    lorebookResults = emptyList(),
                    currentPage = 1,
                    error = null
                )
            }
            if (url.isNotBlank()) {
                loadStats()
                search()
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            when (val result = repository.getStats()) {
                is Result.Success -> {
                    _uiState.update { it.copy(stats = result.data) }
                }
                is Result.Error -> {
                    // Stats are optional, don't show error
                }
            }
        }
    }

    fun loadTags() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTags = true) }
            when (val result = repository.getTags()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            availableTags = result.data,
                            isLoadingTags = false
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoadingTags = false) }
                }
            }
        }
    }

    fun setContentType(type: CardVaultContentType) {
        _uiState.update {
            it.copy(
                contentType = type,
                searchQuery = "",
                currentPage = 1,
                totalCount = 0,
                characterResults = emptyList(),
                lorebookResults = emptyList(),
                selectedCharacter = null,
                selectedLorebook = null
            )
        }
        search()
        if (type == CardVaultContentType.LOREBOOKS) {
            loadLorebookStats()
        }
    }

    fun search(query: String = _uiState.value.searchQuery) {
        val contentType = _uiState.value.contentType
        if (contentType == CardVaultContentType.CHARACTERS) {
            searchCharacters(query)
        } else {
            searchLorebooks(query)
        }
    }

    private fun searchCharacters(query: String) {
        viewModelScope.launch {
            try {
                DebugLogger.log("CardVault: Starting character search, query='$query'")
                _uiState.update {
                    it.copy(
                        searchQuery = query,
                        isLoading = true,
                        currentPage = 1,
                        characterResults = emptyList(),
                        error = null
                    )
                }

                val state = _uiState.value
                when (val result = repository.search(
                    query = query.takeIf { it.isNotBlank() },
                    nsfwFilter = state.nsfwFilter,
                    tags = state.selectedTags.takeIf { it.isNotEmpty() },
                    page = 1,
                    limit = PAGE_SIZE
                )) {
                    is Result.Success -> {
                        DebugLogger.log("CardVault: Search success, got ${result.data.characters.size} results")
                        _uiState.update {
                            it.copy(
                                characterResults = result.data.characters,
                                currentPage = result.data.currentPage,
                                totalPages = result.data.totalPages,
                                totalCount = result.data.totalCount,
                                isLoading = false
                            )
                        }
                    }
                    is Result.Error -> {
                        DebugLogger.logError("CardVault", "Search failed", result.exception)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.exception.message ?: "Search failed"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLogger.logError("CardVault", "Uncaught exception in search", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Crash: ${e.message}"
                    )
                }
            }
        }
    }

    private fun searchLorebooks(query: String) {
        viewModelScope.launch {
            try {
                DebugLogger.log("CardVault: Starting lorebook search, query='$query'")
                _uiState.update {
                    it.copy(
                        searchQuery = query,
                        isLoading = true,
                        currentPage = 1,
                        lorebookResults = emptyList(),
                        error = null
                    )
                }

                val state = _uiState.value
                when (val result = repository.searchLorebooks(
                    query = query.takeIf { it.isNotBlank() },
                    nsfwFilter = state.nsfwFilter,
                    topics = state.selectedTags.takeIf { it.isNotEmpty() },
                    page = 1,
                    limit = PAGE_SIZE
                )) {
                    is Result.Success -> {
                        DebugLogger.log("CardVault: Lorebook search success, got ${result.data.lorebooks.size} results")
                        _uiState.update {
                            it.copy(
                                lorebookResults = result.data.lorebooks,
                                currentPage = result.data.currentPage,
                                totalPages = result.data.totalPages,
                                totalCount = result.data.totalCount,
                                isLoading = false
                            )
                        }
                    }
                    is Result.Error -> {
                        DebugLogger.logError("CardVault", "Lorebook search failed", result.exception)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.exception.message ?: "Search failed"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLogger.logError("CardVault", "Uncaught exception in lorebook search", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Crash: ${e.message}"
                    )
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.currentPage >= state.totalPages) return

        if (state.contentType == CardVaultContentType.CHARACTERS) {
            loadMoreCharacters()
        } else {
            loadMoreLorebooks()
        }
    }

    private fun loadMoreCharacters() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            val nextPage = state.currentPage + 1
            when (val result = repository.search(
                query = state.searchQuery.takeIf { it.isNotBlank() },
                nsfwFilter = state.nsfwFilter,
                tags = state.selectedTags.takeIf { it.isNotEmpty() },
                page = nextPage,
                limit = PAGE_SIZE
            )) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            characterResults = it.characterResults + result.data.characters,
                            currentPage = nextPage,
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

    private fun loadMoreLorebooks() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            val nextPage = state.currentPage + 1
            when (val result = repository.searchLorebooks(
                query = state.searchQuery.takeIf { it.isNotBlank() },
                nsfwFilter = state.nsfwFilter,
                topics = state.selectedTags.takeIf { it.isNotEmpty() },
                page = nextPage,
                limit = PAGE_SIZE
            )) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            lorebookResults = it.lorebookResults + result.data.lorebooks,
                            currentPage = nextPage,
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

    fun loadLorebookStats() {
        viewModelScope.launch {
            when (val result = repository.getLorebookStats()) {
                is Result.Success -> {
                    _uiState.update { it.copy(lorebookStats = result.data) }
                }
                is Result.Error -> {
                    // Stats are optional, don't show error
                }
            }
        }
    }

    fun selectLorebook(lorebook: CardVaultLorebook) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedLorebook = lorebook,
                    isLoadingDetails = true,
                    importSuccess = false
                )
            }

            // Load full details
            when (val result = repository.getLorebookDetails(lorebook.id)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            selectedLorebook = result.data,
                            isLoadingDetails = false
                        )
                    }
                }
                is Result.Error -> {
                    // Keep preview data if full details fail
                    _uiState.update { it.copy(isLoadingDetails = false) }
                }
            }
        }
    }

    fun clearLorebookSelection() {
        _uiState.update {
            it.copy(
                selectedLorebook = null,
                importSuccess = false
            )
        }
    }

    fun importLorebook() {
        val lorebook = _uiState.value.selectedLorebook ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }

            when (val result = repository.importLorebook(lorebook)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            importSuccess = true
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            error = result.exception.message ?: "Import failed"
                        )
                    }
                }
            }
        }
    }

    fun setNsfwFilter(filter: CardVaultNsfwFilter) {
        _uiState.update { it.copy(nsfwFilter = filter) }
        search()
    }

    fun toggleTag(tag: String) {
        _uiState.update { state ->
            val newTags = if (tag in state.selectedTags) {
                state.selectedTags - tag
            } else {
                state.selectedTags + tag
            }
            state.copy(selectedTags = newTags)
        }
        search()
    }

    fun clearTags() {
        _uiState.update { it.copy(selectedTags = emptyList()) }
        search()
    }

    fun selectCharacter(character: CardVaultCharacter) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedCharacter = character,
                    isLoadingDetails = true
                )
            }

            // Load full details
            when (val result = repository.getCardDetails(character.folder, character.file)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            selectedCharacter = result.data,
                            isLoadingDetails = false
                        )
                    }
                }
                is Result.Error -> {
                    // Keep preview data if full details fail
                    _uiState.update { it.copy(isLoadingDetails = false) }
                }
            }
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedCharacter = null,
                importSuccess = false
            )
        }
    }

    fun importCharacter() {
        val character = _uiState.value.selectedCharacter ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }

            when (val result = repository.importCard(character)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            importSuccess = true
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            error = result.exception.message ?: "Import failed"
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun goToPage(page: Int) {
        val state = _uiState.value
        if (page < 1 || page > state.totalPages || page == state.currentPage) return

        if (state.contentType == CardVaultContentType.CHARACTERS) {
            goToCharacterPage(page)
        } else {
            goToLorebookPage(page)
        }
    }

    private fun goToCharacterPage(page: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val state = _uiState.value
            when (val result = repository.search(
                query = state.searchQuery.takeIf { it.isNotBlank() },
                nsfwFilter = state.nsfwFilter,
                tags = state.selectedTags.takeIf { it.isNotEmpty() },
                page = page,
                limit = PAGE_SIZE
            )) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            characterResults = result.data.characters,
                            currentPage = result.data.currentPage,
                            totalPages = result.data.totalPages,
                            totalCount = result.data.totalCount,
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

    private fun goToLorebookPage(page: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val state = _uiState.value
            when (val result = repository.searchLorebooks(
                query = state.searchQuery.takeIf { it.isNotBlank() },
                nsfwFilter = state.nsfwFilter,
                topics = state.selectedTags.takeIf { it.isNotEmpty() },
                page = page,
                limit = PAGE_SIZE
            )) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            lorebookResults = result.data.lorebooks,
                            currentPage = result.data.currentPage,
                            totalPages = result.data.totalPages,
                            totalCount = result.data.totalCount,
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

    fun nextPage() {
        goToPage(_uiState.value.currentPage + 1)
    }

    fun previousPage() {
        goToPage(_uiState.value.currentPage - 1)
    }

    fun buildImageUrl(character: CardVaultCharacter): String {
        return try {
            val url = repository.buildImageUrl(_uiState.value.serverUrl, character)
            DebugLogger.log("CardVault: buildImageUrl for '${character.name}' -> $url")
            url
        } catch (e: Exception) {
            DebugLogger.logError("CardVault", "buildImageUrl failed for '${character.name}'", e)
            ""
        }
    }
}
