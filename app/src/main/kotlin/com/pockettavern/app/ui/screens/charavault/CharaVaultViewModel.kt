package com.pockettavern.app.ui.screens.charavault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.data.repository.CharaVaultRepository
import com.pockettavern.app.domain.model.CharaVaultCharacter
import com.pockettavern.app.domain.model.CharaVaultNsfwFilter
import com.pockettavern.app.domain.model.CharaVaultStats
import com.pockettavern.app.domain.model.CharaVaultLorebook
import com.pockettavern.app.domain.model.CharaVaultLorebookStats
import com.pockettavern.app.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import com.pockettavern.app.util.DebugLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CharaVaultContentType(val displayName: String) {
    CHARACTERS("Character Cards"),
    LOREBOOKS("Lorebooks")
}

data class CharaVaultUiState(
    // Content type switching
    val contentType: CharaVaultContentType = CharaVaultContentType.CHARACTERS,

    // Common fields
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalCount: Int = 0,
    val nsfwFilter: CharaVaultNsfwFilter = CharaVaultNsfwFilter.ALL,
    val selectedTags: List<String> = emptyList(),
    val availableTags: List<Pair<String, Int>> = emptyList(),
    val isLoadingTags: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val isImporting: Boolean = false,
    val error: String? = null,
    val importSuccess: Boolean = false,
    val serverUrl: String = "",
    val isServerConfigured: Boolean = false,

    // CharaVault.net auth state
    val charavaultMode: String = "local", // "local" or "charavault"
    val isLoggedIn: Boolean = false,
    val charavaultEmail: String? = null,
    val nsfwVerified: Boolean = false,
    val showLoginDialog: Boolean = false,
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    val requires2fa: Boolean = false,
    val challengeToken: String? = null,

    // Character-specific
    val characterResults: List<CharaVaultCharacter> = emptyList(),
    val selectedCharacter: CharaVaultCharacter? = null,
    val stats: CharaVaultStats? = null,

    // Lorebook-specific
    val lorebookResults: List<CharaVaultLorebook> = emptyList(),
    val selectedLorebook: CharaVaultLorebook? = null,
    val lorebookStats: CharaVaultLorebookStats? = null,
)

@HiltViewModel
class CharaVaultViewModel @Inject constructor(
    private val repository: CharaVaultRepository,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharaVaultUiState())
    val uiState: StateFlow<CharaVaultUiState> = _uiState.asStateFlow()

    companion object {
        const val PAGE_SIZE = 50
    }

    init {
        loadServerUrl()
    }

    private fun loadServerUrl() {
        viewModelScope.launch {
            val url = settingsDataStore.charaVaultUrlFlow.first()
            val mode = settingsDataStore.getCharaVaultMode()
            val session = settingsDataStore.getCharaVaultSession()
            val isConfigured = if (mode == "charavault") true else url.isNotBlank()

            _uiState.update {
                it.copy(
                    serverUrl = url,
                    isServerConfigured = isConfigured,
                    charavaultMode = mode,
                    isLoggedIn = mode == "charavault" && session != null,
                    charavaultEmail = session?.email
                )
            }

            if (mode == "charavault" && session != null) {
                when (val result = repository.getMe()) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(nsfwVerified = result.data.nsfwVerified, isLoggedIn = true)
                        }
                    }
                    is Result.Error -> {
                        settingsDataStore.clearCharaVaultSession()
                        _uiState.update {
                            it.copy(isLoggedIn = false, charavaultEmail = null, nsfwVerified = false)
                        }
                    }
                }
            }

            if (isConfigured) {
                loadStats()
                loadTags()
                search()
            }
        }
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.saveCharaVaultUrl(url)
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

    fun setMode(mode: String) {
        viewModelScope.launch {
            settingsDataStore.saveCharaVaultMode(mode)
            _uiState.update {
                it.copy(
                    charavaultMode = mode,
                    characterResults = emptyList(),
                    lorebookResults = emptyList(),
                    currentPage = 1,
                    error = null,
                    isServerConfigured = if (mode == "charavault") true else it.serverUrl.isNotBlank()
                )
            }
            loadServerUrl()
        }
    }

    fun showLogin() {
        _uiState.update { it.copy(showLoginDialog = true, loginError = null, requires2fa = false, challengeToken = null) }
    }

    fun hideLogin() {
        _uiState.update { it.copy(showLoginDialog = false, loginError = null, requires2fa = false, challengeToken = null) }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, loginError = null) }
            when (val result = repository.login(email, password)) {
                is Result.Success -> {
                    val response = result.data
                    if (response.requires2fa) {
                        _uiState.update {
                            it.copy(isLoggingIn = false, requires2fa = true, challengeToken = response.challengeToken)
                        }
                    } else if (response.token != null && response.user != null) {
                        settingsDataStore.saveCharaVaultSession(response.token, response.user.email)
                        _uiState.update {
                            it.copy(
                                isLoggingIn = false, isLoggedIn = true,
                                charavaultEmail = response.user.email,
                                nsfwVerified = response.user.nsfwVerified,
                                showLoginDialog = false, requires2fa = false, challengeToken = null
                            )
                        }
                        loadStats(); loadTags(); search()
                    } else {
                        _uiState.update { it.copy(isLoggingIn = false, loginError = "Login failed") }
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoggingIn = false, loginError = result.exception.message ?: "Login failed") }
                }
            }
        }
    }

    fun verify2fa(code: String) {
        val challengeToken = _uiState.value.challengeToken ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, loginError = null) }
            when (val result = repository.verify2fa(challengeToken, code)) {
                is Result.Success -> {
                    val response = result.data
                    if (response.token != null && response.user != null) {
                        settingsDataStore.saveCharaVaultSession(response.token, response.user.email)
                        _uiState.update {
                            it.copy(
                                isLoggingIn = false, isLoggedIn = true,
                                charavaultEmail = response.user.email,
                                nsfwVerified = response.user.nsfwVerified,
                                showLoginDialog = false, requires2fa = false, challengeToken = null
                            )
                        }
                        loadStats(); loadTags(); search()
                    } else {
                        _uiState.update { it.copy(isLoggingIn = false, loginError = "2FA verification failed") }
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoggingIn = false, loginError = result.exception.message ?: "2FA verification failed") }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            settingsDataStore.clearCharaVaultSession()
            _uiState.update {
                it.copy(isLoggedIn = false, charavaultEmail = null, nsfwVerified = false,
                    characterResults = emptyList(), lorebookResults = emptyList())
            }
            search()
        }
    }

    fun verifyAge() {
        viewModelScope.launch {
            when (val result = repository.verifyAge()) {
                is Result.Success -> {
                    val newToken = result.data
                    if (newToken.isNotBlank()) {
                        val email = _uiState.value.charavaultEmail ?: ""
                        settingsDataStore.saveCharaVaultSession(newToken, email)
                    }
                    _uiState.update { it.copy(nsfwVerified = true) }
                    search()
                }
                is Result.Error -> {
                    _uiState.update { it.copy(error = "Age verification failed: ${result.exception.message}") }
                }
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            when (val result = repository.getStats()) {
                is Result.Success -> _uiState.update { it.copy(stats = result.data) }
                is Result.Error -> { /* stats are optional */ }
            }
        }
    }

    fun loadTags() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTags = true) }
            when (val result = repository.getTags()) {
                is Result.Success -> _uiState.update { it.copy(availableTags = result.data, isLoadingTags = false) }
                is Result.Error -> _uiState.update { it.copy(isLoadingTags = false) }
            }
        }
    }

    fun setContentType(type: CharaVaultContentType) {
        _uiState.update {
            it.copy(
                contentType = type, searchQuery = "", currentPage = 1,
                totalCount = 0, characterResults = emptyList(), lorebookResults = emptyList(),
                selectedCharacter = null, selectedLorebook = null
            )
        }
        search()
        if (type == CharaVaultContentType.LOREBOOKS) loadLorebookStats()
    }

    fun search(query: String = _uiState.value.searchQuery) {
        if (_uiState.value.contentType == CharaVaultContentType.CHARACTERS) {
            searchCharacters(query)
        } else {
            searchLorebooks(query)
        }
    }

    private fun searchCharacters(query: String) {
        viewModelScope.launch {
            try {
                DebugLogger.log("CharaVault: Starting character search, query='$query'")
                _uiState.update {
                    it.copy(searchQuery = query, isLoading = true, currentPage = 1, characterResults = emptyList(), error = null)
                }
                val state = _uiState.value
                when (val result = repository.search(
                    query = query.takeIf { it.isNotBlank() },
                    nsfwFilter = state.nsfwFilter,
                    tags = state.selectedTags.takeIf { it.isNotEmpty() },
                    page = 1, limit = PAGE_SIZE
                )) {
                    is Result.Success -> {
                        DebugLogger.log("CharaVault: Search success, got ${result.data.characters.size} results")
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
                        DebugLogger.logError("CharaVault", "Search failed", result.exception)
                        _uiState.update { it.copy(isLoading = false, error = result.exception.message ?: "Search failed") }
                    }
                }
            } catch (e: Exception) {
                DebugLogger.logError("CharaVault", "Uncaught exception in search", e)
                _uiState.update { it.copy(isLoading = false, error = "Crash: ${e.message}") }
            }
        }
    }

    private fun searchLorebooks(query: String) {
        viewModelScope.launch {
            try {
                DebugLogger.log("CharaVault: Starting lorebook search, query='$query'")
                _uiState.update {
                    it.copy(searchQuery = query, isLoading = true, currentPage = 1, lorebookResults = emptyList(), error = null)
                }
                val state = _uiState.value
                when (val result = repository.searchLorebooks(
                    query = query.takeIf { it.isNotBlank() },
                    nsfwFilter = state.nsfwFilter,
                    topics = state.selectedTags.takeIf { it.isNotEmpty() },
                    page = 1, limit = PAGE_SIZE
                )) {
                    is Result.Success -> {
                        DebugLogger.log("CharaVault: Lorebook search success, got ${result.data.lorebooks.size} results")
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
                        DebugLogger.logError("CharaVault", "Lorebook search failed", result.exception)
                        _uiState.update { it.copy(isLoading = false, error = result.exception.message ?: "Search failed") }
                    }
                }
            } catch (e: Exception) {
                DebugLogger.logError("CharaVault", "Uncaught exception in lorebook search", e)
                _uiState.update { it.copy(isLoading = false, error = "Crash: ${e.message}") }
            }
        }
    }

    // ── Pagination ─────────────────────────────────────────────────────────────

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.currentPage >= state.totalPages) return
        if (state.contentType == CharaVaultContentType.CHARACTERS) {
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
                page = nextPage, limit = PAGE_SIZE
            )) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(characterResults = it.characterResults + result.data.characters, currentPage = nextPage, isLoadingMore = false)
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoadingMore = false, error = result.exception.message) }
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
                page = nextPage, limit = PAGE_SIZE
            )) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(lorebookResults = it.lorebookResults + result.data.lorebooks, currentPage = nextPage, isLoadingMore = false)
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoadingMore = false, error = result.exception.message) }
                }
            }
        }
    }

    fun loadLorebookStats() {
        viewModelScope.launch {
            when (val result = repository.getLorebookStats()) {
                is Result.Success -> _uiState.update { it.copy(lorebookStats = result.data) }
                is Result.Error -> { /* optional */ }
            }
        }
    }

    fun goToPage(page: Int) {
        val state = _uiState.value
        if (page < 1 || page > state.totalPages || page == state.currentPage) return
        if (state.contentType == CharaVaultContentType.CHARACTERS) {
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
                page = page, limit = PAGE_SIZE
            )) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(characterResults = result.data.characters, currentPage = result.data.currentPage,
                            totalPages = result.data.totalPages, totalCount = result.data.totalCount, isLoading = false)
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
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
                page = page, limit = PAGE_SIZE
            )) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(lorebookResults = result.data.lorebooks, currentPage = result.data.currentPage,
                            totalPages = result.data.totalPages, totalCount = result.data.totalCount, isLoading = false)
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.exception.message) }
                }
            }
        }
    }

    fun nextPage() { goToPage(_uiState.value.currentPage + 1) }
    fun previousPage() { goToPage(_uiState.value.currentPage - 1) }

    // ── Selection & import ─────────────────────────────────────────────────────

    fun selectCharacter(character: CharaVaultCharacter) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedCharacter = character, isLoadingDetails = true, importSuccess = false) }
            when (val result = repository.getCardDetails(character.folder, character.file)) {
                is Result.Success -> _uiState.update { it.copy(selectedCharacter = result.data, isLoadingDetails = false) }
                is Result.Error -> _uiState.update { it.copy(isLoadingDetails = false) }
            }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedCharacter = null, importSuccess = false) }
    }

    fun importCharacter() {
        val character = _uiState.value.selectedCharacter ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }
            when (val result = repository.importCard(character)) {
                is Result.Success -> _uiState.update { it.copy(isImporting = false, importSuccess = true) }
                is Result.Error -> _uiState.update { it.copy(isImporting = false, error = result.exception.message ?: "Import failed") }
            }
        }
    }

    fun selectLorebook(lorebook: CharaVaultLorebook) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedLorebook = lorebook, isLoadingDetails = true, importSuccess = false) }
            when (val result = repository.getLorebookDetails(lorebook.id)) {
                is Result.Success -> _uiState.update { it.copy(selectedLorebook = result.data, isLoadingDetails = false) }
                is Result.Error -> _uiState.update { it.copy(isLoadingDetails = false) }
            }
        }
    }

    fun clearLorebookSelection() {
        _uiState.update { it.copy(selectedLorebook = null, importSuccess = false) }
    }

    fun importLorebook() {
        val lorebook = _uiState.value.selectedLorebook ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }
            when (val result = repository.importLorebook(lorebook)) {
                is Result.Success -> _uiState.update { it.copy(isImporting = false, importSuccess = true) }
                is Result.Error -> _uiState.update { it.copy(isImporting = false, error = result.exception.message ?: "Import failed") }
            }
        }
    }

    fun setNsfwFilter(filter: CharaVaultNsfwFilter) {
        _uiState.update { it.copy(nsfwFilter = filter) }
        search()
    }

    fun toggleTag(tag: String) {
        _uiState.update { state ->
            val newTags = if (tag in state.selectedTags) state.selectedTags - tag else state.selectedTags + tag
            state.copy(selectedTags = newTags)
        }
        search()
    }

    fun clearTags() {
        _uiState.update { it.copy(selectedTags = emptyList()) }
        search()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Image URL ──────────────────────────────────────────────────────────────

    fun buildImageUrl(character: CharaVaultCharacter): String {
        return try {
            val baseUrl = if (_uiState.value.charavaultMode == "charavault") {
                "https://charavault.net"
            } else {
                _uiState.value.serverUrl
            }
            val url = repository.buildImageUrl(baseUrl, character)
            DebugLogger.log("CharaVault: buildImageUrl for '${character.name}' -> $url")
            url
        } catch (e: Exception) {
            DebugLogger.logError("CharaVault", "buildImageUrl failed for '${character.name}'", e)
            ""
        }
    }
}
