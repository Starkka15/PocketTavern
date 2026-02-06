package com.stark.sillytavern.ui.screens.persona

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.repository.ForgeRepository
import com.stark.sillytavern.data.repository.SettingsRepository
import com.stark.sillytavern.data.repository.SillyTavernRepository
import com.stark.sillytavern.domain.model.ForgeGenerationParams
import com.stark.sillytavern.domain.model.GenerationState
import com.stark.sillytavern.domain.model.Persona
import com.stark.sillytavern.domain.model.PersonaPosition
import com.stark.sillytavern.domain.model.PersonaRole
import com.stark.sillytavern.domain.model.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonaUiState(
    val personas: List<Persona> = emptyList(),
    val selectedPersona: Persona? = null,
    val serverUrl: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val showEditDialog: Boolean = false,
    val editingPersona: Persona? = null,
    val editDescription: String = "",
    val editPosition: PersonaPosition = PersonaPosition.IN_PROMPT,
    val editRole: PersonaRole = PersonaRole.SYSTEM,
    val editDepth: Int = 2,
    val showDeleteConfirm: Boolean = false,
    val showCreateDialog: Boolean = false,
    val createImageBytes: ByteArray? = null,
    val createImageMimeType: String = "image/png",
    val createName: String = "",
    val createDescription: String = "",
    val forgeAvailable: Boolean = false,
    val generationPrompt: String = "",
    val isGenerating: Boolean = false,
    val generationProgress: Float = 0f,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class PersonaViewModel @Inject constructor(
    private val repository: SillyTavernRepository,
    private val settingsRepository: SettingsRepository,
    private val forgeRepository: ForgeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonaUiState())
    val uiState: StateFlow<PersonaUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            _uiState.update {
                it.copy(
                    serverUrl = settings.normalizedServerUrl,
                    forgeAvailable = settings.forgeUrl.isNotBlank()
                )
            }
        }
        loadPersonas()
    }

    fun loadPersonas() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = repository.getPersonas()) {
                is Result.Success -> {
                    val selected = result.data.find { it.isSelected }
                    _uiState.update {
                        it.copy(
                            personas = result.data,
                            selectedPersona = selected,
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

    fun selectPersona(persona: Persona) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = repository.selectPersona(persona.avatarId)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            selectedPersona = persona,
                            personas = it.personas.map { p ->
                                p.copy(isSelected = p.avatarId == persona.avatarId)
                            },
                            successMessage = "Switched to ${persona.name}"
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

    fun showEditDialog(persona: Persona) {
        _uiState.update {
            it.copy(
                showEditDialog = true,
                editingPersona = persona,
                editDescription = persona.description,
                editPosition = persona.position,
                editRole = persona.role,
                editDepth = persona.depth
            )
        }
    }

    fun hideEditDialog() {
        _uiState.update {
            it.copy(
                showEditDialog = false,
                editingPersona = null
            )
        }
    }

    fun updateEditDescription(description: String) {
        _uiState.update { it.copy(editDescription = description) }
    }

    fun updateEditPosition(position: PersonaPosition) {
        _uiState.update { it.copy(editPosition = position) }
    }

    fun updateEditRole(role: PersonaRole) {
        _uiState.update { it.copy(editRole = role) }
    }

    fun updateEditDepth(depth: Int) {
        _uiState.update { it.copy(editDepth = depth.coerceIn(0, 100)) }
    }

    fun savePersonaEdit() {
        val state = _uiState.value
        val persona = state.editingPersona ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = repository.updatePersona(
                avatarId = persona.avatarId,
                description = state.editDescription,
                position = state.editPosition,
                role = state.editRole,
                depth = state.editDepth
            )) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            showEditDialog = false,
                            successMessage = "Persona updated"
                        )
                    }
                    loadPersonas()
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

    fun showDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun hideDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun deletePersona() {
        val persona = _uiState.value.editingPersona ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, showDeleteConfirm = false) }
            when (val result = repository.deletePersona(persona.avatarId)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            showEditDialog = false,
                            successMessage = "Persona deleted"
                        )
                    }
                    loadPersonas()
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

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun showCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                createImageBytes = null,
                createImageMimeType = "image/png",
                createName = "",
                createDescription = "",
                generationPrompt = "",
                isGenerating = false,
                generationProgress = 0f
            )
        }
    }

    fun hideCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = false,
                createImageBytes = null
            )
        }
    }

    fun setCreateImage(bytes: ByteArray, mimeType: String) {
        _uiState.update {
            it.copy(
                createImageBytes = bytes,
                createImageMimeType = mimeType
            )
        }
    }

    fun updateCreateName(name: String) {
        _uiState.update { it.copy(createName = name) }
    }

    fun updateCreateDescription(description: String) {
        _uiState.update { it.copy(createDescription = description) }
    }

    fun createPersona() {
        val state = _uiState.value
        val imageBytes = state.createImageBytes
        if (imageBytes == null) {
            _uiState.update { it.copy(error = "Please select an image") }
            return
        }
        if (state.createName.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a name") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            // Use the name as the filename with appropriate extension
            val extension = when {
                state.createImageMimeType.contains("png") -> "png"
                state.createImageMimeType.contains("gif") -> "gif"
                state.createImageMimeType.contains("webp") -> "webp"
                else -> "jpg"
            }
            val fileName = "${state.createName}.$extension"

            when (val result = repository.createPersona(
                imageBytes = imageBytes,
                fileName = fileName,
                mimeType = state.createImageMimeType,
                description = state.createDescription
            )) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            showCreateDialog = false,
                            createImageBytes = null,
                            successMessage = "Persona created"
                        )
                    }
                    loadPersonas()
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

    fun updateGenerationPrompt(prompt: String) {
        _uiState.update { it.copy(generationPrompt = prompt) }
    }

    fun generateImage() {
        val prompt = _uiState.value.generationPrompt
        if (prompt.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a prompt") }
            return
        }

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, generationProgress = 0f) }

            val params = ForgeGenerationParams(
                prompt = prompt,
                negativePrompt = "blurry, low quality, distorted, deformed, bad anatomy, ugly, disfigured",
                width = 512,
                height = 512,  // Square for avatar
                steps = 20,
                cfgScale = 7f
            )

            forgeRepository.generateImageWithProgress(params).collect { state ->
                when (state) {
                    is GenerationState.Starting -> {
                        _uiState.update { it.copy(generationProgress = 0f) }
                    }
                    is GenerationState.InProgress -> {
                        _uiState.update { it.copy(generationProgress = state.progress) }
                    }
                    is GenerationState.Complete -> {
                        // Decode base64 to bytes
                        val imageBytes = Base64.decode(state.imageBase64, Base64.DEFAULT)
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                createImageBytes = imageBytes,
                                createImageMimeType = "image/png"
                            )
                        }
                    }
                    is GenerationState.Error -> {
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                error = state.message
                            )
                        }
                    }
                    GenerationState.Idle -> {}
                }
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        viewModelScope.launch {
            forgeRepository.interrupt()
            _uiState.update { it.copy(isGenerating = false, generationProgress = 0f) }
        }
    }
}
