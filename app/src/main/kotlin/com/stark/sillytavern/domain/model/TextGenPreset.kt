package com.stark.sillytavern.domain.model

data class TextGenPreset(
    val name: String,
    // Token limits (null = let server decide)
    val maxNewTokens: Int? = null,
    val minTokens: Int = 0,
    val truncationLength: Int = 2048,
    // Temperature and sampling
    val temperature: Float = 0.7f,
    val topP: Float = 0.5f,
    val topK: Int = 40,
    val topA: Float = 0f,
    val minP: Float = 0f,
    val typicalP: Float = 1.0f,
    val tfs: Float = 1.0f,
    // Repetition penalty
    val repPen: Float = 1.2f,
    val repPenRange: Int = 0,
    val repPenSlope: Float = 1f,
    val frequencyPenalty: Float = 0f,
    val presencePenalty: Float = 0f,
    // DRY sampler
    val dryMultiplier: Float = 0f,
    val dryBase: Float = 1.75f,
    val dryAllowedLength: Int = 2,
    val dryPenaltyLastN: Int = 0,
    // Mirostat
    val mirostatMode: Int = 0,
    val mirostatTau: Float = 5f,
    val mirostatEta: Float = 0.1f,
    // XTC
    val xtcThreshold: Float = 0.1f,
    val xtcProbability: Float = 0f,
    // Other sampling
    val skew: Float = 0f,
    val smoothingFactor: Float = 0f,
    val smoothingCurve: Float = 1f,
    // Guidance
    val guidanceScale: Float = 1f,
    // Token handling
    val addBosToken: Boolean = true,
    val banEosToken: Boolean = false,
    val skipSpecialTokens: Boolean = true
)
