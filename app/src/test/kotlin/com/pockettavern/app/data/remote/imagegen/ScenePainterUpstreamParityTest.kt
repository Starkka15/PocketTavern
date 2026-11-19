package com.pockettavern.app.data.remote.imagegen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression checks against the original Scene Painter actions in the
 * official PocketTavern testing/v2.3.8 release. This guards against
 * accidentally removing the "Send this scene" action during a merge.
 */
class ScenePainterUpstreamParityTest {
    private fun bundledScript(relativePath: String): String {
        val roots = listOf(
            File("src/main/assets/extensions"),
            File("app/src/main/assets/extensions")
        )
        val file = roots.map { File(it, relativePath) }.firstOrNull { it.isFile }
            ?: error("Bundled JS asset not found: $relativePath")
        return file.readText()
    }

    @Test fun officialMessageActionOrderIsPreservedForBothRegistrations() {
        val script = bundledScript("pt-scene-painter/index.js")
        val menu = listOf(
            "Send background image in chat",
            "Send a picture of yourself in chat",
            "Send this scene in chat",
            "Set art style..."
        )
        // The Scene Painter registers the same menu on initialization
        // and each time a message is long-pressed.
        menu.forEach { label -> assertEquals("Registration count of $label", 2,
            Regex(Regex.escape("label: '$label'")).findAll(script).count()) }
        val lines = script.lines().filter { it.contains("{ label: '") &&
            menu.any { item -> it.contains(item) } }
        assertEquals(8, lines.size)
        for (offset in listOf(0, 4)) {
            menu.forEachIndexed { i, label -> assertTrue(
                "Unexpected action position for $label", lines[offset + i].contains(label)
            ) }
        }
    }

    @Test fun sceneActionInvokesIndependentScenePrompt() {
        val script = bundledScript("pt-scene-painter/index.js")
        assertTrue(script.contains("data.action === 'sp_scene'"))
        assertTrue(script.contains("paintScene(_lastLongPressedIndex, 'scene')"))
        assertTrue(script.contains("mode === 'scene'"))
        assertTrue(script.contains("showing ' + charName + ' in this scene"))
        assertTrue(script.contains("full setting -- location, time of day"))
        // In contrast, backgrounds explicitly exclude people.
        assertTrue(script.contains("No characters or people"))
    }

    @Test fun multiLinePromptIsNotTruncatedToItsLastLine() {
        val script = bundledScript("pt-scene-painter/index.js")
        assertTrue(script.contains("tagLines.length ? tagLines : lines"))
        assertTrue(script.contains("join(', ')"))
        assertTrue(!script.contains("sdPrompt = lines[lines.length - 1]"))
    }

    @Test fun sceneImg2ImgUsesUpstreamDefaultDenoising() {
        val script = bundledScript("pt-scene-painter/index.js")
        assertTrue(script.contains("sceneImg2Img:      true"))
        assertTrue(script.contains("sceneDenoising:    0.72"))
        assertTrue(script.contains("getImageBackendCapabilities()"))
        assertTrue(script.contains("options.sourceImageBase64 = avatarBase64"))
        assertTrue(script.contains("PT.generateImage(sdPrompt, options)"))
    }

    @Test fun nativeJsCapabilityBridgeIsPresent() {
        val api = bundledScript("pt_api.js")
        assertTrue(api.contains("getImageBackendCapabilities: function ()"))
        assertTrue(api.contains("PtBridge.getImageBackendCapabilities()"))
    }
}
