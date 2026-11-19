package com.pockettavern.app.data.local

import android.content.Context
import com.pockettavern.app.domain.model.CharacterImageWorkflowFile
import com.pockettavern.app.domain.model.CharacterImageWorkflowSettings
import com.pockettavern.app.domain.model.CustomImageWorkflowDefinition
import com.pockettavern.app.domain.model.ImageGenBackendType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores overrides by stable character PNG filename (not display name) and backend.
 * The extensions directory is already included by PocketTavern's ZIP backup.
 */
@Singleton
class CharacterImageWorkflowStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val legacySettingsStorage: JsExtensionStorage
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val settingsFile: File
        get() = File(context.filesDir, "extensions/imagegen_character_workflows.json")

    @Synchronized
    fun get(characterFile: String): CharacterImageWorkflowSettings =
        readAll().characters[characterFile] ?: CharacterImageWorkflowSettings()

    @Synchronized
    fun setMode(characterFile: String, backend: ImageGenBackendType, mode: String) {
        require(characterFile.isNotBlank()) { "Character identity is required" }
        require(mode in setOf("GLOBAL", "STANDARD", "CUSTOM")) { "Invalid workflow mode: $mode" }
        edit(characterFile) { current ->
            current.copy(workflowModes = if (mode == "GLOBAL") {
                current.workflowModes - backend.name
            } else {
                current.workflowModes + (backend.name to mode)
            }, legacyMigrationChecked = current.legacyMigrationChecked + backend.name)
        }
    }

    @Synchronized
    fun setWorkflow(
        characterFile: String,
        backend: ImageGenBackendType,
        name: String,
        template: String
    ) {
        require(characterFile.isNotBlank()) { "Character identity is required" }
        require(template.isNotBlank()) { "Empty image workflow" }
        edit(characterFile) { current ->
            current.copy(customWorkflows = current.customWorkflows +
                (backend.name to CustomImageWorkflowDefinition(name, template)),
                legacyMigrationChecked = current.legacyMigrationChecked + backend.name)
        }
    }

    @Synchronized
    fun clearWorkflow(characterFile: String, backend: ImageGenBackendType) {
        require(characterFile.isNotBlank()) { "Character identity is required" }
        edit(characterFile) { current ->
            current.copy(
                customWorkflows = current.customWorkflows - backend.name,
                legacyMigrationChecked = current.legacyMigrationChecked + backend.name
            )
        }
    }


    /**
     * Non-destructive, per-backend migration from the old Scene Painter settings.
     * Never assigns name-keyed data when multiple characters have that same name.
     * Returns a short UI notice, or null when nothing needs attention.
     */
    @Synchronized
    fun migrateLegacyScenePainter(
        characterFile: String,
        displayName: String,
        backend: ImageGenBackendType,
        matchingCharacterCount: Int,
        hasGlobalCustomTemplate: Boolean
    ): String? {
        val current = readAll().characters[characterFile] ?: CharacterImageWorkflowSettings()
        val decision = LegacyScenePainterWorkflowMigration.evaluate(
            settingsJson = legacySettingsStorage.loadAllSettings(),
            characterName = displayName,
            matchingCharacterCount = matchingCharacterCount,
            backend = backend,
            current = current,
            hasGlobalCustomTemplate = hasGlobalCustomTemplate
        )
        return when (decision) {
            LegacyWorkflowMigrationDecision.None -> null
            LegacyWorkflowMigrationDecision.AmbiguousName ->
                "Older Scene Painter workflow settings could not be assigned automatically: " +
                    "multiple characters share this name. Your existing settings were not changed."
            LegacyWorkflowMigrationDecision.IncompatibleTemplate ->
                "Older Scene Painter workflow settings were left unchanged: " +
                    "the saved template does not match ${backend.displayName}."
            LegacyWorkflowMigrationDecision.MissingCustomTemplate ->
                "Older Scene Painter Custom settings were left unchanged: " +
                    "no matching character template or global Custom template exists for ${backend.displayName}."
            is LegacyWorkflowMigrationDecision.Apply -> {
                edit(characterFile) { old ->
                    old.copy(
                        workflowModes = old.workflowModes + (backend.name to decision.mode),
                        customWorkflows = if (decision.template == null) old.customWorkflows
                            else old.customWorkflows + (backend.name to CustomImageWorkflowDefinition(
                                "Migrated Scene Painter ${backend.templateLabel}", decision.template
                            )),
                        legacyMigrationChecked = old.legacyMigrationChecked + backend.name
                    )
                }
                "Imported older Scene Painter ${decision.mode.lowercase().replaceFirstChar { it.uppercase() }} " +
                    "settings for ${backend.displayName}."
            }
        }
    }

    private fun edit(
        characterFile: String,
        transform: (CharacterImageWorkflowSettings) -> CharacterImageWorkflowSettings
    ) {
        val previous = readAll().characters
        val updated = transform(previous[characterFile] ?: CharacterImageWorkflowSettings())
        val characters = previous.toMutableMap()
        if (updated.workflowModes.isEmpty() && updated.customWorkflows.isEmpty() &&
            updated.legacyMigrationChecked.isEmpty()
        ) {
            characters.remove(characterFile)
        } else {
            characters[characterFile] = updated
        }
        val target = settingsFile
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        try {
            temp.writeText(json.encodeToString(
                CharacterImageWorkflowFile.serializer(),
                CharacterImageWorkflowFile(characters)
            ))
            if (!temp.renameTo(target)) throw IllegalStateException("Could not save character workflows")
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun readAll(): CharacterImageWorkflowFile {
        val file = settingsFile
        if (!file.exists()) return CharacterImageWorkflowFile()
        return try {
            json.decodeFromString(CharacterImageWorkflowFile.serializer(), file.readText())
        } catch (e: Exception) {
            throw IllegalStateException("Cannot read character image workflow settings", e)
        }
    }
}
