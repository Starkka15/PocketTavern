package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.domain.model.ImageGenBackendType
import org.junit.Assert.*
import org.junit.Test

class WorkflowImportValidatorTest {
    private val comfy = """{"100":{"class_type":"KSampler","inputs":{"seed":1}}}"""
    private val forge = """{"prompt":"{{prompt}}","steps":20}"""

    @Test fun acceptsComfyApiWorkflow() {
        assertNull(WorkflowImportValidator.validate(comfy, ImageGenBackendType.COMFYUI))
    }

    @Test fun rejectsComfyWorkflowUnderForge() {
        assertNotNull(WorkflowImportValidator.validate(comfy, ImageGenBackendType.SD_WEBUI))
    }

    @Test fun rejectsForgeTemplateUnderComfy() {
        assertNotNull(WorkflowImportValidator.validate(forge, ImageGenBackendType.COMFYUI))
    }

    @Test fun acceptsForgeTemplate() {
        assertNull(WorkflowImportValidator.validate(forge, ImageGenBackendType.SD_WEBUI))
    }

    @Test fun rejectsEmptyAndMalformedJson() {
        for (value in listOf("", "{}", "{bad json", "[]")) {
            assertNotNull(WorkflowImportValidator.validate(value, ImageGenBackendType.COMFYUI))
        }
    }

    @Test fun rejectsComfyEditorExport() {
        val editor = """{"nodes":[],"links":[]}"""
        assertTrue(WorkflowImportValidator.validate(editor, ImageGenBackendType.COMFYUI)!!.contains("API format"))
    }

    @Test fun rejectsComfyWorkflowWithoutKSampler() {
        val noSampler = """{"100":{"class_type":"CheckpointLoaderSimple","inputs":{}}}"""
        assertNotNull(WorkflowImportValidator.validate(noSampler, ImageGenBackendType.COMFYUI))
    }
    @Test fun rejectsComfyWorkflowForEveryOtherBackendWithItsName() {
        for (backend in ImageGenBackendType.entries.filter { it != ImageGenBackendType.COMFYUI }) {
            val error = WorkflowImportValidator.validate(comfy, backend)
            assertNotNull("Expected rejection for ${backend.displayName}", error)
            assertTrue("Expected backend name in: $error", error!!.contains(backend.displayName))
        }
    }

    @Test fun acceptsBackendSpecificRequestTemplates() {
        val templates = mapOf(
            ImageGenBackendType.SD_WEBUI to """{"prompt":"{{prompt}}"}""",
            ImageGenBackendType.DALLE to """{"prompt":"{{prompt}}","model":"dall-e-3"}""",
            ImageGenBackendType.STABILITY to """{"text_prompts":[{"text":"{{prompt}}","weight":1}]}""",
            ImageGenBackendType.HUGGINGFACE to """{"inputs":"{{prompt}}"}""",
            ImageGenBackendType.NANO_GPT to """{"prompt":"{{prompt}}","model":"chroma"}""",
            ImageGenBackendType.POLLINATIONS to "https://image.pollinations.ai/prompt/{{prompt}}"
        )
        for ((backend, template) in templates) {
            assertNull("Expected valid ${backend.displayName} template", WorkflowImportValidator.validate(template, backend))
        }
    }

    @Test fun rejectsWrongRequestFormatWithBackendSpecificMessage() {
        val wrong = """{"unrelated":"value"}"""
        for (backend in ImageGenBackendType.entries) {
            val error = WorkflowImportValidator.validate(wrong, backend)
            assertNotNull(error)
            assertTrue("Expected backend name in: $error", error!!.contains(backend.displayName))
        }
    }

    @Test fun acceptsNumericPlaceholdersInBackendTemplates() {
        val templates = mapOf(
            ImageGenBackendType.COMFYUI to """{"1":{"class_type":"KSampler","inputs":{"seed":{{seed}},"steps":{{steps}}}}}""",
            ImageGenBackendType.SD_WEBUI to """{"prompt":"{{prompt}}","steps":{{steps}},"cfg_scale":{{cfg}}}""",
            ImageGenBackendType.DALLE to """{"prompt":"{{prompt}}","model":"{{model}}","width":{{width}}}""",
            ImageGenBackendType.STABILITY to """{"text_prompts":[{"text":"{{prompt}}"}],"steps":{{steps}}}""",
            ImageGenBackendType.HUGGINGFACE to """{"inputs":"{{prompt}}","parameters":{"steps":{{steps}}}}""",
            ImageGenBackendType.NANO_GPT to """{"prompt":"{{prompt}}","model":"{{model}}","height":{{height}}}"""
        )
        for ((backend, template) in templates) {
            assertNull("Numeric tokens must be valid for ${backend.displayName}",
                WorkflowImportValidator.validate(template, backend))
        }
    }

    @Test fun unknownUnquotedPlaceholdersRemainInvalidJson() {
        val invalid = """{"prompt":"{{prompt}}","steps":{{unrecognized}}}"""
        assertNotNull(WorkflowImportValidator.validate(invalid, ImageGenBackendType.SD_WEBUI))
    }

}
