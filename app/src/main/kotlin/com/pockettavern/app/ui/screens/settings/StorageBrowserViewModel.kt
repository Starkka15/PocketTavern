package com.pockettavern.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.local.CharacterStorage
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorageBrowserUiState(
    val files: List<CharacterStorage.CharacterFileInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isRescanning: Boolean = false,
    val rescanResult: String? = null,
    val error: String? = null,
    val fileToDelete: CharacterStorage.CharacterFileInfo? = null,
    val showDeleteDialog: Boolean = false,
    val isDeleting: Boolean = false
)

@HiltViewModel
class StorageBrowserViewModel @Inject constructor(
    private val localRepository: LocalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageBrowserUiState())
    val uiState: StateFlow<StorageBrowserUiState> = _uiState.asStateFlow()

    init {
        loadFiles()
    }

    fun loadFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = localRepository.listCharacterFileInfo()) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, files = result.data)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.exception.message)
                }
            }
        }
    }

    fun rescan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRescanning = true, rescanResult = null, error = null) }
            try {
                localRepository.rebuildCharacterIndex()
                when (val result = localRepository.listCharacterFileInfo()) {
                    is Result.Success -> {
                        val onDisk = result.data.count { it.existsOnDisk }
                        _uiState.update {
                            it.copy(
                                isRescanning = false,
                                files = result.data,
                                rescanResult = "Index rebuilt — $onDisk character${if (onDisk == 1) "" else "s"} on disk"
                            )
                        }
                    }
                    is Result.Error -> _uiState.update {
                        it.copy(isRescanning = false, error = result.exception.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRescanning = false, error = e.message) }
            }
        }
    }

    fun showDeleteConfirm(file: CharacterStorage.CharacterFileInfo) {
        _uiState.update { it.copy(showDeleteDialog = true, fileToDelete = file) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false, fileToDelete = null) }
    }

    fun confirmDelete() {
        val file = _uiState.value.fileToDelete ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, showDeleteDialog = false) }
            when (val result = localRepository.deleteCharacter(file.fileName)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isDeleting = false, fileToDelete = null) }
                    loadFiles()
                }
                is Result.Error -> _uiState.update {
                    it.copy(isDeleting = false, fileToDelete = null, error = result.exception.message)
                }
            }
        }
    }

    fun clearRescanResult() {
        _uiState.update { it.copy(rescanResult = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
