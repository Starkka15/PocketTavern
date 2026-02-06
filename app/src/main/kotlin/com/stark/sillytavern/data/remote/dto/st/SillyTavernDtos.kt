package com.stark.sillytavern.data.remote.dto.st

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CsrfTokenResponse(
    val token: String? = null
)

@Serializable
data class SettingsResponse(
    @SerialName("textgenerationwebui_settings")
    val textGenSettings: TextGenSettings? = null,
    val api_type: String? = null,
    val api_server: String? = null
)

@Serializable
data class TextGenSettings(
    val type: String? = null,
    val temp: Float? = null,
    @SerialName("top_p")
    val topP: Float? = null,
    @SerialName("rep_pen")
    val repPen: Float? = null,
    @SerialName("server_urls")
    val serverUrls: Map<String, String>? = null
)

@Serializable
data class CharacterDto(
    val name: String? = null,
    val avatar: String? = null,
    val description: String? = null,
    val personality: String? = null,
    val scenario: String? = null,
    @SerialName("first_mes")
    val firstMes: String? = null,
    val greeting: String? = null,
    @SerialName("mes_example")
    val mesExample: String? = null,
    @SerialName("creator_notes")
    val creatorNotes: String? = null,
    @SerialName("system_prompt")
    val systemPrompt: String? = null,
    val tags: List<String>? = null,
    val creator: String? = null,
    // Top-level fields from SillyTavern
    val talkativeness: Float? = null,
    val fav: Boolean? = null,
    val chat: String? = null,
    @SerialName("create_date")
    val createDate: String? = null,
    val data: CharacterDataDto? = null
)

@Serializable
data class CharacterDataDto(
    val name: String? = null,
    val description: String? = null,
    val personality: String? = null,
    val scenario: String? = null,
    @SerialName("first_mes")
    val firstMes: String? = null,
    @SerialName("mes_example")
    val mesExample: String? = null,
    @SerialName("creator_notes")
    val creatorNotes: String? = null,
    @SerialName("system_prompt")
    val systemPrompt: String? = null,
    @SerialName("post_history_instructions")
    val postHistoryInstructions: String? = null,
    val tags: List<String>? = null,
    val creator: String? = null,
    @SerialName("alternate_greetings")
    val alternateGreetings: List<String>? = null,
    // Extensions including attached world info
    val extensions: CharacterExtensionsDto? = null,
    // Embedded character book (lorebook)
    @SerialName("character_book")
    val characterBook: EmbeddedCharacterBookDto? = null
)

@Serializable
data class CharacterExtensionsDto(
    // Attached world info file name
    val world: String? = null,
    // Other common extensions
    val talkativeness: String? = null,
    val fav: Boolean? = null,
    @SerialName("depth_prompt")
    val depthPrompt: DepthPromptDto? = null
)

@Serializable
data class DepthPromptDto(
    val prompt: String? = null,
    val depth: Int? = null,
    val role: String? = null  // "system", "user", or "assistant"
)

@Serializable
data class EmbeddedCharacterBookDto(
    val name: String? = null,
    val description: String? = null,
    val entries: List<EmbeddedBookEntryDto>? = null,
    @SerialName("scan_depth")
    val scanDepth: Int? = null,
    @SerialName("token_budget")
    val tokenBudget: Int? = null,
    @SerialName("recursive_scanning")
    val recursiveScanning: Boolean? = null
)

@Serializable
data class EmbeddedBookEntryDto(
    val id: Int? = null,
    val keys: List<String> = emptyList(),
    @SerialName("secondary_keys")
    val secondaryKeys: List<String> = emptyList(),
    val content: String = "",
    val comment: String? = null,
    val name: String? = null,
    val enabled: Boolean = true,
    val constant: Boolean = false,
    val selective: Boolean = false,
    @SerialName("insertion_order")
    val insertionOrder: Int? = null,
    val priority: Int? = null,
    val position: String? = null,
    @SerialName("case_sensitive")
    val caseSensitive: Boolean = false
)

@Serializable
data class GetCharacterRequest(
    @SerialName("avatar_url")
    val avatarUrl: String
)

@Serializable
data class ExportCharacterRequest(
    @SerialName("avatar_url")
    val avatarUrl: String,
    val format: String = "png"
)

@Serializable
data class CreateCharacterRequest(
    @SerialName("ch_name")
    val chName: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    @SerialName("first_mes")
    val firstMes: String = "",
    @SerialName("mes_example")
    val mesExample: String = "",
    @SerialName("creator_notes")
    val creatorNotes: String = "",
    val talkativeness: Float = 0.5f,
    val fav: String = "false",
    val tags: List<String> = emptyList(),
    val creator: String = "",
    @SerialName("character_version")
    val characterVersion: String = "",
    @SerialName("alternate_greetings")
    val alternateGreetings: List<String> = emptyList(),
    // Depth prompt fields
    @SerialName("depth_prompt_prompt")
    val depthPromptPrompt: String = "",
    @SerialName("depth_prompt_depth")
    val depthPromptDepth: Int = 4,
    @SerialName("depth_prompt_role")
    val depthPromptRole: String = "system"
)

@Serializable
data class EditCharacterRequest(
    @SerialName("avatar_url")
    val avatarUrl: String,
    @SerialName("ch_name")
    val chName: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    @SerialName("first_mes")
    val firstMes: String = "",
    @SerialName("mes_example")
    val mesExample: String = "",
    @SerialName("creator_notes")
    val creatorNotes: String = "",
    val talkativeness: Float = 0.5f,
    val fav: String = "false",  // SillyTavern expects string "true"/"false"
    val tags: List<String> = emptyList(),
    val creator: String = "",
    @SerialName("character_version")
    val characterVersion: String = "",
    // System prompt (per-character custom system prompt)
    @SerialName("system_prompt")
    val systemPrompt: String = "",
    // Post history instructions
    @SerialName("post_history_instructions")
    val postHistoryInstructions: String = "",
    // Depth prompt fields (not nested in extensions for edit endpoint)
    @SerialName("depth_prompt_prompt")
    val depthPromptPrompt: String = "",
    @SerialName("depth_prompt_depth")
    val depthPromptDepth: Int = 4,
    @SerialName("depth_prompt_role")
    val depthPromptRole: String = "system",
    // World info attachment (stored in extensions.world)
    val world: String? = null,
    // Chat and create date for preserving existing values
    val chat: String? = null,
    @SerialName("create_date")
    val createDate: String? = null
)

@Serializable
data class EditCharacterAttributeRequest(
    @SerialName("avatar_url")
    val avatarUrl: String,
    @SerialName("ch_name")
    val chName: String,
    val field: String,
    val value: String
)

@Serializable
data class DeleteCharacterRequest(
    @SerialName("avatar_url")
    val avatarUrl: String,
    @SerialName("delete_chats")
    val deleteChats: Boolean = true
)

@Serializable
data class GetChatsRequest(
    @SerialName("avatar_url")
    val avatarUrl: String,
    val simple: Boolean = true
)

@Serializable
data class ChatInfoDto(
    @SerialName("file_name")
    val fileName: String? = null,
    @SerialName("last_mes")
    val lastMes: String? = null,
    @SerialName("file_size")
    val fileSize: Long? = null
)

@Serializable
data class GetChatRequest(
    @SerialName("ch_name")
    val chName: String,
    @SerialName("file_name")
    val fileName: String,
    @SerialName("avatar_url")
    val avatarUrl: String
)

@Serializable
data class ChatMetadata(
    val integrity: String? = null,
    // Author's Note fields (per-chat)
    @SerialName("note_prompt")
    val notePrompt: String? = null,
    @SerialName("note_interval")
    val noteInterval: Int? = null,
    @SerialName("note_depth")
    val noteDepth: Int? = null,
    @SerialName("note_position")
    val notePosition: Int? = null,
    @SerialName("note_role")
    val noteRole: Int? = null
)

@Serializable
data class ChatMessageDto(
    val name: String? = null,
    @SerialName("is_user")
    val isUser: Boolean = false,
    val mes: String? = null,
    val content: String? = null,
    @SerialName("send_date")
    val sendDate: String? = null,
    @SerialName("chat_metadata")
    val chatMetadata: ChatMetadata? = null
)

@Serializable
data class SaveChatRequest(
    @SerialName("ch_name")
    val chName: String,
    @SerialName("file_name")
    val fileName: String,
    @SerialName("avatar_url")
    val avatarUrl: String,
    val chat: List<SaveChatMessageDto>
)

@Serializable
data class SaveChatMessageDto(
    val name: String,
    @SerialName("is_user")
    val isUser: Boolean,
    val mes: String,
    @SerialName("send_date")
    val sendDate: String,
    @SerialName("chat_metadata")
    val chatMetadata: ChatMetadata? = null
)

@Serializable
data class DeleteChatRequest(
    @SerialName("ch_name")
    val chName: String,
    @SerialName("file_name")
    val fileName: String,
    @SerialName("avatar_url")
    val avatarUrl: String,
    // Also include as 'chatfile' in case that's what the endpoint expects
    @SerialName("chatfile")
    val chatfile: String
)

@Serializable
data class TextCompletionRequest(
    @SerialName("api_server")
    val apiServer: String,
    @SerialName("api_type")
    val apiType: String,
    val prompt: String,
    // Token limits (null = let server decide)
    @SerialName("max_new_tokens")
    val maxNewTokens: Int? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("n_predict")
    val nPredict: Int? = null,
    @SerialName("num_predict")
    val numPredict: Int? = null,
    @SerialName("min_tokens")
    val minTokens: Int = 0,
    @SerialName("truncation_length")
    val truncationLength: Int = 2048,
    @SerialName("num_ctx")
    val numCtx: Int = 2048,
    // Temperature and sampling
    val temperature: Float = 1.0f,
    @SerialName("top_p")
    val topP: Float = 1.0f,
    @SerialName("top_k")
    val topK: Int = 0,
    @SerialName("top_a")
    val topA: Float = 0f,
    @SerialName("min_p")
    val minP: Float = 0.1f,
    @SerialName("typical_p")
    val typicalP: Float = 1f,
    val typical: Float = 1f,
    val tfs: Float = 1f,
    // Repetition penalty
    @SerialName("rep_pen")
    val repPen: Float = 1.18f,
    @SerialName("repetition_penalty")
    val repetitionPenalty: Float = 1.18f,
    @SerialName("repeat_penalty")
    val repeatPenalty: Float = 1.18f,
    @SerialName("rep_pen_range")
    val repPenRange: Int = 2048,
    @SerialName("repetition_penalty_range")
    val repetitionPenaltyRange: Int = 2048,
    @SerialName("repeat_last_n")
    val repeatLastN: Int = 2048,
    @SerialName("rep_pen_slope")
    val repPenSlope: Float = 0f,
    @SerialName("frequency_penalty")
    val frequencyPenalty: Float = 0f,
    @SerialName("presence_penalty")
    val presencePenalty: Float = 0f,
    // DRY sampler
    @SerialName("dry_multiplier")
    val dryMultiplier: Float = 0f,
    @SerialName("dry_base")
    val dryBase: Float = 1.75f,
    @SerialName("dry_allowed_length")
    val dryAllowedLength: Int = 2,
    @SerialName("dry_penalty_last_n")
    val dryPenaltyLastN: Int = 0,
    @SerialName("dry_sequence_breakers")
    val drySequenceBreakers: String = "[\"\\n\",\":\",\"\\\"\",\"*\"]",
    // Mirostat
    @SerialName("mirostat_mode")
    val mirostatMode: Int = 0,
    val mirostat: Int = 0,
    @SerialName("mirostat_tau")
    val mirostatTau: Float = 5f,
    @SerialName("mirostat_eta")
    val mirostatEta: Float = 0.1f,
    // XTC
    @SerialName("xtc_threshold")
    val xtcThreshold: Float = 0.1f,
    @SerialName("xtc_probability")
    val xtcProbability: Float = 0f,
    // Other sampling
    val skew: Float = 0f,
    val nsigma: Float = 0f,
    @SerialName("top_n_sigma")
    val topNSigma: Float = 0f,
    @SerialName("min_keep")
    val minKeep: Int = 0,
    @SerialName("smoothing_factor")
    val smoothingFactor: Float = 0f,
    @SerialName("smoothing_curve")
    val smoothingCurve: Float = 1f,
    @SerialName("sampler_order")
    val samplerOrder: List<Int> = listOf(6, 0, 1, 3, 4, 2, 5),
    // Guidance
    @SerialName("guidance_scale")
    val guidanceScale: Float = 1f,
    @SerialName("negative_prompt")
    val negativePrompt: String = "",
    // Token handling
    @SerialName("add_bos_token")
    val addBosToken: Boolean = true,
    @SerialName("ban_eos_token")
    val banEosToken: Boolean = false,
    @SerialName("skip_special_tokens")
    val skipSpecialTokens: Boolean = true,
    @SerialName("ignore_eos")
    val ignoreEos: Boolean = false,
    @SerialName("custom_token_bans")
    val customTokenBans: String = "",
    @SerialName("banned_strings")
    val bannedStrings: List<String> = emptyList(),
    // Stopping
    @SerialName("stopping_strings")
    val stoppingStrings: List<String> = listOf("\nUser:", "\n\nUser:", "<|", "\n\n\n"),
    val stop: List<String> = listOf("\nUser:", "\n\nUser:", "<|", "\n\n\n"),
    @SerialName("trim_stop")
    val trimStop: Boolean = true,
    // Other
    @SerialName("max_tokens_second")
    val maxTokensSecond: Int = 0,
    @SerialName("include_reasoning")
    val includeReasoning: Boolean = true,
    val stream: Boolean = false
)

// Chat Completion Request (OpenAI-style APIs via SillyTavern proxy)
@Serializable
data class ChatCompletionRequest(
    // SillyTavern proxy fields
    @SerialName("chat_completion_source")
    val chatCompletionSource: String = "openai",  // openai, custom, claude, nanogpt, etc.
    @SerialName("reverse_proxy")
    val reverseProxy: String? = null,
    @SerialName("proxy_password")
    val proxyPassword: String? = null,
    @SerialName("custom_url")
    val customUrl: String? = null,
    @SerialName("custom_include_headers")
    val customIncludeHeaders: Boolean = false,
    @SerialName("custom_include_body")
    val customIncludeBody: Boolean = false,
    // OpenAI-compatible fields
    val messages: List<ChatCompletionMessage>,
    val model: String = "",
    @SerialName("max_tokens")
    val maxTokens: Int = 350,
    val temperature: Float = 1.0f,
    @SerialName("top_p")
    val topP: Float = 1.0f,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("top_a")
    val topA: Float? = null,
    @SerialName("min_p")
    val minP: Float? = null,
    @SerialName("typical_p")
    val typicalP: Float? = null,
    @SerialName("frequency_penalty")
    val frequencyPenalty: Float = 0f,
    @SerialName("presence_penalty")
    val presencePenalty: Float = 0f,
    @SerialName("repetition_penalty")
    val repetitionPenalty: Float? = null,
    val stop: List<String>? = null,
    val stream: Boolean = false
)

@Serializable
data class ChatCompletionMessage(
    val role: String,
    val content: String
)

@Serializable
data class TextCompletionResponse(
    val text: String? = null,
    val content: String? = null,
    val results: List<TextResult>? = null,
    val choices: List<Choice>? = null
)

@Serializable
data class TextResult(
    val text: String? = null
)

@Serializable
data class Choice(
    val text: String? = null,
    val message: ChoiceMessage? = null
)

@Serializable
data class ChoiceMessage(
    val content: String? = null
)

@Serializable
data class StreamChunk(
    val choices: List<StreamChoice>? = null,
    val text: String? = null,
    val token: String? = null,
    val content: String? = null
)

@Serializable
data class StreamChoice(
    val delta: StreamDelta? = null,
    val text: String? = null
)

@Serializable
data class StreamDelta(
    val content: String? = null
)

// MultiUserMode authentication
@Serializable
data class LoginRequest(
    val handle: String,
    val password: String
)

// User Management DTOs
@Serializable
data class UserInfoResponse(
    val handle: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val admin: Boolean = false,
    val password: String? = null,
    val created: Long? = null
)

@Serializable
data class ChangePasswordRequest(
    val handle: String,
    @SerialName("oldPassword")
    val oldPassword: String? = null,
    @SerialName("newPassword")
    val newPassword: String? = null
)

// Full Settings Response (from /api/settings/get)
// Note: 'settings', 'textgenerationwebui_presets', and 'openai_settings' come as JSON strings
// but 'instruct', 'context', 'sysprompt' are actual JSON objects
@Serializable
data class FullSettingsResponse(
    val settings: String? = null,
    @SerialName("textgenerationwebui_presets")
    val textGenPresets: List<String>? = null,
    @SerialName("textgenerationwebui_preset_names")
    val textGenPresetNames: List<String>? = null,
    // OpenAI/Chat Completion presets (come as JSON strings, need parsing)
    @SerialName("openai_settings")
    val openaiSettings: List<String>? = null,
    @SerialName("openai_setting_names")
    val openaiSettingNames: List<String>? = null,
    val instruct: List<JsonObject>? = null,
    @SerialName("instruct_preset_names")
    val instructPresetNames: List<String>? = null,
    val context: List<JsonObject>? = null,
    @SerialName("context_preset_names")
    val contextPresetNames: List<String>? = null,
    val sysprompt: List<JsonObject>? = null,
    @SerialName("sysprompt_preset_names")
    val syspromptPresetNames: List<String>? = null
)

// Text Generation Preset (sampler parameters)
@Serializable
data class TextGenPresetDto(
    val temp: Float? = null,
    val temperature: Float? = null,
    @SerialName("top_p")
    val topP: Float? = null,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("rep_pen")
    val repPen: Float? = null,
    @SerialName("repetition_penalty")
    val repetitionPenalty: Float? = null,
    @SerialName("rep_pen_range")
    val repPenRange: Int? = null,
    @SerialName("max_new_tokens")
    val maxNewTokens: Int? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("min_length")
    val minLength: Int? = null,
    @SerialName("typical_p")
    val typicalP: Float? = null,
    @SerialName("min_p")
    val minP: Float? = null
)

// Preset Save/Delete Requests
@Serializable
data class SavePresetRequest(
    val name: String,
    val preset: JsonObject,
    @SerialName("apiId")
    val apiId: String = "textgenerationwebui"
)

@Serializable
data class DeletePresetRequest(
    val name: String,
    @SerialName("apiId")
    val apiId: String = "textgenerationwebui"
)

// Instruct Template DTO
@Serializable
data class InstructTemplateDto(
    val name: String? = null,
    @SerialName("system_prompt")
    val systemPrompt: String? = null,
    @SerialName("input_sequence")
    val inputSequence: String? = null,
    @SerialName("input_suffix")
    val inputSuffix: String? = null,
    @SerialName("output_sequence")
    val outputSequence: String? = null,
    @SerialName("output_suffix")
    val outputSuffix: String? = null,
    @SerialName("first_output_sequence")
    val firstOutputSequence: String? = null,
    @SerialName("last_output_sequence")
    val lastOutputSequence: String? = null,
    @SerialName("system_sequence")
    val systemSequence: String? = null,
    @SerialName("system_suffix")
    val systemSuffix: String? = null,
    @SerialName("stop_sequence")
    val stopSequence: String? = null,
    @SerialName("wrap")
    val wrap: Boolean? = null
)

// Context Template DTO
@Serializable
data class ContextTemplateDto(
    val name: String? = null,
    @SerialName("story_string")
    val storyString: String? = null,
    @SerialName("chat_start")
    val chatStart: String? = null,
    @SerialName("example_separator")
    val exampleSeparator: String? = null
)

// System Prompt DTO
@Serializable
data class SystemPromptDto(
    val name: String? = null,
    val content: String? = null
)

// ========== Backend Status DTOs ==========

// Text Completion Status Request
@Serializable
data class TextCompletionStatusRequest(
    @SerialName("api_server")
    val apiServer: String,
    @SerialName("api_type")
    val apiType: String
)

// Chat Completion Status Request
@Serializable
data class ChatCompletionStatusRequest(
    @SerialName("chat_completion_source")
    val chatCompletionSource: String,
    @SerialName("reverse_proxy")
    val reverseProxy: String? = null,
    @SerialName("proxy_password")
    val proxyPassword: String? = null,
    @SerialName("custom_url")
    val customUrl: String? = null,
    @SerialName("custom_include_headers")
    val customIncludeHeaders: String? = null
)

// Backend Status Response (common for both text and chat completions)
@Serializable
data class BackendStatusResponse(
    val result: String? = null,  // Current model name or "Valid"
    val data: List<ModelInfo>? = null  // Available models
)

@Serializable
data class ModelInfo(
    val id: String,
    val name: String? = null,
    val created: Long? = null,
    val owned_by: String? = null,
    // Additional fields that some providers return
    val context_length: Int? = null,
    val pricing: ModelPricing? = null
)

@Serializable
data class ModelPricing(
    val prompt: String? = null,
    val completion: String? = null,
    val input_cache_read: String? = null,
    val input_cache_write: String? = null
)

// ========== API Configuration ==========

// Enum-like objects for API types (matches SillyTavern's constants)
object MainApiTypes {
    const val TEXT_GEN_WEBUI = "textgenerationwebui"
    const val KOBOLD = "kobold"
    const val KOBOLD_HORDE = "koboldhorde"
    const val NOVEL = "novel"
    const val OOBA = "ooba"
    const val OPENAI = "openai"  // This uses chat_completion_source for the actual provider

    val textCompletionApis = setOf(TEXT_GEN_WEBUI, KOBOLD, KOBOLD_HORDE, NOVEL, OOBA)
    val chatCompletionApis = setOf(OPENAI)

    val all = listOf(
        TEXT_GEN_WEBUI to "Text Generation WebUI",
        KOBOLD to "KoboldAI",
        KOBOLD_HORDE to "KoboldAI Horde",
        NOVEL to "NovelAI",
        OOBA to "Oobabooga",
        OPENAI to "Chat Completion (OpenAI-style)"
    )
}

object TextGenTypes {
    const val OOBA = "ooba"
    const val MANCER = "mancer"
    const val VLLM = "vllm"
    const val APHRODITE = "aphrodite"
    const val TABBY = "tabby"
    const val KOBOLDCPP = "koboldcpp"
    const val TOGETHERAI = "togetherai"
    const val LLAMACPP = "llamacpp"
    const val OLLAMA = "ollama"
    const val INFERMATICAI = "infermaticai"
    const val DREAMGEN = "dreamgen"
    const val OPENROUTER = "openrouter"
    const val FEATHERLESS = "featherless"
    const val HUGGINGFACE = "huggingface"
    const val GENERIC = "generic"

    val all = listOf(
        KOBOLDCPP to "KoboldCpp",
        LLAMACPP to "llama.cpp",
        OOBA to "Text Generation WebUI (Ooba)",
        VLLM to "vLLM",
        APHRODITE to "Aphrodite",
        TABBY to "TabbyAPI",
        OLLAMA to "Ollama",
        TOGETHERAI to "Together AI",
        INFERMATICAI to "Infermatic AI",
        OPENROUTER to "OpenRouter (Text)",
        FEATHERLESS to "Featherless",
        MANCER to "Mancer",
        DREAMGEN to "DreamGen",
        HUGGINGFACE to "HuggingFace",
        GENERIC to "Generic OpenAI-compatible"
    )
}

object ChatCompletionSources {
    const val OPENAI = "openai"
    const val CLAUDE = "claude"
    const val OPENROUTER = "openrouter"
    const val AI21 = "ai21"
    const val MAKERSUITE = "makersuite"
    const val VERTEXAI = "vertexai"
    const val MISTRALAI = "mistralai"
    const val CUSTOM = "custom"
    const val COHERE = "cohere"
    const val PERPLEXITY = "perplexity"
    const val GROQ = "groq"
    const val ELECTRONHUB = "electronhub"
    const val CHUTES = "chutes"
    const val NANOGPT = "nanogpt"
    const val DEEPSEEK = "deepseek"
    const val AIMLAPI = "aimlapi"
    const val XAI = "xai"
    const val POLLINATIONS = "pollinations"
    const val MOONSHOT = "moonshot"
    const val FIREWORKS = "fireworks"
    const val AZURE_OPENAI = "azure_openai"
    const val ZAI = "zai"
    const val SILICONFLOW = "siliconflow"

    val all = listOf(
        OPENAI to "OpenAI",
        CLAUDE to "Anthropic Claude",
        OPENROUTER to "OpenRouter",
        NANOGPT to "NanoGPT",
        DEEPSEEK to "DeepSeek",
        MISTRALAI to "Mistral AI",
        COHERE to "Cohere",
        PERPLEXITY to "Perplexity",
        GROQ to "Groq",
        MAKERSUITE to "Google AI Studio",
        VERTEXAI to "Google Vertex AI",
        AI21 to "AI21",
        XAI to "xAI (Grok)",
        FIREWORKS to "Fireworks AI",
        MOONSHOT to "Moonshot",
        AIMLAPI to "AIML API",
        POLLINATIONS to "Pollinations (Free)",
        CHUTES to "Chutes",
        ELECTRONHUB to "ElectronHub",
        SILICONFLOW to "SiliconFlow",
        ZAI to "Z.AI",
        AZURE_OPENAI to "Azure OpenAI",
        CUSTOM to "Custom OpenAI-compatible"
    )
}

// ========== World Info / Lorebook DTOs ==========

@Serializable
data class WorldInfoListItem(
    @SerialName("file_id")
    val fileId: String,
    val name: String,
    val extensions: JsonObject? = null
)

@Serializable
data class GetWorldInfoRequest(
    val name: String
)

@Serializable
data class WorldInfoResponse(
    val entries: Map<String, WorldInfoEntryDto>? = null,
    val name: String? = null,
    val extensions: JsonObject? = null
)

@Serializable
data class WorldInfoEntryDto(
    val uid: Int? = null,
    val key: List<String> = emptyList(),
    val keysecondary: List<String> = emptyList(),
    val content: String = "",
    val comment: String = "",
    val constant: Boolean = false,
    val selective: Boolean = false,
    val order: Int = 100,
    val position: Int = 0,
    val depth: Int = 4,
    val probability: Int = 100,
    val enabled: Boolean = true,
    val group: String = "",
    @SerialName("scanDepth")
    val scanDepth: Int? = null,
    @SerialName("caseSensitive")
    val caseSensitive: Boolean = false,
    @SerialName("matchWholeWords")
    val matchWholeWords: Boolean = false,
    // Additional fields from SillyTavern
    val displayIndex: Int? = null,
    val excludeRecursion: Boolean = false,
    val preventRecursion: Boolean = false,
    val delayUntilRecursion: Boolean = false,
    val useProbability: Boolean = true,
    val delay: Int? = null,
    @SerialName("automation_id")
    val automationId: String = "",
    val role: Int? = null,
    val vectorized: Boolean = false,
    val sticky: Int? = null,
    val cooldown: Int? = null,
    val characterFilter: JsonObject? = null,
    @SerialName("group_override")
    val groupOverride: Boolean = false,
    @SerialName("group_weight")
    val groupWeight: Int? = null
)

// ========== Group Chat DTOs ==========

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val members: List<String> = emptyList(),  // Character avatar URLs
    @SerialName("chat_id")
    val chatId: String? = null,
    val avatar: String? = null,
    val description: String = "",
    @SerialName("favorite")
    val favorite: Boolean = false,
    @SerialName("allow_self_responses")
    val allowSelfResponses: Boolean = false,
    @SerialName("activation_strategy")
    val activationStrategy: Int = 0,
    @SerialName("generation_mode")
    val generationMode: Int = 0,
    @SerialName("disabled_members")
    val disabledMembers: List<String> = emptyList(),
    val chats: List<String> = emptyList()  // Chat IDs
)

@Serializable
data class CreateGroupRequest(
    val name: String,
    val members: List<String> = emptyList()
)

@Serializable
data class GetGroupChatRequest(
    val id: String
)

@Serializable
data class GroupChatMessageDto(
    val name: String? = null,
    @SerialName("is_user")
    val isUser: Boolean = false,
    @SerialName("is_system")
    val isSystem: Boolean = false,
    val mes: String? = null,
    @SerialName("send_date")
    val sendDate: String? = null,
    @SerialName("original_avatar")
    val originalAvatar: String? = null,
    @SerialName("force_avatar")
    val forceAvatar: String? = null,
    val extra: JsonObject? = null
)

@Serializable
data class SaveGroupChatRequest(
    val id: String,
    val chat: List<GroupChatMessageDto>
)

// ========== Chat Context DTOs ==========

// Chat file header with metadata (first line of .jsonl file)
@Serializable
data class ChatHeaderDto(
    @SerialName("chat_metadata")
    val chatMetadata: ChatMetadataDto? = null,
    @SerialName("user_name")
    val userName: String? = null,
    @SerialName("character_name")
    val characterName: String? = null
)

@Serializable
data class ChatMetadataDto(
    // Author's Note fields
    @SerialName("note_prompt")
    val notePrompt: String? = null,
    @SerialName("note_interval")
    val noteInterval: Int? = null,
    @SerialName("note_depth")
    val noteDepth: Int? = null,
    @SerialName("note_position")
    val notePosition: Int? = null,
    @SerialName("note_role")
    val noteRole: Int? = null,
    // Integrity
    val integrity: String? = null,
    // Other metadata
    @SerialName("tainted")
    val tainted: Boolean? = null
)

// ========== Persona / User Avatar DTOs ==========

@Serializable
data class DeleteAvatarRequest(
    val avatar: String
)

@Serializable
data class UploadAvatarResponse(
    val path: String
)

@Serializable
data class PersonaDescriptionDto(
    val description: String = "",
    val position: Int = 0,  // 0 = in prompt, 1 = in chat, 2 = top of AN
    val role: Int = 0,      // 0 = system, 1 = user, 2 = assistant
    val depth: Int = 2,
    val lorebook: String = ""
)
