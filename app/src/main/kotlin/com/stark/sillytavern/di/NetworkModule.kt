package com.stark.sillytavern.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.stark.sillytavern.data.local.SettingsDataStore
import com.stark.sillytavern.data.remote.api.CardVaultApi
import com.stark.sillytavern.data.remote.api.ChubApi
import com.stark.sillytavern.data.remote.api.ChubAvatarApi
import com.stark.sillytavern.data.remote.api.ForgeApi
import com.stark.sillytavern.data.remote.api.GitHubApi
import com.stark.sillytavern.data.remote.api.SillyTavernApi
import com.stark.sillytavern.data.remote.interceptor.AuthInterceptor
import com.stark.sillytavern.data.remote.interceptor.CsrfInterceptor
import com.stark.sillytavern.data.repository.CardVaultRepository
import com.stark.sillytavern.data.repository.ChubRepository
import com.stark.sillytavern.data.repository.ForgeRepository
import com.stark.sillytavern.data.repository.SettingsRepository
import com.stark.sillytavern.data.repository.SillyTavernRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false  // Don't serialize null values
        coerceInputValues = true  // Coerce null to default values
    }

    // Simple in-memory cookie jar for session persistence
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    // Clear all cookies (call when starting fresh connection)
    fun clearCookies() {
        cookieStore.clear()
    }

    // Clear cookies for a specific host
    fun clearCookiesForHost(host: String) {
        cookieStore.remove(host)
    }

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            cookieStore.getOrPut(host) { mutableListOf() }.apply {
                // Remove existing cookies with same name, then add new ones
                cookies.forEach { newCookie ->
                    removeAll { it.name == newCookie.name }
                    add(newCookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        settingsDataStore: SettingsDataStore
    ): AuthInterceptor = AuthInterceptor(settingsDataStore)

    @Provides
    @Singleton
    fun provideCsrfInterceptor(): CsrfInterceptor = CsrfInterceptor()

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            // Use HEADERS for SillyTavern (BODY breaks streaming), BODY for others
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    @Provides
    @Singleton
    @Named("SillyTavern")
    fun provideSillyTavernOkHttpClient(
        authInterceptor: AuthInterceptor,
        csrfInterceptor: CsrfInterceptor
    ): OkHttpClient {
        // Note: No logging interceptor - it buffers the entire response and breaks streaming
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(authInterceptor)
            .addInterceptor(csrfInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)  // Long timeout for text generation
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("Chub")
    fun provideChubOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        settingsDataStore: SettingsDataStore
    ): OkHttpClient {
        // Interceptor to add Chub session cookie to requests
        val chubAuthInterceptor = Interceptor { chain ->
            val session = runBlocking { settingsDataStore.getChubSession() }
            val request = if (session != null) {
                chain.request().newBuilder()
                    .addHeader("Cookie", session.cookie)
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .addInterceptor(chubAuthInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("Forge")
    fun provideForgeOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)  // Very long timeout for image generation
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("CardVault")
    fun provideCardVaultOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Create dynamic Retrofit instances since base URLs come from settings
    private fun createRetrofit(okHttpClient: OkHttpClient, baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl.ifBlank { "http://localhost/" })
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideSillyTavernApiProvider(
        @Named("SillyTavern") okHttpClient: OkHttpClient,
        settingsDataStore: SettingsDataStore
    ): () -> SillyTavernApi {
        return {
            val settings = runBlocking { settingsDataStore.settingsFlow.first() }
            val baseUrl = settings.normalizedServerUrl.ifBlank { "http://localhost" }
            createRetrofit(okHttpClient, "$baseUrl/").create(SillyTavernApi::class.java)
        }
    }

    @Provides
    fun provideSillyTavernApi(
        apiProvider: @JvmSuppressWildcards () -> SillyTavernApi
    ): SillyTavernApi = apiProvider()

    @Provides
    @Singleton
    fun provideChubApi(
        @Named("Chub") okHttpClient: OkHttpClient
    ): ChubApi {
        return createRetrofit(okHttpClient, "https://api.chub.ai/").create(ChubApi::class.java)
    }

    @Provides
    @Singleton
    fun provideChubAvatarApi(
        @Named("Chub") okHttpClient: OkHttpClient
    ): ChubAvatarApi {
        return createRetrofit(okHttpClient, "https://avatars.charhub.io/").create(ChubAvatarApi::class.java)
    }

    @Provides
    @Singleton
    fun provideForgeApiProvider(
        @Named("Forge") okHttpClient: OkHttpClient,
        settingsDataStore: SettingsDataStore
    ): () -> ForgeApi {
        return {
            val settings = runBlocking { settingsDataStore.settingsFlow.first() }
            val baseUrl = settings.normalizedForgeUrl.ifBlank { "http://localhost" }
            createRetrofit(okHttpClient, "$baseUrl/").create(ForgeApi::class.java)
        }
    }

    @Provides
    @Singleton
    fun provideSillyTavernRepository(
        apiProvider: @JvmSuppressWildcards () -> SillyTavernApi,
        csrfInterceptor: CsrfInterceptor,
        settingsRepository: SettingsRepository,
        settingsDataStore: com.stark.sillytavern.data.local.SettingsDataStore,
        @Named("SillyTavern") okHttpClient: OkHttpClient
    ): SillyTavernRepository = SillyTavernRepository(apiProvider, csrfInterceptor, settingsRepository, settingsDataStore, okHttpClient)

    @Provides
    @Singleton
    fun provideChubRepository(
        chubApi: ChubApi,
        chubAvatarApi: ChubAvatarApi,
        sillyTavernApiProvider: @JvmSuppressWildcards () -> SillyTavernApi,
        settingsRepository: SettingsRepository,
        @Named("Chub") okHttpClient: OkHttpClient
    ): ChubRepository = ChubRepository(chubApi, chubAvatarApi, sillyTavernApiProvider, settingsRepository, okHttpClient)

    @Provides
    @Singleton
    fun provideForgeRepository(
        apiProvider: @JvmSuppressWildcards () -> ForgeApi
    ): ForgeRepository = ForgeRepository(apiProvider)

    @Provides
    fun provideCardVaultApi(
        @Named("CardVault") okHttpClient: OkHttpClient,
        settingsDataStore: SettingsDataStore
    ): CardVaultApi {
        val url = runBlocking { settingsDataStore.getCardVaultUrl() }
        val baseUrl = url.ifBlank { "http://localhost" }
        return createRetrofit(okHttpClient, "$baseUrl/").create(CardVaultApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCardVaultRepository(
        cardVaultApiProvider: javax.inject.Provider<CardVaultApi>,
        sillyTavernApiProvider: javax.inject.Provider<SillyTavernApi>
    ): CardVaultRepository = CardVaultRepository(cardVaultApiProvider, sillyTavernApiProvider)

    @Provides
    @Singleton
    @Named("GitHub")
    fun provideGitHubOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGitHubApi(
        @Named("GitHub") okHttpClient: OkHttpClient
    ): GitHubApi {
        return createRetrofit(okHttpClient, "https://api.github.com/").create(GitHubApi::class.java)
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @Named("SillyTavern") okHttpClient: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
