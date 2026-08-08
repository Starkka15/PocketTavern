package com.pockettavern.app.domain.usecase

import com.pockettavern.app.data.repository.LlmRepository
import com.pockettavern.app.domain.model.ApiConfiguration
import com.pockettavern.app.domain.model.ChatMessage
import com.pockettavern.app.domain.model.PromptMessage
import com.pockettavern.app.domain.model.StreamEvent
import javax.inject.Inject

class SummarizeHistoryUseCase @Inject constructor(
    private val llmRepository: LlmRepository
) {
    suspend fun summarize(turns: List<ChatMessage>, config: ApiConfiguration): String {
        if (turns.isEmpty()) return ""

        val messages = mutableListOf<PromptMessage>()
        messages.add(
            PromptMessage(
                role = "system",
                content = "Summarize the key facts, relationship developments, and ongoing plot threads " +
                    "from the conversation excerpt below as concise bullet points. " +
                    "Maximum 300 tokens. Output only the bullet list — no preamble, no roleplay."
            )
        )
        for (turn in turns) {
            val role = if (turn.isUser) "user" else "assistant"
            val content = turn.content.take(1000)
            if (content.isNotBlank()) messages.add(PromptMessage(role, content))
        }
        // Some APIs reject system→assistant without a user turn first
        if (messages.size > 1 && messages[1].role == "assistant") {
            messages.add(1, PromptMessage("user", "[conversation excerpt]"))
        }

        // Use a flat prompt for text-completion APIs, structured messages for chat APIs
        val flatPrompt = buildString {
            append("Summarize key facts and plot threads from this conversation as bullet points (max 300 tokens):\n\n")
            for (turn in turns) {
                val name = if (turn.isUser) "User" else "Character"
                append("$name: ${turn.content.take(500)}\n")
            }
            append("\nSummary bullets:")
        }

        var result = ""
        llmRepository.generate(
            prompt = flatPrompt,
            config = config,
            preset = null,
            stopSequences = emptyList(),
            messages = if (config.usesChatCompletions) messages else null,
            oaiPreset = null
        ).collect { event ->
            when (event) {
                is StreamEvent.Complete -> result = event.fullText.trim()
                else -> {}
            }
        }
        return result
    }

    /**
     * Consolidate an overgrown memory block into a fresh deduplicated summary.
     * Memory blocks are appended to on every summarization pass; without this a
     * marathon chat accumulates an ever-growing [Memory] preamble.
     */
    suspend fun compact(memoryBlock: String, config: ApiConfiguration): String {
        if (memoryBlock.isBlank()) return memoryBlock

        val instruction = "The following are accumulated memory notes from a long roleplay conversation, " +
            "oldest first. Consolidate them into a single deduplicated bullet list. Keep every " +
            "still-relevant fact, relationship development, and open plot thread; merge duplicates; " +
            "prefer the newest version of any fact that changed. Maximum 300 tokens. " +
            "Output only the bullet list — no preamble, no roleplay."

        val messages = listOf(
            PromptMessage("system", instruction),
            PromptMessage("user", memoryBlock)
        )
        val flatPrompt = "$instruction\n\n$memoryBlock\n\nConsolidated bullets:"

        var result = ""
        llmRepository.generate(
            prompt = flatPrompt,
            config = config,
            preset = null,
            stopSequences = emptyList(),
            messages = if (config.usesChatCompletions) messages else null,
            oaiPreset = null
        ).collect { event ->
            when (event) {
                is StreamEvent.Complete -> result = event.fullText.trim()
                else -> {}
            }
        }
        return result
    }
}
