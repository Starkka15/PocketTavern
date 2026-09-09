package com.pockettavern.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug logger that writes to a file for debugging prompt building issues.
 * Log file location: /data/data/com.pockettavern.app/files/debug_log.txt
 */
object DebugLogger {
    private const val TAG = "STDebug"
    private const val LOG_FILE = "debug_log.txt"
    private const val PREFS = "debug_prefs"
    private const val KEY_ENABLED = "logging_enabled"

    /**
     * Hard cap on the log file. Logging is opt-in, but a session left running with it on
     * must not quietly eat storage — past this the file is restarted from a fresh header.
     */
    private const val MAX_LOG_BYTES = 2L * 1024 * 1024

    private var logFile: File? = null
    private var appContext: Context? = null
    private var enabled = false

    /** Whether file logging is currently on. Backs the Debug Log screen's toggle. */
    val isEnabled: Boolean get() = enabled

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        appContext = context.applicationContext
        // The stored preference wins; the default follows the build type, so debug builds
        // keep logging out of the box while release users pay no storage cost until they
        // deliberately turn it on.
        enabled = prefs()?.getBoolean(KEY_ENABLED, com.pockettavern.app.BuildConfig.DEBUG)
            ?: com.pockettavern.app.BuildConfig.DEBUG
        if (enabled) openLogFile()
    }

    /** Turn file logging on or off and remember the choice across launches. */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        prefs()?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
        if (enabled) {
            openLogFile()
        } else {
            // Reclaim the space now — leaving the old file on disk would defeat the toggle.
            try {
                logFile?.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete log file: ${e.message}")
            }
            logFile = null
        }
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun header() = "=== PocketTavern Debug Log Started ${dateFormat.format(Date())} ===\n\n"

    /**
     * Opens (and truncates) the log file. Downloads is preferred so the file can be pulled
     * off the device without root, but scoped storage blocks that on API 29+, so fall back
     * to app-private storage — which the in-app viewer reads either way.
     */
    private fun openLogFile() {
        val context = appContext ?: return
        try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            logFile = File(downloadsDir, LOG_FILE).also { it.writeText(header()) }
            log("DebugLogger initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to Downloads, falling back to app storage", e)
            try {
                logFile = File(context.filesDir, LOG_FILE).also { it.writeText(header()) }
                log("DebugLogger initialized (fallback location)")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open any log file: ${e2.message}", e2)
                logFile = null
                return
            }
        }
        log("Log file location: ${logFile?.absolutePath}")
        Log.i(TAG, "Debug log file: ${logFile?.absolutePath}")
    }

    /** Appends one line, restarting the file if it has outgrown [MAX_LOG_BYTES]. */
    private fun appendCapped(line: String) {
        val f = logFile ?: return
        try {
            if (f.length() > MAX_LOG_BYTES) f.writeText(header())
            f.appendText(line)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file: ${e.message}")
        }
    }

    fun log(message: String) {
        if (!enabled) return

        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] $message\n"

        // Log to Android logcat
        Log.d(TAG, message)

        appendCapped(logLine)
    }

    fun logSection(title: String) {
        log("\n========== $title ==========")
    }

    fun logKeyValue(key: String, value: Any?) {
        log("  $key: $value")
    }

    fun logPrompt(label: String, prompt: String) {
        logSection(label)
        log("--- BEGIN PROMPT ---")
        // Split long prompts into chunks for readability
        prompt.lines().forEach { line ->
            log(line)
        }
        log("--- END PROMPT ---")
    }

    fun logWorldInfo(entries: List<Any>) {
        logSection("World Info Entries (${entries.size} total)")
        if (entries.isEmpty()) {
            log("  (no world info entries)")
        }
    }

    fun logChatContext(context: Any) {
        logSection("Chat Context")
        log(context.toString())
    }

    fun logApiRequest(endpoint: String, requestBody: String) {
        logSection("API Request to $endpoint")
        log("--- REQUEST BODY ---")
        requestBody.lines().take(100).forEach { line ->
            log(line)
        }
        if (requestBody.lines().size > 100) {
            log("... (truncated, ${requestBody.lines().size - 100} more lines)")
        }
        log("--- END REQUEST ---")
    }

    fun getLogContents(): String {
        if (!enabled) return "Debug logging is off. Turn it on above to start collecting a log."
        return try {
            logFile?.readText() ?: "Log file not initialized"
        } catch (e: Exception) {
            "Failed to read log: ${e.message}"
        }
    }

    fun getLogFile(): File? = logFile

    fun clearLog() {
        logFile?.writeText("=== Log Cleared ${dateFormat.format(Date())} ===\n\n")
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val logLine = buildString {
            append("[$timestamp] ERROR [$tag] $message\n")
            if (throwable != null) {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                append("Stack trace:\n$sw\n")
            }
        }

        Log.e(tag, message, throwable)

        appendCapped(logLine)
    }
}
