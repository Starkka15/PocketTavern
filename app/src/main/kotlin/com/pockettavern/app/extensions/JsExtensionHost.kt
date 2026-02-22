package com.pockettavern.app.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.pockettavern.app.data.local.JsExtensionStorage
import com.pockettavern.app.domain.model.QuickReplyButton
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
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

    // Message headers: messageIndex → header text (set by JS via PT.setMessageHeader)
    private val _messageHeaders = MutableStateFlow<Map<Int, String>>(emptyMap())
    val messageHeaders: StateFlow<Map<Int, String>> = _messageHeaders.asStateFlow()

    // Buttons registered by JS extensions: extensionId → button list
    private val _jsButtonSets = MutableStateFlow<Map<String, List<QuickReplyButton>>>(emptyMap())
    val jsButtonSets: StateFlow<Map<String, List<QuickReplyButton>>> = _jsButtonSets.asStateFlow()

    // Output filters registered by JS extensions: extensionId → regex pattern
    private val _outputFilters = mutableMapOf<String, Regex>()

    // Callback wired by ChatViewModel so JS can send messages as the user
    var sendMessageCallback: ((String) -> Unit)? = null

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
            wv.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                    val level = msg.messageLevel()?.name ?: "INFO"
                    DebugLogger.log("[JsExt:$level] ${msg.message()} (line ${msg.lineNumber()})")
                    return true
                }
            }
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
        _outputFilters.clear()
        _messageHeaders.value = emptyMap()
        _jsButtonSets.value = emptyMap()
        scope.launch {
            webView?.loadData("<html><body></body></html>", "text/html", "utf-8")
        }
    }

    // ── Context ───────────────────────────────────────────────────────────────

    fun updateContext(json: String) { _contextJson = json }

    // ── Events ────────────────────────────────────────────────────────────────

    /** Dispatch an event with a plain string payload. */
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

    /**
     * Dispatch an event with a raw JSON payload (object/array, not a quoted string).
     * Used for structured events like MESSAGE_RECEIVED that carry { text, index }.
     */
    fun dispatchEventJson(event: ExtensionEvent, jsonData: String) {
        if (!ready) return
        scope.launch {
            webView?.evaluateJavascript(
                "if(window.__ptDispatchEvent)__ptDispatchEvent('${event.name}',$jsonData);", null
            )
        }
    }

    /** Clear all message headers without reloading the sandbox (e.g. on chat change). */
    fun clearMessageHeaders() { _messageHeaders.value = emptyMap() }

    /** Apply all registered output filters to strip extension metadata from displayed text. */
    fun applyOutputFilters(text: String): String {
        if (_outputFilters.isEmpty()) return text
        var result = text
        _outputFilters.values.forEach { regex ->
            result = regex.replace(result, "")
        }
        return result.trim()
    }

    /** Push a single setting change to the running JS sandbox without full reload. */
    fun updateSettingInSandbox(extensionId: String, key: String, jsonValue: String) {
        if (!ready) return
        val safeKey = key.replace("'", "\\'")
        scope.launch {
            webView?.evaluateJavascript(
                "if(window.PT&&PT.extension_settings['$extensionId']){PT.extension_settings['$extensionId']['$safeKey']=$jsonValue;}", null
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
                val script = ext.scriptFile.readText()
                DebugLogger.log("[JsExtensionHost] Loading '${ext.name}' (${script.length} chars)")
                wv.evaluateJavascript(script) { result ->
                    DebugLogger.log("[JsExtensionHost] '${ext.name}' result: $result")
                }
            } catch (e: Exception) {
                DebugLogger.log("[JsExtensionHost] Error loading '${ext.name}': ${e.message}")
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

        // ── New APIs ──────────────────────────────────────────────────────────

        /**
         * Called by PT.setMessageHeader(index, text).
         * Sets a header box that appears above the AI message at [messageIndex].
         * Pass empty string to clear it.
         */
        @JavascriptInterface
        fun setMessageHeader(messageIndex: Int, text: String) {
            val current = _messageHeaders.value.toMutableMap()
            if (text.isBlank()) current.remove(messageIndex)
            else current[messageIndex] = text
            _messageHeaders.value = current
        }

        /** Called by PT.clearMessageHeader(index). */
        @JavascriptInterface
        fun clearMessageHeader(messageIndex: Int) {
            _messageHeaders.value = _messageHeaders.value - messageIndex
        }

        /** Called by PT.clearAllHeaders(). */
        @JavascriptInterface
        fun clearAllHeaders() {
            _messageHeaders.value = emptyMap()
        }

        /**
         * Called by PT.registerButtons(id, buttons).
         * [buttonsJson] is a JSON array: [{"label":"...", "message":"..."}]
         */
        @JavascriptInterface
        fun registerButtons(extensionId: String, buttonsJson: String) {
            try {
                val arr = JSONArray(buttonsJson)
                val buttons = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    QuickReplyButton(
                        label   = obj.optString("label", "?"),
                        message = obj.optString("message", "")
                    )
                }
                _jsButtonSets.value = _jsButtonSets.value + (extensionId to buttons)
            } catch (e: Exception) {
                DebugLogger.log("[JsExt] registerButtons parse error: ${e.message}")
            }
        }

        /** Called by PT.clearButtons(id). */
        @JavascriptInterface
        fun clearButtons(extensionId: String) {
            _jsButtonSets.value = _jsButtonSets.value - extensionId
        }

        /**
         * Called by PT.sendMessage(text).
         * Sends a message as the user through the normal send pipeline.
         */
        @JavascriptInterface
        fun sendMessage(text: String) {
            if (text.isBlank()) return
            scope.launch(Dispatchers.Main) {
                sendMessageCallback?.invoke(text)
            }
        }

        /**
         * Called by PT.registerOutputFilter(id, pattern).
         * Registers a regex pattern to strip from displayed AI messages.
         */
        @JavascriptInterface
        fun registerOutputFilter(extensionId: String, pattern: String) {
            try {
                _outputFilters[extensionId] = Regex(pattern, setOf(RegexOption.IGNORE_CASE))
                DebugLogger.log("[JsExt] Output filter registered for '$extensionId': $pattern")
            } catch (e: Exception) {
                DebugLogger.log("[JsExt] Invalid output filter regex for '$extensionId': ${e.message}")
            }
        }

        /** Called by PT.clearOutputFilter(id). */
        @JavascriptInterface
        fun clearOutputFilter(extensionId: String) {
            _outputFilters.remove(extensionId)
        }
    }
}
