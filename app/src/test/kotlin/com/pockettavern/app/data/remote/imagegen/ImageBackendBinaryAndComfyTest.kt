package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.ImageGenBackendType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Binary image providers must reject HTTP 200 bodies that are not usable images (3 x 2 = 6). */
@RunWith(Parameterized::class)
class ImageBackendBinaryResponseTest(private val backend: ImageGenBackendType) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} binary response")
        fun cases(): Collection<Array<Any>> =
            listOf(ImageGenBackendType.COMFYUI, ImageGenBackendType.HUGGINGFACE,
                ImageGenBackendType.POLLINATIONS).map { arrayOf<Any>(it) }
    }

    private fun generateWithFinalResponse(reply: FakeImageReply): List<GenerationState> = runBlocking {
        val server = FakeImageHttp { request ->
            if (backend == ImageGenBackendType.COMFYUI && request.url.encodedPath != "/view")
                simulatedImageSuccess(request)
            else reply
        }
        simulatedBackend(backend, server.client, integrationConfig(backend))
            .generate(ForgeGenerationParams(prompt = "bad image", seed = 13)).toList()
    }

    @Test fun emptyImageBytesAreRejected() {
        val states = generateWithFinalResponse(FakeImageReply.image(byteArrayOf()))
        assertTrue("Empty bytes were accepted by " + backend + ": " + states,
            states.last() is GenerationState.Error)
    }

    @Test fun jsonErrorDisguisedAsSuccessfulImageIsRejected() {
        val states = generateWithFinalResponse(FakeImageReply.json("""{"error":"not an image"}"""))
        assertTrue("JSON was accepted as an image by " + backend + ": " + states,
            states.last() is GenerationState.Error)
    }
}

/** Exercises ComfyUI's actual submit → history → download flow, including error boundaries. */
class ComfyUiDownloadSimulationTest {
    private val backend = ImageGenBackendType.COMFYUI

    private fun testRun(reply: (okhttp3.Request) -> FakeImageReply): Pair<List<GenerationState>, FakeImageHttp> {
        val server = FakeImageHttp(reply)
        val states = runBlocking {
            simulatedBackend(backend, server.client, integrationConfig(backend))
                .generate(ForgeGenerationParams(prompt = "ComfyUI workflow test", seed = 42)).toList()
        }
        return states to server
    }

    @Test fun missingPromptIdStopsWithoutPolling() {
        val (states, server) = testRun { FakeImageReply.json("""{"error":"no prompt id"}""") }
        assertTrue(states.last() is GenerationState.Error)
        assertEquals(1, server.requests.size)
        assertTrue((states.last() as GenerationState.Error).message.contains("prompt_id"))
    }

    @Test fun historyHttpErrorStopsBeforeImageDownload() {
        val (states, server) = testRun { req ->
            if (req.url.encodedPath.startsWith("/history/"))
                FakeImageReply.json("""{"error":"history unavailable"}""", 503)
            else simulatedImageSuccess(req)
        }
        assertTrue(states.last() is GenerationState.Error)
        assertEquals(2, server.requests.size)
        assertTrue((states.last() as GenerationState.Error).message.contains("503"))
    }

    @Test fun imageDownloadHttpErrorIsNotReturnedAsSuccess() {
        val (states, server) = testRun { req ->
            if (req.url.encodedPath == "/view")
                FakeImageReply.json("""{"error":"image unavailable"}""", 404)
            else simulatedImageSuccess(req)
        }
        assertTrue(states.last() is GenerationState.Error)
        assertEquals(3, server.requests.size)
        assertTrue((states.last() as GenerationState.Error).message.contains("404"))
    }

    @Test fun encodedFilenameAndSubfolderAreTransferredCorrectly() {
        val (states, server) = testRun { req ->
            if (req.url.encodedPath.startsWith("/history/")) {
                FakeImageReply.json(
                    """{"test-prompt-123":{"outputs":{"9":{"images":[{"filename":"moon & cats.png","subfolder":"folder with spaces","type":"output"}]}}}}"""
                )
            } else simulatedImageSuccess(req)
        }
        assertTrue("ComfyUI image download failed: " + states, states.last() is GenerationState.Complete)
        val imageRequest = server.requests.last()
        assertEquals("/view", imageRequest.url.encodedPath)
        assertEquals("moon & cats.png", imageRequest.url.queryParameter("filename"))
        assertEquals("folder with spaces", imageRequest.url.queryParameter("subfolder"))
        assertEquals("output", imageRequest.url.queryParameter("type"))
    }
}
