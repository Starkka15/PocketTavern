package com.pockettavern.app.data.local

import com.pockettavern.app.data.remote.imagegen.WorkflowImportValidator
import com.pockettavern.app.domain.model.CharacterImageWorkflowSettings
import com.pockettavern.app.domain.model.ImageGenBackendType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Legacy Scene Painter kept per-character workflow preferences in
 * js_extensions/_settings.json keyed by display name, without a backend key.
 *
 * Migrate only when that name identifies exactly one card and the request can
 * be safely assigned to the currently selected backend. Never modify old
 * settings or overwrite native character workflow settings.
 */
internal sealed interface LegacyWorkflowMigrationDecision {
    data object None : LegacyWorkflowMigrationDecision
    data object AmbiguousName : LegacyWorkflowMigrationDecision
    data object IncompatibleTemplate : LegacyWorkflowMigrationDecision
    data object MissingCustomTemplate : LegacyWorkflowMigrationDecision
    data class Apply(val mode: String, val template: String?) : LegacyWorkflowMigrationDecision
}

internal object LegacyScenePainterWorkflowMigration {
    private val json = Json { ignoreUnknownKeys = true }

    fun evaluate(
        settingsJson: String,
        characterName: String,
        matchingCharacterCount: Int,
        backend: ImageGenBackendType,
        current: CharacterImageWorkflowSettings,
        hasGlobalCustomTemplate: Boolean
    ): LegacyWorkflowMigrationDecision {
        // Native settings (including explicit "Use Global") take precedence.
        if (backend.name in current.legacyMigrationChecked ||
            backend.name in current.workflowModes ||
            backend.name in current.customWorkflows
        ) return LegacyWorkflowMigrationDecision.None

        val root = try {
            json.parseToJsonElement(settingsJson) as? JsonObject
        } catch (_: Exception) { null } ?: return LegacyWorkflowMigrationDecision.None

        val painter = root["pt-scene-painter"] as? JsonObject
            ?: return LegacyWorkflowMigrationDecision.None
        val modes = painter["workflowByChar"] as? JsonObject
            ?: return LegacyWorkflowMigrationDecision.None
        val mode = (modes[characterName] as? JsonPrimitive)?.contentOrNull
            ?: return LegacyWorkflowMigrationDecision.None
        if (mode != "STANDARD" && mode != "CUSTOM") return LegacyWorkflowMigrationDecision.None

        // Two cards named alike cannot be mapped reliably from legacy name keys.
        if (matchingCharacterCount != 1) return LegacyWorkflowMigrationDecision.AmbiguousName

        if (mode == "STANDARD") return LegacyWorkflowMigrationDecision.Apply("STANDARD", null)

        if (backend == ImageGenBackendType.LOCAL_SD_MNN) {
            return LegacyWorkflowMigrationDecision.IncompatibleTemplate
        }

        val template = ((painter["customWorkflowByChar"] as? JsonObject)
            ?.get(characterName) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

        if (template != null) {
            // Legacy settings did not record which backend the file belonged to.
            if (WorkflowImportValidator.validate(template, backend) != null) {
                return LegacyWorkflowMigrationDecision.IncompatibleTemplate
            }
        } else if (!hasGlobalCustomTemplate) {
            // Old Custom with a blank file inherited a global Custom template.
            // No global template on this backend means migration would be unsafe.
            return LegacyWorkflowMigrationDecision.MissingCustomTemplate
        }
        return LegacyWorkflowMigrationDecision.Apply("CUSTOM", template)
    }
}
