package com.pockettavern.app.domain.model

/**
 * Represents the current API configuration from SillyTavern.
 * This is what we read from settings and display/edit in the UI.
 */
data class ApiConfiguration(
    // Main API type (textgenerationwebui, kobold, openai, etc.)
    val mainApi: String,

    // For text completion APIs
    val textGenType: String,  // koboldcpp, llamacpp, ooba, etc.
    val apiServer: String,    // Backend server URL

    // For chat completion APIs (when mainApi == "openai")
    val chatCompletionSource: String,  // openai, nanogpt, claude, etc.
    val customUrl: String?,            // Custom OpenAI-compatible endpoint

    // API key (used for cloud/authenticated backends)
    val apiKey: String = "",

    // Current model
    val currentModel: String,

    // Available models (fetched from status endpoint)
    val availableModels: List<AvailableModel> = emptyList(),

    // Connection status
    val isConnected: Boolean = false,
    val connectionError: String? = null,

    // Show reasoning/thinking tokens (DeepSeek R1, QwQ, etc.)
    val showThoughts: Boolean = false
) {
    /**
     * Whether this configuration uses chat completions (OpenAI-style) or text completions
     */
    val usesChatCompletions: Boolean
        get() = mainApi.lowercase() == "openai"

    /**
     * Canonical base URL for chat completion providers.
     * Cloud providers have hardcoded endpoints — they must never fall back to apiServer (a local IP).
     * customUrl always takes priority (allows overriding any provider's endpoint).
     * azure_openai and custom always require a customUrl.
     */
    val chatCompletionBaseUrl: String
        get() {
            // customUrl always wins — user explicitly overrode the endpoint
            if (!customUrl.isNullOrBlank()) return customUrl.trimEnd('/')
            return when (chatCompletionSource.lowercase()) {
                "openai"       -> "https://api.openai.com"
                "nanogpt"      -> "https://nano-gpt.com/api"
                "openrouter"   -> "https://openrouter.ai/api"
                "deepseek"     -> "https://api.deepseek.com"
                "mistralai"    -> "https://api.mistral.ai"
                "cohere"       -> "https://api.cohere.ai/v1"
                "perplexity"   -> "https://api.perplexity.ai"
                "groq"         -> "https://api.groq.com/openai"
                "makersuite"   -> "https://generativelanguage.googleapis.com/v1beta/openai"
                "ai21"         -> "https://api.ai21.com/studio/v1"
                "xai"          -> "https://api.x.ai"
                "fireworks"    -> "https://api.fireworks.ai/inference"
                "moonshot"     -> "https://api.moonshot.cn"
                "aimlapi"      -> "https://api.aimlapi.com"
                "pollinations" -> "https://text.pollinations.ai/openai"
                "chutes"       -> "https://llm.chutes.ai"
                "electronhub"  -> "https://api.electronhub.top"
                "siliconflow"  -> "https://api.siliconflow.cn"
                "zai"          -> "https://api.z.ai"
                "claude"       -> "https://api.anthropic.com"
                // azure_openai and custom require customUrl — warn but don't crash
                else           -> customUrl?.trimEnd('/') ?: ""
            }
        }

    /**
     * Human-readable display name for current API
     */
    val displayName: String
        get() = if (usesChatCompletions) {
            chatCompletionSourceDisplayName(chatCompletionSource)
        } else {
            textGenTypeDisplayName(textGenType)
        }

    companion object {
        fun textGenTypeDisplayName(type: String): String = when (type.lowercase()) {
            "koboldcpp" -> "KoboldCpp"
            "llamacpp" -> "llama.cpp"
            "ooba" -> "Text Gen WebUI"
            "vllm" -> "vLLM"
            "aphrodite" -> "Aphrodite"
            "tabby" -> "TabbyAPI"
            "ollama" -> "Ollama"
            "togetherai" -> "Together AI"
            "infermaticai" -> "Infermatic AI"
            "openrouter" -> "OpenRouter"
            "featherless" -> "Featherless"
            "mancer" -> "Mancer"
            "dreamgen" -> "DreamGen"
            "huggingface" -> "HuggingFace"
            "generic" -> "Generic"
            else -> type
        }

        fun chatCompletionSourceDisplayName(source: String): String = when (source.lowercase()) {
            "openai" -> "OpenAI"
            "claude" -> "Claude"
            "openrouter" -> "OpenRouter"
            "nanogpt" -> "NanoGPT"
            "deepseek" -> "DeepSeek"
            "mistralai" -> "Mistral AI"
            "cohere" -> "Cohere"
            "perplexity" -> "Perplexity"
            "groq" -> "Groq"
            "makersuite" -> "Google AI Studio"
            "vertexai" -> "Vertex AI"
            "ai21" -> "AI21"
            "xai" -> "xAI (Grok)"
            "fireworks" -> "Fireworks"
            "moonshot" -> "Moonshot"
            "aimlapi" -> "AIML API"
            "pollinations" -> "Pollinations"
            "chutes" -> "Chutes"
            "electronhub" -> "ElectronHub"
            "siliconflow" -> "SiliconFlow"
            "zai" -> "Z.AI"
            "azure_openai" -> "Azure OpenAI"
            "custom" -> "Custom"
            else -> source
        }

        val DEFAULT = ApiConfiguration(
            mainApi = "textgenerationwebui",
            textGenType = "koboldcpp",
            apiServer = "http://127.0.0.1:5001",
            chatCompletionSource = "openai",
            customUrl = null,
            apiKey = "",
            currentModel = "",
            availableModels = emptyList()
        )
    }
}

data class AvailableModel(
    val id: String,
    val name: String = id,
    val contextLength: Int? = null
)
