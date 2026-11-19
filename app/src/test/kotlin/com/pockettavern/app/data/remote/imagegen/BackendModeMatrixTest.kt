package com.pockettavern.app.data.remote.imagegen

import com.pockettavern.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** 8 backends x 4 mode/storage checks = 32 independent JUnit cases. */
@RunWith(Parameterized::class)
class BackendModeMatrixTest(private val backend: ImageGenBackendType) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} mode isolation")
        fun cases(): Collection<Array<Any>> =
            ImageGenBackendType.entries.map { arrayOf<Any>(it) }
    }

    private val params = ForgeGenerationParams(prompt = "test")
    private val config get() = ImageGenConfig(
        workflowModes = mapOf(backend.name to "CUSTOM"),
        customWorkflows = mapOf(backend.name to CustomImageWorkflowDefinition("template", "saved"))
    )

    @Test fun savedTemplateSelectedInCustomMode() {
        assertEquals("saved", CustomImageTemplate.selected(config, params, backend))
    }

    @Test fun standardOverrideDisablesSavedTemplate() {
        assertNull(CustomImageTemplate.selected(config,
            params.copy(workflowModeOverride = "STANDARD"), backend))
    }

    @Test fun customOverrideWinsOverSavedTemplate() {
        assertEquals("override", CustomImageTemplate.selected(config,
            params.copy(customWorkflowOverride = "override"), backend))
    }

    @Test fun otherBackendsDoNotInheritTemplate() {
        val other = ImageGenBackendType.entries.first { it != backend }
        assertNull(CustomImageTemplate.selected(config, params, other))
        assertEquals("STANDARD", config.workflowModeFor(other))
    }
}
