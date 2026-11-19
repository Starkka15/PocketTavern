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

/** 12 prompt inputs x 3 independent tests = 36 JUnit cases. */
@RunWith(Parameterized::class)
class PromptEscapingMatrixTest(private val prompt: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: prompt encoding")
        fun cases(): Collection<Array<Any>> = listOf(
            "", "simple", "two words", "a & b", "quote \"here\"",
            "back\\slash", "line\nbreak", "tab\tcharacter", "emoji 🌙",
            "Deutsch äöüß", "日本語", "curly {{not_a_token}}"
        ).map { arrayOf<Any>(it) }
    }

    private val params get() = ForgeGenerationParams(prompt = prompt)

    @Test fun jsonPromptRoundTrips() {
        val rendered = CustomImageTemplate.renderJson("""{"prompt":"{{prompt}}"}""", params)
        assertEquals(prompt, Json.parseToJsonElement(rendered).jsonObject.getValue("prompt").jsonPrimitive.content)
    }

    @Test fun urlPromptRoundTrips() {
        val rendered = CustomImageTemplate.renderUrl("https://example.invalid/prompt/{{prompt}}", params)
        val request = Request.Builder().url(rendered).get().build()
        assertEquals(prompt, request.url.pathSegments.last())
        assertEquals("GET", request.method)
    }

    @Test fun jsonNegativePromptRoundTrips() {
        val rendered = CustomImageTemplate.renderJson("""{"negative":"{{negative_prompt}}"}""",
            ForgeGenerationParams(prompt = "fixed", negativePrompt = prompt))
        assertEquals(prompt, Json.parseToJsonElement(rendered).jsonObject.getValue("negative").jsonPrimitive.content)
    }
}
