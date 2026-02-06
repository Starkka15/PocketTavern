package com.stark.sillytavern.util

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
 * Log file location: /data/data/com.stark.sillytavern/files/debug_log.txt
 */
object DebugLogger {
    private const val TAG = "STDebug"
    private const val LOG_FILE = "debug_log.txt"
    private var logFile: File? = null
    private var enabled = true

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        // Use Downloads folder for easy access without root
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        logFile = File(downloadsDir, LOG_FILE)
        // Clear old log on init
        try {
            logFile?.writeText("=== SillyTavern Debug Log Started ${dateFormat.format(Date())} ===\n\n")
            log("DebugLogger initialized")
            log("Log file location: ${logFile?.absolutePath}")
            Log.i(TAG, "Debug log file: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            // Fallback to app-private storage if external fails
            Log.e(TAG, "Failed to write to Downloads, falling back to app storage", e)
            logFile = File(context.filesDir, LOG_FILE)
            logFile?.writeText("=== SillyTavern Debug Log Started ${dateFormat.format(Date())} ===\n\n")
            log("DebugLogger initialized (fallback location)")
            log("Log file location: ${logFile?.absolutePath}")
        }
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun log(message: String) {
        if (!enabled) return

        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] $message\n"

        // Log to Android logcat
        Log.d(TAG, message)

        // Append to file
        try {
            logFile?.appendText(logLine)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file: ${e.message}")
        }
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

        try {
            logFile?.appendText(logLine)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write error to log file: ${e.message}")
        }
    }
}
