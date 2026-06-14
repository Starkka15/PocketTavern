package com.pockettavern.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.remote.dto.st.ChatCompletionSources
import com.pockettavern.app.data.remote.dto.st.MainApiTypes
import com.pockettavern.app.data.remote.dto.st.TextGenTypes
import com.pockettavern.app.data.local.inference.OnDeviceModelManager
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
    val saveSuccess: Boolean = false,
    // On-device model download
    val isDownloading: Boolean = false,
    val downloadProgress: Float? = null,   // 0f..1f, or null when size unknown
    val downloadStatus: String? = null
)

@HiltViewModel
class ApiConfigViewModel @Inject constructor(
    private val localRepository: LocalRepository,
    private val llmRepository: LlmRepository,
    private val onDeviceModels: OnDeviceModelManager
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

    // ── On-device (LiteRT-LM) model management ───────────────────────────────

    /** Derive a model id (filename minus extension) from a download URL. */
    private fun modelIdFromUrl(url: String): String =
        url.substringAfterLast('/').substringBefore('?')
            .removeSuffix(".litertlm").removeSuffix(".task").removeSuffix(".gguf").ifBlank { "model" }

    fun downloadOnDeviceModel(url: String, token: String?) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(downloadStatus = "Enter a model URL") }
            return
        }
        val modelId = modelIdFromUrl(trimmed)
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = null, downloadStatus = "Starting…", error = null) }
            onDeviceModels.download(trimmed, token?.ifBlank { null }).collect { p ->
                when (p) {
                    is OnDeviceModelManager.Progress.Downloading -> {
                        val frac = if (p.totalBytes > 0) p.bytesDownloaded.toFloat() / p.totalBytes else null
                        val mb = p.bytesDownloaded / (1024 * 1024)
                        val totalMb = if (p.totalBytes > 0) "/${p.totalBytes / (1024 * 1024)}MB" else ""
                        _uiState.update { it.copy(downloadProgress = frac, downloadStatus = "Downloading ${mb}MB$totalMb") }
                    }
                    is OnDeviceModelManager.Progress.Done -> {
                        _uiState.update {
                            it.copy(
                                isDownloading = false, downloadProgress = null,
                                downloadStatus = "Downloaded: $modelId",
                                config = it.config.copy(currentModel = modelId)
                            )
                        }
                        fetchModels()
                    }
                    is OnDeviceModelManager.Progress.Error -> {
                        _uiState.update { it.copy(isDownloading = false, downloadProgress = null, downloadStatus = "Failed: ${p.message}") }
                    }
                }
            }
        }
    }

    fun deleteOnDeviceModel(modelId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { onDeviceModels.delete(modelId) }
            val current = _uiState.value.config.currentModel
            _uiState.update {
                it.copy(
                    downloadStatus = "Deleted: $modelId",
                    config = if (current == modelId) it.config.copy(currentModel = "") else it.config
                )
            }
            fetchModels()
        }
    }

    fun clearDownloadStatus() {
        _uiState.update { it.copy(downloadStatus = null) }
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
    /** Device summary (RAM · cores) for the on-device expectations notice. */
    val deviceSummary: String get() = onDeviceModels.deviceSummary

    /** Catalog for the selected on-device source: GGUF list for llama.cpp, else the LiteRT-LM list. */
    fun catalogFor(source: String): List<com.pockettavern.app.data.local.inference.CatalogModel> =
        if (source.equals("ondevice-gguf", ignoreCase = true))
            com.pockettavern.app.data.local.inference.OnDeviceModelCatalog.ggufModels
        else
            com.pockettavern.app.data.local.inference.OnDeviceModelCatalog.models
}
