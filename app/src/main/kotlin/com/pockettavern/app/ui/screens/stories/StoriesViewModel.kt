package com.pockettavern.app.ui.screens.stories

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.local.StoryStorage
import com.pockettavern.app.domain.model.StoryCardRef
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class StoriesUiState(
    val stories: List<StoryCardRef> = emptyList(),
    val loading: Boolean = true,
    val importMessage: String? = null
)

/**
 * T8/T9 — the Stories tab: lists imported Story cards (cover art), imports pre-made cards from a
 * PNG (`ptensemble` chunk) or raw JSON (V11: import only). The Stories category is gated on
 * [hasStories]==true (V12) — surfaced by the host nav, not here.
 */
@HiltViewModel
class StoriesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storyStorage: StoryStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoriesUiState())
    val uiState: StateFlow<StoriesUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val refs = storyStorage.loadIndex()
        _uiState.update { it.copy(stories = refs, loading = false) }
    }

    /** Resolve the cover image file for a Story (the imported PNG), or null. */
    fun coverFile(ref: StoryCardRef): File? = storyStorage.coverFileFor(ref.id)

    /** Import a pre-made Story card from a picked file (PNG with `ptensemble`, or raw JSON). */
    fun importUri(uri: Uri) = viewModelScope.launch {
        val ref = withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            if (bytes.size >= 8 && bytes[0] == (-119).toByte() && bytes[1] == 'P'.code.toByte()) {
                storyStorage.importFromPng(bytes)                 // PNG → read ptensemble chunk
            } else {
                storyStorage.importFromJson(String(bytes, Charsets.UTF_8))  // raw JSON
            }
        }
        if (ref == null) {
            _uiState.update { it.copy(importMessage = "Not a valid Story card") }
        } else {
            _uiState.update { it.copy(importMessage = "Imported \"${ref.name}\"") }
            refresh()
        }
    }

    fun deleteStory(id: String) = viewModelScope.launch {
        storyStorage.deleteStory(id); refresh()
    }

    fun clearMessage() = _uiState.update { it.copy(importMessage = null) }

    companion object {
        /** Gate for the Stories category (V12). Call from the nav host to decide visibility. */
        suspend fun hasStories(storyStorage: StoryStorage): Boolean = storyStorage.hasAny()
    }
}
