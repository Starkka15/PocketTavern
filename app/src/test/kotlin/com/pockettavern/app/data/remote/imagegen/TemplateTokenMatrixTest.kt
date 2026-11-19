package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.domain.model.ForgeGenerationParams
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** 13 input variations x 3 independent checks = 39 offline JUnit tests. */
@RunWith(Parameterized::class)
class TemplateTokenMatrixTest(private val value: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: placeholder-safe value")
        fun cases(): Collection<Array<Any>> = listOf(
            "{{model}}", "{{seed}}", "{{steps}}", "{{prompt}}",
            "{{negative_prompt}}", "{{width}}", "{{height}}",
            "before {{model}} after", "quote \"{{seed}}\"",
            "a\n{{cfg}}", "literal {{source_image}}",
            "two {{model}} {{steps}}", "plain"
        ).map { arrayOf<Any>(it) }
    }

    @Test fun userPromptIsNeverRecursivelyExpanded() {
        val json = CustomImageTemplate.renderJson(
            """{"prompt":"{{prompt}}","model":"{{model}}"}""",
            ForgeGenerationParams(prompt = value), "real-model"
        )
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals(value, obj.getValue("prompt").jsonPrimitive.content)
        assertEquals("real-model", obj.getValue("model").jsonPrimitive.content)
    }

    @Test fun userNegativePromptIsNeverRecursivelyExpanded() {
        val json = CustomImageTemplate.renderJson(
            """{"negative":"{{negative_prompt}}","seed":{{seed}}}""",
            ForgeGenerationParams(prompt = "fixed", negativePrompt = value, seed = 42)
        )
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals(value, obj.getValue("negative").jsonPrimitive.content)
        assertEquals("42", obj.getValue("seed").jsonPrimitive.content)
    }

    @Test fun urlPromptPreservesLiteralPlaceholderText() {
        val url = CustomImageTemplate.renderUrl(
            "https://example.invalid/prompt/{{prompt}}?model={{model}}",
            ForgeGenerationParams(prompt = value), "real-model"
        )
        val request = Request.Builder().url(url).get().build()
        assertEquals(value, request.url.pathSegments.last())
        assertEquals("real-model", request.url.queryParameter("model"))
    }
}
