package com.stark.sillytavern.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsrfInterceptor @Inject constructor() : Interceptor {

    @Volatile
    var csrfToken: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().apply {
            csrfToken?.let { token ->
                header("X-CSRF-Token", token)
            }
            // Only set Content-Type for requests with a body (POST, PUT, PATCH)
            val method = chain.request().method
            if (method == "POST" || method == "PUT" || method == "PATCH") {
                header("Content-Type", "application/json")
            }
        }.build()

        return chain.proceed(request)
    }

    fun updateToken(token: String?) {
        csrfToken = token
    }

    fun clearToken() {
        csrfToken = null
    }
}
