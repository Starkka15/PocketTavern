package com.pockettavern.app.ui.audio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Pure Kotlin SentencePiece Unigram tokenizer for Pocket TTS.
 * Uses vocab.json (token -> id) and token_scores.json (token -> logprob)
 * to perform exact Viterbi dynamic-programming segmentation.
 */
class PocketTtsTokenizer(
    private val vocab: Map<String, Long>,
    private val scores: Map<String, Float>,
) {
    private val unkId = vocab["<unk>"] ?: 0L
    private val minScore = (scores.values.minOrNull() ?: -100f) - 10f

    /**
     * Preprocesses text according to Pocket TTS specifications:
     * - Capitalizes first letter if lower
     * - Ensures ending punctuation
     * - Replaces newlines and multiple spaces
     * - Prepends SentencePiece whitespace prefix ( \u2581)
     */
    fun prepareText(rawText: String): String {
        var text = rawText.trim()
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\r", " ")
            .replace(Regex("\\s+"), " ")
            .replace(";", ",")

        if (text.isEmpty()) return ""

        if (!text[0].isUpperCase()) {
            text = text[0].uppercaseChar() + text.substring(1)
        }
        if (text.last().isLetterOrDigit()) {
            text += "."
        }

        // SentencePiece uses U+2581 (lower one eighth block) as space prefix
        return "\u2581" + text.replace(" ", "\u2581")
    }

    /**
     * Tokenizes input text into token IDs via Viterbi DP over the unigram vocabulary.
     */
    fun encode(text: String): LongArray {
        val prepared = prepareText(text)
        if (prepared.isEmpty()) return longArrayOf()

        val n = prepared.length
        // bestScore[i] = highest score for prefix prepared[0 until i]
        val bestScore = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val bestPrev = IntArray(n + 1) { -1 }
        val bestTokenId = LongArray(n + 1) { unkId }

        bestScore[0] = 0.0f

        val maxTokenLen = 32

        for (i in 0 until n) {
            if (bestScore[i] == Float.NEGATIVE_INFINITY) continue

            val maxJ = minOf(n, i + maxTokenLen)
            var matchedAny = false
            for (j in i + 1..maxJ) {
                val sub = prepared.substring(i, j)
                val id = vocab[sub]
                if (id != null) {
                    matchedAny = true
                    val score = scores[sub] ?: minScore
                    val total = bestScore[i] + score
                    if (total > bestScore[j]) {
                        bestScore[j] = total
                        bestPrev[j] = i
                        bestTokenId[j] = id
                    }
                }
            }

            // Fallback for unknown character at i
            if (!matchedAny && bestScore[i + 1] < bestScore[i] + minScore) {
                bestScore[i + 1] = bestScore[i] + minScore
                bestPrev[i + 1] = i
                bestTokenId[i + 1] = unkId
            }
        }

        // Backtrack
        val tokens = mutableListOf<Long>()
        var curr = n
        while (curr > 0) {
            val prev = bestPrev[curr]
            if (prev < 0) {
                tokens.add(unkId)
                curr -= 1
            } else {
                tokens.add(bestTokenId[curr])
                curr = prev
            }
        }
        tokens.reverse()
        return tokens.toLongArray()
    }

    companion object {
        fun fromFiles(vocabFile: File, tokenScoresFile: File): PocketTtsTokenizer {
            val json = Json { ignoreUnknownKeys = true }

            val vocabObj = json.parseToJsonElement(vocabFile.readText()).jsonObject
            val vocabMap = vocabObj.entries.associate { (k, v) ->
                k to (v.jsonPrimitive.content.toLongOrNull() ?: 0L)
            }

            val scoresObj = json.parseToJsonElement(tokenScoresFile.readText()).jsonObject
            val scoresMap = scoresObj.entries.associate { (k, v) ->
                k to (v.jsonPrimitive.content.toFloatOrNull() ?: 0f)
            }

            return PocketTtsTokenizer(vocabMap, scoresMap)
        }
    }
}
