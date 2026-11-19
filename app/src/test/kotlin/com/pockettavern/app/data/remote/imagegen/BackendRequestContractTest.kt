package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.ImageGenBackendType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Test

/**
 * Offline request-contract checks. No external service or API key is used.
 * These verify template rendering and HTTP request shape, not live backend integration.
 */
class BackendRequestContractTest {
    private val params = ForgeGenerationParams(prompt = "A \"fox\" & moon", width = 640, height = 768, steps = 24)

    private val jsonTemplates = mapOf(
        ImageGenBackendType.SD_WEBUI to """{"prompt":"{{prompt}}","steps":{{steps}},"width":{{width}}}""",
        ImageGenBackendType.DALLE to """{"model":"{{model}}","prompt":"{{prompt}}","response_format":"b64_json"}""",
        ImageGenBackendType.STABILITY to """{"text_prompts":[{"text":"{{prompt}}","weight":1}],"steps":{{steps}}}""",
        ImageGenBackendType.HUGGINGFACE to """{"inputs":"{{prompt}}","parameters":{"num_inference_steps":{{steps}}}}""",
        ImageGenBackendType.NANO_GPT to """{"model":"{{model}}","prompt":"{{prompt}}"}"""
    )

    @Test fun validJsonRequestsRenderForEveryJsonBackend() {
        for ((backend, template) in jsonTemplates) {
            assertNull("Invalid template for ${backend.displayName}", WorkflowImportValidator.validate(template, backend))
            val rendered = CustomImageTemplate.renderJson(template, params, "test-model")
            val root = Json.parseToJsonElement(rendered).jsonObject
            val prompt = when (backend) {
                ImageGenBackendType.STABILITY -> root.getValue("text_prompts").jsonArray.first().jsonObject.getValue("text").jsonPrimitive.content
                ImageGenBackendType.HUGGINGFACE -> root.getValue("inputs").jsonPrimitive.content
                else -> root.getValue("prompt").jsonPrimitive.content
            }
            assertTrue("Prompt missing for ${backend.displayName}", prompt.contains(params.prompt))
            assertFalse("Unexpanded prompt token for ${backend.displayName}", rendered.contains("{{prompt}}"))
        }
    }

    @Test fun jsonRequestUsesPostAndJsonContentTypeWithoutNetwork() {
        for ((backend, template) in jsonTemplates) {
            val body = CustomImageTemplate.renderJson(template, params, "test-model")
            val request = Request.Builder()
                .url("https://example.invalid/images/generations")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            assertEquals("POST for ${backend.displayName}", "POST", request.method)
            assertEquals("application", request.body!!.contentType()!!.type)
            assertEquals("json", request.body!!.contentType()!!.subtype)
            assertNotNull(Json.parseToJsonElement(body).jsonObject)
        }
    }

    @Test fun pollinationsUrlTemplateUsesGetAndEncodesPrompt() {
        val template = "https://example.invalid/prompt/{{prompt}}?width={{width}}&model={{model}}"
        assertNull(WorkflowImportValidator.validate(template, ImageGenBackendType.POLLINATIONS))
        val url = CustomImageTemplate.renderUrl(template, params, "flux")
        val request = Request.Builder().url(url).get().build()
        assertEquals("GET", request.method)
        assertEquals("640", request.url.queryParameter("width"))
        assertEquals("flux", request.url.queryParameter("model"))
        assertTrue(request.url.toString().contains("%26"))
        assertTrue(request.url.encodedPath.contains("%26"))
    }

    @Test fun incompatibleComfyWorkflowRejectedByEveryNonComfyBackend() {
        val comfy = """{"1":{"class_type":"KSampler","inputs":{"seed":1}}}"""
        assertNull(WorkflowImportValidator.validate(comfy, ImageGenBackendType.COMFYUI))
        for (backend in ImageGenBackendType.entries.filter { it != ImageGenBackendType.COMFYUI }) {
            val error = WorkflowImportValidator.validate(comfy, backend)
            assertNotNull(error)
            assertTrue(error!!.contains(backend.displayName))
        }
    }
}
