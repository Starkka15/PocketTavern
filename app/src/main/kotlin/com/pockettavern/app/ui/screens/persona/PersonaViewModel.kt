package com.pockettavern.app.ui.screens.persona

import android.content.Context
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.repository.ForgeRepository
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.Persona
import com.pockettavern.app.domain.model.PersonaPosition
import com.pockettavern.app.domain.model.PersonaRole
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.domain.model.UserPersona
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
    @ApplicationContext private val context: Context,
    private val localRepository: LocalRepository,
    private val settingsRepository: SettingsRepository,
    private val forgeRepository: ForgeRepository,
    private val imageGenRepository: com.pockettavern.app.data.repository.ImageGenRepository,
    private val settingsDataStore: com.pockettavern.app.data.local.SettingsDataStore,
    private val personaStorage: com.pockettavern.app.data.local.PersonaStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonaUiState())
    val uiState: StateFlow<PersonaUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null
    private var roster: List<com.pockettavern.app.data.local.StoredPersona> = emptyList()
    private var selectedId: String? = null

    init {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            _uiState.update {
                it.copy(forgeAvailable = settings.imageGenBackendConfigured)
            }
        }
        loadPersonas()
    }

    fun loadPersonas() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val (loaded, loadedSelected) = personaStorage.load()
            if (loaded.isEmpty()) {
                // Migration: seed the roster from the old single-slot DataStore persona
                val current = (localRepository.getUserPersona() as? Result.Success)?.data
                val seed = com.pockettavern.app.data.local.StoredPersona(
                    id = java.util.UUID.randomUUID().toString(),
                    name = current?.name?.ifBlank { "User" } ?: "User",
                    description = current?.description ?: "",
                    position = current?.position ?: 0,
                    depth = current?.depth ?: 2,
                    role = current?.role ?: 0,
                    avatarPath = current?.avatarPath
                )
                roster = listOf(seed)
                selectedId = seed.id
                personaStorage.save(roster, selectedId)
            } else {
                roster = loaded
                selectedId = loadedSelected ?: loaded.first().id
            }
            publishRoster()
        }
    }

    private fun publishRoster() {
        val personas = roster.map { it.toPersona(it.id == selectedId) }
        _uiState.update {
            it.copy(
                personas = personas,
                selectedPersona = personas.firstOrNull { p -> p.isSelected },
                isLoading = false
            )
        }
    }

    /** Mirror the selected roster entry into the DataStore slot the prompt path reads. */
    private suspend fun activate(p: com.pockettavern.app.data.local.StoredPersona) {
        val noSpeak = (localRepository.getUserPersona() as? Result.Success)?.data?.noSpeakForUser ?: false
        localRepository.saveUserPersona(
            UserPersona(
                name = p.name,
                description = p.description,
                position = p.position,
                depth = p.depth,
                role = p.role,
                avatarPath = p.avatarPath,
                noSpeakForUser = noSpeak
            )
        )
    }

    fun selectPersona(persona: Persona) {
        val target = roster.firstOrNull { it.id == persona.avatarId } ?: return
        viewModelScope.launch {
            selectedId = target.id
            personaStorage.save(roster, selectedId)
            activate(target)
            publishRoster()
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
            try {
                val idx = roster.indexOfFirst { it.id == persona.avatarId }
                if (idx < 0) throw Exception("Persona not found")
                val updated = roster[idx].copy(
                    description = state.editDescription,
                    position = state.editPosition.value,
                    depth = state.editDepth,
                    role = state.editRole.value
                )
                roster = roster.toMutableList().also { it[idx] = updated }
                personaStorage.save(roster, selectedId)
                if (updated.id == selectedId) activate(updated)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        showEditDialog = false,
                        successMessage = "Persona updated"
                    )
                }
                loadPersonas()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message
                    )
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
        val target = _uiState.value.editingPersona ?: return
        if (roster.size <= 1) {
            _uiState.update {
                it.copy(
                    showDeleteConfirm = false,
                    showEditDialog = false,
                    error = "Cannot delete the last persona"
                )
            }
            return
        }
        viewModelScope.launch {
            val removed = roster.firstOrNull { it.id == target.avatarId } ?: return@launch
            roster = roster.filterNot { it.id == removed.id }
            removed.avatarPath?.let { path -> java.io.File(path).takeIf { f -> f.exists() }?.delete() }
            if (selectedId == removed.id) {
                selectedId = roster.first().id
                activate(roster.first())
            }
            personaStorage.save(roster, selectedId)
            _uiState.update {
                it.copy(
                    showDeleteConfirm = false,
                    showEditDialog = false,
                    successMessage = "Persona deleted"
                )
            }
            publishRoster()
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
        // Bake EXIF rotation into the pixels — camera photos otherwise save sideways
        val upright = com.pockettavern.app.util.ImageOrientation.normalize(bytes)
        _uiState.update {
            it.copy(
                createImageBytes = upright,
                createImageMimeType = mimeType
            )
        }
    }

    fun rotateCreateImage() {
        val bytes = _uiState.value.createImageBytes ?: return
        _uiState.update {
            it.copy(
                createImageBytes = com.pockettavern.app.util.ImageOrientation.rotate(bytes, 90f),
                createImageMimeType = "image/png"
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
        if (state.createName.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a name") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val id = java.util.UUID.randomUUID().toString()
                val avatarPath = state.createImageBytes?.let { bytes ->
                    val file = personaStorage.avatarFile(id)
                    file.writeBytes(bytes)
                    file.absolutePath
                }
                val created = com.pockettavern.app.data.local.StoredPersona(
                    id = id,
                    name = state.createName.trim(),
                    description = state.createDescription,
                    avatarPath = avatarPath
                )
                roster = roster + created
                selectedId = id
                personaStorage.save(roster, selectedId)
                activate(created)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        showCreateDialog = false,
                        createImageBytes = null,
                        successMessage = "Persona \"${created.name}\" created"
                    )
                }
                publishRoster()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
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

            // Use the configured image backend + stored generation settings — this
            // previously hardcoded Forge and 512x512/20 steps regardless of config
            val cfg = settingsDataStore.getImageGenConfig()
            val params = ForgeGenerationParams(
                prompt = prompt,
                negativePrompt = cfg.negativePrompt,
                width = cfg.width,
                height = cfg.height,
                steps = cfg.steps,
                cfgScale = cfg.cfgScale,
                sampler = cfg.sampler,
                seed = cfg.seed,
                clipSkip = cfg.clipSkip
            )

            imageGenRepository.generateImageWithProgress(params).collect { state ->
                when (state) {
                    is GenerationState.Starting -> {
                        _uiState.update { it.copy(generationProgress = 0f) }
                    }
                    is GenerationState.InProgress -> {
                        _uiState.update { it.copy(generationProgress = state.progress) }
                    }
                    is GenerationState.Complete -> {
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

    // Convert a roster record to the UI model. The record id rides in avatarId —
    // it is the stable key the select/edit/delete operations use.
    private fun com.pockettavern.app.data.local.StoredPersona.toPersona(selected: Boolean): Persona = Persona(
        avatarId = id,
        name = name,
        description = description,
        position = PersonaPosition.fromInt(position),
        role = PersonaRole.fromInt(role),
        depth = depth,
        isSelected = selected,
        avatarPath = avatarPath
    )
}
