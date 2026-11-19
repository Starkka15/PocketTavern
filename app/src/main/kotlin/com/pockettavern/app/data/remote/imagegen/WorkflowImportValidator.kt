package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.domain.model.ImageGenBackendType
import kotlinx.serialization.json.*

/** Checks request-template shape for the selected backend before saving it. */
object WorkflowImportValidator {
    private val json = Json { isLenient = false }

    // Bare numeric placeholders are valid template syntax, but not valid JSON before rendering.
    // Replace only the supported numeric tokens with JSON numbers during shape validation.
    private val numericPlaceholders = Regex("""\{\{(?:seed|steps|cfg|width|height|denoise|clip_skip)\}\}""")

    fun validate(template: String, backend: ImageGenBackendType): String? {
        val label = "${backend.displayName} ${backend.templateLabel}"
        if (template.isBlank()) return "Empty file. Not a valid $label."

        // Pollinations uses a URL template rather than a JSON request body.
        if (backend == ImageGenBackendType.POLLINATIONS) {
            val url = template.trim()
            return if ((url.startsWith("https://") || url.startsWith("http://")) &&
                url.contains("{{prompt}}") && !url.any { it.isWhitespace() })
                null else "Not a valid Pollinations URL Template. Provide a full http(s) URL containing {{prompt}}."
        }

        val root = try { json.parseToJsonElement(numericPlaceholders.replace(template, "0")) } catch (_: Exception) {
            return "Invalid JSON. Not a valid $label."
        }
        if (root !is JsonObject || root.isEmpty()) return "Not a valid $label: expected a non-empty JSON object."

        val editor = root["nodes"] is JsonArray && root["links"] is JsonArray
        val comfyNodes = root.values.filterIsInstance<JsonObject>()
            .filter { it["class_type"] is JsonPrimitive && it["inputs"] is JsonObject }
        val comfy = comfyNodes.isNotEmpty()
        if (backend != ImageGenBackendType.COMFYUI && (comfy || editor))
            return "This is a ComfyUI workflow, not a valid $label."

        fun stringField(key: String) = root[key] is JsonPrimitive &&
            (root[key] as JsonPrimitive).isString
        return when (backend) {
            ImageGenBackendType.COMFYUI -> when {
                editor -> "Not a valid ComfyUI Workflow. Export the workflow in API format."
                !comfy || comfyNodes.size != root.size -> "Not a valid ComfyUI API Workflow: expected node IDs, class_type and inputs."
                comfyNodes.none { it["class_type"]?.jsonPrimitive?.contentOrNull == "KSampler" } ->
                    "Not a supported ComfyUI Workflow: KSampler node required."
                else -> null
            }
            ImageGenBackendType.SD_WEBUI ->
                if (stringField("prompt")) null else "Not a valid SD WebUI / Forge JSON Request Template: prompt must be a string."
            ImageGenBackendType.DALLE ->
                if (stringField("prompt") && stringField("model")) null
                else "Not a valid DALL-E (OpenAI) Image Generation Request: prompt and model must be strings."
            ImageGenBackendType.STABILITY ->
                if (root["text_prompts"] is JsonArray &&
                    (root["text_prompts"] as JsonArray).isNotEmpty() &&
                    (root["text_prompts"] as JsonArray).all {
                        it is JsonObject && it["text"] is JsonPrimitive &&
                            (it["text"] as JsonPrimitive).isString
                    }) null
                else "Not a valid Stability AI JSON Request Template: text_prompts must contain text entries."
            ImageGenBackendType.HUGGINGFACE ->
                if (stringField("inputs")) null
                else "Not a valid HuggingFace Inference Request Template: inputs must be a string."
            ImageGenBackendType.NANO_GPT ->
                if (stringField("prompt") && stringField("model")) null
                else "Not a valid nano-gpt Image Generation Request: prompt and model must be strings."
            ImageGenBackendType.LOCAL_SD_MNN ->
                "On-Device (SDXL) does not support custom request templates."
            ImageGenBackendType.POLLINATIONS -> error("Handled above")
        }
    }
}
