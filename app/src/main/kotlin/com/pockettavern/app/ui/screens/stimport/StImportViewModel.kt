package com.pockettavern.app.ui.screens.stimport

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockettavern.app.data.local.LoreBookStorage
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.domain.model.WorldInfoEntry
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import android.util.Base64
import javax.inject.Inject
import javax.inject.Named

data class ImportProgress(
    val current: Int = 0,
    val total: Int = 0,
    val currentItem: String = ""
)

data class StImportUiState(
    // Server import fields
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    // Status
    val isImporting: Boolean = false,
    val progress: ImportProgress = ImportProgress(),
    val log: List<String> = emptyList(),
    val isComplete: Boolean = false,
    // Results
    val charactersImported: Int = 0,
    val lorebooksImported: Int = 0,
    val chatsImported: Int = 0,
    val errors: Int = 0
)

@HiltViewModel
class StImportViewModel @Inject constructor(
    private val localRepository: LocalRepository,
    private val loreBookStorage: LoreBookStorage,
    @ApplicationContext private val context: Context,
    @Named("LLM") private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(StImportUiState())
    val uiState: StateFlow<StImportUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun updateServerUrl(url: String) = _uiState.update { it.copy(serverUrl = url) }
    fun updateUsername(u: String) = _uiState.update { it.copy(username = u) }
    fun updatePassword(p: String) = _uiState.update { it.copy(password = p) }

    fun resetState() {
        _uiState.update {
            it.copy(
                isImporting = false,
                progress = ImportProgress(),
                log = emptyList(),
                isComplete = false,
                charactersImported = 0,
                lorebooksImported = 0,
                chatsImported = 0,
                errors = 0
            )
        }
    }

    // ── Folder Import (SAF) ────────────────────────────────────────────────

    fun importFromFolder(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, isComplete = false, log = emptyList()) }

            withContext(Dispatchers.IO) {
                val root = DocumentFile.fromTreeUri(context, treeUri)
                if (root == null) {
                    addLog("ERROR: Could not open folder")
                    finish(errors = 1)
                    return@withContext
                }

                var chars = 0; var lorebooks = 0; var chats = 0; var errors = 0

                // Characters
                val charsDir = root.findFile("characters")
                if (charsDir != null && charsDir.isDirectory) {
                    val pngs = charsDir.listFiles().filter {
                        it.name?.endsWith(".png", ignoreCase = true) == true
                    }
                    addLog("Found ${pngs.size} character PNG(s)")
                    pngs.forEachIndexed { i, file ->
                        updateProgress(i + 1, pngs.size, file.name ?: "")
                        try {
                            val bytes = context.contentResolver.openInputStream(file.uri)
                                ?.readBytes() ?: return@forEachIndexed
                            val name = file.name ?: "character_$i.png"
                            localRepository.importCharacterCardBytes(bytes, name)
                            chars++
                            addLog("Imported character: $name")
                        } catch (e: Exception) {
                            errors++
                            addLog("ERROR importing ${file.name}: ${e.message}")
                        }
                    }
                } else {
                    addLog("No characters/ folder found")
                }

                // Worlds / Lorebooks
                val worldsDir = root.findFile("worlds")
                    ?: root.findFile("world_info")
                if (worldsDir != null && worldsDir.isDirectory) {
                    val books = worldsDir.listFiles().filter {
                        it.name?.endsWith(".json", ignoreCase = true) == true
                    }
                    addLog("Found ${books.size} lorebook(s)")
                    books.forEach { file ->
                        try {
                            val text = context.contentResolver.openInputStream(file.uri)
                                ?.bufferedReader()?.readText() ?: return@forEach
                            val name = file.nameWithoutExtension ?: file.name ?: "lorebook"
                            val entries = parseWorldInfoJson(text)
                            loreBookStorage.saveLorebook(name, entries)
                            lorebooks++
                            addLog("Imported lorebook: $name (${entries.size} entries)")
                        } catch (e: Exception) {
                            errors++
                            addLog("ERROR importing ${file.name}: ${e.message}")
                        }
                    }
                } else {
                    addLog("No worlds/ folder found")
                }

                // Chats (copy JSONL files preserving directory structure)
                val chatsDir = root.findFile("chats")
                if (chatsDir != null && chatsDir.isDirectory) {
                    val imported = importChatsFromDir(chatsDir)
                    chats = imported.first; errors += imported.second
                } else {
                    addLog("No chats/ folder found")
                }

                finish(chars = chars, lorebooks = lorebooks, chats = chats, errors = errors)
            }
        }
    }

    private fun importChatsFromDir(dir: DocumentFile): Pair<Int, Int> {
        var imported = 0; var errors = 0
        // Each subdir is a character name
        dir.listFiles().filter { it.isDirectory }.forEach { charDir ->
            val charName = charDir.name ?: return@forEach
            charDir.listFiles().filter {
                it.name?.endsWith(".jsonl", ignoreCase = true) == true
            }.forEach { chatFile ->
                try {
                    val text = context.contentResolver.openInputStream(chatFile.uri)
                        ?.bufferedReader()?.readText() ?: return@forEach
                    val fileName = chatFile.name ?: return@forEach
                    // Write directly to local chat storage directory
                    val chatDir = java.io.File(context.filesDir, "chats/$charName").also { it.mkdirs() }
                    java.io.File(chatDir, fileName).writeText(text)
                    imported++
                    addLog("Imported chat: $charName/$fileName")
                } catch (e: Exception) {
                    errors++
                    addLog("ERROR importing chat: ${e.message}")
                }
            }
        }
        return imported to errors
    }

    // ── Server Import ──────────────────────────────────────────────────────

    fun importFromServer() {
        val baseUrl = _uiState.value.serverUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            addLog("ERROR: Server URL is required")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, isComplete = false, log = emptyList()) }
            withContext(Dispatchers.IO) {
                var chars = 0; var lorebooks = 0; var errors = 0

                // 1. Fetch CSRF token and manually capture session cookies from the response.
                //    OkHttp's CookieJar can silently drop node/express session cookies due to
                //    strict attribute parsing (SameSite, Path, etc.), so we capture Set-Cookie
                //    headers as raw strings and replay them on every subsequent request.
                val (csrfToken, sessionCookie) = fetchCsrfTokenAndCookies(baseUrl)
                if (csrfToken == null) {
                    addLog("ERROR: Could not fetch CSRF token from $baseUrl")
                    finish(errors = 1)
                    return@withContext
                }
                addLog("Connected to ST server (cookie: ${sessionCookie?.take(40) ?: "none"})")

                // 2. Log in if credentials provided (ST multi-user mode requires POST /api/users/login)
                val handle = _uiState.value.username.trim()
                val password = _uiState.value.password.trim()
                var activeCookie = sessionCookie
                if (handle.isNotBlank()) {
                    val loginResult = doLogin(baseUrl, csrfToken, activeCookie, handle, password)
                    if (loginResult == null) {
                        // Login failed — might not be multi-user mode, warn and continue
                        addLog("WARN: Login failed or not required — proceeding anyway")
                    } else {
                        activeCookie = loginResult
                        addLog("Logged in as $handle")
                    }
                }

                // 3. Import characters
                addLog("Fetching character list...")
                val characterList = fetchCharacterList(baseUrl, csrfToken, activeCookie)
                addLog("Found ${characterList.size} character(s)")

                characterList.forEachIndexed { i, (name, filename) ->
                    updateProgress(i + 1, characterList.size, name)
                    try {
                        val pngBytes = exportCharacter(baseUrl, csrfToken, activeCookie, name, filename)
                        if (pngBytes != null && pngBytes.isNotEmpty()) {
                            val safeName = filename.ifBlank { "$name.png" }
                            localRepository.importCharacterCardBytes(pngBytes, safeName)
                            chars++
                            addLog("Imported: $name")
                        } else {
                            errors++
                            addLog("ERROR: Empty response for $name")
                        }
                    } catch (e: Exception) {
                        errors++
                        addLog("ERROR importing $name: ${e.message}")
                        DebugLogger.logError("StImport", "Character import failed", e)
                    }
                }

                // 4. Import lorebooks
                addLog("Fetching lorebook list...")
                val lorebookNames = fetchLorebookList(baseUrl, csrfToken, activeCookie)
                addLog("Found ${lorebookNames.size} lorebook(s)")

                lorebookNames.forEach { name ->
                    try {
                        val entries = fetchLorebook(baseUrl, csrfToken, activeCookie, name)
                        loreBookStorage.saveLorebook(name, entries)
                        lorebooks++
                        addLog("Imported lorebook: $name (${entries.size} entries)")
                    } catch (e: Exception) {
                        errors++
                        addLog("ERROR importing lorebook $name: ${e.message}")
                    }
                }

                finish(chars = chars, lorebooks = lorebooks, errors = errors)
            }
        }
    }

    /** Returns (csrfToken, rawCookieHeader). Captures Set-Cookie manually because
     *  OkHttp's CookieJar silently drops cookies with attributes it doesn't fully parse. */
    private fun fetchCsrfTokenAndCookies(baseUrl: String): Pair<String?, String?> {
        return try {
            val req = Request.Builder().url("$baseUrl/csrf-token").get().build()
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return null to null
                if (!resp.isSuccessful) {
                    addLog("ERROR /csrf-token: HTTP ${resp.code} — ${body.take(120)}")
                    return null to null
                }
                // Collect all Set-Cookie values, strip attributes (;Path=...), join as Cookie header
                val cookieHeader = resp.headers("Set-Cookie")
                    .joinToString("; ") { it.substringBefore(";").trim() }
                    .ifBlank { null }
                val obj = json.decodeFromString<JsonObject>(body)
                val token = obj["token"]?.jsonPrimitive?.contentOrNull
                token to cookieHeader
            }
        } catch (e: Exception) {
            addLog("ERROR /csrf-token: ${e.message}")
            null to null
        }
    }

    /** POST /api/users/login — required for ST multi-user mode.
     *  Returns the updated cookie string (with authenticated session) on success, null on failure.
     *  A 404 means ST isn't running multi-user mode; treat as non-fatal. */
    private fun doLogin(
        baseUrl: String,
        csrf: String,
        cookie: String?,
        handle: String,
        password: String
    ): String? {
        return try {
            val bodyJson = """{"handle":"$handle","password":"$password"}"""
            val req = post("$baseUrl/api/users/login", bodyJson, csrf, cookie)
            okHttpClient.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string() ?: ""
                when {
                    resp.isSuccessful -> {
                        // Capture updated session cookie from login response
                        val newCookies = resp.headers("Set-Cookie")
                            .joinToString("; ") { it.substringBefore(";").trim() }
                        // Merge: new cookies override old ones
                        val merged = if (newCookies.isNotBlank()) newCookies else cookie
                        merged
                    }
                    resp.code == 404 -> {
                        addLog("INFO: /api/users/login not found — ST single-user mode, no login needed")
                        cookie // Return original cookie, proceed without login
                    }
                    resp.code == 401 || resp.code == 403 -> {
                        addLog("ERROR: Login rejected (${resp.code}) — check handle/password. Body: ${respBody.take(80)}")
                        null
                    }
                    else -> {
                        addLog("WARN: Login returned HTTP ${resp.code} — ${respBody.take(80)}")
                        cookie
                    }
                }
            }
        } catch (e: Exception) {
            addLog("WARN: Login request failed: ${e.message}")
            cookie
        }
    }

    private fun post(
        url: String,
        body: String,
        csrfToken: String,
        cookie: String?
    ) = Request.Builder()
        .url(url)
        .post(body.toRequestBody("application/json".toMediaType()))
        .header("X-CSRF-Token", csrfToken)
        .also { if (cookie != null) it.header("Cookie", cookie) }
        .build()

    private fun fetchCharacterList(
        base: String,
        csrf: String,
        cookie: String?
    ): List<Pair<String, String>> {
        return try {
            val req = post("$base/api/characters/all", "{}", csrf, cookie)
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return emptyList()
                if (!resp.isSuccessful) {
                    addLog("ERROR /api/characters/all: HTTP ${resp.code} — ${body.take(120)}")
                    return emptyList()
                }
                addLog("DBG char list body: ${body.take(200)}")
                val arr = json.decodeFromString<JsonArray>(body)
                arr.mapNotNull { el ->
                    val obj = el.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val avatar = obj["avatar"]?.jsonPrimitive?.contentOrNull ?: "$name.png"
                    name to avatar
                }
            }
        } catch (e: Exception) {
            addLog("ERROR parsing character list: ${e.message}")
            emptyList()
        }
    }

    private fun exportCharacter(
        base: String,
        csrf: String,
        cookie: String?,
        name: String,
        avatarUrl: String
    ): ByteArray? {
        // ST export endpoint expects {"avatar_url": "<filename>", "format": "png"}
        val body = """{"avatar_url":"$avatarUrl","format":"png"}"""
        val req = post("$base/api/characters/export", body, csrf, cookie)
        return okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string() ?: ""
                addLog("ERROR export $name: HTTP ${resp.code} — ${errBody.take(80)}")
                null
            } else {
                resp.body?.bytes()
            }
        }
    }

    private fun fetchLorebookList(base: String, csrf: String, cookie: String?): List<String> {
        return try {
            val req = post("$base/api/worldinfo/list", "{}", csrf, cookie)
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: return emptyList()
                if (!resp.isSuccessful) {
                    addLog("ERROR /api/worldinfo/list: HTTP ${resp.code} — ${body.take(120)}")
                    return emptyList()
                }
                addLog("DBG lorebook list body: ${body.take(200)}")
                val arr = json.decodeFromString<JsonArray>(body)
                arr.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            }
        } catch (e: Exception) {
            addLog("ERROR parsing lorebook list: ${e.message}")
            emptyList()
        }
    }

    private fun fetchLorebook(
        base: String,
        csrf: String,
        cookie: String?,
        name: String
    ): List<WorldInfoEntry> {
        val body = """{"name":"$name"}"""
        val req = post("$base/api/worldinfo/get", body, csrf, cookie)
        return okHttpClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: return emptyList()
            if (!resp.isSuccessful) {
                addLog("ERROR /api/worldinfo/get $name: HTTP ${resp.code} — ${text.take(80)}")
                return emptyList()
            }
            parseWorldInfoJson(text)
        }
    }

    // Parse ST world info JSON into domain WorldInfoEntry list
    private fun parseWorldInfoJson(text: String): List<WorldInfoEntry> {
        return try {
            val obj = json.decodeFromString<JsonObject>(text)
            val entries = obj["entries"]?.jsonObject ?: return emptyList()
            entries.values.mapNotNull { el ->
                try {
                    val e = el.jsonObject
                    WorldInfoEntry(
                        uid = e["uid"]?.jsonPrimitive?.contentOrNull ?: "",
                        key = e["key"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                            ?: emptyList(),
                        keysecondary = e["keysecondary"]?.jsonArray
                            ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                        content = e["content"]?.jsonPrimitive?.contentOrNull ?: "",
                        comment = e["comment"]?.jsonPrimitive?.contentOrNull ?: "",
                        constant = e["constant"]?.jsonPrimitive?.booleanOrNull ?: false,
                        selective = e["selective"]?.jsonPrimitive?.booleanOrNull ?: false,
                        order = e["order"]?.jsonPrimitive?.intOrNull ?: 100,
                        position = e["position"]?.jsonPrimitive?.intOrNull ?: 0,
                        depth = e["depth"]?.jsonPrimitive?.intOrNull ?: 4,
                        probability = e["probability"]?.jsonPrimitive?.intOrNull ?: 100,
                        enabled = e["disable"]?.jsonPrimitive?.booleanOrNull?.not() ?: true
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun addLog(message: String) {
        _uiState.update { it.copy(log = it.log + message) }
    }

    private fun updateProgress(current: Int, total: Int, item: String) {
        _uiState.update { it.copy(progress = ImportProgress(current, total, item)) }
    }

    private fun finish(chars: Int = 0, lorebooks: Int = 0, chats: Int = 0, errors: Int = 0) {
        _uiState.update {
            it.copy(
                isImporting = false,
                isComplete = true,
                charactersImported = chars,
                lorebooksImported = lorebooks,
                chatsImported = chats,
                errors = errors
            )
        }
        addLog("Done. Characters: $chars, Lorebooks: $lorebooks, Chats: $chats, Errors: $errors")
    }
}

private val DocumentFile.nameWithoutExtension: String?
    get() = name?.substringBeforeLast('.')
