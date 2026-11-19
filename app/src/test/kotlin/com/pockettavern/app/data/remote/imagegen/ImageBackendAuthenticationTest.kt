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

/** 4 providers × 2 authentication-related request assertions = 8 cases. */
@RunWith(Parameterized::class)
class ImageBackendAuthenticationTest(
    private val backend: ImageGenBackendType,
    private val fakeKey: String
) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} authorization")
        fun cases(): Collection<Array<Any>> = listOf(
            arrayOf<Any>(ImageGenBackendType.DALLE, "fake-openai-key"),
            arrayOf<Any>(ImageGenBackendType.STABILITY, "fake-stability-key"),
            arrayOf<Any>(ImageGenBackendType.HUGGINGFACE, "fake-huggingface-key"),
            arrayOf<Any>(ImageGenBackendType.NANO_GPT, "fake-nanogpt-key")
        )
    }

    private fun request(): okhttp3.Request {
        val server = FakeImageHttp(::simulatedImageSuccess)
        val states = runBlocking {
            simulatedBackend(backend, server.client, integrationConfig(backend))
                .generate(ForgeGenerationParams(prompt = "testing auth", seed = 5)).toList()
        }
        assertTrue(states.last() is GenerationState.Complete)
        return server.requests.single()
    }

    @Test fun authorizationHeaderContainsTheConfiguredFakeKey() {
        assertEquals("Bearer " + fakeKey, request().header("Authorization"))
    }

    @Test fun fakeApiKeyNeverLeaksIntoRequestBodyOrUrl() {
        val req = request()
        assertFalse(req.url.toString().contains(fakeKey))
        assertFalse(req.jsonBodyText().contains(fakeKey))
    }
}
