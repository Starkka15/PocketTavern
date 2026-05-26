package com.pockettavern.app.ui.screens.worldinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.domain.model.WorldInfoEntry
import com.pockettavern.app.domain.model.WorldInfoListItem
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
    val error: String? = null,
    val editingEntry: WorldInfoEntry? = null,
    val deleteLorebookConfirm: String? = null
)

@HiltViewModel
class WorldInfoViewModel @Inject constructor(
    private val localRepository: LocalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorldInfoUiState())
    val uiState: StateFlow<WorldInfoUiState> = _uiState.asStateFlow()

    init {
        loadLorebooks()
    }

    fun loadLorebooks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = localRepository.getWorldInfoList()) {
                is Result.Success -> {
                    // Convert names to WorldInfoListItem (fileId == name for local files)
                    val items = result.data.map { name -> WorldInfoListItem(fileId = name, name = name) }
                    _uiState.update {
                        it.copy(
                            lorebooks = items,
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
            when (val result = localRepository.getWorldInfo(name)) {
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

    fun importJson(name: String, bytes: ByteArray) {
        viewModelScope.launch {
            when (val result = localRepository.importWorldInfoJson(name, bytes)) {
                is Result.Success -> loadLorebooks()
                is Result.Error -> _uiState.update { it.copy(error = "Import failed: ${result.exception.message}") }
            }
        }
    }

    fun requestDeleteLorebook(name: String) {
        _uiState.update { it.copy(deleteLorebookConfirm = name) }
    }

    fun dismissDeleteLorebook() {
        _uiState.update { it.copy(deleteLorebookConfirm = null) }
    }

    fun confirmDeleteLorebook() {
        val name = _uiState.value.deleteLorebookConfirm ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(deleteLorebookConfirm = null) }
            when (val result = localRepository.deleteWorldInfo(name)) {
                is Result.Success -> {
                    if (_uiState.value.selectedLorebook == name) {
                        _uiState.update { it.copy(selectedLorebook = null, entries = emptyList()) }
                    }
                    loadLorebooks()
                }
                is Result.Error -> _uiState.update { it.copy(error = result.exception.message) }
            }
        }
    }

    fun startEditEntry(entry: WorldInfoEntry) {
        _uiState.update { it.copy(editingEntry = entry) }
    }

    fun dismissEditEntry() {
        _uiState.update { it.copy(editingEntry = null) }
    }

    fun saveEntry(entry: WorldInfoEntry) {
        val name = _uiState.value.selectedLorebook ?: return
        viewModelScope.launch {
            val current = _uiState.value.entries.toMutableList()
            val idx = current.indexOfFirst { it.uid == entry.uid }
            if (idx >= 0) current[idx] = entry else current.add(entry)
            _uiState.update { it.copy(editingEntry = null, entries = current.sortedBy { it.order }) }
            when (val result = localRepository.saveWorldInfo(name, current)) {
                is Result.Error -> _uiState.update { it.copy(error = result.exception.message) }
                else -> {}
            }
        }
    }

    fun deleteEntry(uid: String) {
        val name = _uiState.value.selectedLorebook ?: return
        viewModelScope.launch {
            val updated = _uiState.value.entries.filter { it.uid != uid }
            _uiState.update { it.copy(entries = updated, editingEntry = null) }
            when (val result = localRepository.saveWorldInfo(name, updated)) {
                is Result.Error -> _uiState.update { it.copy(error = result.exception.message) }
                else -> {}
            }
        }
    }

    fun addNewEntry() {
        val newEntry = WorldInfoEntry(
            uid = System.currentTimeMillis().toString(),
            key = emptyList(),
            keysecondary = emptyList(),
            content = "",
            comment = "New Entry",
            enabled = true,
            order = (_uiState.value.entries.maxOfOrNull { it.order } ?: 0) + 10
        )
        _uiState.update { it.copy(editingEntry = newEntry) }
    }
}
