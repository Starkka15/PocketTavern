package com.stark.sillytavern.data.remote.interceptor

import android.util.Base64
import com.stark.sillytavern.data.local.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val settings = runBlocking { settingsDataStore.settingsFlow.first() }

        val request = chain.request().newBuilder().apply {
            if (settings.username.isNotBlank() && settings.password.isNotBlank()) {
                val credentials = "${settings.username}:${settings.password}"
                val encoded = Base64.encodeToString(
                    credentials.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                header("Authorization", "Basic $encoded")
            }
        }.build()

        return chain.proceed(request)
    }
}
