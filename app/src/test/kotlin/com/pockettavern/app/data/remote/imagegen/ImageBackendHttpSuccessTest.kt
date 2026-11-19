package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.ImageGenBackendType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * 7 backends x 3 real generate() tests = 21 cases.
 * The HTTP transport is entirely in memory; no real provider is contacted.
 */
@RunWith(Parameterized::class)
class ImageBackendHttpSuccessTest(private val backend: ImageGenBackendType) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} mocked HTTP success")
        fun cases(): Collection<Array<Any>> = ImageGenBackendType.entries
            .filter { it != ImageGenBackendType.LOCAL_SD_MNN }
            .map { arrayOf<Any>(it) }
    }

    private val params = ForgeGenerationParams(
        prompt = "a moon & two cats", negativePrompt = "no dogs",
        width = 640, height = 768, steps = 24, seed = 97
    )

    @Test fun importedTemplateProducesARealBackendHttpRequest() = runBlocking {
        assertNull(WorkflowImportValidator.validate(integrationTemplate(backend), backend))
        val server = FakeImageHttp(::simulatedImageSuccess)
        val states = simulatedBackend(backend, server.client, integrationConfig(backend))
            .generate(params).toList()

        assertTrue(states.first() is GenerationState.Starting)
        assertTrue("Missing image result for " + backend + ": " + states,
            states.last() is GenerationState.Complete)
        assertEquals("AQIDBA==", (states.last() as GenerationState.Complete).imageBase64)
        assertFalse(server.requests.isEmpty())

        val request = server.requests.first()
        if (backend == ImageGenBackendType.POLLINATIONS) {
            assertEquals("GET", request.method)
            assertEquals(params.prompt, request.url.pathSegments.last())
            assertEquals("test-custom", request.url.queryParameter("custom_marker"))
        } else {
            assertEquals("POST", request.method)
            val body = request.jsonBodyText()
            assertTrue("Custom body marker missing for " + backend + ": " + body,
                body.contains("test-custom"))
            assertNotNull(Json.parseToJsonElement(body).jsonObject)
            assertEquals("application", request.body!!.contentType()!!.type)
            assertEquals("json", request.body!!.contentType()!!.subtype)
        }

        if (backend == ImageGenBackendType.COMFYUI) {
            assertEquals(listOf("/prompt", "/history/test-prompt-123", "/view"),
                server.requests.map { it.url.encodedPath })
        }
    }

    @Test fun requestOverrideTakesPrecedenceOverSavedTemplate() = runBlocking {
        val overridden = integrationTemplate(backend).replace("test-custom", "test-override")
        val server = FakeImageHttp(::simulatedImageSuccess)
        val states = simulatedBackend(backend, server.client, integrationConfig(backend))
            .generate(params.copy(customWorkflowOverride = overridden)).toList()

        assertTrue("Failed on " + backend + ": " + states,
            states.last() is GenerationState.Complete)
        val request = server.requests.first()
        val sent = if (backend == ImageGenBackendType.POLLINATIONS)
            request.url.toString() else request.jsonBodyText()
        assertTrue(sent, sent.contains("test-override"))
        assertFalse(sent, sent.contains("test-custom"))
    }

    @Test fun customRequestHasTheCorrectProviderEndpoint() = runBlocking {
        val server = FakeImageHttp(::simulatedImageSuccess)
        val states = simulatedBackend(backend, server.client, integrationConfig(backend))
            .generate(params).toList()
        assertTrue(states.last() is GenerationState.Complete)
        val url = server.requests.first().url
        when (backend) {
            ImageGenBackendType.SD_WEBUI -> assertEquals("/sdapi/v1/txt2img", url.encodedPath)
            ImageGenBackendType.COMFYUI -> assertEquals("/prompt", url.encodedPath)
            ImageGenBackendType.DALLE -> assertEquals("/v1/images/generations", url.encodedPath)
            ImageGenBackendType.STABILITY -> assertTrue(url.encodedPath.endsWith("/text-to-image"))
            ImageGenBackendType.HUGGINGFACE -> assertTrue(url.encodedPath.startsWith("/models/"))
            ImageGenBackendType.NANO_GPT -> assertEquals("/v1/images/generations", url.encodedPath)
            ImageGenBackendType.POLLINATIONS -> assertTrue(url.encodedPath.startsWith("/prompt/"))
            ImageGenBackendType.LOCAL_SD_MNN -> fail("No local network backend expected")
        }
    }
}

/** 6 actual standard-mode request checks (Forge's legacy Retrofit path is tested separately). */
@RunWith(Parameterized::class)
class ImageBackendStandardModeHttpTest(private val backend: ImageGenBackendType) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} standard mode")
        fun cases(): Collection<Array<Any>> = ImageGenBackendType.entries
            .filter { it != ImageGenBackendType.SD_WEBUI && it != ImageGenBackendType.LOCAL_SD_MNN }
            .map { arrayOf<Any>(it) }
    }

    @Test fun standardModeNeverSubmitsSavedCustomTemplate() = runBlocking {
        val params = ForgeGenerationParams(prompt = "ordinary generation", seed = 7)
        val server = FakeImageHttp(::simulatedImageSuccess)
        val states = simulatedBackend(backend, server.client, integrationConfig(backend))
            .generate(params.copy(workflowModeOverride = "STANDARD")).toList()
        assertTrue("Failure on " + backend + ": " + states,
            states.last() is GenerationState.Complete)
        val request = server.requests.first()
        val text = if (backend == ImageGenBackendType.POLLINATIONS)
            request.url.toString() else request.jsonBodyText()
        assertFalse("Custom template accidentally used by " + backend, text.contains("test-custom"))
        assertTrue(text.contains("ordinary"))
    }
}
