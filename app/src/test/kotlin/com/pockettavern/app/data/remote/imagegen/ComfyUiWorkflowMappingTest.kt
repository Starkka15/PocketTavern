package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.domain.model.ForgeGenerationParams
import com.pockettavern.app.domain.model.GenerationState
import com.pockettavern.app.domain.model.ImageGenBackendType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/** Real generate() → ComfyUI graph transformation → intercepted POST /prompt. */
class ComfyUiWorkflowMappingTest {
    private val graph = """
        {
          "3":{"class_type":"KSampler","inputs":{
            "seed":1,"steps":12,"cfg":5.0,"sampler_name":"euler","scheduler":"normal",
            "positive":["6",0],"negative":["7",0],"latent_image":["5",0],"model":["4",0]
          }},
          "4":{"class_type":"CheckpointLoaderSimple","inputs":{"ckpt_name":"original.safetensors"}},
          "5":{"class_type":"EmptyLatentImage","inputs":{"width":512,"height":512,"batch_size":1}},
          "6":{"class_type":"CLIPTextEncode","inputs":{"text":"old positive","clip":["4",1]}},
          "7":{"class_type":"CLIPTextEncode","inputs":{"text":"old negative","clip":["4",1]}},
          "8":{"class_type":"VAEDecode","inputs":{"samples":["3",0],"vae":["4",2]}},
          "9":{"class_type":"SaveImage","inputs":{"filename_prefix":"test","images":["8",0]}}
        }
    """.trimIndent()

    @Test fun overridesParametersWhilePreservingOriginalGraphConnections() = runBlocking {
        assertNull(WorkflowImportValidator.validate(graph, ImageGenBackendType.COMFYUI))
        val server = FakeImageHttp(::simulatedImageSuccess)
        val config = integrationConfig(ImageGenBackendType.COMFYUI)
        val states = simulatedBackend(ImageGenBackendType.COMFYUI, server.client, config)
            .generate(ForgeGenerationParams(
                prompt = "new positive", negativePrompt = "new negative",
                steps = 32, width = 896, height = 1152, seed = 123, cfgScale = 8.5f,
                customWorkflowOverride = graph
            )).toList()
        assertTrue("ComfyUI failed: " + states, states.last() is GenerationState.Complete)

        val body = Json.parseToJsonElement(server.requests.first().jsonBodyText()).jsonObject
        val nodes = body.getValue("prompt").jsonObject
        fun nodeInput(node: String, input: String): String =
            nodes.getValue(node).jsonObject.getValue("inputs").jsonObject
                .getValue(input).jsonPrimitive.content

        assertEquals("123", nodeInput("3", "seed"))
        assertEquals("32", nodeInput("3", "steps"))
        assertEquals("8.5", nodeInput("3", "cfg"))
        assertEquals("euler", nodeInput("3", "sampler_name"))
        assertEquals("new positive", nodeInput("6", "text"))
        assertEquals("new negative", nodeInput("7", "text"))
        assertEquals("896", nodeInput("5", "width"))
        assertEquals("1152", nodeInput("5", "height"))
        assertEquals("offline-model", nodeInput("4", "ckpt_name"))
        assertEquals("SaveImage", nodes.getValue("9").jsonObject.getValue("class_type").jsonPrimitive.content)
    }

    @Test fun invalidSamplerGraphFailsBeforeAnyHttpRequest() = runBlocking {
        val server = FakeImageHttp(::simulatedImageSuccess)
        val invalid = """{"7":{"class_type":"CLIPTextEncode","inputs":{"text":"something"}}}"""
        val states = simulatedBackend(ImageGenBackendType.COMFYUI, server.client,
            integrationConfig(ImageGenBackendType.COMFYUI))
            .generate(ForgeGenerationParams(prompt = "test", customWorkflowOverride = invalid)).toList()
        assertTrue(states.last() is GenerationState.Error)
        assertTrue(server.requests.isEmpty())
    }
}

class ForgeCustomImg2ImgSimulationTest {
    @Test fun img2imgUsesCorrectEndpointAndSourceImagePayload() = runBlocking {
        val backend = ImageGenBackendType.SD_WEBUI
        val server = FakeImageHttp(::simulatedImageSuccess)
        val bodyTemplate = """{"prompt":"{{prompt}}","init_images":["{{source_image}}"],"denoising_strength":{{denoise}}}"""
        assertNull(WorkflowImportValidator.validate(bodyTemplate, backend))
        val states = simulatedBackend(backend, server.client, integrationConfig(backend))
            .generate(ForgeGenerationParams(
                prompt = "restyle", sourceImageBase64 = "AQIDBA==",
                customWorkflowOverride = bodyTemplate
            )).toList()
        assertTrue(states.last() is GenerationState.Complete)
        val request = server.requests.single()
        assertEquals("/sdapi/v1/img2img", request.url.encodedPath)
        val obj = Json.parseToJsonElement(request.jsonBodyText()).jsonObject
        assertEquals("restyle", obj.getValue("prompt").jsonPrimitive.content)
        assertTrue(request.jsonBodyText().contains("AQIDBA=="))
    }
}
