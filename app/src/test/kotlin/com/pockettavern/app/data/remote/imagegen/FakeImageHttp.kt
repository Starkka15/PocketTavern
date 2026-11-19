package com.pockettavern.app.data.remote.imagegen

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A deterministic in-memory HTTP server simulation.
 *
 * Uses the real OkHttp request pipeline but never opens a socket. The response
 * and even network exceptions are supplied by a test callback. Not an external
 * API integration test; it exercises each backend's actual generate() path.
 */
internal data class FakeImageReply(
    val code: Int = 200,
    val body: ByteArray = byteArrayOf(),
    val contentType: String = "application/json"
) {
    companion object {
        fun json(body: String, code: Int = 200) =
            FakeImageReply(code, body.toByteArray(Charsets.UTF_8))

        fun image(bytes: ByteArray = byteArrayOf(1, 2, 3, 4), code: Int = 200) =
            FakeImageReply(code, bytes, "image/png")
    }
}

internal class FakeImageHttp(private val response: (Request) -> FakeImageReply) {
    val requests = CopyOnWriteArrayList<Request>()

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            requests.add(request)
            val reply = response(request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(reply.code)
                .message("Test HTTP " + reply.code)
                .body(reply.body.toResponseBody(reply.contentType.toMediaType()))
                .build()
        })
        .build()
}

internal fun Request.jsonBodyText(): String {
    val buffer = Buffer()
    checkNotNull(body).writeTo(buffer)
    return buffer.readUtf8()
}

internal fun simulatedImageSuccess(request: Request): FakeImageReply {
    if (request.url.encodedPath == "/prompt") {
        return FakeImageReply.json("""{"prompt_id":"test-prompt-123"}""")
    }
    if (request.url.encodedPath == "/history/test-prompt-123") {
        return FakeImageReply.json(
            """{"test-prompt-123":{"outputs":{"9":{"images":[{"filename":"test.png","subfolder":"","type":"output"}]}}}}"""
        )
    }
    if (request.url.encodedPath == "/view") {
        return FakeImageReply.image()
    }
    val host = request.url.host
    return when {
        host.contains("forge") -> FakeImageReply.json("""{"images":["AQIDBA=="]}""")
        host.contains("openai") -> FakeImageReply.json("""{"data":[{"b64_json":"AQIDBA=="}]}""")
        host.contains("stability") -> FakeImageReply.json("""{"artifacts":[{"base64":"AQIDBA=="}]}""")
        host.contains("nano-gpt") -> FakeImageReply.json("""{"data":[{"b64_json":"AQIDBA=="}]}""")
        host.contains("huggingface") -> FakeImageReply.image()
        host.contains("pollinations") -> FakeImageReply.image()
        else -> error("Unexpected test request: " + request.url)
    }
}

/** All credentials/hosts are test-only and are intercepted in memory. */
internal fun integrationConfig(
    backend: com.pockettavern.app.domain.model.ImageGenBackendType,
    custom: Boolean = true
): com.pockettavern.app.domain.model.ImageGenConfig {
    val config = com.pockettavern.app.domain.model.ImageGenConfig(
        sdWebuiUrl = "https://forge.example.invalid",
        comfyuiUrl = "https://comfy.example.invalid",
        dalleApiKey = "fake-openai-key",
        stabilityApiKey = "fake-stability-key",
        huggingfaceApiKey = "fake-huggingface-key",
        nanoGptApiKey = "fake-nanogpt-key",
        pollinationsApiKey = "fake-pollinations-key",
        pollinationsModel = "flux",
        sdModel = "offline-model",
        sampler = "Euler",
        workflowModes = if (custom) mapOf(backend.name to "CUSTOM") else emptyMap(),
        customWorkflows = mapOf(
            backend.name to com.pockettavern.app.domain.model.CustomImageWorkflowDefinition(
                "integration-test", integrationTemplate(backend)
            )
        )
    )
    return config
}

internal fun integrationTemplate(backend: com.pockettavern.app.domain.model.ImageGenBackendType): String =
    when (backend) {
        com.pockettavern.app.domain.model.ImageGenBackendType.SD_WEBUI ->
            """{"prompt":"{{prompt}}","seed":{{seed}},"custom_marker":"test-custom"}"""
        com.pockettavern.app.domain.model.ImageGenBackendType.COMFYUI ->
            """{"3":{"class_type":"KSampler","inputs":{"seed":1,"steps":12,"custom_marker":"test-custom"}}}"""
        com.pockettavern.app.domain.model.ImageGenBackendType.DALLE ->
            """{"prompt":"{{prompt}}","model":"{{model}}","custom_marker":"test-custom"}"""
        com.pockettavern.app.domain.model.ImageGenBackendType.STABILITY ->
            """{"text_prompts":[{"text":"{{prompt}}"}],"custom_marker":"test-custom"}"""
        com.pockettavern.app.domain.model.ImageGenBackendType.HUGGINGFACE ->
            """{"inputs":"{{prompt}}","custom_marker":"test-custom"}"""
        com.pockettavern.app.domain.model.ImageGenBackendType.NANO_GPT ->
            """{"prompt":"{{prompt}}","model":"{{model}}","custom_marker":"test-custom"}"""
        com.pockettavern.app.domain.model.ImageGenBackendType.POLLINATIONS ->
            "https://pollinations.example.invalid/prompt/{{prompt}}?custom_marker=test-custom&seed={{seed}}"
        com.pockettavern.app.domain.model.ImageGenBackendType.LOCAL_SD_MNN ->
            error("On-device backend does not use network templates")
    }

internal fun simulatedBackend(
    backend: com.pockettavern.app.domain.model.ImageGenBackendType,
    http: OkHttpClient,
    config: com.pockettavern.app.domain.model.ImageGenConfig
): ImageGenBackend {
    // JVM unit tests have no android.util.Log implementation.
    com.pockettavern.app.util.DebugLogger.setEnabled(false)
    return when (backend) {
        com.pockettavern.app.domain.model.ImageGenBackendType.SD_WEBUI ->
            SdWebuiBackend({ error("Legacy Forge API should not be called in custom mode") }, http, config)
        com.pockettavern.app.domain.model.ImageGenBackendType.COMFYUI -> ComfyUiBackend(http, config)
        com.pockettavern.app.domain.model.ImageGenBackendType.DALLE -> DalleBackend(http, config)
        com.pockettavern.app.domain.model.ImageGenBackendType.STABILITY -> StabilityBackend(http, config)
        com.pockettavern.app.domain.model.ImageGenBackendType.HUGGINGFACE -> HuggingFaceBackend(http, config)
        com.pockettavern.app.domain.model.ImageGenBackendType.NANO_GPT -> NanoGptBackend(http, config)
        com.pockettavern.app.domain.model.ImageGenBackendType.POLLINATIONS -> PollinationsBackend(http, config)
        com.pockettavern.app.domain.model.ImageGenBackendType.LOCAL_SD_MNN ->
            error("On-device backend does not use HTTP")
    }
}
