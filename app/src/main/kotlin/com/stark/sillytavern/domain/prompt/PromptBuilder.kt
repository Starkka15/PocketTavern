package com.stark.sillytavern.domain.prompt

import com.stark.sillytavern.domain.model.*
import com.stark.sillytavern.util.DebugLogger

/**
 * Builds prompts following SillyTavern's prompt construction pipeline.
 *
 * Prompt order (for text completion APIs):
 * 1. Story string (system prompt + description + personality + scenario)
 * 2. Message examples
 * 3. World Info (position: before character)
 * 4. Chat history with depth-based injections (Author's Note, World Info by depth)
 * 5. World Info (position: after character)
 * 6. New user message
 * 7. Assistant response start
 */
class PromptBuilder(
    private val character: Character,
    private val chatContext: ChatContext,
    private val userName: String = "User"
) {
    private val instructTemplate = chatContext.instructTemplate
    // Combine global system prompt with character's custom system prompt
    // Character's system prompt takes priority and is appended after global
    private val systemPrompt: String

    init {
        val globalPrompt = chatContext.systemPromptPreset.ifBlank {
            instructTemplate?.systemPrompt ?: ""
        }
        val characterPrompt = character.systemPrompt

        DebugLogger.logSection("System Prompt Construction")
        DebugLogger.logKeyValue("Global system prompt", globalPrompt.take(100).ifBlank { "(empty)" })
        DebugLogger.logKeyValue("Character system prompt", characterPrompt.take(100).ifBlank { "(empty)" })

        systemPrompt = buildString {
            if (globalPrompt.isNotBlank()) {
                append(globalPrompt)
            }
            if (characterPrompt.isNotBlank()) {
                if (isNotBlank()) append("\n\n")
                append(characterPrompt)
            }
        }

        DebugLogger.logKeyValue("Combined system prompt length", systemPrompt.length)
    }

    /**
     * Build the complete prompt for text completion APIs.
     */
    fun buildPrompt(
        chatHistory: List<ChatMessage>,
        newMessage: String
    ): String {
        return if (instructTemplate != null && instructTemplate.inputSequence.isNotBlank()) {
            buildInstructPrompt(chatHistory, newMessage)
        } else {
            buildSimplePrompt(chatHistory, newMessage)
        }
    }

    /**
     * Build prompt with instruct mode formatting.
     */
    private fun buildInstructPrompt(
        chatHistory: List<ChatMessage>,
        newMessage: String
    ): String {
        val template = instructTemplate!!
        val sb = StringBuilder()

        // === SYSTEM PROMPT + STORY STRING ===
        val storyString = buildStoryString()
        if (storyString.isNotBlank() || systemPrompt.isNotBlank()) {
            if (template.systemSequence.isNotBlank()) {
                sb.append(template.systemSequence)
            }
            if (systemPrompt.isNotBlank()) {
                sb.append(substituteMacros(systemPrompt))
                sb.append("\n\n")
            }
            if (storyString.isNotBlank()) {
                sb.append(storyString)
            }
            if (template.systemSuffix.isNotBlank()) {
                sb.append(template.systemSuffix)
            } else if (template.stopSequence.isNotBlank()) {
                sb.append(template.stopSequence)
            }
            sb.append("\n")
        }

        // === MESSAGE EXAMPLES ===
        val examples = buildMessageExamples()
        if (examples.isNotBlank()) {
            sb.append(examples)
        }

        // === WORLD INFO (position: before character / at depth 0) ===
        val worldInfoBefore = getWorldInfoByPosition(position = 0, chatHistory, newMessage)
        if (worldInfoBefore.isNotBlank()) {
            sb.append(wrapAsSystem(worldInfoBefore, template))
        }

        // === CHAT HISTORY with depth-based injections ===
        val historyWithInjections = injectDepthPrompts(chatHistory)

        var isFirstAssistant = true
        historyWithInjections.forEach { item ->
            when (item) {
                is HistoryItem.Message -> {
                    val msg = item.message
                    if (msg.isUser) {
                        sb.append(template.inputSequence)
                        sb.append(substituteMacros(msg.content))
                        appendSuffix(sb, template, isUser = true)
                        sb.append("\n")
                    } else {
                        val outputSeq = if (isFirstAssistant && template.firstOutputSequence.isNotBlank()) {
                            template.firstOutputSequence
                        } else {
                            template.outputSequence
                        }
                        sb.append(outputSeq)
                        sb.append(msg.content)
                        appendSuffix(sb, template, isUser = false)
                        sb.append("\n")
                        isFirstAssistant = false
                    }
                }
                is HistoryItem.Injection -> {
                    // Inject Author's Note or World Info at this depth
                    sb.append(wrapAsSystem(item.content, template))
                }
            }
        }

        // === WORLD INFO (position: after character) ===
        val worldInfoAfter = getWorldInfoByPosition(position = 1, chatHistory, newMessage)
        if (worldInfoAfter.isNotBlank()) {
            sb.append(wrapAsSystem(worldInfoAfter, template))
        }

        // === NEW USER MESSAGE ===
        sb.append(template.inputSequence)
        sb.append(substituteMacros(newMessage))
        appendSuffix(sb, template, isUser = true)
        sb.append("\n")

        // === START ASSISTANT RESPONSE ===
        val lastOutputSeq = if (template.lastOutputSequence.isNotBlank()) {
            template.lastOutputSequence
        } else {
            template.outputSequence
        }
        sb.append(lastOutputSeq)

        return sb.toString()
    }

    /**
     * Build a simple prompt without instruct formatting.
     */
    private fun buildSimplePrompt(
        chatHistory: List<ChatMessage>,
        newMessage: String
    ): String {
        val sb = StringBuilder()

        // Story string
        val storyString = buildStoryString()
        if (storyString.isNotBlank()) {
            sb.append(storyString)
            sb.append("\n\n")
        }

        // Message examples
        val examples = character.messageExample
        if (examples.isNotBlank()) {
            sb.append(substituteMacros(examples))
            sb.append("\n\n")
        }

        // Chat history
        val historyWithInjections = injectDepthPrompts(chatHistory)
        historyWithInjections.forEach { item ->
            when (item) {
                is HistoryItem.Message -> {
                    val msg = item.message
                    val name = if (msg.isUser) userName else character.name
                    sb.append("$name: ${msg.content}\n")
                }
                is HistoryItem.Injection -> {
                    sb.append("[${item.content}]\n")
                }
            }
        }

        // New message
        sb.append("$userName: ${substituteMacros(newMessage)}\n")
        sb.append("${character.name}:")

        return sb.toString()
    }

    /**
     * Build the story string (character description, personality, scenario).
     * Includes user persona if position is IN_PROMPT (0).
     */
    private fun buildStoryString(): String {
        val parts = mutableListOf<String>()
        val persona = chatContext.userPersona

        // Character description
        if (character.description.isNotBlank()) {
            parts.add(substituteMacros(character.description))
        }

        // Personality
        if (character.personality.isNotBlank()) {
            parts.add("${character.name}'s personality: ${substituteMacros(character.personality)}")
        }

        // Scenario
        if (character.scenario.isNotBlank()) {
            parts.add("Scenario: ${substituteMacros(character.scenario)}")
        }

        // User persona (position 0 = in prompt)
        if (persona.position == 0 && persona.description.isNotBlank()) {
            parts.add("[${persona.name}'s persona: ${substituteMacros(persona.description)}]")
        }

        return parts.joinToString("\n\n")
    }

    /**
     * Build formatted message examples.
     */
    private fun buildMessageExamples(): String {
        val examples = character.messageExample
        if (examples.isBlank()) return ""

        val template = instructTemplate ?: return substituteMacros(examples) + "\n"

        // Parse examples into individual messages
        val parsedExamples = parseMessageExamples(examples)
        if (parsedExamples.isEmpty()) return ""

        val sb = StringBuilder()
        parsedExamples.forEach { (isUser, content) ->
            if (isUser) {
                sb.append(template.inputSequence)
                sb.append(substituteMacros(content))
                appendSuffix(sb, template, isUser = true)
                sb.append("\n")
            } else {
                sb.append(template.outputSequence)
                sb.append(substituteMacros(content))
                appendSuffix(sb, template, isUser = false)
                sb.append("\n")
            }
        }
        return sb.toString()
    }

    /**
     * Parse message examples from ST format: <START>\n{{user}}: msg\n{{char}}: msg
     */
    private fun parseMessageExamples(examples: String): List<Pair<Boolean, String>> {
        val result = mutableListOf<Pair<Boolean, String>>()
        val lines = examples.split("\n")

        var currentIsUser: Boolean? = null
        var currentContent = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.equals("<START>", ignoreCase = true)) continue

            val userMatch = trimmed.startsWith("{{user}}:", ignoreCase = true) ||
                    trimmed.startsWith("$userName:", ignoreCase = true)
            val charMatch = trimmed.startsWith("{{char}}:", ignoreCase = true) ||
                    trimmed.startsWith("${character.name}:", ignoreCase = true)

            when {
                userMatch -> {
                    if (currentIsUser != null && currentContent.isNotBlank()) {
                        result.add(currentIsUser!! to currentContent.toString().trim())
                    }
                    currentIsUser = true
                    currentContent = StringBuilder(trimmed.substringAfter(":").trim())
                }
                charMatch -> {
                    if (currentIsUser != null && currentContent.isNotBlank()) {
                        result.add(currentIsUser!! to currentContent.toString().trim())
                    }
                    currentIsUser = false
                    currentContent = StringBuilder(trimmed.substringAfter(":").trim())
                }
                currentIsUser != null -> {
                    currentContent.append("\n").append(trimmed)
                }
            }
        }

        if (currentIsUser != null && currentContent.isNotBlank()) {
            result.add(currentIsUser!! to currentContent.toString().trim())
        }

        return result
    }

    /**
     * Inject Author's Note, User Persona, and World Info at correct depths in chat history.
     */
    private fun injectDepthPrompts(chatHistory: List<ChatMessage>): List<HistoryItem> {
        val result = mutableListOf<HistoryItem>()
        val reversedHistory = chatHistory.reversed()
        val historySize = chatHistory.size

        // Author's Note settings
        val authorsNote = chatContext.authorsNote
        val depthPrompt = character.depthPrompt.ifBlank { authorsNote.content }
        val depthPromptDepth = if (character.depthPrompt.isNotBlank()) {
            character.depthPromptDepth
        } else {
            authorsNote.depth
        }

        // Debug logging for Author's Note / Depth Prompt
        DebugLogger.logSection("Author's Note / Depth Prompt")
        DebugLogger.logKeyValue("Character depthPrompt", character.depthPrompt.take(100).ifBlank { "(empty)" })
        DebugLogger.logKeyValue("Character depthPromptDepth", character.depthPromptDepth)
        DebugLogger.logKeyValue("Chat authorsNote.content", authorsNote.content.take(100).ifBlank { "(empty)" })
        DebugLogger.logKeyValue("Chat authorsNote.depth", authorsNote.depth)
        DebugLogger.logKeyValue("Using depthPrompt", depthPrompt.take(100).ifBlank { "(empty)" })
        DebugLogger.logKeyValue("Using depthPromptDepth", depthPromptDepth)
        DebugLogger.logKeyValue("History size", historySize)

        // Handle depth 0: inject at the very end (bottom of history, right before new message)
        // This is done by adding to result FIRST, so when reversed it appears at the END
        if (depthPromptDepth == 0 && depthPrompt.isNotBlank()) {
            DebugLogger.log("Injecting Author's Note at depth 0 (end of history)")
            when (chatContext.userPersona.position) {
                2 -> { // TOP_OF_AN - persona before AN
                    val personaDesc = chatContext.userPersona.description
                    if (personaDesc.isNotBlank()) {
                        result.add(HistoryItem.Injection("[${chatContext.userPersona.name}'s persona: ${substituteMacros(personaDesc)}]"))
                    }
                    result.add(HistoryItem.Injection(substituteMacros(depthPrompt)))
                }
                3 -> { // BOTTOM_OF_AN - persona after AN
                    result.add(HistoryItem.Injection(substituteMacros(depthPrompt)))
                    val personaDesc = chatContext.userPersona.description
                    if (personaDesc.isNotBlank()) {
                        result.add(HistoryItem.Injection("[${chatContext.userPersona.name}'s persona: ${substituteMacros(personaDesc)}]"))
                    }
                }
                else -> {
                    result.add(HistoryItem.Injection(substituteMacros(depthPrompt)))
                }
            }
        }

        // User persona settings
        val persona = chatContext.userPersona
        val personaContent = if (persona.description.isNotBlank()) {
            "[${persona.name}'s persona: ${substituteMacros(persona.description)}]"
        } else ""

        // World Info by depth
        val worldInfoByDepth = getWorldInfoByDepth(chatHistory)

        // Collect World Info entries with depth > chat history size
        // These should be injected at the beginning (after all chat messages)
        val overflowWorldInfo = worldInfoByDepth.filter { it.key > historySize }
            .values.joinToString("\n")

        reversedHistory.forEachIndexed { index, message ->
            // Check if we need to inject at this depth
            val depth = index + 1  // 1-indexed depth from bottom

            // Inject World Info for this depth
            worldInfoByDepth[depth]?.let { wiContent ->
                result.add(HistoryItem.Injection(substituteMacros(wiContent)))
            }

            // Inject Author's Note / Depth Prompt at correct depth
            if (depth == depthPromptDepth && depthPrompt.isNotBlank()) {
                // Check if persona should be injected with Author's Note
                when (persona.position) {
                    2 -> { // TOP_OF_AN - persona before AN
                        if (personaContent.isNotBlank()) {
                            result.add(HistoryItem.Injection(personaContent))
                        }
                        result.add(HistoryItem.Injection(substituteMacros(depthPrompt)))
                    }
                    3 -> { // BOTTOM_OF_AN - persona after AN
                        result.add(HistoryItem.Injection(substituteMacros(depthPrompt)))
                        if (personaContent.isNotBlank()) {
                            result.add(HistoryItem.Injection(personaContent))
                        }
                    }
                    else -> {
                        result.add(HistoryItem.Injection(substituteMacros(depthPrompt)))
                    }
                }
            }

            // Inject persona at depth if position is IN_CHAT (1)
            if (persona.position == 1 && depth == persona.depth && personaContent.isNotBlank()) {
                result.add(HistoryItem.Injection(personaContent))
            }

            result.add(HistoryItem.Message(message))
        }

        // Inject overflow World Info at the beginning (top of chat history)
        if (overflowWorldInfo.isNotBlank()) {
            result.add(HistoryItem.Injection(substituteMacros(overflowWorldInfo)))
        }

        // Also inject Author's Note at beginning if depth > history size
        if (depthPromptDepth > historySize && depthPrompt.isNotBlank()) {
            result.add(HistoryItem.Injection(substituteMacros(depthPrompt)))
        }

        return result.reversed()
    }

    /**
     * Get World Info entries that should be injected at specific depths.
     */
    private fun getWorldInfoByDepth(chatHistory: List<ChatMessage>): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        val triggered = scanWorldInfo(chatHistory)

        val depthEntries = triggered.filter { it.depth > 0 }
        DebugLogger.logSection("World Info By Depth")
        DebugLogger.logKeyValue("Entries with depth > 0", depthEntries.size)
        DebugLogger.logKeyValue("Chat history size", chatHistory.size)

        depthEntries.groupBy { it.depth }
            .forEach { (depth, entries) ->
                DebugLogger.log("  Depth $depth: ${entries.size} entries (${entries.map { it.comment }.joinToString(", ")})")
                result[depth] = entries.joinToString("\n") { it.content }
            }

        return result
    }

    /**
     * Get World Info entries by position (0 = before char, 1 = after char).
     */
    private fun getWorldInfoByPosition(
        position: Int,
        chatHistory: List<ChatMessage>,
        newMessage: String
    ): String {
        val triggered = scanWorldInfo(chatHistory, newMessage)
        val positionEntries = triggered.filter { it.position == position && it.depth == 0 }

        DebugLogger.logSection("World Info By Position $position")
        DebugLogger.logKeyValue("Total triggered", triggered.size)
        DebugLogger.logKeyValue("With position=$position and depth=0", positionEntries.size)
        positionEntries.forEach { entry ->
            DebugLogger.log("  - ${entry.comment}: ${entry.content.take(50)}...")
        }

        return positionEntries
            .sortedBy { it.order }
            .joinToString("\n") { substituteMacros(it.content) }
    }

    /**
     * Scan chat history for World Info keyword triggers.
     */
    private fun scanWorldInfo(
        chatHistory: List<ChatMessage>,
        newMessage: String = ""
    ): List<WorldInfoEntry> {
        val entries = chatContext.worldInfoEntries.filter { it.enabled }
        if (entries.isEmpty()) {
            DebugLogger.log("PromptBuilder: No enabled World Info entries to scan")
            return emptyList()
        }

        val settings = chatContext.worldInfoSettings
        val scanDepth = settings.depth

        // Build text to scan (recent messages up to scan depth)
        val textToScan = buildString {
            append(newMessage)
            append(" ")
            chatHistory.takeLast(scanDepth).forEach { msg ->
                append(msg.content)
                append(" ")
            }
            // Also scan character description and scenario
            append(character.description)
            append(" ")
            append(character.scenario)
        }.lowercase()

        DebugLogger.logSection("PromptBuilder - World Info Scan")
        DebugLogger.logKeyValue("Scan depth", scanDepth)
        DebugLogger.logKeyValue("Text to scan length", textToScan.length)
        DebugLogger.logKeyValue("Text preview", textToScan.take(200))

        val triggered = entries.filter { entry ->
            // Constant entries are always included
            if (entry.constant) {
                DebugLogger.log("  Entry '${entry.comment}' is CONSTANT - always included")
                return@filter true
            }

            // Check if any primary key matches
            val primaryMatch = entry.key.any { key ->
                if (key.isBlank()) return@any false
                val pattern = if (entry.matchWholeWords) {
                    "\\b${Regex.escape(key)}\\b"
                } else {
                    Regex.escape(key)
                }
                val regex = if (entry.caseSensitive) {
                    Regex(pattern)
                } else {
                    Regex(pattern, RegexOption.IGNORE_CASE)
                }
                val matches = regex.containsMatchIn(if (entry.caseSensitive) textToScan else textToScan.lowercase())
                if (matches) {
                    DebugLogger.log("  Entry '${entry.comment}' matched key '$key'")
                }
                matches
            }

            if (!primaryMatch) return@filter false

            // If selective, also check secondary keys
            if (entry.selective && entry.keysecondary.isNotEmpty()) {
                val secondaryMatch = entry.keysecondary.any { key ->
                    if (key.isBlank()) return@any false
                    textToScan.contains(key, ignoreCase = !entry.caseSensitive)
                }
                if (!secondaryMatch) {
                    DebugLogger.log("  Entry '${entry.comment}' - primary matched but secondary keys NOT matched")
                }
                secondaryMatch
            } else {
                true
            }
        }

        DebugLogger.logSection("World Info Triggered Entries")
        DebugLogger.logKeyValue("Total triggered", triggered.size)
        triggered.forEach { entry ->
            DebugLogger.log("  - ${entry.comment}: position=${entry.position}, depth=${entry.depth}, content=${entry.content.take(50)}...")
        }

        return triggered
    }

    /**
     * Wrap content as a system message in instruct format.
     */
    private fun wrapAsSystem(content: String, template: InstructTemplate): String {
        if (content.isBlank()) return ""
        val sb = StringBuilder()
        if (template.systemSequence.isNotBlank()) {
            sb.append(template.systemSequence)
        }
        sb.append(content)
        if (template.systemSuffix.isNotBlank()) {
            sb.append(template.systemSuffix)
        } else if (template.stopSequence.isNotBlank()) {
            sb.append(template.stopSequence)
        }
        sb.append("\n")
        return sb.toString()
    }

    /**
     * Append the appropriate suffix to a message.
     */
    private fun appendSuffix(sb: StringBuilder, template: InstructTemplate, isUser: Boolean) {
        val suffix = if (isUser) template.inputSuffix else template.outputSuffix
        if (suffix.isNotBlank()) {
            sb.append(suffix)
        } else if (template.stopSequence.isNotBlank()) {
            sb.append(template.stopSequence)
        }
    }

    /**
     * Substitute macros like {{char}}, {{user}}, etc.
     */
    private fun substituteMacros(text: String): String {
        if (text.isBlank()) return text

        return text
            .replace("{{char}}", character.name, ignoreCase = true)
            .replace("{{user}}", userName, ignoreCase = true)
            .replace("{{charname}}", character.name, ignoreCase = true)
            .replace("{{username}}", userName, ignoreCase = true)
            .replace("{{description}}", character.description, ignoreCase = true)
            .replace("{{personality}}", character.personality, ignoreCase = true)
            .replace("{{scenario}}", character.scenario, ignoreCase = true)
            .replace("{{persona}}", chatContext.userPersona.description, ignoreCase = true)
            .replace("{{mesexample}}", character.messageExample, ignoreCase = true)
            .replace("{{mes_example}}", character.messageExample, ignoreCase = true)
            // Time macros
            .replace("{{time}}", java.time.LocalTime.now().toString().substringBeforeLast(":"), ignoreCase = true)
            .replace("{{date}}", java.time.LocalDate.now().toString(), ignoreCase = true)
            .replace("{{weekday}}", java.time.LocalDate.now().dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }, ignoreCase = true)
            // Trim whitespace macro - just remove it, trimming happens naturally
            .replace("{{trim}}", "", ignoreCase = true)
            // Original macros - leave them if we don't have a replacement
            .replace(Regex("\\{\\{original\\}\\}", RegexOption.IGNORE_CASE), "")
    }

    /**
     * Represents an item in the chat history with injections.
     */
    private sealed class HistoryItem {
        data class Message(val message: ChatMessage) : HistoryItem()
        data class Injection(val content: String) : HistoryItem()
    }
}
