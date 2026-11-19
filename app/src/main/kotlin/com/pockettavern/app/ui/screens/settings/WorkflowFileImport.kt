package com.pockettavern.app.ui.screens.settings

/** Keep file-provider failures separate from backend-specific workflow validation. */
internal data class SelectedWorkflowFile(val name: String, val content: String)

internal object WorkflowFileImport {
    fun read(
        readContent: () -> String?,
        readDisplayName: () -> String?
    ): Result<SelectedWorkflowFile> = runCatching {
        val content = readContent()
            ?: error("The selected document could not be opened")
        // A broken metadata lookup must not prevent importing otherwise valid content.
        val name = runCatching { readDisplayName() }.getOrNull()
            ?.trim()?.takeIf { it.isNotEmpty() } ?: "workflow.json"
        SelectedWorkflowFile(name, content)
    }
}
