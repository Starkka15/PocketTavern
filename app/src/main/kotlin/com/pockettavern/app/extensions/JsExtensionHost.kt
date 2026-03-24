package com.pockettavern.app.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.pockettavern.app.data.local.JsExtensionStorage
import com.pockettavern.app.domain.model.MessageHeaderEntry
import com.pockettavern.app.domain.model.QuickReplyButton
import com.pockettavern.app.util.DebugLogger
import com.pockettavern.app.util.FaceExtractor
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

    // Current character file (e.g. "seraphina.png") — set by ExtensionManager.updateCharacterFilter()
    @Volatile private var _currentCharacterFile: String = ""

    // Character settings field definitions registered by JS extensions
    data class CharSettingField(
        val key: String,
        val label: String,
        val type: String,           // "text", "toggle", "select"
        val default: String = "",   // default value (string for all types; "true"/"false" for toggle)
        val options: List<SelectOption>? = null,  // static options for select type
        val source: String? = null  // dynamic source key: "sd_samplers", "sd_models", "sd_styles", etc.
    )
    data class SelectOption(val label: String, val value: String)

    private val _charSettingsDefs = MutableStateFlow<Map<String, List<CharSettingField>>>(emptyMap())
    val charSettingsDefs: StateFlow<Map<String, List<CharSettingField>>> = _charSettingsDefs.asStateFlow()

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

    // Character import: JS calls PT.importCharacter() / PT.importCharacterFromBase64()
    var importCharacterCallback: ((String, String, String) -> Unit)? = null         // (url, filename, callbackId)
    var importCharacterBase64Callback: ((String, String, String) -> Unit)? = null   // (base64, filename, callbackId)

    // Toast: JS calls PT._showToast()
    var showToastCallback: ((String, String) -> Unit)? = null  // (type, message)

    // Panel registrations: extensionId → PanelRegistration
    /**
     * @param extensionDir  Set for file-based extensions; ExtensionPanelScreen serves all files
     *                      from this directory via shouldInterceptRequest.
     * @param entryPoint    Relative path within extensionDir to load first (e.g. "browser.html",
     *                      "app/library.html"). Ignored when extensionDir is null.
     */
    data class PanelRegistration(
        val extensionId: String,
        val title: String,
        val html: String,
        val css: String,
        val extensionDir: java.io.File? = null,
        val entryPoint: String = "browser.html",
        val urlParams: String = ""
    )
    private val _panelRegistrations = MutableStateFlow<Map<String, PanelRegistration>>(emptyMap())
    val panelRegistrations: StateFlow<Map<String, PanelRegistration>> = _panelRegistrations.asStateFlow()

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

    /** Update the current character file for per-character settings lookups. */
    fun updateCurrentCharacterFile(characterFile: String) {
        _currentCharacterFile = characterFile
    }

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

    /**
     * Strip ES module import/export syntax so ST extensions written as ES modules
     * can run in the evaluateJavascript context (which is non-module).
     *
     * All imported names are already available as globals via st_compat.js, so
     * removing the import declarations is sufficient.
     *
     * Handles:
     *   import { a, b } from '...';
     *   import defaultExport from '...';
     *   import * as ns from '...';
     *   import '...';                          (side-effect imports)
     *   Multi-line imports spanning several lines
     *   export default ...
     *   export { a, b };
     *   export const/let/var/function/class/async function
     */
    /**
     * Scans an extension directory for a standalone web panel entry point by cross-referencing
     * HTML files on disk with references inside index.js.  Works generically for any extension
     * that constructs its panel URL from its own directory (e.g. via import.meta.url or by
     * scanning <script> tags) — no hardcoded filenames required.
     *
     * Algorithm:
     *  1. Collect every .html file in the extension root and one level of subdirectories.
     *  2. Keep only those whose filename (or subdir/filename path) appears in the script text.
     *  3. Return the best candidate (prefer root-level files, then subdirectory files).
     *
     * Returns a relative path like "browser.html" or "app/library.html", or null if the
     * extension has no web panel (pure chat-plugin extensions have no .html files at all).
     */
    private fun detectPanelEntryPoint(extDir: java.io.File, scriptContent: String): String? {
        // Collect .html files: root level first, then one subdirectory level
        val candidates = mutableListOf<String>()
        extDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("html", ignoreCase = true) }
            ?.forEach { candidates.add(it.name) }
        extDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.forEach { subdir ->
                subdir.listFiles()
                    ?.filter { it.isFile && it.extension.equals("html", ignoreCase = true) }
                    ?.forEach { candidates.add("${subdir.name}/${it.name}") }
            }

        if (candidates.isEmpty()) return null

        // Filter to candidates whose filename is actually referenced in the script.
        // CharacterLibrary: `app/library.html` appears in a template literal.
        // BotBrowser:       `'browser.html'` appears as a string literal.
        val referenced = candidates.filter { rel ->
            val filename = rel.substringAfterLast('/')
            scriptContent.contains(filename)
        }

        // Prefer root-level entry over subdirectory entry; within same level prefer the
        // first alphabetical match so the result is deterministic.
        return (referenced.firstOrNull { '/' !in it }
            ?: referenced.firstOrNull())
            ?: candidates.firstOrNull { '/' !in it }   // fallback: any root .html
    }

    /**
     * Reads manifest.json from the extension directory and returns the display_name field,
     * or null if the manifest is absent or unparseable.
     */
    private fun readManifestDisplayName(extDir: java.io.File): String? {
        return try {
            val manifest = java.io.File(extDir, "manifest.json")
            if (!manifest.exists()) return null
            val json = org.json.JSONObject(manifest.readText())
            json.optString("display_name", "").takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    private fun stripEsModuleSyntax(script: String): String {
        var result = script

        // Multi-line import ... from '...' (handles braces spanning lines)
        result = result.replace(
            Regex("""^\s*import\s+(?:\{[^}]*\}|\*\s+as\s+\w+|\w+)(?:\s*,\s*(?:\{[^}]*\}|\*\s+as\s+\w+|\w+))*\s+from\s+['"][^'"]*['"]\s*;?\s*$""",
                setOf(RegexOption.MULTILINE)),
            ""
        )
        // Multi-line import with braces (fallback for complex cases)
        result = result.replace(
            Regex("""import\s*\{[^}]*\}\s*from\s*['"][^'"]*['"];?""",
                setOf(RegexOption.DOT_MATCHES_ALL)),
            ""
        )
        // Bare side-effect imports: import '...'
        result = result.replace(
            Regex("""^\s*import\s+['"][^'"]*['"]\s*;?\s*$""", setOf(RegexOption.MULTILINE)),
            ""
        )
        // export default (keep the value, just remove the keyword)
        result = result.replace(
            Regex("""^\s*export\s+default\s+""", setOf(RegexOption.MULTILINE)),
            ""
        )
        // export const/let/var/function/async function/class (keep the declaration)
        result = result.replace(
            Regex("""^\s*export\s+((?:async\s+)?(?:const|let|var|function|class)\s)""", setOf(RegexOption.MULTILINE)),
            "$1"
        )
        // export { ... };
        result = result.replace(
            Regex("""^\s*export\s*\{[^}]*\}\s*(?:from\s*['"][^'"]*['"])?\s*;?\s*$""", setOf(RegexOption.MULTILINE)),
            ""
        )

        // import.meta — replace with a stub using the per-extension URL injected before the script runs.
        // Must be done textually since 'import' is a reserved word and can't be assigned.
        result = result.replace("import.meta.url", "(window.__ptCurrentExtUrl||'https://pt-ext.local/index.js')")
        result = result.replace("import.meta.resolve", "function(s){return s;}")
        result = result.replace("import.meta", "({url:(window.__ptCurrentExtUrl||'https://pt-ext.local/index.js'),resolve:function(s){return s;}})")

        return result
    }

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

        // Inject bundled jQuery (if present) before the ST compat shim
        try {
            val jqueryJs = context.assets.open("extensions/jquery.min.js").bufferedReader().readText()
            wv.evaluateJavascript(jqueryJs, null)
        } catch (_: Exception) { /* not bundled, skip */ }

        // Inject ST compatibility shim (after pt_api.js + settings, before extensions)
        try {
            val compatJs = context.assets.open("extensions/st_compat.js").bufferedReader().readText()
            wv.evaluateJavascript(compatJs, null)
        } catch (_: Exception) { /* not present, skip */ }

        // Files in the extensions/ asset directory that are not themselves extension folders
        val assetFileSkipList = setOf("pt_api.js", "st_compat.js", "jquery.min.js")

        // Load bundled extensions from assets (before user-installed ones)
        val bundledIds = mutableSetOf<String>()
        try {
            val extDirs = context.assets.list("extensions") ?: emptyArray()
            for (dirName in extDirs) {
                if (dirName in assetFileSkipList) continue // skip non-extension files
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
                val extDir = ext.scriptFile.parentFile
                val extBaseUrl = "https://pt-ext.local/extensions/${ext.id}/"
                val rawScript = ext.scriptFile.readText()
                val script = if (rawScript.contains("import ") || rawScript.contains("export "))
                    stripEsModuleSyntax(rawScript) else rawScript
                DebugLogger.log("[JsExtensionHost] Loading '${ext.name}' (${script.length} chars)")
                // Tag event handlers and set base URL for import.meta.url stubs
                wv.evaluateJavascript("window.__ptCurrentExtId='${ext.id}';window.__ptCurrentExtUrl='${extBaseUrl}index.js';", null)
                wv.evaluateJavascript(script) { result ->
                    DebugLogger.log("[JsExtensionHost] '${ext.name}' result: $result")
                }
                wv.evaluateJavascript("window.__ptCurrentExtId=null;window.__ptCurrentExtUrl=null;", null)

                // Auto-register a panel for extensions with a standalone web UI.
                // Detects the entry point by scanning the extension dir for .html files
                // that are also referenced in index.js — no hardcoded filenames needed.
                if (extDir != null) {
                    val entryPoint = detectPanelEntryPoint(extDir, rawScript)
                    if (entryPoint != null) {
                        val title = readManifestDisplayName(extDir)
                            ?: ext.name
                                .replace(Regex("^sillytavern-", RegexOption.IGNORE_CASE), "")
                                .replace(Regex("-(?:master|main|latest)$", RegexOption.IGNORE_CASE), "")
                                .replace("-", " ").replace("_", " ")
                                .split(" ").filter { it.isNotEmpty() }
                                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                        val registration = PanelRegistration(
                            extensionId = ext.id,
                            title = title,
                            html = "",
                            css = "",
                            extensionDir = extDir,
                            entryPoint = entryPoint,
                            urlParams = if (entryPoint.contains("library.html")) "?embedded=1&csrf=pt-panel-noop" else "?csrf=pt-panel-noop"
                        )
                        _panelRegistrations.value = _panelRegistrations.value + (ext.id to registration)
                        DebugLogger.log("[JsExtensionHost] Auto-registered panel '$title' for '${ext.id}' (entry: $entryPoint)")
                    }
                }
            } catch (e: Exception) {
                DebugLogger.log("[JsExtensionHost] Error loading '${ext.name}': ${e.message}")
            }
        }

        // Fire ST lifecycle events so extensions waiting for APP_READY (e.g. BotBrowser) initialise.
        // Queued after all extension evals, so all handlers are already registered when this runs.
        wv.evaluateJavascript(
            "(function(){['SETTINGS_LOADED_BEFORE','SETTINGS_LOADED_AFTER','EXTENSION_SETTINGS_LOADED','APP_READY']" +
            ".forEach(function(e){if(window.__ptDispatchEvent)__ptDispatchEvent(e,null);});})();",
            null
        )
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

        // ── Character settings registration ─────────────────────────────────

        /**
         * Called by PT.registerCharacterSettings(extensionId, fieldsJson).
         * Registers per-character settings fields that render in Character Settings UI.
         */
        @JavascriptInterface
        fun registerCharacterSettings(extensionId: String, fieldsJson: String) {
            try {
                val arr = JSONArray(fieldsJson)
                val fields = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    val options = if (obj.has("options")) {
                        val optArr = obj.getJSONArray("options")
                        (0 until optArr.length()).map { j ->
                            val optObj = optArr.getJSONObject(j)
                            SelectOption(
                                label = optObj.optString("label", ""),
                                value = optObj.optString("value", "")
                            )
                        }
                    } else null
                    CharSettingField(
                        key = obj.optString("key", "field$i"),
                        label = obj.optString("label", "Field ${i + 1}"),
                        type = obj.optString("type", "text"),
                        default = obj.optString("default", ""),
                        options = options,
                        source = if (obj.has("source")) obj.optString("source") else null
                    )
                }
                _charSettingsDefs.value = _charSettingsDefs.value + (extensionId to fields)
                DebugLogger.log("[JsExt] Character settings registered for '$extensionId': ${fields.size} field(s)")
            } catch (e: Exception) {
                DebugLogger.log("[JsExt] registerCharacterSettings parse error: ${e.message}")
            }
        }

        /**
         * Called by PT.getCharacterSetting(key).
         * Returns the per-character value for the currently loaded extension's setting,
         * or the field's default if not set. Returns empty string if key not found.
         */
        @JavascriptInterface
        fun getCharacterSetting(key: String): String {
            val charFile = _currentCharacterFile
            // Find which extension registered this key
            val extId = _charSettingsDefs.value.entries.firstOrNull { (_, fields) ->
                fields.any { it.key == key }
            }?.key ?: return ""

            // Try per-character saved value first
            if (charFile.isNotBlank()) {
                val saved = storage.getCharacterExtensionSetting(charFile, extId, key)
                if (saved != null) return saved
            }

            // Fall back to field default
            val field = _charSettingsDefs.value[extId]?.find { it.key == key }
            return field?.default ?: ""
        }

        /**
         * Called by PT.getCharacterFace().
         * Loads the current character's avatar, detects and crops the face region,
         * and returns it as a base64 PNG string. Returns empty string if no face found.
         */
        @JavascriptInterface
        fun getCharacterFace(): String {
            val charFile = _currentCharacterFile
            if (charFile.isBlank()) return ""

            return try {
                val avatarFile = java.io.File(
                    java.io.File(context.filesDir, "characters"), charFile
                )
                if (!avatarFile.exists()) return ""

                val bitmap = BitmapFactory.decodeFile(avatarFile.absolutePath) ?: return ""
                val face = FaceExtractor.extractFace(bitmap) ?: return ""

                val stream = java.io.ByteArrayOutputStream()
                face.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            } catch (e: Exception) {
                DebugLogger.log("[JsExt] getCharacterFace error: ${e.message}")
                ""
            }
        }

        /**
         * Called by PT.getCharacterAvatar().
         * Returns the full avatar as a base64 PNG string.
         */
        @JavascriptInterface
        fun getCharacterAvatar(): String {
            val charFile = _currentCharacterFile
            if (charFile.isBlank()) return ""

            return try {
                val avatarFile = java.io.File(
                    java.io.File(context.filesDir, "characters"), charFile
                )
                if (!avatarFile.exists()) return ""

                Base64.encodeToString(avatarFile.readBytes(), Base64.NO_WRAP)
            } catch (e: Exception) {
                DebugLogger.log("[JsExt] getCharacterAvatar error: ${e.message}")
                ""
            }
        }

        // ── Character import ──────────────────────────────────────────────────

        /** Called by PT.importCharacter(url, filename). */
        @JavascriptInterface
        fun importCharacterFromUrl(url: String, filename: String, callbackId: String) {
            if (url.isBlank()) { deliverImportResult(callbackId, false); return }
            scope.launch(Dispatchers.Main) { importCharacterCallback?.invoke(url, filename, callbackId) }
        }

        /** Called by PT.importCharacterFromBase64(base64, filename). */
        @JavascriptInterface
        fun importCharacterFromBase64(base64: String, filename: String, callbackId: String) {
            if (base64.isBlank()) { deliverImportResult(callbackId, false); return }
            scope.launch(Dispatchers.Main) { importCharacterBase64Callback?.invoke(base64, filename, callbackId) }
        }

        // ── Toast ─────────────────────────────────────────────────────────────

        /** Called by PT._showToast(type, message). */
        @JavascriptInterface
        fun showToast(type: String, message: String) {
            scope.launch(Dispatchers.Main) { showToastCallback?.invoke(type, message) }
        }

        // ── Panel registration ────────────────────────────────────────────────

        /** Called by PT.registerPanel(config). */
        @JavascriptInterface
        fun registerPanel(extensionId: String, configJson: String) {
            try {
                val cfg = JSONObject(configJson)
                val reg = PanelRegistration(
                    extensionId = extensionId,
                    title       = cfg.optString("title", extensionId),
                    html        = cfg.optString("html", ""),
                    css         = cfg.optString("css", "")
                )
                _panelRegistrations.value = _panelRegistrations.value + (extensionId to reg)
            } catch (e: Exception) {
                DebugLogger.log("[JsExt] registerPanel parse error: ${e.message}")
            }
        }
    }

    /** Deliver a character import result back to the JS callback. */
    fun deliverImportResult(callbackId: String, success: Boolean) {
        scope.launch {
            webView?.evaluateJavascript(
                "if(window.__ptImportCharacterResult)__ptImportCharacterResult('$callbackId',$success);", null
            )
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
