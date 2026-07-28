package com.pockettavern.app.ui.screens.extensions

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.pockettavern.app.util.DebugLogger
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionPanelScreen(
    extensionId: String,
    onBack: () -> Unit,
    viewModel: ExtensionPanelViewModel = hiltViewModel()
) {
    val panels by viewModel.extensionManager.panelRegistrations.collectAsState()
    val panel = panels[extensionId]
    val title = panel?.title ?: extensionId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (panel != null) {
                if (panel.extensionDir != null) {
                    BrowserHtmlPanel(
                        extensionDir = panel.extensionDir,
                        entryPoint = panel.entryPoint,
                        urlParams = panel.urlParams,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    InlineHtmlPanel(
                        html = panel.html,
                        css = panel.css,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ── Browser.html panel ────────────────────────────────────────────────────────

/**
 * Loads browser.html from the extension's directory via loadUrl("https://pt-ext.local/browser.html").
 * All resources are served from extensionDir via shouldInterceptRequest.
 * Using loadUrl (not loadDataWithBaseURL) ensures 100vh resolves to the real viewport height.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserHtmlPanel(
    extensionDir: File,
    entryPoint: String,
    urlParams: String = "",
    viewModel: ExtensionPanelViewModel,
    modifier: Modifier = Modifier
) {
    // Share data with shouldInterceptRequest (background thread) via AtomicReferences.
    val localCharactersJson by viewModel.localCharactersJson.collectAsState()
    val worldInfoListJson   by viewModel.worldInfoListJson.collectAsState()
    val tagsJson            by viewModel.tagsJson.collectAsState()
    val charsJsonRef     = remember { AtomicReference("[]") }
    val worldInfoListRef = remember { AtomicReference("[]") }
    val tagsRef          = remember { AtomicReference("[]") }
    LaunchedEffect(localCharactersJson) { charsJsonRef.set(localCharactersJson) }
    LaunchedEffect(worldInfoListJson)   { worldInfoListRef.set(worldInfoListJson) }
    LaunchedEffect(tagsJson)            { tagsRef.set(tagsJson) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).also { wv ->
                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                }

                // Bridge for JS→Kotlin calls: character lookups, file persistence, proxy, import.
                val bridge = PanelBridge(
                    webView        = wv,
                    charsRef       = charsJsonRef,
                    filesDir       = ctx.filesDir,
                    onGetChats     = { avatarUrl -> viewModel.getCharacterChatsJson(avatarUrl) },
                    onEditCharacter = { avatarUrl, charJson -> viewModel.editCharacterFromJson(avatarUrl, charJson) },
                    onImport       = { base64, filename, callbackId ->
                        viewModel.importCharacterFromBase64(base64, filename) { ok ->
                            val safe = callbackId.replace("'", "\\'")
                            wv.post {
                                wv.evaluateJavascript(
                                    "if(window.__ptPanelCbs&&window.__ptPanelCbs['$safe'])" +
                                    "{window.__ptPanelCbs['$safe']($ok);delete window.__ptPanelCbs['$safe'];}",
                                    null
                                )
                            }
                        }
                    }
                )
                wv.addJavascriptInterface(bridge, "PtPanelBridge")

                wv.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                        val level = msg.messageLevel()?.name ?: "INFO"
                        DebugLogger.log("[BotBrowser:$level] ${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})")
                        return true
                    }
                }

                wv.webViewClient = object : WebViewClient() {

                    /**
                     * Serve all extension files — including browser.html itself — from extensionDir.
                     * Using loadUrl() instead of loadDataWithBaseURL() ensures shouldInterceptRequest
                     * is called for every resource (main page + all module scripts/CSS/images).
                     */
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        val url = request.url.toString()
                        val base = "https://pt-ext.local/"
                        if (!url.startsWith(base)) return null

                        var rel = url.removePrefix(base).substringBefore("?").substringBefore("#")
                        if (rel.isEmpty() || rel == "/") rel = "browser.html"

                        // CORS headers for all responses.
                        val corsHeaders = mapOf(
                            "Access-Control-Allow-Origin" to "*",
                            "Cache-Control" to "no-store"
                        )
                        // JSON-specific headers — BotBrowser's n1() validates Content-Type
                        // via response.headers.get("content-type") before parsing the body.
                        val jsonHeaders = corsHeaders + mapOf(
                            "Content-Type" to "application/json; charset=UTF-8"
                        )

                        // Log all non-file requests for debugging.
                        if (rel.startsWith("api/") || rel.startsWith("user/") || rel == "csrf-token") {
                            DebugLogger.log("[Panel] ${request.method} $rel")
                        }

                        // Stub minimal SillyTavern endpoints.
                        if (rel == "csrf-token") {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, """{"token":"pt-panel-noop"}""".byteInputStream())
                        }

                        // BotBrowser plugin endpoints.
                        // Probe returns ok=true → BotBrowser uses POST /api/plugins/bot-browser/fetch
                        // for all card fetches instead of Puter.js CORS relays.
                        // /fetch and /api/characters/import are POST endpoints with bodies;
                        // they are handled by the JS fetch interceptor in PT_PANEL_BRIDGE_JS.
                        if (rel == "api/plugins/bot-browser/probe") {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, """{"ok":true}""".byteInputStream())
                        }
                        if (rel.startsWith("api/plugins/bot-browser/")) {
                            return WebResourceResponse("application/json", "UTF-8", 404, "Not Found",
                                jsonHeaders, """{"error":"not available"}""".byteInputStream())
                        }

                        // ── Characters ───────────────────────────────────────────────────────
                        if (rel == "api/characters" || rel == "api/characters/all") {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, charsJsonRef.get().byteInputStream())
                        }
                        // api/characters/get and api/characters/chats require the POST body
                        // (avatar_url). They are intercepted in PT_PANEL_BRIDGE_JS and dispatched
                        // to PanelBridge.getCharacterData / getCharacterChats.
                        // shouldInterceptRequest returns a fallback here for safety only.
                        if (rel == "api/characters/get") {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, """{"name":"","avatar":"","data":{"name":"","description":"","personality":"","scenario":"","first_mes":"","mes_example":"","creator_notes":"","tags":[],"alternate_greetings":[],"extensions":{}}}""".byteInputStream())
                        }
                        if (rel == "api/characters/chats") {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, "[]".byteInputStream())
                        }
                        // Character delete/rename: stubs — PT handles these via its own UI.
                        if (rel == "api/characters/delete" || rel == "api/characters/rename" ||
                            rel == "api/characters/duplicate") {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, """{"ok":true}""".byteInputStream())
                        }

                        // ── Settings ─────────────────────────────────────────────────────────
                        // settings field must be a stringified JSON string per ST protocol.
                        if (rel == "api/settings/get" || rel == "api/settings") {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, """{"ok":true,"settings":"{}"}""".byteInputStream())
                        }

                        // ── Tags ─────────────────────────────────────────────────────────────
                        if (rel == "api/tags" || rel == "api/tags/all" || rel == "api/tags/list") {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, tagsRef.get().byteInputStream())
                        }

                        // ── World Info / Lorebooks ────────────────────────────────────────────
                        if (rel == "api/worldinfo/list" || rel == "api/worldinfo") {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, worldInfoListRef.get().byteInputStream())
                        }
                        if (rel.startsWith("api/worldinfo")) {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, """{"entries":{},"ok":true}""".byteInputStream())
                        }

                        // ── Groups ───────────────────────────────────────────────────────────
                        if (rel.startsWith("api/groups")) {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, "[]".byteInputStream())
                        }

                        // ── Images ───────────────────────────────────────────────────────────
                        if (rel == "api/images/list") {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, """{"files":[]}""".byteInputStream())
                        }
                        if (rel == "api/images/folders") {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, "[]".byteInputStream())
                        }

                        // ── Version / ping ───────────────────────────────────────────────────
                        if (rel == "api/version" || rel == "version") {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, """{"version":"1.0.0","release":"PocketTavern","isLatest":true}""".byteInputStream())
                        }
                        if (rel == "api/ping" || rel == "ping") {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, """{"pong":true}""".byteInputStream())
                        }

                        // ── Users ────────────────────────────────────────────────────────────
                        if (rel.startsWith("api/users")) {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, """{"handle":"user","name":"User","avatar":"default-user.png","admin":true,"password":false}""".byteInputStream())
                        }

                        // ── Generic API catch-all ────────────────────────────────────────────
                        if (rel.startsWith("api/")) {
                            return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                jsonHeaders, """{"ok":true}""".byteInputStream())
                        }

                        // ── User files (extension data persistence) ──────────────────────────
                        // Writes are intercepted in PT_PANEL_BRIDGE_JS; reads served from disk.
                        if (rel.startsWith("user/files/")) {
                            val filename = rel.removePrefix("user/files/").substringBefore("?")
                            if (filename.isNotEmpty() && !filename.contains("..") && !filename.contains("/")) {
                                val dataFile = File(ctx.filesDir, "pt_ext_data/$filename")
                                if (dataFile.exists() && dataFile.isFile) {
                                    return WebResourceResponse("application/json", "UTF-8", 200, "OK",
                                        corsHeaders, dataFile.inputStream())
                                }
                            }
                            return WebResourceResponse("application/json", "UTF-8", 404, "Not Found",
                                corsHeaders, "null".byteInputStream())
                        }
                        if (rel.startsWith("user/")) {
                            return WebResourceResponse("application/json", "UTF-8", 404, "Not Found",
                                corsHeaders, "null".byteInputStream())
                        }

                        // Character avatar images for the local library display.
                        if (rel.startsWith("characters/")) {
                            val filename = rel.removePrefix("characters/").substringBefore("?")
                            if (!filename.contains("..") && !filename.contains("/")) {
                                val charFile = File(ctx.filesDir, "characters/$filename")
                                if (charFile.exists() && charFile.isFile) {
                                    val mime = when {
                                        filename.endsWith(".png", ignoreCase = true) -> "image/png"
                                        filename.endsWith(".jpg", ignoreCase = true) || filename.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                                        else -> "application/octet-stream"
                                    }
                                    return WebResourceResponse(mime, null, 200, "OK",
                                        corsHeaders, charFile.inputStream())
                                }
                            }
                        }

                        val file = File(extensionDir, rel)
                        if (!file.exists() || !file.isFile) return null
                        // Path traversal guard
                        if (!file.canonicalPath.startsWith(extensionDir.canonicalPath)) return null

                        val mime = when {
                            rel.endsWith(".js")   -> "text/javascript"
                            rel.endsWith(".css")  -> "text/css"
                            rel.endsWith(".html") -> "text/html"
                            rel.endsWith(".json") -> "application/json"
                            rel.endsWith(".png")  -> "image/png"
                            rel.endsWith(".jpg") || rel.endsWith(".jpeg") -> "image/jpeg"
                            rel.endsWith(".svg")  -> "image/svg+xml"
                            else                  -> "application/octet-stream"
                        }
                        return WebResourceResponse(mime, "UTF-8", 200, "OK",
                            corsHeaders, file.inputStream()
                        )
                    }

                    /** Inject the PT bridge after the page finishes loading. */
                    override fun onPageFinished(view: WebView, url: String) {
                        // Use inline style.setProperty('important') which beats everything —
                        // including runtime-injected !important stylesheets from BotBrowser/React.
                        // Re-applied at multiple delays to override post-mount React CSS changes.
                        view.evaluateJavascript("""
                            (function(){
                                function ptHFix(){
                                    var vh=window.innerHeight;
                                    if(!vh||vh<=0)return;
                                    var px=vh+'px';
                                    document.documentElement.style.setProperty('height',px,'important');
                                    document.documentElement.style.setProperty('overflow','hidden','important');
                                    if(document.body){
                                        document.body.style.setProperty('height',px,'important');
                                        document.body.style.setProperty('overflow','hidden','important');
                                        document.body.style.setProperty('margin','0','important');
                                    }
                                    var root=document.getElementById('root')||document.getElementById('app')
                                            ||(document.body&&document.body.firstElementChild);
                                    if(root&&root!==document.body){
                                        root.style.setProperty('height',px,'important');
                                        root.style.setProperty('overflow','hidden','important');
                                        // Also fix the first child container (e.g. BotBrowser's h-screen div)
                                        // so that h-screen/100vh uses our measured height, not a stale value.
                                        if(root.firstElementChild){
                                            root.firstElementChild.style.setProperty('height',px,'important');
                                        }
                                    }
                                }
                                ptHFix();
                                [150,400,900,1600,2600].forEach(function(t){setTimeout(ptHFix,t);});
                            })();
                        """.trimIndent(), null)
                        view.evaluateJavascript(PT_PANEL_BRIDGE_JS, null)
                        // DOM diagnostic: log root element state after React has had time to mount.
                        view.postDelayed({
                            view.evaluateJavascript("""
                                (function() {
                                    var w = window.innerWidth, h = window.innerHeight;
                                    var root = document.getElementById('root') || document.getElementById('app')
                                             || (document.body && document.body.firstElementChild);
                                    var rc = root ? getComputedStyle(root).height : 'n/a';
                                    var bc = document.body ? getComputedStyle(document.body).height : 'n/a';
                                    var hc = getComputedStyle(document.documentElement).height;
                                    var hi = document.documentElement.style.height;
                                    console.log('[PTDebug] viewport=' + w + 'x' + h + ' html.inline=' + hi + ' html.computed=' + hc + ' body.computed=' + bc + ' root.computed=' + rc);
                                    if (root) console.log('[PTDebug] root.id=' + (root.id||'(none)') + ' children=' + root.children.length + ' innerHTML.len=' + root.innerHTML.length);
                                    // Log first child (BotBrowser main container)
                                    if (root && root.firstElementChild) {
                                        var mc = root.firstElementChild;
                                        var mcs = getComputedStyle(mc);
                                        console.log('[PTDebug] mainDiv cls=' + (mc.className||'').slice(0,80) + ' h=' + mcs.height + ' display=' + mcs.display + ' overflow=' + mcs.overflow);
                                        // Log flex children heights
                                        for (var ci = 0; ci < Math.min(mc.children.length, 4); ci++) {
                                            var ch = mc.children[ci], chs = getComputedStyle(ch);
                                            console.log('[PTDebug] child[' + ci + '] tag=' + ch.tagName + ' cls=' + (ch.className||'').slice(0,60) + ' h=' + chs.height + ' display=' + chs.display + ' vis=' + chs.visibility + ' opacity=' + chs.opacity);
                                        }
                                    }
                                    var err = document.querySelector('[data-error],.error-boundary,.error-screen');
                                    if (err) console.log('[PTDebug] error el: ' + err.textContent.slice(0,120));
                                })();
                            """.trimIndent(), null)
                        }, 2000)
                    }
                }

                // loadUrl (not loadDataWithBaseURL) so that 100vh resolves to the real
                // viewport height. All resources are served via shouldInterceptRequest.
                wv.loadUrl("https://pt-ext.local/$entryPoint$urlParams")
            }
        }
    )
}

/**
 * Bridge injected into browser.html after page load.
 * - Replaces importCharacterFile() with a FileReader→Base64→PtPanelBridge path.
 * - Listens for botbrowser_import_request postMessages.
 */
private val PT_PANEL_BRIDGE_JS = """
(function() {
    if (window.__ptPanelBridgeInstalled) return;
    window.__ptPanelBridgeInstalled = true;
    window.__ptPanelCbs = {};

    // ── Global error capture ──────────────────────────────────────────────────
    window.onerror = function(msg, src, line, col, err) {
        console.log('[PTBridge:ONERROR] ' + msg + ' @ ' + (src||'?') + ':' + (line||'?'));
        return false;
    };
    window.addEventListener('unhandledrejection', function(e) {
        console.log('[PTBridge:UNHANDLED] ' + (e.reason && (e.reason.stack || e.reason.message || String(e.reason))));
    });

    // ── CORS proxy intercept ──────────────────────────────────────────────────
    // BotBrowser POSTs JSON to /api/plugins/bot-browser/fetch; shouldInterceptRequest
    // cannot read POST bodies, so we intercept window.fetch before it hits the network.
    var _origFetch = window.fetch;
    window.fetch = function(input, init) {
        var url = (typeof input === 'string') ? input : (input && input.url ? input.url : '');
        var method = (init && init.method) ? init.method.toUpperCase() : 'GET';
        if (url === '/api/plugins/bot-browser/fetch' ||
            url === 'https://pt-ext.local/api/plugins/bot-browser/fetch') {
            var bodyStr = '';
            if (init && init.body) {
                bodyStr = (typeof init.body === 'string') ? init.body : JSON.stringify(init.body);
            }
            var _fetchTargetUrl = '';
            try { _fetchTargetUrl = JSON.parse(bodyStr).url || '?'; } catch(e) {}
            console.log('[PTBridge] /api/plugins/bot-browser/fetch url=' + _fetchTargetUrl);
            return new Promise(function(resolve, reject) {
                var cid = 'bbf_' + Date.now() + '_' + Math.random().toString(36).slice(2);
                var _cbTargetUrl = _fetchTargetUrl;
                window.__ptPanelCbs[cid] = function(responseJson) {
                    try {
                        var r = JSON.parse(responseJson);
                        var bodyContent;
                        if (r.bodyType === 'chunked') {
                            // Large binary (e.g. PNG card) — reassemble from Kotlin cache in chunks.
                            var key = r.cacheKey;
                            var total = r.totalBytes || 0;
                            var CHUNK = 65535; // must be multiple of 3 → no mid-stream base64 padding
                            var b64Parts = [];
                            var offset = 0;
                            console.log('[PTBridge] proxyFetch chunked: key=' + key + ' total=' + total + ' ct=' + r.contentType + ' icf=' + typeof window.importCharacterFile);
                            while (offset < total) {
                                var chunk = PtPanelBridge.getProxyChunk(key, offset, CHUNK);
                                if (!chunk) break;
                                b64Parts.push(chunk);
                                offset += CHUNK;
                            }
                            var fullB64 = b64Parts.join('');
                            console.log('[PTBridge] proxyFetch reassembled b64len=' + fullB64.length);
                            var bstr = atob(fullB64);
                            var bytes = new Uint8Array(bstr.length);
                            for (var i = 0; i < bstr.length; i++) bytes[i] = bstr.charCodeAt(i);
                            bodyContent = bytes.buffer;
                        } else if (r.bodyType === 'base64') {
                            var bstr = atob(r.body || '');
                            var bytes = new Uint8Array(bstr.length);
                            for (var i = 0; i < bstr.length; i++) bytes[i] = bstr.charCodeAt(i);
                            bodyContent = bytes.buffer;
                        } else {
                            bodyContent = r.body || '';
                        }
                        resolve(new Response(bodyContent, {
                            status: r.status || 200,
                            statusText: r.statusText || 'OK',
                            headers: {'content-type': r.contentType || 'application/json'}
                        }));
                    } catch(e) {
                        console.log('[PTBridge] proxyFetch CB error: ' + e);
                        reject(e);
                    }
                };
                if (window.PtPanelBridge) {
                    PtPanelBridge.proxyFetch(bodyStr, cid);
                } else {
                    reject(new Error('PtPanelBridge not available'));
                }
            });
        }
        // Handle character import — BotBrowser POSTs FormData to /api/characters/import.
        // shouldInterceptRequest can't read POST bodies, so we intercept here.
        if (url === '/api/characters/import' ||
            url === 'https://pt-ext.local/api/characters/import') {
            var body = init && init.body;
            if (body instanceof FormData) {
                var file = body.get('avatar');
                var fname = body.get('preserved_name') || (file && file.name) || 'character.png';
                return new Promise(function(resolve, reject) {
                    if (!file) { reject(new Error('No avatar in FormData')); return; }
                    var reader = new FileReader();
                    reader.onload = function(e) {
                        var result = e.target.result;
                        var b64 = result.indexOf(',') >= 0 ? result.split(',')[1] : result;
                        var cid = 'imp_' + Date.now() + '_' + Math.random().toString(36).slice(2);
                        window.__ptPanelCbs[cid] = function(ok) {
                            resolve(new Response(
                                ok ? JSON.stringify({file_name: fname})
                                   : JSON.stringify({error: 'Import failed'}),
                                {status: ok ? 200 : 500,
                                 headers: {'content-type': 'application/json'}}
                            ));
                        };
                        if (window.PtPanelBridge) {
                            console.log('[PTBridge] calling importCharacterFromBase64 via FormData, fname=' + fname + ' b64len=' + b64.length);
                            PtPanelBridge.importCharacterFromBase64(b64, fname, cid);
                        } else {
                            reject(new Error('PtPanelBridge not available'));
                        }
                    };
                    reader.onerror = function() { reject(new Error('FileReader error')); };
                    reader.readAsDataURL(file);
                });
            } else {
                console.log('[PTBridge] /api/characters/import body is not FormData, skipping');
            }
        }

        // ── api/characters/get — POST with body {avatar_url:'file.png'} ──────────
        if (url === '/api/characters/get' ||
            url === 'https://pt-ext.local/api/characters/get') {
            var bodyStr = (init && init.body)
                ? (typeof init.body === 'string' ? init.body : JSON.stringify(init.body)) : '{}';
            var avatar = '';
            try { avatar = JSON.parse(bodyStr).avatar_url || JSON.parse(bodyStr).avatar || ''; } catch(e) {}
            return new Promise(function(resolve) {
                var cid = 'cget_' + Date.now() + '_' + Math.random().toString(36).slice(2);
                window.__ptPanelCbs[cid] = function(json) {
                    resolve(new Response(json, {status:200,headers:{'content-type':'application/json'}}));
                };
                if (window.PtPanelBridge && PtPanelBridge.getCharacterData) {
                    PtPanelBridge.getCharacterData(avatar, cid);
                } else {
                    resolve(new Response('{}', {status:404,headers:{'content-type':'application/json'}}));
                }
            });
        }

        // ── api/characters/chats — POST with body {avatar_url:'file.png'} ────────
        if (url === '/api/characters/chats' ||
            url === 'https://pt-ext.local/api/characters/chats') {
            var bodyStr = (init && init.body)
                ? (typeof init.body === 'string' ? init.body : JSON.stringify(init.body)) : '{}';
            var avatar = '';
            try { avatar = JSON.parse(bodyStr).avatar_url || JSON.parse(bodyStr).avatar || ''; } catch(e) {}
            return new Promise(function(resolve) {
                var cid = 'cchats_' + Date.now() + '_' + Math.random().toString(36).slice(2);
                window.__ptPanelCbs[cid] = function(json) {
                    resolve(new Response(json, {status:200,headers:{'content-type':'application/json'}}));
                };
                if (window.PtPanelBridge && PtPanelBridge.getCharacterChats) {
                    PtPanelBridge.getCharacterChats(avatar, cid);
                } else {
                    resolve(new Response('[]', {status:200,headers:{'content-type':'application/json'}}));
                }
            });
        }

        // ── user/files writes — PUT or POST to /user/files/:filename ─────────────
        var _ufw = url.match(/^(?:https:\/\/pt-ext\.local)?\/user\/files\/([^/?#]+)(\?.*)?$/);
        if (_ufw && init && (init.method === 'PUT' || init.method === 'POST')) {
            var _ufname = _ufw[1];
            var _ufbody = (init.body != null)
                ? (typeof init.body === 'string' ? init.body : JSON.stringify(init.body)) : '';
            if (window.PtPanelBridge && PtPanelBridge.writeUserFile) {
                PtPanelBridge.writeUserFile(_ufname, _ufbody);
            }
            return Promise.resolve(new Response('{"ok":true}', {status:200,headers:{'content-type':'application/json'}}));
        }

        // ── user/files/upload — POST {name, data:base64} ─────────────────────────
        if (url === '/user/files/upload' ||
            url === 'https://pt-ext.local/user/files/upload') {
            var bodyStr = (init && init.body)
                ? (typeof init.body === 'string' ? init.body : JSON.stringify(init.body)) : '{}';
            try {
                var req = JSON.parse(bodyStr);
                if (req.name && req.data && window.PtPanelBridge && PtPanelBridge.writeUserFileBase64) {
                    PtPanelBridge.writeUserFileBase64(req.name, req.data);
                }
            } catch(e) {}
            return Promise.resolve(new Response(JSON.stringify({path:'/user/files/'+(JSON.parse(bodyStr).name||'')}),
                {status:200,headers:{'content-type':'application/json'}}));
        }

        // ── api/characters/edit — POST with full character body ───────────────────
        if (url === '/api/characters/edit' ||
            url === 'https://pt-ext.local/api/characters/edit' ||
            url === '/api/characters/edit-attribute' ||
            url === 'https://pt-ext.local/api/characters/edit-attribute') {
            var bodyStr = (init && init.body)
                ? (typeof init.body === 'string' ? init.body : JSON.stringify(init.body)) : '{}';
            return new Promise(function(resolve) {
                var cid = 'cedit_' + Date.now() + '_' + Math.random().toString(36).slice(2);
                window.__ptPanelCbs[cid] = function(json) {
                    resolve(new Response(json, {status:200,headers:{'content-type':'application/json'}}));
                };
                if (window.PtPanelBridge && PtPanelBridge.editCharacter) {
                    PtPanelBridge.editCharacter(bodyStr, cid);
                } else {
                    resolve(new Response('{"ok":true}', {status:200,headers:{'content-type':'application/json'}}));
                }
            });
        }

        // ── api/content/importURL — POST {url:'...'} → proxy-fetch, return raw bytes ──
        if (url === '/api/content/importURL' ||
            url === 'https://pt-ext.local/api/content/importURL') {
            var bodyStr = (init && init.body)
                ? (typeof init.body === 'string' ? init.body : JSON.stringify(init.body)) : '{}';
            var targetUrl = '';
            try { targetUrl = JSON.parse(bodyStr).url || ''; } catch(e) {}
            if (!targetUrl) {
                return Promise.resolve(new Response('{"error":"no url"}',
                    {status:400, headers:{'content-type':'application/json'}}));
            }
            return new Promise(function(resolve, reject) {
                var cid = 'curl_' + Date.now() + '_' + Math.random().toString(36).slice(2);
                window.__ptPanelCbs[cid] = function(responseJson) {
                    try {
                        var r = JSON.parse(responseJson);
                        var bodyContent;
                        if (r.bodyType === 'chunked') {
                            var key = r.cacheKey, total = r.totalBytes || 0, CHUNK = 65535;
                            var parts = [], off = 0;
                            while (off < total) { var c = PtPanelBridge.getProxyChunk(key, off, CHUNK); if (!c) break; parts.push(c); off += CHUNK; }
                            var bstr = atob(parts.join('')); var bytes = new Uint8Array(bstr.length);
                            for (var i = 0; i < bstr.length; i++) bytes[i] = bstr.charCodeAt(i);
                            bodyContent = bytes.buffer;
                        } else if (r.bodyType === 'base64') {
                            var bstr = atob(r.body || '');
                            var bytes = new Uint8Array(bstr.length);
                            for (var i = 0; i < bstr.length; i++) bytes[i] = bstr.charCodeAt(i);
                            bodyContent = bytes.buffer;
                        } else {
                            bodyContent = r.body || '';
                        }
                        resolve(new Response(bodyContent, {
                            status: r.status || 200,
                            headers: {'content-type': r.contentType || 'application/octet-stream'}
                        }));
                    } catch(e) { reject(e); }
                };
                if (window.PtPanelBridge) {
                    PtPanelBridge.proxyFetch(
                        JSON.stringify({url:targetUrl,method:'GET',headers:{},body:'',bodyType:'json',timeoutMs:30000}),
                        cid);
                } else {
                    reject(new Error('PtPanelBridge not available'));
                }
            });
        }

        // ── Stub write-only ST endpoints ─────────────────────────────────────────
        var _stubPosts = ['/api/settings/save','/api/characters/delete',
            '/api/characters/rename','/api/characters/edit-attribute',
            '/api/worldinfo/edit','/api/worldinfo/delete','/api/worldinfo/import',
            '/api/groups/edit','/api/groups/delete',
            '/api/chats/save','/api/chats/delete','/api/chats/rename'];
        for (var _si = 0; _si < _stubPosts.length; _si++) {
            if (url === _stubPosts[_si] || url === 'https://pt-ext.local' + _stubPosts[_si]) {
                return Promise.resolve(new Response('{"ok":true,"result":"ok"}',
                    {status:200,headers:{'content-type':'application/json'}}));
            }
        }

        return _origFetch.apply(this, arguments);
    };

    function ptImport(file, preservedName) {
        return new Promise(function(resolve, reject) {
            if (!file) { reject(new Error('No file')); return; }
            console.log('[PTBridge] ptImport: reading file, size=' + (file.size || 0) + ' type=' + (file.type || '') + ' name=' + (file.name || ''));
            var reader = new FileReader();
            reader.onload = function(e) {
                var result = e.target.result;
                var b64 = result.indexOf(',') >= 0 ? result.split(',')[1] : result;
                var fname = preservedName || (file.name) || 'character.png';
                if (!fname.endsWith('.png') && !fname.endsWith('.json') && !fname.endsWith('.card')) fname += '.png';
                if (window.PtPanelBridge) {
                    var cid = 'bb_' + Date.now() + '_' + Math.random().toString(36).slice(2);
                    window.__ptPanelCbs[cid] = function(ok) { if (ok) resolve(true); else reject(new Error('Import failed')); };
                    console.log('[PTBridge] ptImport calling importCharacterFromBase64, fname=' + fname + ' b64len=' + b64.length);
                    PtPanelBridge.importCharacterFromBase64(b64, fname, cid);
                } else {
                    reject(new Error('PtPanelBridge not available'));
                }
            };
            reader.onerror = function() { reject(new Error('FileReader error')); };
            reader.readAsDataURL(file);
        });
    }

    // Stub importWorldInfo — BotBrowser calls this after fetching lorebook bytes via
    // /api/content/importURL. The file is a JSON blob in ST WorldInfo format.
    // We read it as base64 and hand it to PtPanelBridge.saveWorldInfo.
    if (!window.importWorldInfo) {
        window.importWorldInfo = function(file) {
            return new Promise(function(resolve, reject) {
                if (!file) { resolve(); return; }
                var reader = new FileReader();
                reader.onload = function(e) {
                    var result = e.target.result;
                    var b64 = result.indexOf(',') >= 0 ? result.split(',')[1] : result;
                    var fname = (file.name || 'lorebook.json').replace(/[^a-zA-Z0-9._\-]/g, '_');
                    if (!fname.endsWith('.json')) fname += '.json';
                    if (window.PtPanelBridge && PtPanelBridge.saveWorldInfo) {
                        PtPanelBridge.saveWorldInfo(b64, fname);
                    }
                    resolve();
                };
                reader.onerror = function() { resolve(); }; // non-fatal
                reader.readAsDataURL(file);
            });
        };
    }

    // Override importCharacterFile (used by BotBrowser detail modal direct imports).
    // Use defineProperty so we can detect if BotBrowser later overwrites it.
    var _ptImportFn = function(file, preservedName) {
        console.log('[PTBridge] importCharacterFile called, file=' + (file ? file.constructor.name : 'null') + ' name=' + (file && file.name) + ' preservedName=' + preservedName);
        return ptImport(file, preservedName);
    };
    try {
        Object.defineProperty(window, 'importCharacterFile', {
            get: function() { return _ptImportFn; },
            set: function(v) {
                console.log('[PTBridge] importCharacterFile OVERWRITTEN by BotBrowser! fn=' + (typeof v));
                _ptImportFn = v; // let BotBrowser set its own version
            },
            configurable: true
        });
    } catch(e) {
        window.importCharacterFile = _ptImportFn;
    }

    // Listen for postMessage-based imports (BotBrowser standalone bridge)
    window.addEventListener('message', function(e) {
        var d = e.data;
        if (!d || typeof d !== 'object') return;
        if (d.type === 'botbrowser_import_request') {
            var file = d.file;
            var name = d.preservedName || (file && file.name) || 'character.png';
            var rid = d.requestId || '';
            console.log('[PTBridge] botbrowser_import_request: file=' + (file ? file.constructor.name : 'null') + ' name=' + name + ' rid=' + rid);
            if (file instanceof File || file instanceof Blob) {
                ptImport(file, name).then(function() {
                    window.postMessage({type:'botbrowser_import_result',requestId:rid,ok:true,error:''},'*');
                }).catch(function(err) {
                    console.log('[PTBridge] import failed: ' + err);
                    window.postMessage({type:'botbrowser_import_result',requestId:rid,ok:false,error:String(err)},'*');
                });
            } else {
                console.log('[PTBridge] import_request skipped: file is not File/Blob, type=' + (file ? typeof file : 'null'));
            }
        }
    });
})();
""".trimIndent()

/** Receives calls from browser.html via JavascriptInterface. */
private class PanelBridge(
    private val webView: WebView,
    private val charsRef: AtomicReference<String>,
    private val filesDir: File,
    private val onGetChats: (avatarUrl: String) -> String,
    private val onEditCharacter: (avatarUrl: String, charJson: String) -> Boolean,
    private val onImport: (base64: String, filename: String, callbackId: String) -> Unit
) {
    // Cache for large binary proxy responses — avoids evaluateJavascript size limits.
    // keyed by callbackId; JS retrieves in 64KB chunks via getProxyChunk().
    private val proxyBinaryCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    @JavascriptInterface
    fun importCharacterFromBase64(base64: String, filename: String, callbackId: String) {
        onImport(base64, filename, callbackId)
    }

    /**
     * Look up a character by avatar filename and return its ST V2 JSON via callback.
     * Called from JS fetch intercept for POST /api/characters/get.
     */
    @JavascriptInterface
    fun getCharacterData(avatarUrl: String, callbackId: String) {
        val json = try {
            val arr = JSONArray(charsRef.get())
            var found: JSONObject? = null
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                val av  = c.optString("avatar", "")
                val nm  = c.optString("name", "")
                if (av == avatarUrl || nm == avatarUrl || "$nm.png" == avatarUrl) {
                    found = c; break
                }
            }
            found?.toString()
                ?: """{"name":"","avatar":"$avatarUrl","data":{"name":"","description":"","personality":"","scenario":"","first_mes":"","mes_example":"","creator_notes":"","tags":[],"alternate_greetings":[],"extensions":{}}}"""
        } catch (e: Exception) {
            """{"name":"","data":{"name":"","description":"","tags":[],"extensions":{}}}"""
        }
        jsCallback(callbackId, JSONObject.quote(json))
    }

    /**
     * Return the chat list for a character by avatar filename via callback.
     * Called from JS fetch intercept for POST /api/characters/chats.
     */
    @JavascriptInterface
    fun getCharacterChats(avatarUrl: String, callbackId: String) {
        Thread {
            val json = try { onGetChats(avatarUrl) } catch (e: Exception) { "[]" }
            jsCallback(callbackId, JSONObject.quote(json))
        }.start()
    }

    /**
     * Apply ST-format character edit JSON to PT's character storage.
     * Called from JS fetch intercept for POST /api/characters/edit.
     * bodyJson is the full request body: {avatar_url, ch_name, description, ...}
     */
    @JavascriptInterface
    fun editCharacter(bodyJson: String, callbackId: String) {
        Thread {
            val ok = try {
                val obj = JSONObject(bodyJson)
                val avatarUrl = obj.optString("avatar_url", obj.optString("avatar", ""))
                onEditCharacter(avatarUrl, bodyJson)
            } catch (e: Exception) {
                DebugLogger.log("[Panel] editCharacter failed: ${e.message}")
                false
            }
            val resp = if (ok) """{"ok":true}""" else """{"error":"edit failed"}"""
            jsCallback(callbackId, JSONObject.quote(resp))
        }.start()
    }

    /**
     * Persist a user data file (extension settings/indexes) to pt_ext_data/.
     * Called from JS fetch intercept for PUT/POST /user/files/:name.
     */
    @JavascriptInterface
    fun writeUserFile(filename: String, content: String) {
        try {
            val safe = filename.filter { it.isLetterOrDigit() || it in "._-" }.take(128)
            if (safe.isEmpty()) return
            val dir = File(filesDir, "pt_ext_data")
            if (!dir.exists()) dir.mkdirs()
            File(dir, safe).writeText(content, Charsets.UTF_8)
            DebugLogger.log("[Panel] writeUserFile: $safe (${content.length} chars)")
        } catch (e: Exception) {
            DebugLogger.log("[Panel] writeUserFile failed: ${e.message}")
        }
    }

    /**
     * Save a lorebook JSON (base64-encoded) to PT's worlds directory.
     * Called from importWorldInfo stub in PT_PANEL_BRIDGE_JS.
     * The content must be in ST WorldInfo format ({"name":"...","entries":{...}}).
     */
    @JavascriptInterface
    fun saveWorldInfo(base64: String, filename: String) {
        Thread {
            try {
                val safe = filename.filter { it.isLetterOrDigit() || it in "._-" }.take(128)
                if (safe.isEmpty()) return@Thread
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                val dir = File(filesDir, "worlds")
                if (!dir.exists()) dir.mkdirs()
                File(dir, safe).writeBytes(bytes)
                DebugLogger.log("[Panel] saveWorldInfo: $safe (${bytes.size} bytes)")
            } catch (e: Exception) {
                DebugLogger.log("[Panel] saveWorldInfo failed: ${e.message}")
            }
        }.start()
    }

    /** Variant for base64-encoded content from /user/files/upload. */
    @JavascriptInterface
    fun writeUserFileBase64(filename: String, base64: String) {
        try {
            val safe = filename.filter { it.isLetterOrDigit() || it in "._-/" }.take(256)
            if (safe.isEmpty()) return
            val dir = File(filesDir, "pt_ext_data")
            if (!dir.exists()) dir.mkdirs()
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            File(dir, safe.substringAfterLast('/')).writeBytes(bytes)
        } catch (e: Exception) {
            DebugLogger.log("[Panel] writeUserFileBase64 failed: ${e.message}")
        }
    }

    /**
     * Retrieve a chunk of cached binary proxy data (for large responses like PNG cards).
     * JS calls this in a loop with increasing offsets until an empty string is returned.
     * The cache entry is removed after the last chunk is read (offset >= data size).
     * chunkSize is capped at 65536 bytes (base64 output ≤ 87382 chars).
     */
    @JavascriptInterface
    fun getProxyChunk(key: String, offset: Int, chunkSize: Int): String {
        val data = proxyBinaryCache[key] ?: return ""
        if (offset >= data.size) {
            proxyBinaryCache.remove(key)
            return ""
        }
        val end = minOf(offset + chunkSize.coerceIn(1, 65535), data.size)
        val chunk = data.copyOfRange(offset, end)
        if (end >= data.size) proxyBinaryCache.remove(key) // auto-clean last chunk
        return android.util.Base64.encodeToString(chunk, android.util.Base64.NO_WRAP)
    }

    private fun jsCallback(callbackId: String, quotedJson: String) {
        DebugLogger.log("[Panel] jsCallback: ${callbackId.take(24)} len=${quotedJson.length}")
        val safe = callbackId.replace("'", "\\'")
        webView.post {
            DebugLogger.log("[Panel] jsCallback eval: ${callbackId.take(24)}")
            webView.evaluateJavascript(
                "if(window.__ptPanelCbs&&window.__ptPanelCbs['$safe']){" +
                "window.__ptPanelCbs['$safe']($quotedJson);" +
                "delete window.__ptPanelCbs['$safe'];}",
                null
            )
        }
    }

    /**
     * CORS proxy for BotBrowser's /api/plugins/bot-browser/fetch endpoint.
     * Called from the JS fetch interceptor in PT_PANEL_BRIDGE_JS because
     * shouldInterceptRequest cannot read POST request bodies.
     *
     * requestJson: JSON string with {url, method, headers, body, bodyType, timeoutMs}
     * callbackId:  key into window.__ptPanelCbs for the response callback
     */
    @JavascriptInterface
    fun proxyFetch(requestJson: String, callbackId: String) {
        Thread {
            val responseJson = try {
                val req = JSONObject(requestJson)
                val url = req.getString("url")
                val method = req.optString("method", "GET").uppercase()
                val headersObj = req.optJSONObject("headers")
                val bodyStr = req.optString("body", "")
                val bodyType = req.optString("bodyType", "json")
                val timeoutMs = req.optInt("timeoutMs", 15_000).coerceIn(1_000, 60_000)

                DebugLogger.log("[BotBrowser proxy] $method $url")

                val parsedUrl = java.net.URL(url)
                val scheme = parsedUrl.protocol.lowercase()
                if (scheme != "http" && scheme != "https")
                    throw SecurityException("Blocked scheme: $scheme")
                val host = parsedUrl.host.lowercase().trimEnd('.')
                if (host == "localhost" || host == "::1" || host == "0.0.0.0" || host.startsWith("127."))
                    throw SecurityException("Blocked loopback host: $host")
                val conn = parsedUrl.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = timeoutMs
                conn.readTimeout   = timeoutMs
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; PocketTavern)")
                headersObj?.keys()?.forEach { key ->
                    conn.setRequestProperty(key, headersObj.getString(key))
                }
                if (method != "GET" && method != "HEAD" && bodyStr.isNotEmpty()) {
                    conn.doOutput = true
                    val bytes = if (bodyType == "base64")
                        android.util.Base64.decode(bodyStr, android.util.Base64.DEFAULT)
                    else bodyStr.toByteArray(Charsets.UTF_8)
                    conn.outputStream.use { it.write(bytes) }
                }

                val status = conn.responseCode
                val contentType = conn.contentType ?: "application/octet-stream"
                val bytes = try { conn.inputStream.readBytes() }
                            catch (_: Exception) { conn.errorStream?.readBytes() ?: ByteArray(0) }

                DebugLogger.log("[BotBrowser proxy] → $status ${String(bytes.take(120).toByteArray())}")

                val result = JSONObject()
                result.put("status", status)
                result.put("statusText", conn.responseMessage ?: "OK")
                result.put("contentType", contentType)
                val isBinary = contentType.startsWith("image/") || contentType == "application/octet-stream"
                if (isBinary) {
                    // Store binary in Kotlin cache instead of inline base64 to avoid
                    // evaluateJavascript size limits (large PNGs can be 1-5MB).
                    proxyBinaryCache[callbackId] = bytes
                    result.put("bodyType", "chunked")
                    result.put("cacheKey", callbackId)
                    result.put("totalBytes", bytes.size)
                    DebugLogger.log("[BotBrowser proxy] cached ${bytes.size} bytes for $callbackId")
                } else {
                    result.put("bodyType", "text")
                    result.put("body", String(bytes, Charsets.UTF_8))
                }
                result.toString()
            } catch (e: Exception) {
                val msg = (e.message ?: "proxy error")
                    .replace("\\", "\\\\").replace("\"", "\\\"")
                DebugLogger.log("[BotBrowser proxy] ERROR: ${e.message}")
                """{"status":502,"statusText":"Bad Gateway","contentType":"application/json","bodyType":"text","body":"{\"error\":\"$msg\"}"}"""
            }

            try {
                jsCallback(callbackId, JSONObject.quote(responseJson))
            } catch (e: Exception) {
                DebugLogger.log("[Panel] jsCallback EXCEPTION: ${e.message}")
            }
        }.start()
    }
}

// ── Proxy helper ──────────────────────────────────────────────────────────────

/**
 * Synchronous HTTP proxy for BotBrowser's /api/plugins/bot-browser/proxy endpoint.
 * Called on WebView's background resource-loading thread — blocking is fine here.
 */
private fun proxyRequest(url: String, corsHeaders: Map<String, String>): WebResourceResponse {
    return try {
        val parsedUrl = java.net.URL(url)
        val scheme = parsedUrl.protocol.lowercase()
        if (scheme != "http" && scheme != "https")
            return WebResourceResponse("application/json", "UTF-8", 403, "Forbidden",
                corsHeaders, """{"error":"blocked scheme"}""".byteInputStream())
        val host = parsedUrl.host.lowercase().trimEnd('.')
        if (host == "localhost" || host == "::1" || host == "0.0.0.0" || host.startsWith("127."))
            return WebResourceResponse("application/json", "UTF-8", 403, "Forbidden",
                corsHeaders, """{"error":"blocked host"}""".byteInputStream())
        val conn = parsedUrl.openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; PocketTavern)")
        conn.setRequestProperty("Accept", "*/*")
        conn.connectTimeout = 15_000
        conn.readTimeout   = 30_000
        conn.instanceFollowRedirects = true
        val status = conn.responseCode
        val contentType = conn.contentType ?: "application/octet-stream"
        val mime    = contentType.substringBefore(";").trim()
        val charset = Regex("charset=([^;\\s]+)").find(contentType)?.groupValues?.get(1) ?: "UTF-8"
        val stream  = try { conn.inputStream } catch (_: Exception) {
            conn.errorStream ?: ByteArray(0).inputStream()
        }
        WebResourceResponse(mime, charset, status, conn.responseMessage ?: "OK", corsHeaders, stream)
    } catch (e: Exception) {
        WebResourceResponse("application/json", "UTF-8", 502, "Bad Gateway",
            corsHeaders, """{"error":"proxy failed: ${e.message?.replace("\"","\\\"")}"}""".byteInputStream())
    }
}

// ── Inline HTML panel ─────────────────────────────────────────────────────────

/** Legacy panel for extensions that call PT.registerPanel() with inline HTML. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InlineHtmlPanel(
    html: String,
    css: String,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                val ptApiJs = try { ctx.assets.open("extensions/pt_api.js").bufferedReader().readText() } catch (_: Exception) { "" }
                val jqueryJs = try { ctx.assets.open("extensions/jquery.min.js").bufferedReader().readText() } catch (_: Exception) { "" }
                val compatJs = try { ctx.assets.open("extensions/st_compat.js").bufferedReader().readText() } catch (_: Exception) { "" }

                val page = """<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>body{margin:0;padding:12px;font-family:sans-serif;}$css</style>
</head>
<body>
$html
<script>$ptApiJs</script>
<script>$jqueryJs</script>
<script>$compatJs</script>
<script>window.__ptIsPanelContext=true;</script>
</body>
</html>""".trimIndent()

                loadData(
                    android.util.Base64.encodeToString(page.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP),
                    "text/html", "base64"
                )
            }
        }
    )
}
