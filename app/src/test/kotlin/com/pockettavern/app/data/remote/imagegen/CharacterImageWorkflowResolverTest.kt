package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.domain.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** 7 network backends x 5 distinct character-isolation checks = 35 JUnit tests. */
@RunWith(Parameterized::class)
class CharacterImageWorkflowResolverTest(private val backend: ImageGenBackendType) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} character override")
        fun cases(): Collection<Array<Any>> = ImageGenBackendType.entries
            .filter { it != ImageGenBackendType.LOCAL_SD_MNN }
            .map { arrayOf<Any>(it) }
    }

    private val request = ForgeGenerationParams(prompt = "portrait")
    private val config = ImageGenConfig(
        activeBackend = backend.name,
        workflowModes = mapOf(backend.name to "CUSTOM"),
        customWorkflows = mapOf(backend.name to
            CustomImageWorkflowDefinition("global", integrationTemplate(backend)))
    )

    @Test fun globalInheritsSavedBackendMode() {
        val effective = CharacterImageWorkflowResolver.resolve(config, request, CharacterImageWorkflowSettings())
        assertNull(effective.workflowModeOverride)
        assertEquals(integrationTemplate(backend), CustomImageTemplate.checkedSelected(config, effective, backend))
    }

    @Test fun standardBeatsGlobalCustomAndRequestOverride() {
        val prefs = CharacterImageWorkflowSettings(workflowModes = mapOf(backend.name to "STANDARD"))
        val effective = CharacterImageWorkflowResolver.resolve(config,
            request.copy(workflowModeOverride = "CUSTOM", customWorkflowOverride = "old extension"),
            prefs)
        assertEquals("STANDARD", effective.workflowModeOverride)
        assertNull(effective.customWorkflowOverride)
        assertNull(CustomImageTemplate.checkedSelected(config, effective, backend))
    }

    @Test fun characterCustomWinsOverGlobalAndExtension() {
        val own = integrationTemplate(backend).replace("test-custom", "character-custom")
        val prefs = CharacterImageWorkflowSettings(
            workflowModes = mapOf(backend.name to "CUSTOM"),
            customWorkflows = mapOf(backend.name to CustomImageWorkflowDefinition("own", own)))
        val effective = CharacterImageWorkflowResolver.resolve(config,
            request.copy(customWorkflowOverride = "old extension"), prefs)
        assertEquals("CUSTOM", effective.workflowModeOverride)
        assertEquals(own, CustomImageTemplate.checkedSelected(config, effective, backend))
    }

    @Test fun characterCustomWithoutOwnFileUsesGlobalCustomFile() {
        val prefs = CharacterImageWorkflowSettings(workflowModes = mapOf(backend.name to "CUSTOM"))
        val effective = CharacterImageWorkflowResolver.resolve(config, request, prefs)
        assertNull(effective.customWorkflowOverride)
        assertEquals(integrationTemplate(backend), CustomImageTemplate.checkedSelected(config, effective, backend))
    }

    @Test fun anotherBackendOverrideCannotAffectActiveBackend() {
        val different = ImageGenBackendType.entries.first { it != backend }
        val prefs = CharacterImageWorkflowSettings(
            workflowModes = mapOf(different.name to "STANDARD"),
            customWorkflows = mapOf(different.name to CustomImageWorkflowDefinition("elsewhere", "{}")))
        val effective = CharacterImageWorkflowResolver.resolve(config, request, prefs)
        assertNull(effective.workflowModeOverride)
        assertEquals(integrationTemplate(backend), CustomImageTemplate.checkedSelected(config, effective, backend))
    }
}

class CharacterImageWorkflowPersistenceTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun twoCharactersWithSameDisplayNameStayIndependentByFileName() {
        val a = CharacterImageWorkflowSettings(workflowModes = mapOf("COMFYUI" to "STANDARD"))
        val b = CharacterImageWorkflowSettings(workflowModes = mapOf("COMFYUI" to "CUSTOM"))
        val file = CharacterImageWorkflowFile(mapOf("Alice.png" to a, "Alice_2.png" to b))
        val restored = json.decodeFromString(CharacterImageWorkflowFile.serializer(),
            json.encodeToString(CharacterImageWorkflowFile.serializer(), file))
        assertEquals("STANDARD", restored.characters.getValue("Alice.png").modeFor(ImageGenBackendType.COMFYUI))
        assertEquals("CUSTOM", restored.characters.getValue("Alice_2.png").modeFor(ImageGenBackendType.COMFYUI))
    }

    @Test fun customJsonAndModesSurviveBackupSerialization() {
        val graph = integrationTemplate(ImageGenBackendType.COMFYUI)
        val prefs = CharacterImageWorkflowSettings(
            workflowModes = mapOf("COMFYUI" to "CUSTOM", "DALLE" to "STANDARD"),
            customWorkflows = mapOf("COMFYUI" to CustomImageWorkflowDefinition("work.json", graph)))
        val file = CharacterImageWorkflowFile(mapOf("card.png" to prefs))
        val decoded = json.decodeFromString(CharacterImageWorkflowFile.serializer(),
            json.encodeToString(CharacterImageWorkflowFile.serializer(), file))
        assertEquals(file, decoded)
    }

    @Test fun noSavedPreferenceDoesNotOverrideExistingGenerationParams() {
        val request = ForgeGenerationParams(prompt = "hello", workflowModeOverride = "CUSTOM",
            customWorkflowOverride = "{}")
        val effective = CharacterImageWorkflowResolver.resolve(ImageGenConfig(), request, null)
        assertEquals(request, effective)
    }

    @Test fun explicitCustomWithoutAnyTemplateFailsClearly() {
        val prefs = CharacterImageWorkflowSettings(workflowModes = mapOf("COMFYUI" to "CUSTOM"))
        val config = ImageGenConfig(activeBackend = "COMFYUI")
        val effective = CharacterImageWorkflowResolver.resolve(config,
            ForgeGenerationParams(prompt = "cat"), prefs)
        try {
            CustomImageTemplate.checkedSelected(config, effective, ImageGenBackendType.COMFYUI)
            fail("Missing template must never silently fall back to Standard")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("no template is configured"))
        }
    }

    @Test fun unknownCharacterModeIsRejected() {
        val prefs = CharacterImageWorkflowSettings(workflowModes = mapOf("COMFYUI" to "UNKNOWN"))
        try {
            CharacterImageWorkflowResolver.resolve(
                ImageGenConfig(activeBackend = "COMFYUI"),
                ForgeGenerationParams(prompt = "cat"), prefs)
            fail("Invalid mode must not be ignored")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("Unknown character image workflow mode"))
        }
    }
}
