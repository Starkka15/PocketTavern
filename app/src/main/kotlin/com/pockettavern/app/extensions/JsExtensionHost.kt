package com.pockettavern.app.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.pockettavern.app.data.local.JsExtensionStorage
import com.pockettavern.app.domain.model.MessageHeaderEntry
import com.pockettavern.app.domain.model.QuickReplyButton
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    // Message headers: messageIndex → list of headers (multiple extensions can each set one)
    private val _messageHeaders = MutableStateFlow<Map<Int, List<MessageHeaderEntry>>>(emptyMap())
    val messageHeaders: StateFlow<Map<Int, List<MessageHeaderEntry>>> = _messageHeaders.asStateFlow()

    // Buttons registered by JS extensions: extensionId → button list
    private val _jsButtonSets = MutableStateFlow<Map<String, List<QuickReplyButton>>>(emptyMap())
    val jsButtonSets: StateFlow<Map<String, List<QuickReplyButton>>> = _jsButtonSets.asStateFlow()

    // Inline header buttons: extensionId → list of actions rendered inside the header box
    data class HeaderAction(val label: String, val action: String)
    private val _headerButtons = MutableStateFlow<Map<String, List<HeaderAction>>>(emptyMap())
    val headerButtons: StateFlow<Map<String, List<HeaderAction>>> = _headerButtons.asStateFlow()

    // Header context menus: extensionId → list of actions shown as a popup on long-press
    private val _headerMenus = MutableStateFlow<Map<String, List<HeaderAction>>>(emptyMap())
    val headerMenus: StateFlow<Map<String, List<HeaderAction>>> = _headerMenus.asStateFlow()

    // Message context menu actions: extensionId → list of actions shown in message long-press menu
    private val _messageActions = MutableStateFlow<Map<String, List<HeaderAction>>>(emptyMap())
    val messageActions: StateFlow<Map<String, List<HeaderAction>>> = _messageActions.asStateFlow()

    // Output filters registered by JS extensions: extensionId → regex pattern
    private val _outputFilters = mutableMapOf<String, Regex>()

    // Per-character disabled extensions list (updated when character changes)
    @Volatile private var _disabledExtensions: Set<String> = emptySet()

    // Callback wired by ChatViewModel so JS can send messages as the user
    var sendMessageCallback: ((String) -> Unit)? = null

    // Edit dialog request: JS calls PT.showEditDialog() → Kotlin shows a native dialog
    data class EditDialogRequest(
        val title: String,
        val fields: List<EditField>,
        val callbackId: String
    )
    data class EditField(val key: String, val label: String, val value: String)
    private val _editDialogRequest = MutableStateFlow<EditDialogRequest?>(null)
    val editDialogRequest: StateFlow<EditDialogRequest?> = _editDialogRequest.asStateFlow()

    // Hidden generate request: JS calls PT.generateHidden() → Kotlin sends to LLM without adding to chat
    var hiddenGenerateCallback: ((String, String) -> Unit)? = null  // (prompt, callbackId) -> Unit

    // Image generate request: JS calls PT.generateImage() → Kotlin runs image gen pipeline
    var imageGenerateCallback: ((String, String, String) -> Unit)? = null  // (prompt, optionsJson, callbackId) -> Unit

    // Insert message request: JS calls PT.insertMessage() → Kotlin inserts a non-LLM message into chat
    var insertMessageCallback: ((String, String) -> Unit)? = null  // (content, optionsJson) -> Unit

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
        _headerButtons.value = emptyMap()
        _headerMenus.value = emptyMap()
        _messageActions.value = emptyMap()
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
    fun clearMessageHeaders() {
        _messageHeaders.value = emptyMap()
    }

    /** Replace the entire message headers map (e.g. after index shifts from deleting a message). */
    fun replaceMessageHeaders(headers: Map<Int, List<MessageHeaderEntry>>) {
        _messageHeaders.value = headers
    }

    /**
     * Restore persisted message headers when loading a chat from disk.
     *
     * Deferred via a queued no-op JS eval so that any CHAT_CHANGED handlers
     * (which typically call PT.clearAllHeaders()) execute first.  The callback
     * fires on Main after all previously queued evaluateJavascript calls —
     * including bridge calls like clearAllHeaders() — have completed.
     */
    fun restoreMessageHeaders(headers: Map<Int, List<MessageHeaderEntry>>) {
        if (headers.isEmpty()) return
        if (!ready || webView == null) {
            _messageHeaders.value = headers
            return
        }
        scope.launch {
            webView?.evaluateJavascript("0") { _ ->
                DebugLogger.log("[JsExtensionHost] Restoring headers for ${headers.size} message(s) after JS event queue drained")
                _messageHeaders.value = headers
            }
        }
    }

    /** Apply all registered output filters to strip extension metadata from displayed text. */
    fun applyOutputFilters(text: String): String {
        if (_outputFilters.isEmpty()) return text
        var result = text
        _outputFilters.forEach { (extId, regex) ->
            if (extId !in _disabledExtensions) {
                result = regex.replace(result, "")
            }
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

    // ── Per-character filtering ──────────────────────────────────────────────

    /**
     * Update the list of disabled extensions for the current character.
     * Pushes the list to the JS sandbox so event handlers are filtered.
     */
    fun updateDisabledExtensions(disabledIds: List<String>) {
        _disabledExtensions = disabledIds.toSet()
        if (!ready) return
        val jsArray = disabledIds.joinToString(",") { "'${it.replace("'", "\\'")}'" }
        scope.launch {
            webView?.evaluateJavascript(
                "window.__ptDisabledExtensions=[$jsArray];", null
            )
        }
        DebugLogger.log("[JsExtensionHost] Disabled extensions: $disabledIds")
    }

    // ── Prompt injections ─────────────────────────────────────────────────────

    fun getInjections(): List<String> = _injections
        .filter { it.key !in _disabledExtensions }
        .values.toList()

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

        // Load bundled extensions from assets (before user-installed ones)
        val bundledIds = mutableSetOf<String>()
        try {
            val extDirs = context.assets.list("extensions") ?: emptyArray()
            for (dirName in extDirs) {
                if (dirName == "pt_api.js") continue // skip the API file
                try {
                    val script = context.assets.open("extensions/$dirName/index.js")
                        .bufferedReader().readText()
                    bundledIds.add(dirName)
                    DebugLogger.log("[JsExtensionHost] Loading bundled '$dirName' (${script.length} chars)")
                    wv.evaluateJavascript("window.__ptCurrentExtId='$dirName';", null)
                    wv.evaluateJavascript(script) { result ->
                        DebugLogger.log("[JsExtensionHost] Bundled '$dirName' result: $result")
                    }
                    wv.evaluateJavascript("window.__ptCurrentExtId=null;", null)
                } catch (_: Exception) {
                    // Not a directory with index.js, skip
                }
            }
        } catch (e: Exception) {
            DebugLogger.log("[JsExtensionHost] Error scanning bundled extensions: ${e.message}")
        }

        // Load each enabled user-installed extension (skip if bundled version already loaded)
        val extensions = storage.listExtensions().filter { it.enabled && it.id !in bundledIds }
        DebugLogger.log("[JsExtensionHost] " + "Loading ${extensions.size} user JS extension(s)")
        extensions.forEach { ext ->
            try {
                val script = ext.scriptFile.readText()
                DebugLogger.log("[JsExtensionHost] Loading '${ext.name}' (${script.length} chars)")
                // Tag event handlers registered during this script with the extension's ID
                wv.evaluateJavascript("window.__ptCurrentExtId='${ext.id}';", null)
                wv.evaluateJavascript(script) { result ->
                    DebugLogger.log("[JsExtensionHost] '${ext.name}' result: $result")
                }
                wv.evaluateJavascript("window.__ptCurrentExtId=null;", null)
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
         * Called by PT.setMessageHeader(index, text, extensionId, collapsibleText).
         * Each extension gets its own header entry per message.
         * Pass empty text to remove this extension's header at that index.
         * collapsibleText is optional — shown/hidden on tap in the header UI.
         */
        @JavascriptInterface
        @JvmOverloads
        fun setMessageHeader(messageIndex: Int, text: String, extensionId: String, collapsibleText: String = "") {
            _messageHeaders.update { current ->
                val list = current[messageIndex]?.toMutableList() ?: mutableListOf()
                // Remove any existing entry for this extension
                list.removeAll { entry -> entry.extensionId == extensionId }
                if (text.isNotBlank()) {
                    list.add(MessageHeaderEntry(text = text, extensionId = extensionId, collapsibleText = collapsibleText))
                }
                val updated = current.toMutableMap()
                if (list.isEmpty()) updated.remove(messageIndex) else updated[messageIndex] = list.toList()
                updated
            }
        }

        /** Called by PT.clearMessageHeader(index). Removes ALL extension headers at this index. */
        @JavascriptInterface
        fun clearMessageHeader(messageIndex: Int) {
            _messageHeaders.update { current -> current - messageIndex }
        }

        /** Called by PT.clearAllHeaders(). */
        @JavascriptInterface
        fun clearAllHeaders() {
            _messageHeaders.value = emptyMap()
        }

        /**
         * Called by PT.getMessageHeaders(messageIndex).
         * Returns a JSON array of header entries for the given message:
         * [{"text":"...","extensionId":"..."}]
         * Returns "[]" if no headers exist for that index.
         */
        @JavascriptInterface
        fun getMessageHeaders(messageIndex: Int): String {
            val entries = _messageHeaders.value[messageIndex] ?: return "[]"
            val arr = org.json.JSONArray()
            entries.forEach { entry ->
                val obj = org.json.JSONObject()
                obj.put("text", entry.text)
                obj.put("extensionId", entry.extensionId)
                if (entry.collapsibleText.isNotBlank()) {
                    obj.put("collapsibleText", entry.collapsibleText)
                }
                arr.put(obj)
            }
            return arr.toString()
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
                        message = obj.optString("message", ""),
                        action  = obj.optString("action", "")
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

        // ── Header buttons & menus ──────────────────────────────────────────

        /**
         * Called by PT.registerHeaderButtons(extensionId, buttonsJson).
         * Registers inline buttons rendered inside the header box.
         * Long-press toggles their visibility.
         */
        @JavascriptInterface
        fun registerHeaderButtons(extensionId: String, buttonsJson: String) {
            try {
                val arr = JSONArray(buttonsJson)
                val buttons = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    HeaderAction(
                        label  = obj.optString("label", "?"),
                        action = obj.optString("action", "")
                    )
                }
                _headerButtons.value = _headerButtons.value + (extensionId to buttons)
            } catch (e: Exception) {
                DebugLogger.log("[JsExt] registerHeaderButtons parse error: ${e.message}")
            }
        }

        /** Called by PT.clearHeaderButtons(extensionId). */
        @JavascriptInterface
        fun clearHeaderButtons(extensionId: String) {
            _headerButtons.value = _headerButtons.value - extensionId
        }

        /**
         * Called by PT.registerHeaderMenu(extensionId, menuJson).
         * Pre-registers a context menu shown as a popup on header long-press.
         */
        @JavascriptInterface
        fun registerHeaderMenu(extensionId: String, menuJson: String) {
            try {
                val arr = JSONArray(menuJson)
                val items = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    HeaderAction(
                        label  = obj.optString("label", "?"),
                        action = obj.optString("action", "")
                    )
                }
                _headerMenus.value = _headerMenus.value + (extensionId to items)
            } catch (e: Exception) {
                DebugLogger.log("[JsExt] registerHeaderMenu parse error: ${e.message}")
            }
        }

        /** Called by PT.clearHeaderMenu(extensionId). */
        @JavascriptInterface
        fun clearHeaderMenu(extensionId: String) {
            _headerMenus.value = _headerMenus.value - extensionId
        }

        // ── Message context menu actions ─────────────────────────────────────

        /**
         * Called by PT.registerMessageActions(extensionId, actionsJson).
         * Registers actions that appear in the message long-press context menu.
         * Clicking an action dispatches BUTTON_CLICKED with { action, label }.
         */
        @JavascriptInterface
        fun registerMessageActions(extensionId: String, actionsJson: String) {
            try {
                val arr = JSONArray(actionsJson)
                val actions = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    HeaderAction(
                        label  = obj.optString("label", "?"),
                        action = obj.optString("action", "")
                    )
                }
                _messageActions.value = _messageActions.value + (extensionId to actions)
            } catch (e: Exception) {
                DebugLogger.log("[JsExt] registerMessageActions parse error: ${e.message}")
            }
        }

        /** Called by PT.clearMessageActions(extensionId). */
        @JavascriptInterface
        fun clearMessageActions(extensionId: String) {
            _messageActions.value = _messageActions.value - extensionId
        }

        /**
         * Called by PT.showEditDialog(title, fieldsJson, callbackId).
         * Shows a native Android dialog with editable text fields.
         * Results are returned to JS via __ptEditDialogResult(callbackId, resultsJson).
         */
        @JavascriptInterface
        fun showEditDialog(title: String, fieldsJson: String, callbackId: String) {
            try {
                val arr = JSONArray(fieldsJson)
                val fields = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    EditField(
                        key   = obj.optString("key", "field$i"),
                        label = obj.optString("label", "Field ${i + 1}"),
                        value = obj.optString("value", "")
                    )
                }
                _editDialogRequest.value = EditDialogRequest(title, fields, callbackId)
            } catch (e: Exception) {
                DebugLogger.log("[JsExt] showEditDialog parse error: ${e.message}")
            }
        }

        /**
         * Called by PT.generateHidden(prompt, callbackId).
         * Sends a prompt to the LLM without adding messages to the chat.
         * Result is returned to JS via __ptHiddenGenerateResult(callbackId, text).
         */
        @JavascriptInterface
        fun generateHidden(prompt: String, callbackId: String) {
            if (prompt.isBlank()) return
            scope.launch(Dispatchers.Main) {
                hiddenGenerateCallback?.invoke(prompt, callbackId)
            }
        }

        /**
         * Called by PT.generateImage(prompt, optionsJson, callbackId).
         * Triggers the app's image generation pipeline and returns base64 via callback.
         */
        @JavascriptInterface
        fun generateImage(prompt: String, optionsJson: String, callbackId: String) {
            if (prompt.isBlank()) return
            scope.launch(Dispatchers.Main) {
                imageGenerateCallback?.invoke(prompt, optionsJson, callbackId)
            }
        }

        /**
         * Called by PT.insertMessage(content, optionsJson).
         * Inserts a non-user, non-LLM message into the chat (narrator or image).
         */
        @JavascriptInterface
        fun insertMessage(content: String, optionsJson: String) {
            scope.launch(Dispatchers.Main) {
                insertMessageCallback?.invoke(content, optionsJson)
            }
        }
    }

    /** Called by ChatViewModel after the user submits the edit dialog. */
    fun completeEditDialog(callbackId: String, results: Map<String, String>) {
        _editDialogRequest.value = null
        val json = JSONObject(results).toString()
            .replace("\\", "\\\\").replace("'", "\\'")
        scope.launch {
            webView?.evaluateJavascript(
                "if(window.__ptEditDialogResult)__ptEditDialogResult('$callbackId',$json);", null
            )
        }
    }

    /** Called by ChatViewModel when the user cancels the edit dialog. */
    fun cancelEditDialog() {
        val callbackId = _editDialogRequest.value?.callbackId
        _editDialogRequest.value = null
        if (callbackId != null) {
            scope.launch {
                webView?.evaluateJavascript(
                    "if(window.__ptEditDialogResult)__ptEditDialogResult('$callbackId',null);", null
                )
            }
        }
    }

    /** Called by ChatViewModel after a hidden generate completes. */
    fun completeHiddenGenerate(callbackId: String, resultText: String) {
        val safe = resultText.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        scope.launch {
            webView?.evaluateJavascript(
                "if(window.__ptHiddenGenerateResult)__ptHiddenGenerateResult('$callbackId','$safe');", null
            )
        }
    }

    /** Called by ChatViewModel after image generation completes (or fails). */
    fun completeImageGenerate(callbackId: String, base64: String) {
        val safe = base64.replace("'", "\\'").replace("\n", "\\n")
        scope.launch {
            webView?.evaluateJavascript(
                "if(window.__ptImageGenerateResult)__ptImageGenerateResult('$callbackId','$safe');", null
            )
        }
    }
}
