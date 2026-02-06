package com.stark.sillytavern.ui.screens.chub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stark.sillytavern.data.local.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChubLoginViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess.asStateFlow()

    private var lastProcessedCookies: String = ""

    /**
     * Check if the user has successfully logged in based on the current URL.
     * After successful login, chub.ai typically redirects to the main page or dashboard.
     */
    fun checkLoginStatus(url: String) {
        // Check if we're NOT on login/register page anymore
        val isLoginPage = url.contains("/login") || url.contains("/register") || url.contains("/sign")

        // If we left the login page and have cookies, we're probably logged in
        if (!isLoginPage && lastProcessedCookies.isNotBlank()) {
            // Check for auth cookies
            val hasAuthCookie = lastProcessedCookies.contains("__session") ||
                    lastProcessedCookies.contains("samwise") ||
                    lastProcessedCookies.contains("CH_") ||
                    lastProcessedCookies.contains("chub")

            if (hasAuthCookie) {
                _loginSuccess.value = true
            }
        }
    }

    /**
     * Process cookies from the WebView and save the session if valid.
     */
    fun processCookies(cookies: String) {
        if (cookies.isBlank()) return
        lastProcessedCookies = cookies

        // Save cookies if they look substantial (more than just Cloudflare cookies)
        // We'll save any cookies we get - the important thing is having them for API calls
        if (cookies.length > 50) {
            viewModelScope.launch {
                // Save the full cookie string - we'll send all cookies with requests
                settingsDataStore.saveChubSession(
                    cookie = cookies,
                    username = null // We could extract username from the page if needed
                )
            }
        }
    }

    /**
     * Force login success - called when cookies are verified.
     */
    fun forceLoginSuccess() {
        _loginSuccess.value = true
    }
}
