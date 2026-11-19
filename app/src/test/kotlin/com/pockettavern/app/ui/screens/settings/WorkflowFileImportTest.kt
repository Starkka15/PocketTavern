package com.pockettavern.app.ui.screens.settings

import com.pockettavern.app.data.remote.imagegen.WorkflowImportValidator
import com.pockettavern.app.domain.model.ImageGenBackendType
import org.junit.Assert.*
import org.junit.Test

class WorkflowFileImportTest {
    @Test fun usesProviderDisplayNameInsteadOfDocumentIdentifier() {
        val selected = WorkflowFileImport.read(
            readContent = { """{"prompt":"{{prompt}}"}""" },
            readDisplayName = { " my-workflow.json " }
        ).getOrThrow()
        assertEquals("my-workflow.json", selected.name)
    }

    @Test fun missingDisplayNameFallsBackToGenericName() {
        val selected = WorkflowFileImport.read(
            readContent = { "body" },
            readDisplayName = { null }
        ).getOrThrow()
        assertEquals("workflow.json", selected.name)
    }

    @Test fun blankDisplayNameFallsBackToGenericName() {
        val selected = WorkflowFileImport.read(
            readContent = { "body" },
            readDisplayName = { "  " }
        ).getOrThrow()
        assertEquals("workflow.json", selected.name)
    }

    @Test fun providerMetadataFailureDoesNotBlockImport() {
        val selected = WorkflowFileImport.read(
            readContent = { "body" },
            readDisplayName = { throw SecurityException("Metadata unavailable") }
        ).getOrThrow()
        assertEquals("workflow.json", selected.name)
        assertEquals("body", selected.content)
    }

    @Test fun unreadableDocumentReturnsFailureInsteadOfSilentlyIgnoringIt() {
        val result = WorkflowFileImport.read(
            readContent = { throw java.io.IOException("No permission") },
            readDisplayName = { "workflow.json" }
        )
        assertTrue(result.isFailure)
    }

    @Test fun missingInputStreamReturnsFailure() {
        val result = WorkflowFileImport.read(
            readContent = { null },
            readDisplayName = { "workflow.json" }
        )
        assertTrue(result.isFailure)
    }

    @Test fun emptyDocumentIsPassedThroughForBackendSpecificValidation() {
        val selected = WorkflowFileImport.read(
            readContent = { "" },
            readDisplayName = { "empty.json" }
        ).getOrThrow()
        assertEquals("", selected.content)
        assertNotNull(WorkflowImportValidator.validate(selected.content, ImageGenBackendType.COMFYUI))
    }
}
