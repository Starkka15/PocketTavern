package com.pockettavern.app.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pockettavern.app.data.local.CharacterStorage
import com.pockettavern.app.data.local.LoreBookStorage
import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.data.remote.api.CharaVaultApi
import com.pockettavern.app.data.remote.api.ForgeApi
import com.pockettavern.app.data.remote.api.GitHubApi
import com.pockettavern.app.data.repository.CharaVaultRepository
import com.pockettavern.app.data.remote.imagegen.*
import com.pockettavern.app.data.repository.ForgeRepository
import com.pockettavern.app.data.repository.ImageGenRepository
import com.pockettavern.app.data.repository.LlmRepository
import com.pockettavern.app.domain.model.ImageGenBackendType
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private const val CHARAVAULT_NET_URL = "https://charavault.net"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

    // ── LLM Backend Client ────────────────────────────────────────────────────
    // Long timeouts for text generation; no auth interceptor (each request adds its own header)

    @Provides
    @Singleton
    @Named("LLM")
    fun provideLlmOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS) // 5 min for slow local LLMs
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideLlmRepository(
        settingsDataStore: SettingsDataStore,
        @Named("LLM") okHttpClient: OkHttpClient
    ): LlmRepository = LlmRepository(settingsDataStore, okHttpClient)

    // ── Forge (Stable Diffusion) ─────────────────────────────────────────────

    @Provides
    @Singleton
    @Named("Forge")
    fun provideForgeOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideForgeApiProvider(
        @Named("Forge") okHttpClient: OkHttpClient,
        settingsDataStore: SettingsDataStore
    ): () -> ForgeApi = {
        val forgeUrl = runBlocking { settingsDataStore.getForgeUrl() }
        val baseUrl = forgeUrl.ifBlank { "http://localhost" }
        createRetrofit(okHttpClient, "$baseUrl/").create(ForgeApi::class.java)
    }

    @Provides
    @Singleton
    fun provideForgeRepository(
        apiProvider: @JvmSuppressWildcards () -> ForgeApi
    ): ForgeRepository = ForgeRepository(apiProvider)

    @Provides
    @Singleton
    fun provideImageGenBackends(
        apiProvider: @JvmSuppressWildcards () -> ForgeApi,
        @Named("Forge") forgeClient: OkHttpClient,
        settingsDataStore: SettingsDataStore
    ): Map<ImageGenBackendType, @JvmSuppressWildcards ImageGenBackend> {
        return mapOf(
            ImageGenBackendType.SD_WEBUI to SdWebuiBackend(apiProvider),
            ImageGenBackendType.COMFYUI to ComfyUiBackend(forgeClient, settingsDataStore),
            ImageGenBackendType.DALLE to DalleBackend(forgeClient, settingsDataStore),
            ImageGenBackendType.STABILITY to StabilityBackend(forgeClient, settingsDataStore),
            ImageGenBackendType.POLLINATIONS to PollinationsBackend(forgeClient, settingsDataStore),
            ImageGenBackendType.HUGGINGFACE to HuggingFaceBackend(forgeClient, settingsDataStore),
            ImageGenBackendType.NANO_GPT to NanoGptBackend(forgeClient, settingsDataStore)
        )
    }

    @Provides
    @Singleton
    fun provideImageGenRepository(
        backends: Map<ImageGenBackendType, @JvmSuppressWildcards ImageGenBackend>,
        settingsDataStore: SettingsDataStore
    ): ImageGenRepository = ImageGenRepository(backends, settingsDataStore)

    // ── CharaVault ────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    @Named("CharaVault")
    fun provideCharaVaultOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        settingsDataStore: SettingsDataStore
    ): OkHttpClient {
        val charavaultAuthInterceptor = Interceptor { chain ->
            val mode = runBlocking { settingsDataStore.getCharaVaultMode() }
            val session = if (mode == "charavault") {
                runBlocking { settingsDataStore.getCharaVaultSession() }
            } else null
            val request = if (session != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${session.token}")
                    .build()
            } else chain.request()
            chain.proceed(request)
        }
        return OkHttpClient.Builder()
            .addInterceptor(charavaultAuthInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    fun provideCharaVaultApi(
        @Named("CharaVault") okHttpClient: OkHttpClient,
        settingsDataStore: SettingsDataStore
    ): CharaVaultApi {
        val mode = runBlocking { settingsDataStore.getCharaVaultMode() }
        val baseUrl = if (mode == "charavault") {
            CHARAVAULT_NET_URL
        } else {
            val url = runBlocking { settingsDataStore.getCharaVaultUrl() }
            url.ifBlank { "http://localhost" }
        }
        return createRetrofit(okHttpClient, "$baseUrl/").create(CharaVaultApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCharaVaultRepository(
        charaVaultApiProvider: javax.inject.Provider<CharaVaultApi>,
        characterStorage: CharacterStorage,
        loreBookStorage: LoreBookStorage
    ): CharaVaultRepository = CharaVaultRepository(charaVaultApiProvider, characterStorage, loreBookStorage)

    // ── GitHub ────────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    @Named("GitHub")
    fun provideGitHubOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideGitHubApi(
        @Named("GitHub") okHttpClient: OkHttpClient
    ): GitHubApi = createRetrofit(okHttpClient, "https://api.github.com/").create(GitHubApi::class.java)

    // ── Image Loader ──────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        settingsDataStore: SettingsDataStore
    ): ImageLoader {
        // Add CharaVault.net Bearer token for avatar image requests
        val charavaultImageAuthInterceptor = Interceptor { chain ->
            val request = chain.request()
            if (request.url.host == "charavault.net") {
                val mode = runBlocking { settingsDataStore.getCharaVaultMode() }
                val session = if (mode == "charavault") runBlocking { settingsDataStore.getCharaVaultSession() } else null
                if (session != null) {
                    return@Interceptor chain.proceed(
                        request.newBuilder().addHeader("Authorization", "Bearer ${session.token}").build()
                    )
                }
            }
            chain.proceed(request)
        }

        val imageHttpClient = OkHttpClient.Builder()
            .addInterceptor(charavaultImageAuthInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(context)
            .okHttpClient(imageHttpClient)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder(context).maxSizePercent(0.25).build()
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

    // ── Shared ────────────────────────────────────────────────────────────────

    private fun createRetrofit(okHttpClient: OkHttpClient, baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl.ifBlank { "http://localhost/" })
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
