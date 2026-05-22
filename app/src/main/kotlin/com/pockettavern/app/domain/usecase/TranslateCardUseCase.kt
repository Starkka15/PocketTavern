package com.pockettavern.app.domain.usecase

import com.pockettavern.app.data.repository.LlmRepository
import com.pockettavern.app.domain.model.ApiConfiguration
import com.pockettavern.app.domain.model.PromptMessage
import com.pockettavern.app.domain.model.StreamEvent
import com.pockettavern.app.util.CharacterCardData
import javax.inject.Inject

class TranslateCardUseCase @Inject constructor(
    private val llmRepository: LlmRepository
) {
    enum class Field(val label: String) {
        DESCRIPTION("Description"),
        PERSONALITY("Personality"),
        SCENARIO("Scenario"),
        FIRST_MES("First Message"),
        ALTERNATE_GREETINGS("Alternate Greetings"),
        MES_EXAMPLE("Example Messages"),
        CREATOR_NOTES("Creator Notes")
    }

    // Separator used to batch-translate alternate greetings in one LLM call
    private val GREETING_SEP = "\n\n---GREETING---\n\n"

    // V9: collect all translations before applying any — partial writes forbidden
    suspend fun translate(
        data: CharacterCardData,
        fields: List<Field>,
        config: ApiConfiguration
    ): CharacterCardData {
        val results = mutableMapOf<Field, String>()

        for (field in fields) {
            if (field == Field.ALTERNATE_GREETINGS) continue  // handled below
            val text = getField(data, field)
            if (text.isNotBlank() && isNonEnglish(text)) {
                results[field] = translateText(text, config)
            }
        }

        // Alternate greetings: translate each non-English greeting individually
        var translatedGreetings: List<String>? = null
        if (Field.ALTERNATE_GREETINGS in fields && data.alternateGreetings.isNotEmpty()) {
            translatedGreetings = data.alternateGreetings.map { greeting ->
                if (greeting.isNotBlank() && isNonEnglish(greeting))
                    translateText(greeting, config)
                else greeting
            }
        }

        return applyAll(data, results, translatedGreetings)
    }

    fun isNonEnglish(text: String): Boolean {
        if (text.length < 20) return false
        val sample = text.take(400)
        val nonAscii = sample.count { it.code > 127 }
        return nonAscii.toFloat() / sample.length > 0.15f
    }

    fun hasNonEnglishGreeting(greetings: List<String>): Boolean =
        greetings.any { isNonEnglish(it) }

    private suspend fun translateText(text: String, config: ApiConfiguration): String {
        val messages = listOf(
            PromptMessage("system", SYSTEM_PROMPT),
            PromptMessage("user", text)
        )
        val flatPrompt = "$SYSTEM_PROMPT\n\n$text"

        var result = ""
        llmRepository.generate(
            prompt = flatPrompt,
            config = config,
            preset = null,
            stopSequences = emptyList(),
            messages = messages,
            oaiPreset = null
        ).collect { event ->
            if (event is StreamEvent.Complete) result = event.fullText
        }
        return result.trim().ifBlank { text }
    }

    private fun getField(data: CharacterCardData, field: Field): String = when (field) {
        Field.DESCRIPTION -> data.description
        Field.PERSONALITY -> data.personality
        Field.SCENARIO -> data.scenario
        Field.FIRST_MES -> data.firstMes
        Field.MES_EXAMPLE -> data.mesExample
        Field.CREATOR_NOTES -> data.creatorNotes
        Field.ALTERNATE_GREETINGS -> ""  // handled separately
    }

    private fun applyAll(
        data: CharacterCardData,
        t: Map<Field, String>,
        translatedGreetings: List<String>?
    ): CharacterCardData = data.copy(
        description = t[Field.DESCRIPTION] ?: data.description,
        personality = t[Field.PERSONALITY] ?: data.personality,
        scenario = t[Field.SCENARIO] ?: data.scenario,
        firstMes = t[Field.FIRST_MES] ?: data.firstMes,
        mesExample = t[Field.MES_EXAMPLE] ?: data.mesExample,
        creatorNotes = t[Field.CREATOR_NOTES] ?: data.creatorNotes,
        alternateGreetings = translatedGreetings ?: data.alternateGreetings
    )

    companion object {
        private const val SYSTEM_PROMPT = """Translate the following text to English.
Rules (follow exactly):
- Preserve {{user}}, {{char}}, {{original}} template variables verbatim
- Preserve <img src=(...)> tags verbatim
- Preserve all markdown: *italic*, **bold**, _italic_, ***bold italic***
- Preserve §text§ markers verbatim
- Preserve code blocks verbatim
- Translate only natural language content
Output ONLY the translated text with no explanation or preamble."""
    }
}
