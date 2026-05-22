package com.pockettavern.app.ui.screens.risuRealm

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
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

data class RisuRealmUiState(
    val isImporting: Boolean = false,
    val importedName: String? = null,
    val error: String? = null
)

@HiltViewModel
class RisuRealmBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localRepository: LocalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RisuRealmUiState())
    val uiState: StateFlow<RisuRealmUiState> = _uiState.asStateFlow()

    fun importFromUrl(url: String, cookies: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null, importedName = null) }
            val tempFile = File(context.cacheDir, "risurealm_dl_${System.currentTimeMillis()}")
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

    private suspend fun downloadToFile(url: String, cookies: String?, dest: File) = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            if (!cookies.isNullOrBlank()) conn.setRequestProperty("Cookie", cookies)
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

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RisuRealmBrowserScreen(
    onBack: () -> Unit
) {
    val viewModel: RisuRealmBrowserViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RisuRealm") },
                navigationIcon = {
                    IconButton(onClick = {
                        val wv = webView
                        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        wv.settings.javaScriptEnabled = true
                        wv.settings.domStorageEnabled = true
                        wv.settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

                        wv.setDownloadListener { url, _, _, _, _ ->
                            val cookies = CookieManager.getInstance().getCookie(url)
                            viewModel.importFromUrl(url, cookies)
                        }

                        wv.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                val url = request.url.toString()
                                // Intercept API download URLs directly
                                if (isDownloadUrl(url)) {
                                    val cookies = CookieManager.getInstance().getCookie(url)
                                    viewModel.importFromUrl(url, cookies)
                                    return true
                                }
                                return false
                            }
                        }

                        wv.loadUrl("https://realm.risuai.net/")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Importing overlay
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
                                Text("Importing character...")
                            }
                        }
                    }
                }
            }
        }
    }

    // Success snackbar
    uiState.importedName?.let { name ->
        LaunchedEffect(name) {
            viewModel.clearStatus()
        }
        AlertDialog(
            onDismissRequest = { viewModel.clearStatus() },
            title = { Text("Imported") },
            text = { Text("\"$name\" added to your characters.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearStatus() }) { Text("OK") }
            }
        )
    }

    // Error snackbar
    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearStatus() },
            title = { Text("Import failed") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearStatus() }) { Text("OK") }
            }
        )
    }
}

private fun isDownloadUrl(url: String): Boolean {
    return url.contains("/api/v1/download/") ||
           url.contains("/download/") && (
               url.contains(".charx") || url.contains(".png") ||
               url.contains("charx") || url.contains("png-v")
           )
}
