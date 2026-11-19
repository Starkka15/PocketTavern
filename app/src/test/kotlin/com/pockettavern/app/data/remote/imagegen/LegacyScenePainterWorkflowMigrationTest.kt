package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.data.local.LegacyScenePainterWorkflowMigration
import com.pockettavern.app.data.local.LegacyWorkflowMigrationDecision
import com.pockettavern.app.domain.model.CharacterImageWorkflowPresentation
import com.pockettavern.app.domain.model.CharacterImageWorkflowSettings
import com.pockettavern.app.domain.model.CustomImageWorkflowDefinition
import com.pockettavern.app.domain.model.ImageGenBackendType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Offline migration validation. The original name-keyed extension settings
 * are simulated without modifying any native character workflow files.
 */
@RunWith(Parameterized::class)
class LegacyScenePainterWorkflowMigrationTest(private val backend: ImageGenBackendType) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} legacy Scene Painter")
        fun cases(): Collection<Array<Any>> = ImageGenBackendType.entries
            .filter { it != ImageGenBackendType.LOCAL_SD_MNN }
            .map { arrayOf<Any>(it) }
    }

    private fun legacy(mode: String, template: String? = null): String = buildJsonObject {
        put("pt-scene-painter", buildJsonObject {
            put("workflowByChar", buildJsonObject { put("Alexandra", JsonPrimitive(mode)) })
            put("customWorkflowByChar", buildJsonObject {
                if (template != null) put("Alexandra", JsonPrimitive(template))
            })
        })
    }.toString()

    private fun migrate(
        settingsJson: String,
        count: Int = 1,
        current: CharacterImageWorkflowSettings = CharacterImageWorkflowSettings(),
        hasGlobal: Boolean = true
    ) = LegacyScenePainterWorkflowMigration.evaluate(
        settingsJson, "Alexandra", count, backend, current, hasGlobal
    )

    @Test fun matchingCustomTemplateCanBeMigratedForEachBackend() {
        val template = integrationTemplate(backend)
        val decision = migrate(legacy("CUSTOM", template))
        assertTrue("Valid template not migrated for $backend: $decision",
            decision is LegacyWorkflowMigrationDecision.Apply)
        val applied = decision as LegacyWorkflowMigrationDecision.Apply
        assertEquals("CUSTOM", applied.mode)
        assertEquals(template, applied.template)
    }

    @Test fun duplicateCharacterNamesAreNeverAutomaticallyMapped() {
        assertEquals(LegacyWorkflowMigrationDecision.AmbiguousName,
            migrate(legacy("CUSTOM", integrationTemplate(backend)), count = 2))
    }

    @Test fun existingNativeCharacterModeIsNeverOverwritten() {
        val alreadyCustom = CharacterImageWorkflowSettings(
            workflowModes = mapOf(backend.name to "STANDARD")
        )
        assertEquals(LegacyWorkflowMigrationDecision.None,
            migrate(legacy("CUSTOM", integrationTemplate(backend)), current = alreadyCustom))
    }

    @Test fun explicitNativeGlobalChoiceRemainsGlobal() {
        val explicitlyGlobal = CharacterImageWorkflowSettings(
            legacyMigrationChecked = setOf(backend.name)
        )
        assertEquals(LegacyWorkflowMigrationDecision.None,
            migrate(legacy("CUSTOM", integrationTemplate(backend)), current = explicitlyGlobal))
    }

    @Test fun nativeCustomTemplateIsNeverReplaced() {
        val current = CharacterImageWorkflowSettings(
            customWorkflows = mapOf(backend.name to
                CustomImageWorkflowDefinition("existing", integrationTemplate(backend)))
        )
        assertEquals(LegacyWorkflowMigrationDecision.None,
            migrate(legacy("CUSTOM", integrationTemplate(backend)), current = current))
    }

    @Test fun customWithoutOwnTemplateUsesConfiguredGlobalTemplate() {
        val decision = migrate(legacy("CUSTOM"), hasGlobal = true)
        assertEquals(LegacyWorkflowMigrationDecision.Apply("CUSTOM", null), decision)
    }

    @Test fun customWithoutAnyTemplateIsNotMigratedSilently() {
        assertEquals(LegacyWorkflowMigrationDecision.MissingCustomTemplate,
            migrate(legacy("CUSTOM"), hasGlobal = false))
    }

    @Test fun wrongBackendTemplateIsLeftUntouched() {
        val wrong = if (backend == ImageGenBackendType.COMFYUI)
            integrationTemplate(ImageGenBackendType.SD_WEBUI)
        else integrationTemplate(ImageGenBackendType.COMFYUI)
        assertEquals(LegacyWorkflowMigrationDecision.IncompatibleTemplate,
            migrate(legacy("CUSTOM", wrong)))
    }

    @Test fun standardModeMigratesWithoutCustomTemplate() {
        assertEquals(LegacyWorkflowMigrationDecision.Apply("STANDARD", null),
            migrate(legacy("STANDARD")))
    }
}

class LegacyScenePainterMigrationEdgeTest {
    private val backend = ImageGenBackendType.COMFYUI

    private fun evaluate(raw: String, characterName: String = "Alexandra") =
        LegacyScenePainterWorkflowMigration.evaluate(
            raw, characterName, 1, backend,
            CharacterImageWorkflowSettings(), false
        )

    @Test fun invalidLegacyJsonIsNotMigrated() {
        assertEquals(LegacyWorkflowMigrationDecision.None, evaluate("not-json"))
    }

    @Test fun otherExtensionsSettingsAreUntouched() {
        assertEquals(LegacyWorkflowMigrationDecision.None, evaluate("""{"other-extension":{"x":1}}"""))
    }

    @Test fun differentCharacterIsNotMigrated() {
        assertEquals(LegacyWorkflowMigrationDecision.None, evaluate(
            """{"pt-scene-painter":{"workflowByChar":{"Alice":"STANDARD"}}}"""
        ))
    }

    @Test fun oldGlobalModeDoesNotCreateAnOverride() {
        assertEquals(LegacyWorkflowMigrationDecision.None, evaluate(
            """{"pt-scene-painter":{"workflowByChar":{"Alexandra":"GLOBAL"}}}"""
        ))
    }

    @Test fun unsupportedLocalBackendDoesNotImportCustomTemplate() {
        val result = LegacyScenePainterWorkflowMigration.evaluate(
            """{"pt-scene-painter":{"workflowByChar":{"Alexandra":"CUSTOM"},"customWorkflowByChar":{"Alexandra":"{}"}}}""",
            "Alexandra", 1, ImageGenBackendType.LOCAL_SD_MNN,
            CharacterImageWorkflowSettings(), true
        )
        assertEquals(LegacyWorkflowMigrationDecision.IncompatibleTemplate, result)
    }
}

@RunWith(Parameterized::class)
class CharacterImageWorkflowPresentationTest(private val backend: ImageGenBackendType) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} displayed workflow")
        fun cases(): Collection<Array<Any>> = ImageGenBackendType.entries
            .map { arrayOf<Any>(it) }
    }

    @Test fun globalStandardDisplaysEffectiveBuiltIn() {
        val shown = CharacterImageWorkflowPresentation.describe(
            backend, "GLOBAL", "STANDARD", "unused-character.json", "unused-global.json"
        )
        assertFalse(shown.hasMissingTemplate)
        assertTrue(shown.summary.contains("Standard"))
        assertFalse(shown.summary.contains("unused"))
    }

    @Test fun characterCustomTakesPrecedenceOverGlobalTemplate() {
        val shown = CharacterImageWorkflowPresentation.describe(
            backend, "CUSTOM", "CUSTOM", "char.json", "global.json"
        )
        assertEquals("Custom — character ${backend.templateLabel}: char.json", shown.summary)
        assertFalse(shown.hasMissingTemplate)
    }

    @Test fun customWithoutCharacterFileUsesGlobalFile() {
        val shown = CharacterImageWorkflowPresentation.describe(
            backend, "CUSTOM", "STANDARD", null, "global.json"
        )
        assertEquals("Custom — global ${backend.templateLabel}: global.json", shown.summary)
    }

    @Test fun globalCustomWithNoTemplateReportsMissingFile() {
        val shown = CharacterImageWorkflowPresentation.describe(
            backend, "GLOBAL", "CUSTOM", null, null
        )
        assertTrue(shown.hasMissingTemplate)
        assertTrue(shown.summary.contains("no"))
    }

    @Test fun standardHidesUnusedCharacterFile() {
        val shown = CharacterImageWorkflowPresentation.describe(
            backend, "STANDARD", "CUSTOM", "hidden.json", "global.json"
        )
        assertFalse(shown.hasMissingTemplate)
        assertFalse(shown.summary.contains("hidden"))
    }
}
