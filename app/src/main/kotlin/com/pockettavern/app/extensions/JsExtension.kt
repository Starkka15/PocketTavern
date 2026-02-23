package com.pockettavern.app.extensions

import java.io.File

/**
 * Represents an installed JavaScript extension loaded in the WebView sandbox.
 */
data class JsExtension(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val author: String = "",
    val sourceUrl: String = "",
    val enabled: Boolean = true,
    val scriptFile: File,
    val bundled: Boolean = false
)
