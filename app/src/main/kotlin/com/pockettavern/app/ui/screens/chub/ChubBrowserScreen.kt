package com.pockettavern.app.ui.screens.chub

import com.pockettavern.app.R
import androidx.compose.ui.res.stringResource
import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.Result
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class ChubUiState(
    val isImporting: Boolean = false,
    val importedName: String? = null,
    val error: String? = null
)

@HiltViewModel
class ChubBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localRepository: LocalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChubUiState())
    val uiState: StateFlow<ChubUiState> = _uiState.asStateFlow()

    fun importFromUrl(url: String, cookies: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null, importedName = null) }
            val tempFile = File(context.cacheDir, "chub_dl_${System.currentTimeMillis()}.png")
            try {
                downloadToFile(url, cookies, tempFile)
                when (val result = localRepository.importCharacterCardFile(tempFile)) {
                    is Result.Success -> _uiState.update {
                        it.copy(isImporting = false, importedName = result.data)
                    }
                    is Result.Error -> _uiState.update {
                        it.copy(isImporting = false, error = result.exception.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isImporting = false, error = e.message ?: "Download failed") }
            } finally {
                tempFile.delete()
            }
        }
    }

    fun importFromBytes(bytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null, importedName = null) }
            val tempFile = File(context.cacheDir, "chub_blob_${System.currentTimeMillis()}.png")
            try {
                withContext(Dispatchers.IO) { tempFile.writeBytes(bytes) }
                when (val result = localRepository.importCharacterCardFile(tempFile)) {
                    is Result.Success -> _uiState.update {
                        it.copy(isImporting = false, importedName = result.data)
                    }
                    is Result.Error -> _uiState.update {
                        it.copy(isImporting = false, error = result.exception.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isImporting = false, error = e.message ?: "Import failed") }
            } finally {
                tempFile.delete()
            }
        }
    }

    private suspend fun downloadToFile(url: String, cookies: String?, dest: File) = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            if (!cookies.isNullOrBlank()) conn.setRequestProperty("Cookie", cookies)
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw Exception("HTTP $code")
            FileOutputStream(dest).use { out -> conn.inputStream.copyTo(out) }
        } finally {
            conn.disconnect()
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(importedName = null, error = null) }
    }
}

private class BlobBridge(private val onBlob: (ByteArray) -> Unit) {
    @JavascriptInterface
    fun receiveBlob(base64: String) {
        runCatching { onBlob(Base64.decode(base64, Base64.DEFAULT)) }
    }
}

/**
 * Chub serves the full Character Card V2 PNG as a plain GET on avatars.charhub.io
 * (verified: .../avatars/<fullPath>/chara_card_v2.png -> 200 image/png). A direct
 * navigation to that URL is caught by shouldOverrideUrlLoading / the download
 * listener, but a SPA that fetches the bytes and hands the user a blob: URL bypasses
 * both — so the same blob interception used for BotBooru is installed as well.
 * Whichever path the site takes, the card reaches importFromBytes/importFromUrl.
 */
private val BLOB_HOOK_JS = """
(function() {
    if (window.__ptBlobHooked) return;
    window.__ptBlobHooked = true;

    var blobStore = {};

    var _createObjectURL = URL.createObjectURL.bind(URL);
    URL.createObjectURL = function(obj) {
        var url = _createObjectURL(obj);
        if (obj instanceof Blob || obj instanceof File) {
            blobStore[url] = obj;
        }
        return url;
    };

    var _revokeObjectURL = URL.revokeObjectURL.bind(URL);
    URL.revokeObjectURL = function(url) {
        delete blobStore[url];
        _revokeObjectURL(url);
    };

    function sendBlob(blob) {
        var reader = new FileReader();
        reader.onloadend = function() {
            var b64 = reader.result ? reader.result.split(',')[1] : null;
            if (b64) Android.receiveBlob(b64);
        };
        reader.readAsDataURL(blob);
    }

    function tryIntercept(anchor) {
        var href = anchor.href || '';
        if (!anchor.download || !href.startsWith('blob:')) return false;
        var blob = blobStore[href];
        if (blob) { sendBlob(blob); return true; }
        // Blob already revoked — try fetch as last resort
        fetch(href).then(function(r) { return r.blob(); }).then(sendBlob).catch(function(){});
        return true;
    }

    // Intercept programmatic .click() — fires before revokeObjectURL
    var _click = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function() {
        if (tryIntercept(this)) return;
        _click.call(this);
    };

    // Intercept real user clicks (belt-and-suspenders)
    document.addEventListener('click', function(e) {
        var a = e.target.closest('a[download]');
        if (a && a.href && a.href.startsWith('blob:')) {
            e.preventDefault();
            e.stopImmediatePropagation();
            tryIntercept(a);
        }
    }, true);
})();
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChubBrowserScreen(
    onBack: () -> Unit
) {
    val viewModel: ChubBrowserViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chub)) },
                navigationIcon = {
                    IconButton(onClick = {
                        val wv = webView
                        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).also { wv ->
                        webView = wv
                        // chub.ai hangs its whole layout off height:100%. Without explicit
                        // MATCH_PARENT params the view measures as WRAP_CONTENT, that chain
                        // resolves to 0, and the page paints its background and nothing else.
                        wv.layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        wv.settings.javaScriptEnabled = true
                        wv.settings.domStorageEnabled = true
                        wv.settings.useWideViewPort = true
                        wv.settings.loadWithOverviewMode = true
                        wv.settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                        // Chub gates much of its catalogue behind a login; third-party
                        // cookies keep the user's own session across chub.ai/charhub.io.
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

                        wv.addJavascriptInterface(
                            BlobBridge { bytes -> viewModel.importFromBytes(bytes) },
                            "Android"
                        )

                        wv.setDownloadListener { url, _, _, _, _ ->
                            if (!url.startsWith("blob:")) {
                                val cookies = CookieManager.getInstance().getCookie(url)
                                viewModel.importFromUrl(url, cookies)
                            }
                            // blob: case is fully handled by BLOB_HOOK_JS
                        }

                        wv.webViewClient = object : WebViewClient() {
                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: android.webkit.WebResourceError
                            ) {
                                DebugLogger.log(
                                    "ChubWeb/netError ${error.errorCode} ${error.description} " +
                                        "for ${request.url}"
                                )
                                super.onReceivedError(view, request, error)
                            }

                            override fun onReceivedHttpError(
                                view: WebView,
                                request: WebResourceRequest,
                                errorResponse: android.webkit.WebResourceResponse
                            ) {
                                DebugLogger.log(
                                    "ChubWeb/httpError ${errorResponse.statusCode} for ${request.url}"
                                )
                                super.onReceivedHttpError(view, request, errorResponse)
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                view.evaluateJavascript(BLOB_HOOK_JS, null)
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                // Re-inject: chub.ai is a SPA and navigates without full reloads
                                view.evaluateJavascript(BLOB_HOOK_JS, null)

                                // Cookies live in memory until flushed, so a login would be lost
                                // whenever the process is killed. Persist after every navigation.
                                CookieManager.getInstance().flush()
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                val url = request.url.toString()
                                if (!url.startsWith("blob:") && isChubCardUrl(url)) {
                                    val cookies = CookieManager.getInstance().getCookie(url)
                                    viewModel.importFromUrl(url, cookies)
                                    return true
                                }
                                return false
                            }
                        }

                        wv.loadUrl("https://chub.ai/")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (uiState.isImporting) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card {
                            Column(
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(stringResource(R.string.importing_character))
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.importedName?.let { name ->
        LaunchedEffect(name) { viewModel.clearStatus() }
        AlertDialog(
            onDismissRequest = { viewModel.clearStatus() },
            title = { Text(stringResource(R.string.imported_2)) },
            text = { Text(stringResource(R.string.added_to_characters, name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearStatus() }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearStatus() },
            title = { Text(stringResource(R.string.import_failed)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearStatus() }) { Text(stringResource(R.string.ok)) }
            }
        )
    }
}

/**
 * True only for a full character card download.
 *
 * Cards live at avatars.charhub.io/avatars/<creator>/<slug>/chara_card_v2.png. The
 * same directory also serves avatar.webp thumbnails, which appear all over the browse
 * grid — matching those would fire an import on every listing image, so the card
 * filename is required rather than just the host.
 */
private fun isChubCardUrl(url: String): Boolean {
    val u = url.lowercase()
    val isCardHost = u.contains("avatars.charhub.io") || u.contains("chub.ai")
    if (!isCardHost) return false
    return u.contains("chara_card") || u.contains("/download") || u.endsWith(".charx")
}
