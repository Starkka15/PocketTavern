package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.domain.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.*
import org.junit.Test

class CustomImageTemplateTest {
    private val params = ForgeGenerationParams(prompt = "A \"quoted\" image & more", steps = 23, width = 640)

    @Test fun jsonTemplateRendersValidJsonWithEscapedPromptAndNumericFields() {
        val template = """{"prompt":"{{prompt}}","steps":{{steps}},"width":{{width}}}"""
        val root = Json.parseToJsonElement(CustomImageTemplate.renderJson(template, params)).jsonObject
        assertEquals(params.prompt, root.getValue("prompt").jsonPrimitive.content)
        assertEquals(23, root.getValue("steps").jsonPrimitive.int)
        assertEquals(640, root.getValue("width").jsonPrimitive.int)
    }

    @Test fun urlTemplateEncodesPrompt() {
        val result = CustomImageTemplate.renderUrl("https://example.org/prompt/{{prompt}}", params)
        assertTrue(result.startsWith("https://example.org/prompt/"))
        assertFalse(result.contains(" "))
        assertFalse(result.contains("\"quoted\""))
        assertTrue(result.contains("%26"))
    }

    @Test fun backendTemplatesAreStoredIndependently() {
        val config = ImageGenConfig(
            workflowModes = mapOf("COMFYUI" to "CUSTOM", "SD_WEBUI" to "CUSTOM"),
            customWorkflows = mapOf(
                "COMFYUI" to CustomImageWorkflowDefinition("comfy.json", "comfy"),
                "SD_WEBUI" to CustomImageWorkflowDefinition("forge.json", "forge")
            )
        )
        assertEquals("comfy", config.customWorkflowFor(ImageGenBackendType.COMFYUI)?.template)
        assertEquals("forge", config.customWorkflowFor(ImageGenBackendType.SD_WEBUI)?.template)
        assertNull(config.customWorkflowFor(ImageGenBackendType.DALLE))
        assertEquals("comfy", CustomImageTemplate.selected(config, params, ImageGenBackendType.COMFYUI))
        assertEquals("forge", CustomImageTemplate.selected(config, params, ImageGenBackendType.SD_WEBUI))
        assertNull(CustomImageTemplate.selected(config, params, ImageGenBackendType.DALLE))
    }

    @Test fun standardModeDoesNotUseSavedCustomTemplate() {
        val config = ImageGenConfig(customWorkflows = mapOf(
            "SD_WEBUI" to CustomImageWorkflowDefinition("forge.json", "custom")
        ))
        assertNull(CustomImageTemplate.selected(config, params, ImageGenBackendType.SD_WEBUI))
    }
}
