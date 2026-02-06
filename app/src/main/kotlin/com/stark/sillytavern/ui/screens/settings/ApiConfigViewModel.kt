package com.stark.sillytavern.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.remote.dto.st.ChatCompletionSources
import com.stark.sillytavern.data.remote.dto.st.MainApiTypes
import com.stark.sillytavern.data.remote.dto.st.TextGenTypes
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.ApiConfiguration
import com.stark.sillytavern.domain.model.AvailableModel
import com.stark.sillytavern.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApiConfigUiState(
    val isLoading: Boolean = true,
    val config: ApiConfiguration = ApiConfiguration.DEFAULT,
    val availableModels: List<AvailableModel> = emptyList(),
    val isLoadingModels: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class ApiConfigViewModel @Inject constructor(
    private val repository: SillyTavernRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiConfigUiState())
    val uiState: StateFlow<ApiConfigUiState> = _uiState.asStateFlow()

    init {
        loadConfiguration()
    }

    fun loadConfiguration() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = repository.getApiConfiguration()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            config = result.data
                        )
                    }
                    // Also fetch available models
                    fetchModels()
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

    fun fetchModels() {
        val config = _uiState.value.config
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true) }

            when (val result = repository.getAvailableModels(config)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingModels = false,
                            availableModels = result.data
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingModels = false,
                            error = result.exception.message
                        )
                    }
                }
            }
        }
    }

    fun setMainApi(mainApi: String) {
        _uiState.update {
            it.copy(
                config = it.config.copy(mainApi = mainApi),
                availableModels = emptyList()  // Clear models when API changes
            )
        }
    }

    fun setTextGenType(type: String) {
        _uiState.update {
            it.copy(config = it.config.copy(textGenType = type))
        }
    }

    fun setApiServer(server: String) {
        _uiState.update {
            it.copy(config = it.config.copy(apiServer = server))
        }
    }

    fun setChatCompletionSource(source: String) {
        _uiState.update {
            it.copy(
                config = it.config.copy(chatCompletionSource = source),
                availableModels = emptyList()  // Clear models when source changes
            )
        }
    }

    fun setCustomUrl(url: String) {
        _uiState.update {
            it.copy(config = it.config.copy(customUrl = url.ifBlank { null }))
        }
    }

    fun setCurrentModel(model: String) {
        _uiState.update {
            it.copy(config = it.config.copy(currentModel = model))
        }
    }

    fun saveConfiguration() {
        val config = _uiState.value.config
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, saveSuccess = false) }

            when (val result = repository.saveApiConfiguration(config)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = true
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
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

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    // Helper to get display options
    val mainApiOptions: List<Pair<String, String>> = MainApiTypes.all
    val textGenTypeOptions: List<Pair<String, String>> = TextGenTypes.all
    val chatCompletionSourceOptions: List<Pair<String, String>> = ChatCompletionSources.all
}
