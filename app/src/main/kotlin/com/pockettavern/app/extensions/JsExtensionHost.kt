package com.pockettavern.app.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.pockettavern.app.data.local.JsExtensionStorage
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a hidden WebView sandbox for running JavaScript extensions.
 *
 * Extensions are loaded from [Context.filesDir]/js_extensions/{id}/index.js.
 * The PT global object (pt_api.js) is injected first so extensions have a stable API.
 *
 * Communication:
 *   Kotlin → JS  : [dispatchEvent] calls window.__ptDispatchEvent() via evaluateJavascript
 *   JS → Kotlin  : [PtJsBridge] @JavascriptInterface methods exposed as window.PtBridge
 */
@Singleton
class JsExtensionHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: JsExtensionStorage
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var webView: WebView? = null
    private var ready = false

    // Kotlin-side injection map populated synchronously by PtJsBridge.setPromptInjection()
    private val _injections = mutableMapOf<String, String>()

    // Context JSON updated before each generation by ExtensionManager.updateContext()
    @Volatile private var _contextJson: String = "{}"

    // ── Init / reload ─────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    fun init() {
        scope.launch {
            if (webView != null) return@launch  // already initialised
            val wv = WebView(context).also { webView = it }
            wv.settings.apply {
                javaScriptEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                allowUniversalAccessFromFileURLs = false
            }
            wv.addJavascriptInterface(PtJsBridge(), "PtBridge")
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    ready = true
                    loadExtensionScripts(view)
                }
            }
            wv.loadData("<html><body></body></html>", "text/html", "utf-8")
        }
    }

    /** Call after installing or uninstalling an extension to reload the sandbox. */
    fun reload() {
        ready = false
        _injections.clear()
        scope.launch {
            webView?.loadData("<html><body></body></html>", "text/html", "utf-8")
        }
    }

    // ── Context ───────────────────────────────────────────────────────────────

    fun updateContext(json: String) { _contextJson = json }

    // ── Events ────────────────────────────────────────────────────────────────

    fun dispatchEvent(event: ExtensionEvent, data: Any? = null) {
        if (!ready) return
        val safe = data?.toString()
            ?.replace("\\", "\\\\")
            ?.replace("\"", "\\\"") ?: ""
        val dataJson = if (data != null) "\"$safe\"" else "null"
        scope.launch {
            webView?.evaluateJavascript(
                "if(window.__ptDispatchEvent)__ptDispatchEvent('${event.name}',$dataJson);", null
            )
        }
    }

    // ── Prompt injections ─────────────────────────────────────────────────────

    fun getInjections(): List<String> = _injections.values.toList()

    // ── Private ───────────────────────────────────────────────────────────────

    private fun loadExtensionScripts(wv: WebView) {
        val apiJs = try {
            context.assets.open("extensions/pt_api.js").bufferedReader().readText()
        } catch (e: Exception) {
            DebugLogger.log("[JsExtensionHost] " + "Failed to read pt_api.js: ${e.message}")
            return
        }
        // Inject the PT API
        wv.evaluateJavascript(apiJs, null)

        // Restore persisted settings
        val settingsEscaped = storage.loadAllSettings()
            .replace("\\", "\\\\").replace("'", "\\'")
        wv.evaluateJavascript(
            "if(window.PT){try{Object.assign(PT.extension_settings,JSON.parse('$settingsEscaped'));}catch(e){}}",
            null
        )

        // Load each enabled extension
        val extensions = storage.listExtensions().filter { it.enabled }
        DebugLogger.log("[JsExtensionHost] " + "Loading ${extensions.size} JS extension(s)")
        extensions.forEach { ext ->
            try {
                wv.evaluateJavascript(ext.scriptFile.readText()) { result ->
                    if (result != null && result != "null")
                        DebugLogger.log("[JsExtensionHost] " + "Loaded '${ext.name}': $result")
                }
            } catch (e: Exception) {
                DebugLogger.log("[JsExtensionHost] " + "Error loading '${ext.name}': ${e.message}")
            }
        }
    }

    // ── JavascriptInterface bridge ────────────────────────────────────────────

    inner class PtJsBridge {

        /**
         * Called by PT.setExtensionPrompt() to register a prompt injection.
         * Stored on the Kotlin side so it's available synchronously at generation time.
         */
        @JavascriptInterface
        fun setPromptInjection(extensionId: String, text: String, position: Int, depth: Int) {
            if (text.isNotBlank()) _injections[extensionId] = text
            else _injections.remove(extensionId)
        }

        /** Called by PT.getContext(). */
        @JavascriptInterface
        fun getContext(): String = _contextJson

        /** Called by PT.saveSettings(). */
        @JavascriptInterface
        fun saveAllSettings(settingsJson: String) {
            storage.saveAllSettings(settingsJson)
        }

        /** Called by PT.log(). */
        @JavascriptInterface
        fun log(message: String) {
            DebugLogger.log("[JsExt] $message")
        }
    }
}
