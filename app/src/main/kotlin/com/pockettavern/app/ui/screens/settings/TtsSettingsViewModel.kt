package com.pockettavern.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.data.local.inference.OnDevicePocketTtsModelManager
import com.pockettavern.app.domain.model.TtsConfig
import com.pockettavern.app.ui.audio.TtsEngineInfo
import com.pockettavern.app.ui.audio.TtsManager
import com.pockettavern.app.ui.audio.TtsVoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TtsSettingsUiState(
    val config: TtsConfig = TtsConfig(),
    val voices: List<TtsVoice> = emptyList(),
    val systemVoices: List<TtsVoice> = emptyList(),
    val availableEngines: List<TtsEngineInfo> = emptyList(),
    val isLoading: Boolean = true,
    val isTesting: Boolean = false,
    val isPocketTtsModelDownloaded: Boolean = false,
    val isDownloadingPocketTts: Boolean = false,
    val pocketTtsDownloadProgress: Float? = null,
    val pocketTtsDownloadStatus: String = ""
)

@HiltViewModel
class TtsSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val ttsManager: TtsManager,
    private val pocketTtsModelManager: OnDevicePocketTtsModelManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TtsSettingsUiState())
    val uiState: StateFlow<TtsSettingsUiState> = _uiState.asStateFlow()

    init {
        checkModelStatus()
        viewModelScope.launch {
            settingsDataStore.ttsConfigFlow.collect { config ->
                _uiState.update { it.copy(config = config, isLoading = false) }
                if (config.provider == "system" && config.systemEngine.isNotEmpty() && _uiState.value.systemVoices.isEmpty()) {
                    val voices = ttsManager.getVoicesForSystemEngine(config.systemEngine)
                    _uiState.update { it.copy(systemVoices = voices) }
                }
            }
        }
        loadVoices()
        loadEngines()
    }

    fun checkModelStatus() {
        _uiState.update {
            it.copy(isPocketTtsModelDownloaded = pocketTtsModelManager.isModelDownloaded())
        }
    }

    fun downloadPocketTtsModel() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloadingPocketTts = true,
                    pocketTtsDownloadProgress = 0.0f,
                    pocketTtsDownloadStatus = "Starting download..."
                )
            }
            pocketTtsModelManager.downloadModel().collect { prog ->
                when (prog) {
                    is OnDevicePocketTtsModelManager.DownloadProgress.Progress -> {
                        val fraction = if (prog.totalBytes > 0) prog.bytesDownloaded.toFloat() / prog.totalBytes else 0f
                        val percent = (fraction * 100).toInt().coerceIn(0, 100)
                        _uiState.update {
                            it.copy(
                                pocketTtsDownloadProgress = fraction,
                                pocketTtsDownloadStatus = "Downloading ${prog.currentFile} (${prog.fileIndex}/${prog.totalFiles}): $percent%"
                            )
                        }
                    }
                    is OnDevicePocketTtsModelManager.DownloadProgress.Done -> {
                        _uiState.update {
                            it.copy(
                                isDownloadingPocketTts = false,
                                isPocketTtsModelDownloaded = true,
                                pocketTtsDownloadProgress = null,
                                pocketTtsDownloadStatus = "Download complete!"
                            )
                        }
                        loadVoices()
                    }
                    is OnDevicePocketTtsModelManager.DownloadProgress.Error -> {
                        _uiState.update {
                            it.copy(
                                isDownloadingPocketTts = false,
                                pocketTtsDownloadProgress = null,
                                pocketTtsDownloadStatus = "Error: ${prog.message}"
                            )
                        }
                    }
                }
            }
        }
    }

    fun deletePocketTtsModel() {
        pocketTtsModelManager.deleteModel()
        checkModelStatus()
        loadVoices()
    }

    private fun loadEngines() {
        val engines = ttsManager.getSystemEngines()
        _uiState.update { it.copy(availableEngines = engines) }
    }

    private fun loadVoices() {
        viewModelScope.launch {
            // Ensure the OpenAI provider has current config before fetching
            val config = settingsDataStore.getTtsConfig()
            val voices = ttsManager.getVoicesForProvider(config.provider)
            _uiState.update { it.copy(voices = voices) }
        }
    }

    fun refreshVoices() {
        loadVoices()
    }

    fun updateEnabled(enabled: Boolean) {
        updateConfig { it.copy(enabled = enabled) }
    }

    fun updateProvider(provider: String) {
        updateConfig { it.copy(provider = provider) }
        // Reload voices for new provider
        viewModelScope.launch {
            val voices = ttsManager.getVoicesForProvider(provider)
            _uiState.update { it.copy(voices = voices) }
        }
    }

    fun updateAutoPlay(autoPlay: Boolean) {
        updateConfig { it.copy(autoPlay = autoPlay) }
    }

    fun updateOpenAiUrl(url: String) {
        updateConfig { it.copy(openAiUrl = url) }
    }

    fun updateOpenAiKey(key: String) {
        updateConfig { it.copy(openAiKey = key) }
    }

    fun updateOpenAiVoice(voice: String) {
        updateConfig { it.copy(openAiVoice = voice) }
    }

    fun updateOpenAiModel(model: String) {
        updateConfig { it.copy(openAiModel = model) }
    }

    fun updateSpeed(speed: Float) {
        updateConfig { it.copy(speed = speed) }
    }

    fun updateFilterMode(mode: String) {
        updateConfig { it.copy(filterMode = mode) }
    }

    fun updateSystemEngine(engine: String) {
        updateConfig {
            it.copy(
                systemEngine = engine,
                systemVoice = ""  // Reset voice when engine changes
            )
        }
        viewModelScope.launch {
            val voices = ttsManager.getVoicesForSystemEngine(
                if (engine.isNotEmpty()) engine else null
            )
            _uiState.update { it.copy(systemVoices = voices) }
        }
    }

    fun updateSystemVoice(voice: String) {
        updateConfig { it.copy(systemVoice = voice) }
    }

    fun updatePocketTtsVoice(voice: String) {
        updateConfig { it.copy(pocketTtsVoice = voice) }
    }

    fun testVoice() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true) }
            ttsManager.speak("Hello! This is a test of the text to speech system.", null)
            _uiState.update { it.copy(isTesting = false) }
        }
    }

    fun stopTest() {
        ttsManager.stop()
        _uiState.update { it.copy(isTesting = false) }
    }

    private fun updateConfig(transform: (TtsConfig) -> TtsConfig) {
        val newConfig = transform(_uiState.value.config)
        _uiState.update { it.copy(config = newConfig) }
        viewModelScope.launch { settingsDataStore.saveTtsConfig(newConfig) }
    }
}
