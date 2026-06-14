package com.pockettavern.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.remote.dto.st.ChatCompletionSources
import com.pockettavern.app.data.remote.dto.st.MainApiTypes
import com.pockettavern.app.data.remote.dto.st.TextGenTypes
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.data.repository.LlmRepository
import com.pockettavern.app.domain.model.ApiConfiguration
import com.pockettavern.app.domain.model.AvailableModel
import com.pockettavern.app.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ApiConfigUiState(
    val isLoading: Boolean = true,
    val config: ApiConfiguration = ApiConfiguration.DEFAULT,
    val apiKey: String = "",
    val availableModels: List<AvailableModel> = emptyList(),
    val isLoadingModels: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class ApiConfigViewModel @Inject constructor(
    private val localRepository: LocalRepository,
    private val llmRepository: LlmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiConfigUiState())
    val uiState: StateFlow<ApiConfigUiState> = _uiState.asStateFlow()

    init {
        loadConfiguration()
    }

    fun loadConfiguration() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = localRepository.getApiConfiguration()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            config = result.data,
                            apiKey = result.data.apiKey
                        )
                    }
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
            _uiState.update { it.copy(isLoadingModels = true, error = null) }
            val models = withContext(Dispatchers.IO) {
                llmRepository.getAvailableModels(config)
            }
            _uiState.update {
                it.copy(
                    isLoadingModels = false,
                    availableModels = models,
                    error = if (models.isEmpty()) "No models found — check URL, API key, and Debug Log" else null
                )
            }
        }
    }

    fun setMainApi(mainApi: String) {
        _uiState.update {
            it.copy(
                config = it.config.copy(mainApi = mainApi),
                availableModels = emptyList()
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
                availableModels = emptyList()
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

    fun setApiKey(key: String) {
        _uiState.update {
            it.copy(
                apiKey = key,
                config = it.config.copy(apiKey = key)
            )
        }
    }

    fun setShowThoughts(enabled: Boolean) {
        _uiState.update { it.copy(config = it.config.copy(showThoughts = enabled)) }
    }

    fun saveConfiguration() {
        val config = _uiState.value.config
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, saveSuccess = false) }
            try {
                localRepository.saveApiConfiguration(config)
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
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
