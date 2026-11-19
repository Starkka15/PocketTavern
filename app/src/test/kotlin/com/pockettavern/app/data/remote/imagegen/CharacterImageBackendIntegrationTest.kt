package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.domain.model.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Uses production backend generate() with an intercepted in-memory HTTP transport. */
@RunWith(Parameterized::class)
class CharacterImageBackendIntegrationTest(private val backend: ImageGenBackendType) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} character HTTP integration")
        fun cases(): Collection<Array<Any>> = ImageGenBackendType.entries
            .filter { it != ImageGenBackendType.LOCAL_SD_MNN }
            .map { arrayOf<Any>(it) }
    }

    private val config get() = integrationConfig(backend).copy(activeBackend = backend.name)
    private val request = ForgeGenerationParams(prompt = "a dragon", seed = 41)

    @Test fun characterCustomWorkflowReachesProviderRequest() = runBlocking {
        val ownTemplate = integrationTemplate(backend).replace("test-custom", "character-owned")
        val settings = CharacterImageWorkflowSettings(
            workflowModes = mapOf(backend.name to "CUSTOM"),
            customWorkflows = mapOf(backend.name to CustomImageWorkflowDefinition("own", ownTemplate))
        )
        val params = CharacterImageWorkflowResolver.resolve(config, request, settings)
        val server = FakeImageHttp(::simulatedImageSuccess)
        val result = simulatedBackend(backend, server.client, config).generate(params).toList()
        assertTrue("Generation failed on $backend: $result", result.last() is GenerationState.Complete)
        val sent = if (backend == ImageGenBackendType.POLLINATIONS)
            server.requests.first().url.toString() else server.requests.first().jsonBodyText()
        assertTrue("Own template not sent by $backend: $sent", sent.contains("character-owned"))
        assertFalse("Global template unexpectedly used by $backend: $sent", sent.contains("test-custom"))
    }

    @Test fun characterCustomWithoutTemplateUsesGlobalTemplate() = runBlocking {
        val settings = CharacterImageWorkflowSettings(
            workflowModes = mapOf(backend.name to "CUSTOM")
        )
        val params = CharacterImageWorkflowResolver.resolve(config, request, settings)
        val server = FakeImageHttp(::simulatedImageSuccess)
        val result = simulatedBackend(backend, server.client, config).generate(params).toList()
        assertTrue("Generation failed on $backend: $result", result.last() is GenerationState.Complete)
        val sent = if (backend == ImageGenBackendType.POLLINATIONS)
            server.requests.first().url.toString() else server.requests.first().jsonBodyText()
        assertTrue("Global custom template not sent by $backend: $sent", sent.contains("test-custom"))
    }
}

/**
 * Standard mode dispatches to the real built-in implementations.
 * Forge's built-in mode uses a separate Retrofit service, so that path has
 * existing independent tests instead of the in-memory OkHttp transport here.
 */
@RunWith(Parameterized::class)
class CharacterImageStandardHttpIntegrationTest(private val backend: ImageGenBackendType) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} character standard HTTP")
        fun cases(): Collection<Array<Any>> = ImageGenBackendType.entries
            .filter { it != ImageGenBackendType.LOCAL_SD_MNN && it != ImageGenBackendType.SD_WEBUI }
            .map { arrayOf<Any>(it) }
    }

    @Test fun characterStandardOverridesGlobalCustomInRealBackendRequest() = runBlocking {
        val config = integrationConfig(backend).copy(activeBackend = backend.name)
        val settings = CharacterImageWorkflowSettings(
            workflowModes = mapOf(backend.name to "STANDARD")
        )
        val params = CharacterImageWorkflowResolver.resolve(config,
            ForgeGenerationParams(prompt = "a tiger", seed = 24), settings)
        val server = FakeImageHttp(::simulatedImageSuccess)
        val states = simulatedBackend(backend, server.client, config).generate(params).toList()
        assertTrue("Standard image generation failed on $backend: $states",
            states.last() is GenerationState.Complete)
        val sent = if (backend == ImageGenBackendType.POLLINATIONS)
            server.requests.first().url.toString() else server.requests.first().jsonBodyText()
        assertFalse("Global custom workflow leaked into Standard for $backend: $sent",
            sent.contains("test-custom"))
    }
}

/** A character's imported ComfyUI checkpoint must not be replaced by the global model. */
class CharacterComfyUiCheckpointPreservationTest {
    @Test fun ownComfyUiWorkflowKeepsItsCheckpoint() = runBlocking {
        val backend = ImageGenBackendType.COMFYUI
        val graph = """
            {
              "3":{"class_type":"KSampler","inputs":{"seed":3,"steps":15,
                    "model":["4",0],"sampler_name":"euler","cfg":5.5}},
              "4":{"class_type":"CheckpointLoaderSimple",
                    "inputs":{"ckpt_name":"character-anime-model.safetensors"}}
            }
        """.trimIndent()
        val config = integrationConfig(backend).copy(activeBackend = backend.name)
        val prefs = CharacterImageWorkflowSettings(
            workflowModes = mapOf(backend.name to "CUSTOM"),
            customWorkflows = mapOf(backend.name to CustomImageWorkflowDefinition("anime.json", graph))
        )
        val params = CharacterImageWorkflowResolver.resolve(config,
            ForgeGenerationParams(prompt = "portrait", seed = 3), prefs)
        assertTrue(params.preserveCustomWorkflowModel)
        val fake = FakeImageHttp(::simulatedImageSuccess)
        val states = simulatedBackend(backend, fake.client, config).generate(params).toList()
        assertTrue("Unexpected ComfyUI result: $states", states.last() is GenerationState.Complete)
        val sent = fake.requests.first().jsonBodyText()
        assertTrue("Character checkpoint was overwritten by global model: $sent",
            sent.contains("character-anime-model.safetensors"))
        assertFalse("Global checkpoint leaked into character-specific graph: $sent",
            sent.contains("offline-model"))
    }
}
