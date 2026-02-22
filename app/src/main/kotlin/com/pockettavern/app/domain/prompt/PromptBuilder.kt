package com.pockettavern.app.domain.prompt

import com.pockettavern.app.domain.model.*
import com.pockettavern.app.util.DebugLogger

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
    private val userName: String = "User",
    private val mainPromptOverride: String = "",  // OAI preset's main_prompt, takes priority over sysprompt preset
    private val extensionInjections: List<String> = emptyList()
) {
    private val instructTemplate = chatContext.instructTemplate
    // Combine global system prompt with character's custom system prompt.
    // Priority: OAI preset mainPrompt > sysprompt preset > instruct template system prompt
    private val systemPrompt: String

    init {
        // Strip ST comment macros before blank-testing the override so that
        // {{// Empty for the purposes of card override }} falls through correctly.
        val strippedOverride = stripCommentMacros(mainPromptOverride).trim()
        val globalPrompt = strippedOverride.ifBlank {
            chatContext.systemPromptPreset.ifBlank {
                instructTemplate?.systemPrompt ?: ""
            }
        }
        val characterPrompt = character.systemPrompt

        DebugLogger.logSection("System Prompt Construction")
        DebugLogger.logKeyValue("mainPromptOverride (stripped)", strippedOverride.take(120).ifBlank { "(empty/comment-only)" })
        DebugLogger.logKeyValue("Global system prompt", globalPrompt.take(120).ifBlank { "(empty)" })
        DebugLogger.logKeyValue("Character system prompt", characterPrompt.take(120).ifBlank { "(empty)" })

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
     * Build structured messages for chat completion APIs (OpenAI, Claude, Mistral, etc.).
     * Returns a list of role-tagged messages instead of a single flat string.
     *
     * Message order mirrors SillyTavern's OAI prompt ordering:
     *  1. system  — combined system prompt + character card
     *  2. system  — World Info (before_char, position=0)
     *  3. user/assistant — message examples
     *  4. user/assistant/system — chat history with depth injections
     *  5. system  — World Info (after_char, position=1)
     *  6. system  — post-history instructions
     *  7. user    — new user message
     */
    fun buildChatCompletionMessages(
        chatHistory: List<ChatMessage>,
        newMessage: String,
        promptOrder: List<OaiPromptOrderItem> = OaiPromptOrderItem.defaultOrder()
    ): List<PromptMessage> {
        val messages = mutableListOf<PromptMessage>()

        // Find the chat_history pivot in the order
        val historyIdx = promptOrder.indexOfFirst { it.id == "chat_history" }
        val beforeHistory = if (historyIdx >= 0) promptOrder.take(historyIdx) else promptOrder
        val afterHistory = if (historyIdx >= 0) promptOrder.drop(historyIdx + 1) else emptyList()

        // Build content for a given order item (handles both built-in and custom blocks)
        fun blockContent(item: OaiPromptOrderItem): String {
            // Non-marker blocks (built-in content + custom) use their stored content
            if (!item.isMarker) {
                val presetText = item.content ?: ""
                return when (item.id) {
                    // main_prompt combines preset text + character system prompt (already done in systemPrompt)
                    "main_prompt" -> if (systemPrompt.isNotBlank()) substituteMacros(systemPrompt) else ""
                    // All other content blocks: use preset text; fall back to character field for post-history
                    "post_history_instructions" -> {
                        val text = presetText.ifBlank { character.postHistoryInstructions }
                        if (text.isNotBlank()) substituteMacros(text).trim() else ""
                    }
                    else -> if (presetText.isNotBlank()) substituteMacros(presetText).trim() else ""
                }
            }
            // Marker blocks inject dynamic content
            return when (item.id) {
                "world_info_before" -> getWorldInfoByPosition(0, chatHistory, newMessage)
                "persona_description" -> {
                    val persona = chatContext.userPersona
                    // Only emit if there's an actual description — a bare name with no
                    // description produces "[User's persona]" which is noise to the model.
                    if (persona.description.isBlank()) ""
                    else buildString {
                        if (persona.name.isNotBlank()) append("[${persona.name}'s persona: ")
                        else append("[Persona: ")
                        append(substituteMacros(persona.description))
                        append("]")
                    }
                }
                "char_description" -> if (character.description.isNotBlank()) substituteMacros(character.description) else ""
                "char_personality" -> if (character.personality.isNotBlank())
                    "${character.name}'s personality: ${substituteMacros(character.personality)}" else ""
                "scenario" -> if (character.scenario.isNotBlank())
                    "Scenario: ${substituteMacros(character.scenario)}" else ""
                "world_info_after" -> getWorldInfoByPosition(1, chatHistory, newMessage)
                else -> ""
            }
        }

        // Collect all enabled depth-injection blocks (injection_position=1) from anywhere in the order.
        // These are NOT emitted at their order position — they go into the chat history at their depth.
        val depthInjectionItems = promptOrder.filter { it.enabled && it.injectionPosition == 1 && !it.isMarker }
        DebugLogger.logSection("Prompt Order Processing")
        DebugLogger.logKeyValue("Total order items", promptOrder.size)
        DebugLogger.logKeyValue("History pivot index", historyIdx)
        DebugLogger.logKeyValue("Depth-injection items", depthInjectionItems.size)
        depthInjectionItems.forEach { DebugLogger.log("  [depth-inject] ${it.id} (${it.customLabel ?: ""}) depth=${it.injectionDepth} role=${it.role}") }

        // Walk pre-history blocks in order, emitting each at its correct position.
        // Blocks with injection_position=1 are skipped here — they go into the chat history.
        // chat_examples are emitted inline (not after the loop) so XML wrapper blocks
        // like <examples> / </examples> appear in the right positions.
        for (item in beforeHistory) {
            if (!item.enabled) {
                DebugLogger.log("  [SKIP disabled] ${item.id} (${item.customLabel ?: ""})")
                continue
            }
            if (item.injectionPosition == 1) {
                DebugLogger.log("  [DEFER depth=${item.injectionDepth}] ${item.id} (${item.customLabel ?: ""})")
                continue
            }
            if (item.id == "chat_examples") {
                val examples = parseMessageExamples(character.messageExample)
                DebugLogger.log("  [chat_examples] injecting ${examples.size} example pairs")
                examples.forEach { (isUser, content) ->
                    messages.add(PromptMessage(if (isUser) "user" else "assistant", substituteMacros(content)))
                }
            } else {
                val content = blockContent(item)
                val role = if (item.isMarker) "system" else item.role
                if (content.isNotBlank()) {
                    DebugLogger.log("  [OK role=$role] ${item.id} (${item.customLabel ?: ""}) — ${content.length} chars: ${content.take(120).replace('\n', ' ')}")
                    messages.add(PromptMessage(role, content))
                } else {
                    DebugLogger.log("  [SKIP blank] ${item.id} (${item.customLabel ?: ""})")
                }
            }
        }

        // Extension prompt injections (after char defs, before chat history)
        if (extensionInjections.isNotEmpty()) {
            extensionInjections.forEach { injection ->
                if (injection.isNotBlank()) {
                    DebugLogger.log("  [extension] injecting ${injection.length} chars: ${injection.take(120).replace('\n', ' ')}")
                    messages.add(PromptMessage("system", substituteMacros(injection)))
                }
            }
        }

        // Chat history with depth-based injections — includes OAI preset depth-injection blocks
        val historyItem = promptOrder.find { it.id == "chat_history" }
        if (historyItem?.enabled != false) {
            DebugLogger.log("  [chat_history] injecting ${chatHistory.size} messages + ${depthInjectionItems.size} depth-injected blocks")
            injectDepthPrompts(chatHistory, depthInjectionItems).forEach { item ->
                when (item) {
                    is HistoryItem.Message -> {
                        val msg = item.message
                        if (msg.isUser) {
                            messages.add(PromptMessage("user", substituteMacros(cleanMessageContent(msg.content))))
                        } else {
                            val clean = substituteMacros(cleanMessageContent(msg.content))
                            val content = if (msg.senderName != null && msg.senderName != character.name) {
                                "${msg.senderName}: $clean"
                            } else {
                                clean
                            }
                            messages.add(PromptMessage("assistant", content))
                        }
                    }
                    is HistoryItem.Injection -> {
                        if (item.content.isNotBlank()) {
                            messages.add(PromptMessage(item.role, item.content))
                        }
                    }
                }
            }
        } else {
            DebugLogger.log("  [SKIP disabled] chat_history")
        }

        // Walk post-history blocks in order (skip depth-injection items)
        for (item in afterHistory) {
            if (!item.enabled) {
                DebugLogger.log("  [SKIP disabled] ${item.id} (${item.customLabel ?: ""})")
                continue
            }
            if (item.injectionPosition == 1) {
                DebugLogger.log("  [DEFER depth=${item.injectionDepth}] ${item.id} (${item.customLabel ?: ""})")
                continue
            }
            val content = blockContent(item)
            val role = if (item.isMarker) "system" else item.role
            if (content.isNotBlank()) {
                DebugLogger.log("  [OK role=$role] ${item.id} (${item.customLabel ?: ""}) — ${content.length} chars: ${content.take(120).replace('\n', ' ')}")
                messages.add(PromptMessage(role, content))
            } else {
                DebugLogger.log("  [SKIP blank] ${item.id} (${item.customLabel ?: ""})")
            }
        }

        // New user message always last
        messages.add(PromptMessage("user", substituteMacros(newMessage)))

        // Normalize: merge consecutive same-role messages and ensure proper alternation.
        // This fixes cases like chat_examples ending with assistant + first_mes also assistant.
        val normalized = normalizeForChatApi(messages)

        DebugLogger.logSection("Chat Completion Messages Summary")
        DebugLogger.logKeyValue("Total messages (raw)", messages.size)
        DebugLogger.logKeyValue("Total messages (normalized)", normalized.size)
        normalized.forEachIndexed { i, m ->
            DebugLogger.log("  [msg $i] role=${m.role} len=${m.content.length}: ${m.content.take(200).replace('\n', ' ')}")
        }

        return normalized
    }

    /**
     * Normalize a chat completion message list to satisfy API requirements:
     * 1. Merge adjacent user+user or assistant+assistant messages (joining with double newline).
     *    System messages are kept as separate entries — each prompt block is its own message,
     *    matching SillyTavern's behaviour.
     * 2. Ensure the first non-system message is `user` — if it's `assistant`, insert a
     *    "[Start a new chat]" user placeholder so the sequence alternates correctly.
     */
    private fun normalizeForChatApi(messages: List<PromptMessage>): List<PromptMessage> {
        if (messages.isEmpty()) return messages

        // Step 1: merge adjacent non-system same-role messages only.
        // (system blocks stay separate — each is its own message like ST does it)
        val merged = mutableListOf<PromptMessage>()
        for (msg in messages) {
            val last = merged.lastOrNull()
            if (last != null && last.role == msg.role && msg.role != "system") {
                merged[merged.size - 1] = last.copy(content = "${last.content}\n\n${msg.content}")
            } else {
                merged.add(msg)
            }
        }

        // Step 2: if first non-system message is assistant, prepend a user placeholder
        val firstNonSystem = merged.indexOfFirst { it.role != "system" }
        if (firstNonSystem >= 0 && merged[firstNonSystem].role == "assistant") {
            merged.add(firstNonSystem, PromptMessage("user", "[Start a new chat]"))
        }

        return merged
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

        // === EXTENSION PROMPT INJECTIONS ===
        if (extensionInjections.isNotEmpty()) {
            extensionInjections.forEach { injection ->
                if (injection.isNotBlank()) {
                    sb.append(wrapAsSystem(substituteMacros(injection), template))
                }
            }
            DebugLogger.log("Injected ${extensionInjections.size} extension prompt(s)")
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
                        // In group context, prefix with sender name if different from current character
                        if (msg.senderName != null && msg.senderName != character.name) {
                            sb.append("${msg.senderName}: ")
                        }
                        sb.append(substituteMacros(cleanMessageContent(msg.content)))
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

        // === POST-HISTORY INSTRUCTIONS (injected as system message before assistant turn) ===
        if (character.postHistoryInstructions.isNotBlank()) {
            sb.append(wrapAsSystem(substituteMacros(character.postHistoryInstructions), template))
        }

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

        // Extension prompt injections
        if (extensionInjections.isNotEmpty()) {
            extensionInjections.forEach { injection ->
                if (injection.isNotBlank()) {
                    sb.append("[${substituteMacros(injection)}]\n")
                }
            }
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
                    val name = msg.senderName ?: (if (msg.isUser) userName else character.name)
                    sb.append("$name: ${substituteMacros(cleanMessageContent(msg.content))}\n")
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
     * Inject Author's Note, User Persona, World Info, and OAI preset depth-injection blocks
     * at correct depths in chat history.
     *
     * @param extraDepthInjections OAI preset blocks with injection_position=1. Each is injected
     *   at its injection_depth from the bottom of the chat history (depth 0 = after last message).
     */
    private fun injectDepthPrompts(
        chatHistory: List<ChatMessage>,
        extraDepthInjections: List<OaiPromptOrderItem> = emptyList()
    ): List<HistoryItem> {
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
        // OAI preset blocks at depth 0.
        // Added in REVERSED promptOrder so that after result.reversed() they appear in correct order.
        extraDepthInjections.filter { it.injectionDepth == 0 }.reversed().forEach { item ->
            val content = substituteMacros(item.content ?: "").trim()
            if (content.isNotBlank()) {
                DebugLogger.log("Injecting OAI block '${item.customLabel ?: item.id}' at depth 0 (role=${item.role})")
                result.add(HistoryItem.Injection(content, item.role))
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

            // Inject OAI preset depth blocks at this depth.
            // Reversed so correct order is restored after result.reversed().
            extraDepthInjections.filter { it.injectionDepth == depth }.reversed().forEach { item ->
                val content = substituteMacros(item.content ?: "").trim()
                if (content.isNotBlank()) {
                    DebugLogger.log("Injecting OAI block '${item.customLabel ?: item.id}' at depth $depth (role=${item.role})")
                    result.add(HistoryItem.Injection(content, item.role))
                }
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

        // Inject OAI preset blocks whose depth exceeds history size (goes to top of history).
        // Reversed so correct order is restored after result.reversed().
        extraDepthInjections.filter { it.injectionDepth > historySize }.reversed().forEach { item ->
            val content = substituteMacros(item.content ?: "").trim()
            if (content.isNotBlank()) {
                DebugLogger.log("Injecting OAI block '${item.customLabel ?: item.id}' at top (depth ${item.injectionDepth} > history $historySize, role=${item.role})")
                result.add(HistoryItem.Injection(content, item.role))
            }
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
     * Supports probability, regex keys, and recursive scanning.
     */
    private fun scanWorldInfo(
        chatHistory: List<ChatMessage>,
        newMessage: String = ""
    ): List<WorldInfoEntry> {
        val allEntries = chatContext.worldInfoEntries.filter { it.enabled }
        if (allEntries.isEmpty()) {
            DebugLogger.log("PromptBuilder: No enabled World Info entries to scan")
            return emptyList()
        }

        val settings = chatContext.worldInfoSettings
        val scanDepth = settings.depth

        // Build base scan text (recent messages + character context)
        val baseText = buildString {
            append(newMessage)
            append(" ")
            chatHistory.takeLast(scanDepth).forEach { msg ->
                append(msg.content)
                append(" ")
            }
            append(character.description)
            append(" ")
            append(character.scenario)
        }

        DebugLogger.logSection("PromptBuilder - World Info Scan")
        DebugLogger.logKeyValue("Scan depth", scanDepth)
        DebugLogger.logKeyValue("Recursive", settings.recursive)
        DebugLogger.logKeyValue("Base text length", baseText.length)

        // Run scan pass(es); recursive adds triggered content to next pass
        val triggered = mutableListOf<WorldInfoEntry>()
        val remainingEntries = allEntries.toMutableList()
        var scanText = baseText
        var passes = 0

        do {
            val passTriggered = mutableListOf<WorldInfoEntry>()
            val stillRemaining = mutableListOf<WorldInfoEntry>()
            val scanLower = scanText.lowercase()

            for (entry in remainingEntries) {
                if (matchesWorldInfoEntry(entry, scanText, scanLower)) {
                    passTriggered.add(entry)
                } else {
                    stillRemaining.add(entry)
                }
            }

            triggered.addAll(passTriggered)
            remainingEntries.clear()
            remainingEntries.addAll(stillRemaining)
            passes++

            // For recursive mode: add triggered content to scan text and repeat
            if (settings.recursive && passTriggered.isNotEmpty()) {
                val newContent = passTriggered.joinToString(" ") { it.content }
                scanText = "$scanText $newContent"
                DebugLogger.log("  Recursive pass $passes triggered ${passTriggered.size} entries")
            }
        } while (settings.recursive && passTriggered.isNotEmpty() && remainingEntries.isNotEmpty())

        // Apply token budget: sort by order, drop lowest priority entries if over cap
        val budgeted = applyTokenBudget(triggered, settings)

        DebugLogger.logSection("World Info Triggered Entries")
        DebugLogger.logKeyValue("Total triggered", budgeted.size)
        budgeted.forEach { entry ->
            DebugLogger.log("  - ${entry.comment}: position=${entry.position}, depth=${entry.depth}, content=${entry.content.take(50)}...")
        }

        return budgeted
    }

    // Returns true if entry's keys match the given scan text
    private fun matchesWorldInfoEntry(entry: WorldInfoEntry, scanText: String, scanLower: String): Boolean {
        // Constant entries are always included
        if (entry.constant) {
            DebugLogger.log("  Entry '${entry.comment}' is CONSTANT - always included")
            return true
        }

        // Probability check (skip if random roll fails)
        if (entry.probability < 100) {
            val roll = (Math.random() * 100).toInt()
            if (roll >= entry.probability) {
                DebugLogger.log("  Entry '${entry.comment}' failed probability check ($roll >= ${entry.probability})")
                return false
            }
        }

        // Check primary keys
        val primaryMatch = entry.key.any { key ->
            if (key.isBlank()) return@any false
            val matches = keyMatchesText(key, scanText, scanLower, entry.caseSensitive, entry.matchWholeWords)
            if (matches) DebugLogger.log("  Entry '${entry.comment}' matched key '$key'")
            matches
        }

        if (!primaryMatch) return false

        // If selective, also require at least one secondary key match
        if (entry.selective && entry.keysecondary.isNotEmpty()) {
            val secondaryMatch = entry.keysecondary.any { key ->
                if (key.isBlank()) return@any false
                keyMatchesText(key, scanText, scanLower, entry.caseSensitive, false)
            }
            if (!secondaryMatch) {
                DebugLogger.log("  Entry '${entry.comment}' - primary matched but secondary keys NOT matched")
            }
            return secondaryMatch
        }

        return true
    }

    // Match a single key against scan text; supports /regex/flags syntax
    private fun keyMatchesText(
        key: String,
        scanText: String,
        scanLower: String,
        caseSensitive: Boolean,
        wholeWords: Boolean
    ): Boolean {
        // Regex key: /pattern/ or /pattern/i
        if (key.startsWith("/") && key.length > 2) {
            val lastSlash = key.lastIndexOf('/')
            if (lastSlash > 0) {
                val pattern = key.substring(1, lastSlash)
                val flags = key.substring(lastSlash + 1)
                return try {
                    val options = if ('i' in flags) setOf(RegexOption.IGNORE_CASE) else emptySet()
                    Regex(pattern, options).containsMatchIn(scanText)
                } catch (e: Exception) { false }
            }
        }

        // Literal key
        val pattern = if (wholeWords) "\\b${Regex.escape(key)}\\b" else Regex.escape(key)
        val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return try {
            Regex(pattern, options).containsMatchIn(if (caseSensitive) scanText else scanLower)
        } catch (e: Exception) { false }
    }

    // Enforce token budget: drop lowest-priority entries if total content exceeds cap
    private fun applyTokenBudget(
        entries: List<WorldInfoEntry>,
        settings: WorldInfoSettings
    ): List<WorldInfoEntry> {
        if (settings.budgetCap <= 0) return entries

        // Sort by order (ascending = higher priority), accumulate until budget exceeded
        val sorted = entries.sortedBy { it.order }
        val result = mutableListOf<WorldInfoEntry>()
        var tokenCount = 0

        for (entry in sorted) {
            val entryTokens = estimateTokens(entry.content)
            if (tokenCount + entryTokens > settings.budgetCap) {
                DebugLogger.log("  WI budget cap (${settings.budgetCap}) reached at '${entry.comment}' — skipping")
                continue
            }
            result.add(entry)
            tokenCount += entryTokens
        }

        if (result.size < entries.size) {
            DebugLogger.log("  Token budget dropped ${entries.size - result.size} entries")
        }
        return result
    }

    // Rough token estimate: ~0.75 tokens per character on average
    private fun estimateTokens(text: String): Int = (text.length * 0.75).toInt().coerceAtLeast(1)

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
     * Strip ST-specific hidden annotations from message content before sending to the LLM.
     * Removes: [](#'...') and [](#"...") hidden author's note markers embedded in messages.
     * Also normalises \r\n → \n.
     */
    private fun cleanMessageContent(text: String): String {
        return text
            // Hidden annotations: [](#'anything') or [](#"anything")
            .replace(Regex("""\[\]\(#['"][^'"]*['"]\)"""), "")
            // Normalise Windows line endings
            .replace("\r\n", "\n")
            .trim()
    }

    /**
     * Strip only the ST comment macros {{// ... }} from a string without doing
     * any other substitutions. Used in init to blank-test the mainPromptOverride
     * before the PromptBuilder instance is fully constructed.
     */
    private fun stripCommentMacros(text: String): String =
        text.replace(Regex("\\{\\{//.*?\\}\\}", RegexOption.DOT_MATCHES_ALL), "")

    /**
     * Substitute macros like {{char}}, {{user}}, {{random:a,b,c}}, etc.
     */
    private fun substituteMacros(text: String): String {
        if (text.isBlank()) return text

        var result = text

        // Strip ST comment macros: {{// anything }} → empty string
        result = stripCommentMacros(result)

        // {{random:a,b,c,...}} → pick one value at random from the comma-separated list
        result = result.replace(Regex("\\{\\{random:(.*?)\\}\\}", RegexOption.IGNORE_CASE)) { match ->
            val options = match.groupValues[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (options.isEmpty()) "" else options.random()
        }

        return result
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
        data class Injection(val content: String, val role: String = "system") : HistoryItem()
    }
}
