package com.pockettavern.app.data.local.inference

/**
 * Curated catalog of freely-available on-device models in LiteRT-LM's **`.litertlm`** format.
 * PocketTavern never bundles weights — these are user-downloaded from HuggingFace on demand.
 *
 * [socVariants] maps a device SoC (Build.SOC_MODEL, uppercased — e.g. "SM8550", "MT6989") to an
 * accelerator-optimized model file for that chip. When the running device matches, we download
 * and run that variant (NPU/GPU) instead of the generic CPU/GPU build.
 */
data class CatalogModel(
    val name: String,
    val category: String,
    val description: String,
    val url: String,
    val sizeBytes: Long,
    val gated: Boolean = false,
    val socVariants: Map<String, String> = emptyMap(),
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val temperature: Float = 1.0f,
) {
    private fun idOf(u: String) =
        u.substringAfterLast('/').substringBefore('?')
            .removeSuffix(".litertlm").removeSuffix(".task").removeSuffix(".gguf")

    /** Download URL for this device — the SoC-optimized variant if one exists, else generic. */
    fun urlFor(soc: String?): String = socVariants[soc?.uppercase()] ?: url

    /** Local model id (filename w/o extension) for this device's chosen variant. */
    fun modelIdFor(soc: String?): String = idOf(urlFor(soc))

    val modelId: String get() = idOf(url)
}

object OnDeviceModelCatalog {
    private const val MB = 1024L * 1024L
    private const val LC = "https://huggingface.co/litert-community"

    // Per-SoC NPU/GPU-optimized variant maps (SoC token uppercased → file).
    private val GEMMA3_1B_SOC = mapOf(
        "SM8550" to "$LC/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_q4_ekv1280_sm8550.litertlm?download=true",
        "SM8650" to "$LC/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_q4_ekv1280_sm8650.litertlm?download=true",
        "SM8750" to "$LC/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm?download=true",
        "SM8850" to "$LC/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_q4_ekv1280_sm8850.litertlm?download=true",
        "MT6989" to "$LC/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_q4_ekv1280_mt6989.litertlm?download=true",
        "MT6991" to "$LC/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_q4_ekv1280_mt6991.litertlm?download=true",
        "MT6993" to "$LC/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_q4_ekv1280_mt6993.litertlm?download=true",
    )
    private val GEMMA3_270M_SOC = mapOf(
        "SM8550" to "$LC/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.qualcomm.sm8550.litertlm?download=true",
        "SM8650" to "$LC/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.qualcomm.sm8650.litertlm?download=true",
        "SM8750" to "$LC/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.qualcomm.sm8750.litertlm?download=true",
        "SM8850" to "$LC/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.qualcomm.sm8850.litertlm?download=true",
        "MT6991" to "$LC/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.mediatek.mt6991.litertlm?download=true",
        "MT6993" to "$LC/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.mediatek.mt6993.litertlm?download=true",
    )

    val models: List<CatalogModel> = listOf(
        // ── PocketTavern (official, RP-tuned, hosted on CharaVault) ───────────
        CatalogModel(
            name = "PocketTavern 1.5B (q8)", category = "PocketTavern (official)",
            description = "Our own RP-tuned model — lean prompt, stays in character, doesn't ramble. Apache-2.0.",
            url = "https://charavault.net/models/PocketTavern-1.5B.litertlm",
            sizeBytes = 1490 * MB, topK = 40, topP = 0.9f, temperature = 0.85f,
        ),
        CatalogModel(
            name = "PocketTavern 360M (q8)", category = "PocketTavern (official)",
            description = "Tiny RP-tuned model for low-end devices. Lean prompt. Apache-2.0.",
            url = "https://charavault.net/models/PocketTavern-360M.litertlm",
            sizeBytes = 356 * MB, topK = 40, topP = 0.9f, temperature = 0.85f,
        ),
        // ── Recommended ──────────────────────────────────────────────────────
        CatalogModel(
            name = "Qwen2.5 1.5B Instruct (q8)", category = "Recommended",
            description = "Balanced quality/size. Apache-2.0.",
            url = "$LC/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
            sizeBytes = 1523 * MB, topK = 40,
        ),
        CatalogModel(
            name = "Gemma 3 1B (q4)", category = "Recommended",
            description = "Small, capable. NPU-optimized on recent flagships. Gemma license.",
            url = "$LC/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm?download=true",
            sizeBytes = 557 * MB, socVariants = GEMMA3_1B_SOC, topK = 64,
        ),
        // ── Small / Fast ─────────────────────────────────────────────────────
        CatalogModel(
            name = "Gemma 3 270M (q8)", category = "Small / Fast",
            description = "Tiny — runs on almost anything. Limited quality. Gemma license.",
            url = "$LC/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.litertlm?download=true",
            sizeBytes = 289 * MB, socVariants = GEMMA3_270M_SOC, topK = 64,
        ),
        CatalogModel(
            name = "Qwen3 0.6B (int4)", category = "Small / Fast",
            description = "Small, newer Qwen3. Apache-2.0.",
            url = "$LC/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm?download=true",
            sizeBytes = 474 * MB, topK = 40,
        ),
        // ── Larger / Higher quality ──────────────────────────────────────────
        CatalogModel(
            name = "Qwen3 4B Instruct (int4)", category = "Larger / Higher quality",
            description = "Best general quality here (~2.5GB). Needs a capable device. Apache-2.0.",
            url = "$LC/Qwen3-4B-Instruct-2507/resolve/main/qwen3_4b_instruct_2507_mixed_int4.litertlm?download=true",
            sizeBytes = 2535 * MB, topK = 40,
        ),
        CatalogModel(
            name = "Phi-4 mini Instruct (q8)", category = "Larger / Higher quality",
            description = "~3.7GB, high quality. MIT.",
            url = "$LC/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
            sizeBytes = 3728 * MB, topK = 40,
        ),
        // ── Reasoning ────────────────────────────────────────────────────────
        CatalogModel(
            name = "DeepSeek-R1 Distill Qwen 1.5B (q8)", category = "Reasoning",
            description = "Shows reasoning/thinking tokens. MIT.",
            url = "$LC/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
            sizeBytes = 1748 * MB, topK = 40,
        ),
        // ── Uncensored (community builds — unvetted, use at your own risk) ─────
        CatalogModel(
            name = "Gemma 4 E2B Uncensored", category = "Uncensored (community)",
            description = "Community uncensored build for unfiltered RP (~2.4GB). Unofficial — quality varies.",
            url = "https://huggingface.co/PeppX/gemma-4-e2b-uncensored-litertlm/resolve/main/gemma-4-E2B-it-Uncensored-MAX.litertlm?download=true",
            sizeBytes = 2431 * MB, topK = 64,
        ),
        CatalogModel(
            name = "Gemma 4 E2B Abliterated", category = "Uncensored (community)",
            description = "Community abliterated build, refusals removed (~4.8GB). Unofficial.",
            url = "https://huggingface.co/nqd145/Gemma-4-E2B-it-abliterated-litertlm/resolve/main/Gemma-4-E2B-it-abliterated.litertlm?download=true",
            sizeBytes = 4830 * MB, topK = 64,
        ),
    )

    /**
     * GGUF models (llama.cpp / Llamatik backend). This is where the uncensored/RP ecosystem
     * lives — abliterated and RP-tuned fine-tunes that don't exist as .litertlm. Q4_K_M quants.
     */
    val ggufModels: List<CatalogModel> = listOf(
        // ── PocketTavern (official, RP-tuned) — GGUF supports repetition penalty ──
        CatalogModel(
            name = "PocketTavern 1.5B (Q4_K_M)", category = "PocketTavern (official)",
            description = "Our RP-tuned model, GGUF (repetition penalty available). Apache-2.0.",
            url = "https://charavault.net/models/PocketTavern-1.5B-Q4_K_M.gguf",
            sizeBytes = 941 * MB,
        ),
        CatalogModel(
            name = "PocketTavern 360M (Q4_K_M)", category = "PocketTavern (official)",
            description = "Tiny RP-tuned model for low-end devices, GGUF. Apache-2.0.",
            url = "https://charavault.net/models/PocketTavern-360M-Q4_K_M.gguf",
            sizeBytes = 259 * MB,
        ),
        CatalogModel(
            name = "Josiefied Llama 3.2 1B (uncensored)", category = "Small / Uncensored",
            description = "Tiny uncensored 1B — best for budget/4GB phones (~770MB Q4_K_M). Won't refuse.",
            url = "https://huggingface.co/mradermacher/Josiefied-Llama-3.2-1B-Instruct-abliterated-v1-GGUF/resolve/main/Josiefied-Llama-3.2-1B-Instruct-abliterated-v1.Q4_K_M.gguf?download=true",
            sizeBytes = 770 * MB,
        ),
        CatalogModel(
            name = "Josiefied Qwen2.5 1.5B (uncensored)", category = "Small / Uncensored",
            description = "Uncensored 1.5B — small but more capable than 1B (~940MB Q4_K_M).",
            url = "https://huggingface.co/Goekdeniz-Guelmez/Josiefied-Qwen2.5-1.5B-Instruct-abliterated-v2-gguf/resolve/main/josiefied-qwen2.5-1.5b-instruct-abliterated-v2.Q4_K_M.gguf?download=true",
            sizeBytes = 940 * MB,
        ),
        CatalogModel(
            name = "Impish LLAMA 3B (RP)", category = "Roleplay / Uncensored",
            description = "RP-tuned, uncensored 3B. Great for character chat (~1.9GB Q4_K_M).",
            url = "https://huggingface.co/SicariusSicariiStuff/Impish_LLAMA_3B_GGUF/resolve/main/Impish_LLAMA_3B-Q4_K_M.gguf?download=true",
            sizeBytes = 1925 * MB,
        ),
        CatalogModel(
            name = "Llama 3.2 3B Abliterated", category = "Roleplay / Uncensored",
            description = "Refusals removed (huihui abliterated). Solid all-rounder (~2.1GB Q4_K_M).",
            url = "https://huggingface.co/Hasaranga85/Llama-3.2-3B-Instruct-abliterated-Q4_K_M-GGUF/resolve/main/llama-3.2-3b-instruct-abliterated-q4_k_m.gguf?download=true",
            sizeBytes = 2137 * MB,
        ),
        CatalogModel(
            name = "Qwen2.5 3B Abliterated", category = "Roleplay / Uncensored",
            description = "Uncensored Qwen2.5 3B (~2.0GB Q4_K_M).",
            url = "https://huggingface.co/mradermacher/Qwen2.5-3B-Instruct-abliterated-GGUF/resolve/main/Qwen2.5-3B-Instruct-Abliterated.Q4_K_M.gguf?download=true",
            sizeBytes = 2007 * MB,
        ),
        CatalogModel(
            name = "Qwen2.5 1.5B Instruct", category = "Small / Fast",
            description = "Small, aligned (will refuse NSFW). Fast baseline (~1.1GB Q4_K_M). Apache-2.0.",
            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true",
            sizeBytes = 1120 * MB,
        ),
    )
}
