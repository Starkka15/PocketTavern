package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.ImageGenBackendType
import com.pockettavern.app.domain.model.Result
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.IOException

/** 7 HTTP backends x 6 error codes = 42 isolated end-to-end offline tests. */
@RunWith(Parameterized::class)
class ImageBackendHttpStatusMatrixTest(
    private val backend: ImageGenBackendType,
    private val status: Int
) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} HTTP {1}")
        fun cases(): Collection<Array<Any>> =
            ImageGenBackendType.entries
                .filter { it != ImageGenBackendType.LOCAL_SD_MNN }
                .flatMap { backend ->
                    listOf(400, 401, 403, 429, 500, 503)
                        .map { status -> arrayOf<Any>(backend, status) }
                }
    }

    @Test fun serverErrorCannotBeMistakenForASuccessfulImage() = runBlocking {
        val server = FakeImageHttp {
            FakeImageReply.json("""{"error":"simulated failure"}""", status)
        }
        val states = simulatedBackend(backend, server.client, integrationConfig(backend))
            .generate(ForgeGenerationParams(prompt = "failure test", seed = 10)).toList()
        assertTrue(states.first() is GenerationState.Starting)
        assertTrue("HTTP " + status + " returned a success for " + backend + ": " + states,
            states.last() is GenerationState.Error)
        assertTrue((states.last() as GenerationState.Error).message.isNotBlank())
        assertEquals(1, server.requests.size)
    }
}

/** 7 connection failures simulate a real IOException, not merely an HTTP 500. */
@RunWith(Parameterized::class)
class ImageBackendNetworkFailureTest(private val backend: ImageGenBackendType) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} offline")
        fun cases(): Collection<Array<Any>> =
            ImageGenBackendType.entries
                .filter { it != ImageGenBackendType.LOCAL_SD_MNN }
                .map { arrayOf<Any>(it) }
    }

    @Test fun unavailableNetworkBecomesAnErrorStateWithoutExternalCall() = runBlocking {
        val server = FakeImageHttp { throw IOException("offline network simulator") }
        val states = simulatedBackend(backend, server.client, integrationConfig(backend))
            .generate(ForgeGenerationParams(prompt = "offline test")).toList()
        assertTrue(states.first() is GenerationState.Starting)
        assertTrue("Unexpected state for " + backend + ": " + states,
            states.last() is GenerationState.Error)
        assertTrue((states.last() as GenerationState.Error).message.contains("offline network simulator"))
        assertEquals(1, server.requests.size)
    }
}

/** 6 client implementations × 2 connection states = 12 actual method calls. */
@RunWith(Parameterized::class)
class ImageBackendConnectionSimulationTest(private val backend: ImageGenBackendType) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} connection")
        fun cases(): Collection<Array<Any>> =
            ImageGenBackendType.entries
                .filter { it != ImageGenBackendType.LOCAL_SD_MNN && it != ImageGenBackendType.SD_WEBUI }
                .map { arrayOf<Any>(it) }
    }

    @Test fun successfulConnectionProbeReturnsSuccess() = runBlocking {
        val server = FakeImageHttp { FakeImageReply.json("{}") }
        val result = simulatedBackend(backend, server.client, integrationConfig(backend)).testConnection()
        assertTrue("Connection probe failed for " + backend + ": " + result,
            result is Result.Success)
        assertEquals(1, server.requests.size)
        val request = server.requests.single()
        assertEquals(if (backend == ImageGenBackendType.POLLINATIONS) "HEAD" else "GET", request.method)
    }

    @Test fun rejectedConnectionProbeReturnsError() = runBlocking {
        val server = FakeImageHttp { FakeImageReply.json("{}", 503) }
        val result = simulatedBackend(backend, server.client, integrationConfig(backend)).testConnection()
        assertTrue("Connection probe accepted 503 for " + backend, result is Result.Error)
        assertEquals(1, server.requests.size)
    }
}

/** For parsers that expect JSON, 5 providers × 3 bad payloads = 15 cases. */
@RunWith(Parameterized::class)
class ImageBackendMalformedResponseTest(
    private val backend: ImageGenBackendType,
    private val payload: String
) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} bad body")
        fun cases(): Collection<Array<Any>> =
            listOf(
                ImageGenBackendType.SD_WEBUI, ImageGenBackendType.COMFYUI,
                ImageGenBackendType.DALLE, ImageGenBackendType.STABILITY,
                ImageGenBackendType.NANO_GPT
            ).flatMap { backend ->
                listOf("not json", "[]", """{"irrelevant":true}""").map { body ->
                    arrayOf<Any>(backend, body)
                }
            }
    }

    @Test fun malformedOrMissingImageResultsCannotBeReportedAsSuccess() = runBlocking {
        val server = FakeImageHttp { FakeImageReply.json(payload) }
        val states = simulatedBackend(backend, server.client, integrationConfig(backend))
            .generate(ForgeGenerationParams(prompt = "bad response")).toList()
        assertTrue("Unexpected image success on " + backend + ": " + states,
            states.last() is GenerationState.Error)
    }
}
