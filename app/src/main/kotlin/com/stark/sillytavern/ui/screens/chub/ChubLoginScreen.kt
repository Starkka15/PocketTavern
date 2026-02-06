package com.stark.sillytavern.ui.screens.chub

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.stark.sillytavern.ui.theme.DarkSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChubLoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: ChubLoginViewModel = hiltViewModel()
) {
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }
    val loginSuccess by viewModel.loginSuccess.collectAsState()

    // Navigate back when login succeeds
    LaunchedEffect(loginSuccess) {
        if (loginSuccess) {
            onLoginSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to Chub.ai") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                actions = {
                    // Manual "Done" button to confirm login
                    TextButton(onClick = {
                        viewModel.forceLoginSuccess()
                    }) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Text("Done")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ChubWebView(
                onUrlChanged = { url -> currentUrl = url },
                onPageStarted = { isLoading = true },
                onPageFinished = { url ->
                    isLoading = false
                    // Check if we're on an authenticated page
                    viewModel.checkLoginStatus(url)
                },
                onCookiesAvailable = { cookies ->
                    viewModel.processCookies(cookies)
                }
            )

            // Loading indicator
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChubWebView(
    onUrlChanged: (String) -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: (String) -> Unit,
    onCookiesAvailable: (String) -> Unit
) {
    val cookieManager = remember { CookieManager.getInstance() }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false

                // Fix for text input issues
                settings.setSupportZoom(true)
                settings.textZoom = 100

                // Use Chrome Mobile user agent to help with Cloudflare
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                // Enable cookies
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
                cookieManager.flush()

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onPageStarted()
                        url?.let { onUrlChanged(it) }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        url?.let {
                            onPageFinished(it)
                            // Get cookies for chub.ai domain
                            val cookies = cookieManager.getCookie("https://chub.ai") ?: ""
                            val venusCookies = cookieManager.getCookie("https://venus.chub.ai") ?: ""
                            val allCookies = listOf(cookies, venusCookies)
                                .filter { c -> c.isNotBlank() }
                                .joinToString("; ")
                            if (allCookies.isNotBlank()) {
                                onCookiesAvailable(allCookies)
                            }
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        // Stay in WebView for all URLs
                        return false
                    }
                }

                // Load the chub.ai login page
                loadUrl("https://chub.ai/login")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
