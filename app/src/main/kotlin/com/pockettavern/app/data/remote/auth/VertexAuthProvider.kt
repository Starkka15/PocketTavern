package com.pockettavern.app.data.remote.auth

import com.pockettavern.app.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Fields we need out of a Google service account key file. The file holds more than
 * this (private_key_id, client_id, cert URLs); none of it is needed to mint a token.
 */
data class ServiceAccount(
    val clientEmail: String,
    val privateKeyPem: String,
    val projectId: String,
    val tokenUri: String
) {
    companion object {
        /**
         * Parses a service account JSON key file.
         * Returns null if it is not one, or is missing the fields required to sign.
         */
        fun parse(json: String): ServiceAccount? = try {
            val o = JSONObject(json)
            val email = o.optString("client_email")
            val key = o.optString("private_key")
            if (email.isBlank() || key.isBlank()) null
            else ServiceAccount(
                clientEmail = email,
                privateKeyPem = key,
                projectId = o.optString("project_id"),
                tokenUri = o.optString("token_uri").ifBlank { DEFAULT_TOKEN_URI }
            )
        } catch (e: Exception) {
            null
        }

        const val DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token"
    }
}

/**
 * Mints Google OAuth2 access tokens from a service account key, for Vertex AI.
 *
 * Vertex does not accept a static API key the way the other providers do — it wants a
 * short-lived bearer token. The standard flow is to self-sign a JWT with the service
 * account's private key and trade it for an access token, which is what this does. There
 * is no Google auth library dependency here; the exchange is two dozen lines and pulling
 * in the full SDK for it would be far heavier than the app needs.
 *
 * Tokens last an hour, so they are cached and reused until shortly before expiry.
 */
@Singleton
class VertexAuthProvider @Inject constructor(
    @Named("LLM") private val okHttpClient: OkHttpClient
) {
    private data class CachedToken(val value: String, val expiresAtMillis: Long)

    private val mutex = Mutex()
    private var cache: CachedToken? = null
    private var cacheKey: String? = null

    /**
     * Returns a usable access token, minting one if the cache is empty or stale.
     * Throws on failure so the caller can surface a real error rather than sending an
     * unauthenticated request that fails later with a confusing 401.
     */
    suspend fun accessToken(account: ServiceAccount): String = mutex.withLock {
        val now = System.currentTimeMillis()
        val cached = cache
        // Re-mint if the account changed, so switching key files takes effect at once.
        if (cached != null && cacheKey == account.clientEmail && now < cached.expiresAtMillis) {
            return@withLock cached.value
        }
        val fresh = withContext(Dispatchers.IO) { requestToken(account) }
        cache = fresh
        cacheKey = account.clientEmail
        fresh.value
    }

    /** Drop any cached token, e.g. when the user replaces the key file. */
    suspend fun invalidate() = mutex.withLock {
        cache = null
        cacheKey = null
    }

    private fun requestToken(account: ServiceAccount): CachedToken {
        val assertion = signJwt(account)
        val body = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", assertion)
            .build()
        val request = Request.Builder().url(account.tokenUri).post(body).build()

        okHttpClient.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                // Google returns {"error":"...","error_description":"..."} — the
                // description is the only part that tells the user what to fix.
                val detail = try {
                    val o = JSONObject(text)
                    o.optString("error_description").ifBlank { o.optString("error") }
                } catch (e: Exception) {
                    text.take(200)
                }
                DebugLogger.logError("VertexAuth", "Token exchange failed (${resp.code}): $detail")
                throw IllegalStateException("Vertex AI sign-in failed: $detail")
            }
            val o = JSONObject(text)
            val token = o.optString("access_token")
            if (token.isBlank()) throw IllegalStateException("Vertex AI returned no access token")
            // Refresh a minute early so a token cannot expire mid-request.
            val ttlSeconds = o.optLong("expires_in", 3600L)
            val expiresAt = System.currentTimeMillis() + (ttlSeconds - 60L).coerceAtLeast(0L) * 1000L
            DebugLogger.log("[VertexAuth] Minted access token, valid ${ttlSeconds}s")
            return CachedToken(token, expiresAt)
        }
    }

    /** Builds and RS256-signs the JWT assertion Google exchanges for an access token. */
    private fun signJwt(account: ServiceAccount): String {
        val nowSeconds = System.currentTimeMillis() / 1000
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val claims = JSONObject().apply {
            put("iss", account.clientEmail)
            put("scope", CLOUD_PLATFORM_SCOPE)
            put("aud", account.tokenUri)
            put("iat", nowSeconds)
            put("exp", nowSeconds + 3600)
        }.toString()

        val signingInput = "${header.b64Url()}.${claims.b64Url()}"
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(parsePrivateKey(account.privateKeyPem))
            update(signingInput.toByteArray(Charsets.UTF_8))
        }.sign()
        return "$signingInput.${signature.b64Url()}"
    }

    /** PEM (PKCS#8) private key from the key file into something Signature can use. */
    private fun parsePrivateKey(pem: String) = try {
        val body = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace(Regex("\\s"), "")
        val der = Base64.getDecoder().decode(body)
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
    } catch (e: Exception) {
        throw IllegalStateException("Service account private key could not be read", e)
    }

    private fun String.b64Url() = toByteArray(Charsets.UTF_8).b64Url()

    private fun ByteArray.b64Url(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(this)

    companion object {
        private const val CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
    }
}
