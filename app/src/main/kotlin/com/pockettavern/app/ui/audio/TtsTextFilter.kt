package com.pockettavern.app.ui.audio

object TtsTextFilter {

    fun filter(text: String, mode: String): String = when (mode) {
        "quotes_only" -> extractQuotes(text)
        "no_asterisks" -> removeAsterisks(text)
        else -> cleanForSpeech(text)
    }

    /** Extract only text between double quotes. */
    private fun extractQuotes(text: String): String {
        val quotes = Regex("\"([^\"]+)\"").findAll(text)
            .map { it.groupValues[1] }
            .toList()
        return quotes.joinToString(" ").ifBlank { "" }
    }

    /** Remove action text wrapped in asterisks (*action*). */
    private fun removeAsterisks(text: String): String =
        collapseWhitespace(text.replace(Regex("\\*[^*]+\\*"), ""))

    /** Basic cleanup: strip markdown artifacts, collapse whitespace. */
    private fun cleanForSpeech(text: String): String =
        text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")  // **bold** → bold
            .replace(Regex("\\*([^*]+)\\*"), "$1")          // *italic* → italic
            .replace(Regex("`[^`]+`"), "")                   // strip inline code
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun collapseWhitespace(text: String): String =
        text.replace(Regex("\\s+"), " ").trim()
}
