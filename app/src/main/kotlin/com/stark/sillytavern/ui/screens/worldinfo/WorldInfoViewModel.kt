package com.stark.sillytavern.ui.screens.worldinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.Result
import com.stark.sillytavern.domain.model.WorldInfoEntry
import com.stark.sillytavern.domain.model.WorldInfoListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorldInfoUiState(
    val lorebooks: List<WorldInfoListItem> = emptyList(),
    val selectedLorebook: String? = null,
    val entries: List<WorldInfoEntry> = emptyList(),
    val expandedEntryId: String? = null,
    val isLoading: Boolean = false,
    val isLoadingEntries: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WorldInfoViewModel @Inject constructor(
    private val repository: SillyTavernRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorldInfoUiState())
    val uiState: StateFlow<WorldInfoUiState> = _uiState.asStateFlow()

    init {
        loadLorebooks()
    }

    fun loadLorebooks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = repository.getWorldInfoList()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            lorebooks = result.data,
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

    fun selectLorebook(name: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedLorebook = name,
                    isLoadingEntries = true,
                    entries = emptyList()
                )
            }
            when (val result = repository.getWorldInfo(name)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            entries = result.data.sortedBy { entry -> entry.order },
                            isLoadingEntries = false
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingEntries = false,
                            error = result.exception.message
                        )
                    }
                }
            }
        }
    }

    fun toggleEntryExpanded(entryId: String) {
        _uiState.update {
            it.copy(
                expandedEntryId = if (it.expandedEntryId == entryId) null else entryId
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedLorebook = null,
                entries = emptyList()
            )
        }
    }
}
