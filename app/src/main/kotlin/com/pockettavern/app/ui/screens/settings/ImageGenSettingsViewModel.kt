package com.pockettavern.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.data.local.inference.SdxlModelManager
import com.pockettavern.app.data.repository.ImageGenRepository
import com.pockettavern.app.domain.model.AvailableModel
import com.pockettavern.app.domain.model.ImageGenBackendType
import com.pockettavern.app.domain.model.ImageGenCapabilities
import com.pockettavern.app.domain.model.ImageGenConfig
import com.pockettavern.app.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImageGenSettingsUiState(
    val config: ImageGenConfig = ImageGenConfig(),
    val capabilities: ImageGenCapabilities = ImageGenCapabilities(),
    val availableBackends: List<ImageGenBackendType> = ImageGenBackendType.entries.filter {
        // Hidden in release until MNN-converted SDXL models are actually published.
        it != ImageGenBackendType.LOCAL_SD_MNN || com.pockettavern.app.BuildConfig.MNN_SDXL_ENABLED
    },
    val samplers: List<String> = emptyList(),
    val models: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val isLoadingSamplers: Boolean = false,
    val isLoadingModels: Boolean = false,
    // On-device SDXL (MNN) model sets
    val sdxlModels: List<AvailableModel> = emptyList(),
    val isDownloadingSdxl: Boolean = false,
    val sdxlDownloadProgress: Float? = null,
    val sdxlDownloadStatus: String? = null,
)

@HiltViewModel
class ImageGenSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val imageGenRepository: ImageGenRepository,
    private val sdxlModelManager: SdxlModelManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageGenSettingsUiState())
    val uiState: StateFlow<ImageGenSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.imageGenConfigFlow.collect { config ->
                val backend = imageGenRepository.getBackend(config.activeBackendType)
                _uiState.update {
                    it.copy(
                        config = config,
                        capabilities = backend?.capabilities ?: ImageGenCapabilities(),
                        isLoading = false
                    )
                }
            }
        }
        refreshSdxlModels()
    }

    private fun refreshSdxlModels() {
        _uiState.update { it.copy(sdxlModels = sdxlModelManager.listModels()) }
    }

    fun selectSdxlModel(modelId: String) {
        sdxlModelManager.pathFor(modelId)?.let { updateLocalSdxlModelPath(it) }
    }

    fun deleteSdxlModel(modelId: String) {
        val wasActive = sdxlModelManager.pathFor(modelId) == _uiState.value.config.localSdxlModelPath
        sdxlModelManager.delete(modelId)
        if (wasActive) updateLocalSdxlModelPath("")
        refreshSdxlModels()
    }

    fun downloadSdxlModel(url: String, token: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingSdxl = true, sdxlDownloadProgress = null, sdxlDownloadStatus = null) }
            sdxlModelManager.download(url, token).collect { progress ->
                when (progress) {
                    is SdxlModelManager.Progress.Downloading -> _uiState.update {
                        it.copy(
                            sdxlDownloadProgress = if (progress.totalBytes > 0)
                                progress.bytesDownloaded.toFloat() / progress.totalBytes else null,
                            sdxlDownloadStatus = "Downloading ${progress.bytesDownloaded / (1024 * 1024)} MB" +
                                if (progress.totalBytes > 0) " / ${progress.totalBytes / (1024 * 1024)} MB" else ""
                        )
                    }
                    is SdxlModelManager.Progress.Extracting -> _uiState.update {
                        it.copy(
                            sdxlDownloadProgress = if (progress.totalFiles > 0)
                                progress.filesExtracted.toFloat() / progress.totalFiles else null,
                            sdxlDownloadStatus = "Extracting ${progress.filesExtracted}/${progress.totalFiles} files"
                        )
                    }
                    is SdxlModelManager.Progress.Done -> {
                        _uiState.update {
                            it.copy(isDownloadingSdxl = false, sdxlDownloadProgress = null, sdxlDownloadStatus = "Done")
                        }
                        updateLocalSdxlModelPath(progress.path)
                        refreshSdxlModels()
                    }
                    is SdxlModelManager.Progress.Error -> _uiState.update {
                        it.copy(isDownloadingSdxl = false, sdxlDownloadProgress = null, sdxlDownloadStatus = progress.message)
                    }
                }
            }
        }
    }

    fun updateBackend(type: ImageGenBackendType) {
        updateConfig { it.copy(activeBackend = type.name) }
        // Update capabilities for new backend
        val backend = imageGenRepository.getBackend(type)
        _uiState.update {
            it.copy(
                capabilities = backend?.capabilities ?: ImageGenCapabilities(),
                samplers = emptyList(),
                models = emptyList()
            )
        }
    }

    fun updateSdWebuiUrl(url: String) {
        updateConfig { it.copy(sdWebuiUrl = url) }
    }

    fun updateComfyUiUrl(url: String) {
        updateConfig { it.copy(comfyuiUrl = url) }
    }

    fun updateLocalSdxlModelPath(path: String) {
        updateConfig { it.copy(localSdxlModelPath = path) }
    }

    fun updateDalleApiKey(key: String) {
        updateConfig { it.copy(dalleApiKey = key) }
    }

    fun updateDalleModel(model: String) {
        updateConfig { it.copy(dalleModel = model) }
    }

    fun updateStabilityApiKey(key: String) {
        updateConfig { it.copy(stabilityApiKey = key) }
    }

    fun updateHuggingFaceApiKey(key: String) {
        updateConfig { it.copy(huggingfaceApiKey = key) }
    }

    fun updateHuggingFaceModel(model: String) {
        updateConfig { it.copy(huggingfaceModel = model) }
    }

    fun updatePollinationsApiKey(key: String) {
        updateConfig { it.copy(pollinationsApiKey = key) }
    }

    fun updatePollinationsModel(model: String) {
        updateConfig { it.copy(pollinationsModel = model) }
    }

    fun updateNanoGptApiKey(key: String) {
        updateConfig { it.copy(nanoGptApiKey = key) }
    }

    fun updateNanoGptModel(model: String) {
        updateConfig { it.copy(nanoGptModel = model) }
    }

    fun updateSampler(sampler: String) {
        updateConfig { it.copy(sampler = sampler) }
    }

    fun updateSteps(steps: Int) {
        updateConfig { it.copy(steps = steps) }
    }

    fun updateCfgScale(cfg: Float) {
        updateConfig { it.copy(cfgScale = cfg) }
    }

    fun updateSeed(seed: Int) {
        updateConfig { it.copy(seed = seed) }
    }

    fun updateNegativePrompt(prompt: String) {
        updateConfig { it.copy(negativePrompt = prompt) }
    }

    fun updateClipSkip(clipSkip: Int) {
        updateConfig { it.copy(clipSkip = clipSkip) }
    }

    fun updateResolution(width: Int, height: Int) {
        updateConfig { it.copy(width = width, height = height) }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null) }
            when (val result = imageGenRepository.testConnection()) {
                is Result.Success -> {
                    _uiState.update { it.copy(isTesting = false, testResult = "Connection successful!") }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isTesting = false, testResult = result.exception.message ?: "Connection failed") }
                }
            }
        }
    }

    fun fetchSamplers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSamplers = true) }
            when (val result = imageGenRepository.getSamplers()) {
                is Result.Success -> _uiState.update { it.copy(samplers = result.data, isLoadingSamplers = false) }
                is Result.Error -> _uiState.update { it.copy(isLoadingSamplers = false) }
            }
        }
    }

    fun fetchModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true) }
            when (val result = imageGenRepository.getModels()) {
                is Result.Success -> _uiState.update { it.copy(models = result.data, isLoadingModels = false) }
                is Result.Error -> _uiState.update { it.copy(isLoadingModels = false) }
            }
        }
    }

    fun updateSdModel(model: String) {
        updateConfig { it.copy(sdModel = model) }
        viewModelScope.launch {
            imageGenRepository.setModel(model)
        }
    }

    private fun updateConfig(transform: (ImageGenConfig) -> ImageGenConfig) {
        val newConfig = transform(_uiState.value.config)
        _uiState.update { it.copy(config = newConfig) }
        viewModelScope.launch {
            settingsDataStore.saveImageGenConfig(newConfig)
        }
    }
}
