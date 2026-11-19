package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.domain.model.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Strict preflight exercises the real production backend generate() entry points.
 * FakeImageHttp intercepts all requests: no paid provider, device or API key required.
 */
@RunWith(Parameterized::class)
class ImageWorkflowErrorHandlingTest(private val backend: ImageGenBackendType) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} workflow errors")
        fun backends(): Collection<Array<Any>> = ImageGenBackendType.entries
            .filter { it != ImageGenBackendType.LOCAL_SD_MNN }
            .map { arrayOf<Any>(it) }
    }

    private val params = ForgeGenerationParams(prompt = "workflow preflight test", seed = 15)

    @Test fun customModeWithoutImportedTemplateFailsBeforeNetwork() = runBlocking {
        val config = integrationConfig(backend).copy(customWorkflows = emptyMap())
        val fake = FakeImageHttp(::simulatedImageSuccess)
        val states = simulatedBackend(backend, fake.client, config).generate(params).toList()
        assertTrue(states.first() is GenerationState.Starting)
        assertTrue("Unexpected result on $backend: $states", states.last() is GenerationState.Error)
        assertTrue((states.last() as GenerationState.Error).message.contains("no template is configured"))
        assertTrue("Network call despite missing template on $backend", fake.requests.isEmpty())
    }

    @Test fun malformedCustomTemplateFailsBeforeNetwork() = runBlocking {
        val fake = FakeImageHttp(::simulatedImageSuccess)
        val config = integrationConfig(backend)
        val states = simulatedBackend(backend, fake.client, config)
            .generate(params.copy(customWorkflowOverride = "{this-is-not-valid-json}")).toList()
        assertTrue("Unexpected result on $backend: $states", states.last() is GenerationState.Error)
        assertTrue("Network call despite invalid template on $backend", fake.requests.isEmpty())
    }

    @Test fun validRequestOverrideWorksWhenNoGlobalTemplateExists() = runBlocking {
        val fake = FakeImageHttp(::simulatedImageSuccess)
        val config = integrationConfig(backend).copy(customWorkflows = emptyMap())
        val states = simulatedBackend(backend, fake.client, config)
            .generate(params.copy(customWorkflowOverride = integrationTemplate(backend))).toList()
        assertTrue("Failed to use valid request override on $backend: $states",
            states.last() is GenerationState.Complete)
        assertTrue("No generation request made for $backend", fake.requests.isNotEmpty())
    }
}

class ImageWorkflowSelectionValidationTest {
    private val params = ForgeGenerationParams(prompt = "example")

    @Test fun standardModeNeverNeedsAnImportedTemplate() {
        val config = ImageGenConfig(workflowModes = mapOf("COMFYUI" to "STANDARD"))
        assertNull(CustomImageTemplate.checkedSelected(config, params, ImageGenBackendType.COMFYUI))
    }

    @Test fun unknownWorkflowModeIsRejected() {
        val config = ImageGenConfig(workflowModes = mapOf("COMFYUI" to "WRONG"))
        try {
            CustomImageTemplate.checkedSelected(config, params, ImageGenBackendType.COMFYUI)
            fail("Unknown modes must not be silently treated as Standard")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("Unknown image workflow mode"))
        }
    }

    @Test fun localSdxlCannotAcceptCustomTemplates() {
        val config = ImageGenConfig(workflowModes = mapOf("LOCAL_SD_MNN" to "CUSTOM"))
        try {
            CustomImageTemplate.checkedSelected(config, params, ImageGenBackendType.LOCAL_SD_MNN)
            fail("On-device SDXL does not support custom templates")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("does not support"))
        }
    }
}
