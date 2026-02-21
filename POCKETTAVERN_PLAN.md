
│ PocketTavern — Standalone Android App Plan                                                                           │
│                                                                                                                      │
│ Context                                                                                                              │
│                                                                                                                      │
│ PocketTavern currently works as a thin Android client for a SillyTavern Node.js server. The goal is to make it fully │
│  standalone — "SillyTavern for Android" — with no server required. The app will:                                     │
│ - Store characters as PNG cards and chats as JSON files (100% ST-compatible format)                                  │
│ - Talk directly to any LLM backend (KoboldCPP, Ollama, OpenAI-compatible, etc.)                                      │
│ - Bundle all ST instruct/context/sampler templates from ST's open-source repo                                        │
│ - Support ST extensions via a hybrid WebView sandbox + native Kotlin core                                            │
│                                                                                                                      │
│ This is a multi-week, phased project. Phases are ordered by dependency, not complexity.                              │
│                                                                                                                      │
│ ---                                                                                                                  │
│ Current State Summary                                                                                                │
│                                                                                                                      │
│ What Already Exists (Keep/Adapt)                                                                                     │
│                                                                                                                      │
│ - 20 screens with full Compose UI — keep all, adapt data sources                                                     │
│ - All domain models (Character, Chat, ChatContext, WorldInfoEntry, InstructTemplate, ContextTemplate, TextGenPreset, │
│  etc.) — keep as-is                                                                                                  │
│ - PromptBuilder.kt — fully standalone, no ST dependency — keep, extend                                               │
│ - PngCharacterCard.kt — PNG tEXt chunk read/write — keep, it's the file format                                       │
│ - ForgeRepository / BackgroundRepository / ChubRepository / CardVaultRepository — keep, already standalone           │
│ - Streaming infrastructure (OkHttp SSE parsing, Flow<StreamEvent>) — keep, reuse for direct backends                 │
│ - DebugLogger — keep                                                                                                 │
│                                                                                                                      │
│ What Gets Replaced                                                                                                   │
│                                                                                                                      │
│ - SillyTavernRepository.kt (3300 lines) — the entire ST server dependency                                            │
│ - SillyTavernApi.kt / SillyTavernDtos.kt — ST REST client                                                            │
│ - NetworkModule.kt — remove ST client, keep others, add direct LLM clients                                           │
│ - SettingsDataStore.kt — remove SERVER_URL, USERNAME, PASSWORD, add local storage paths                              │
│                                                                                                                      │
│ ---                                                                                                                  │
│ Architecture (Target State)                                                                                          │
│                                                                                                                      │
│ PocketTavern (Android)                                                                                               │
│ ├── Local Storage                                                                                                    │
│ │   ├── /files/characters/*.png     ← Character cards (ST-compatible)                                                │
│ │   ├── /files/chats/{char}/*.jsonl ← Chat history (ST-compatible)                                                   │
│ │   ├── /files/worlds/*.json        ← Lorebooks (ST-compatible)                                                      │
│ │   ├── /files/presets/*/*.json     ← User presets (overrides)                                                       │
│ │   └── Room DB                     ← Index only (fast search/list)                                                  │
│ ├── Bundled Assets (from ST)                                                                                         │
│ │   ├── assets/presets/instruct/*.json                                                                               │
│ │   ├── assets/presets/context/*.json                                                                                │
│ │   ├── assets/presets/textgen/*.json                                                                                │
│ │   ├── assets/presets/sysprompt/*.json                                                                              │
│ │   └── assets/backgrounds/                                                                                          │
│ ├── Direct LLM Backends                                                                                              │
│ │   ├── KoboldCPP  → POST /api/v1/generate                                                                           │
│ │   ├── Ollama     → POST /api/generate or /api/chat                                                                 │
│ │   ├── OpenAI-compatible → POST /v1/chat/completions                                                                │
│ │   ├── TextGenWebUI/Ooba → POST /api/v1/generate                                                                    │
│ │   ├── LlamaCpp server  → POST /completion                                                                          │
│ │   └── Cloud APIs (OpenAI, Claude, Mistral, etc.)                                                                   │
│ └── Extension System                                                                                                 │
│     ├── Native: quick-reply, regex, token-counter, expressions                                                       │
│     └── WebView sandbox: third-party JS extensions                                                                   │
│                                                                                                                      │
│ ---                                                                                                                  │
│ Phase 1 — Rename & Repackage                                                                                         │
│                                                                                                                      │
│ Goal: Clean foundation before any logic changes.                                                                     │
│                                                                                                                      │
│ Files to modify:                                                                                                     │
│ - settings.gradle.kts — change rootProject.name                                                                      │
│ - app/build.gradle.kts — change applicationId to com.pockettavern.app                                                │
│ - app/src/main/AndroidManifest.xml — update package                                                                  │
│ - All *.kt files — rename package from com.stark.sillytavern → com.pockettavern.app                                  │
│ - SillyTavernApp.kt → PocketTavernApp.kt                                                                             │
│ - App name strings, launcher icon                                                                                    │
│                                                                                                                      │
│ No logic changes in this phase.                                                                                      │
│                                                                                                                      │
│ ---                                                                                                                  │
│ Phase 2 — Local Storage Layer                                                                                        │
│                                                                                                                      │
│ Goal: Replace ST server as the data store.                                                                           │
│                                                                                                                      │
│ 2a — File Storage (Characters, Chats, Lorebooks)                                                                     │
│                                                                                                                      │
│ New files:                                                                                                           │
│ - data/local/CharacterStorage.kt                                                                                     │
│   - listCharacters() — scan /files/characters/, parse PNG tEXt chunks                                                │
│   - getCharacter(fileName) — read single PNG card                                                                    │
│   - saveCharacter(card, avatarBitmap) — write PNG with embedded metadata                                             │
│   - deleteCharacter(fileName)                                                                                        │
│   - Reuse existing PngCharacterCard.kt for read/write                                                                │
│ - data/local/ChatStorage.kt                                                                                          │
│   - listChats(characterName) — list .jsonl files in /files/chats/{char}/                                             │
│   - loadChat(fileName) — parse JSONL format (one message per line)                                                   │
│   - saveChat(chat) — write JSONL                                                                                     │
│   - deleteChat(fileName)                                                                                             │
│   - Format: ST-compatible JSONL (first line = metadata, subsequent lines = messages)                                 │
│ - data/local/LoreBookStorage.kt                                                                                      │
│   - listLorebooks() — scan /files/worlds/*.json                                                                      │
│   - loadLorebook(name) — parse ST world info JSON format                                                             │
│   - saveLorebook(name, entries)                                                                                      │
│   - deleteLorebook(name)                                                                                             │
│ - data/local/PresetStorage.kt                                                                                        │
│   - listInstructTemplates() — bundled assets + user overrides in /files/presets/instruct/                            │
│   - listContextTemplates() — same pattern                                                                            │
│   - listTextGenPresets() — same pattern                                                                              │
│   - listSystemPrompts() — same pattern                                                                               │
│   - savePreset(type, name, json) — save user preset to files                                                         │
│   - deletePreset(type, name) — delete (only user presets, not bundled)                                               │
│   - Uses AssetManager for bundled, File for user-created                                                             │
│                                                                                                                      │
│ 2b — Room Database (Index)                                                                                           │
│                                                                                                                      │
│ New files:                                                                                                           │
│ - data/local/db/AppDatabase.kt — Room database                                                                       │
│ - data/local/db/entity/CharacterEntity.kt — id, fileName, name, tags, favorite, lastChatDate                         │
│ - data/local/db/entity/ChatEntity.kt — id, fileName, characterName, createDate, modifyDate, messageCount             │
│ - data/local/db/dao/CharacterDao.kt — search, filter by tag, sort                                                    │
│ - data/local/db/dao/ChatDao.kt — list by character, sort by date                                                     │
│                                                                                                                      │
│ 2c — Replace SillyTavernRepository                                                                                   │
│                                                                                                                      │
│ New file: data/repository/LocalRepository.kt                                                                         │
│ Exposes same interface shape as the relevant parts of SillyTavernRepository:                                         │
│ - getCharacters(), getCharacter(), createCharacter(), editCharacter(), deleteCharacter()                             │
│ - importCharacterCard(uri) — copy PNG to /files/characters/, index in Room                                           │
│ - exportCharacterCard(character) — returns Uri to share                                                              │
│ - getCharacterChats(), getChat(), saveChat(), deleteChat()                                                           │
│ - getWorldInfoList(), getWorldInfo(), saveWorldInfo()                                                                │
│ - getFormattingSettings() — loads from PresetStorage                                                                 │
│ - saveFormattingSettings()                                                                                           │
│ - getUserPersona(), saveUserPersona()                                                                                │
│                                                                                                                      │
│ Modify SettingsDataStore.kt:                                                                                         │
│ - Remove: SERVER_URL, USERNAME, PASSWORD                                                                             │
│ - Add: CHARACTERS_DIR, CHATS_DIR (optional custom paths for power users)                                             │
│ - Keep: SELECTED_TEXTGEN_PRESET, SELECTED_INSTRUCT_PRESET, etc.                                                      │
│                                                                                                                      │
│ ---                                                                                                                  │
│ Phase 3 — Direct LLM Backends                                                                                        │
│                                                                                                                      │
│ Goal: Cut ST out of the generation pipeline entirely.                                                                │
│                                                                                                                      │
│ Backend API Clients                                                                                                  │
│                                                                                                                      │
│ New files in data/remote/api/:                                                                                       │
│ - KoboldCppApi.kt — POST /api/v1/generate, POST /api/extra/abort, GET /api/v1/model                                  │
│ - OllamaApi.kt — POST /api/generate (streaming), GET /api/tags (model list)                                          │
│ - OpenAiCompatibleApi.kt — POST /v1/chat/completions, GET /v1/models (covers: LM Studio, TabbyAPI, vLLM, Aphrodite,  │
│ TextGenWebUI OpenAI mode, OpenAI, Mistral, Groq, DeepSeek, etc.)                                                     │
│ - LlamaCppServerApi.kt — POST /completion (streaming)                                                                │
│ - AnthropicApi.kt — POST /v1/messages                                                                                │
│ - NovelAiApi.kt — POST /ai/generate-stream                                                                           │
│                                                                                                                      │
│ Backend Repository                                                                                                   │
│                                                                                                                      │
│ New file: data/repository/LlmRepository.kt                                                                           │
│ - generate(context: ChatContext, config: ApiConfiguration): Flow<StreamEvent>                                        │
│ - abortGeneration(config: ApiConfiguration)                                                                          │
│ - getAvailableModels(config: ApiConfiguration): List<AvailableModel>                                                 │
│ - testConnection(config: ApiConfiguration): Boolean                                                                  │
│ - Internally dispatches to correct API client based on ApiConfiguration.apiType                                      │
│ - Reuses existing StreamEvent model and SSE parsing logic from current streaming code                                │
│                                                                                                                      │
│ Modify NetworkModule.kt:                                                                                             │
│ - Remove: SillyTavern Retrofit client, CSRF/Auth interceptors                                                        │
│ - Add: Named clients for each backend with appropriate timeouts                                                      │
│ - Keep: Chub, CardVault, Forge, GitHub clients                                                                       │
│                                                                                                                      │
│ Modify ChatViewModel.kt and GroupChatViewModel.kt:                                                                   │
│ - Replace SillyTavernRepository.streamGenerationForCharacter() with LlmRepository.generate()                         │
│                                                                                                                      │
│ ---                                                                                                                  │
│ Phase 4 — Bundle ST Templates & Assets                                                                               │
│                                                                                                                      │
│ Goal: Ship with all the templates users expect.                                                                      │
│                                                                                                                      │
│ Assets to bundle from ST's open-source repo                                                                          │
│                                                                                                                      │
│ Copy from /mnt/c/Users/stark/SillyTavern/default/content/presets/ into app/src/main/assets/presets/:                 │
│ - instruct/*.json — all 30+ instruct templates (Mistral V1-V7, ChatML, Llama 3, Alpaca, etc.)                        │
│ - context/*.json — all 30+ context templates                                                                         │
│ - textgen/*.json — KoboldCPP/textgen presets                                                                         │
│ - kobold/*.json — KoboldAI presets                                                                                   │
│ - openai/*.json — OAI presets                                                                                        │
│ - sysprompt/*.json — system prompt presets                                                                           │
│                                                                                                                      │
│ Copy backgrounds from default/content/backgrounds/ into app/src/main/assets/backgrounds/                             │
│                                                                                                                      │
│ Modify FormattingScreen + TextGenSettingsScreen:                                                                     │
│ - Load templates from PresetStorage (bundled + user) instead of from ST server                                       │
│                                                                                                                      │
│ ---                                                                                                                  │
│ Phase 5 — Complete World Info Pipeline                                                                               │
│                                                                                                                      │
│ Goal: Fix the lorebook gap (embedded entries not injecting).                                                         │
│                                                                                                                      │
│ The bug: character_book entries are parsed into Character.characterBook but never loaded into                        │
│ ChatContext.worldInfoEntries before PromptBuilder.scanWorldInfo() runs.                                              │
│                                                                                                                      │
│ Fix in LocalRepository.loadChatContext():                                                                            │
│ 1. After loading character, check character.characterBook != null                                                    │
│ 2. If so, convert CharacterBook.entries → List<WorldInfoEntry> and add to context                                    │
│ 3. Also load any attached lorebook file (character.attachedWorldInfo)                                                │
│ 4. Merge both lists into ChatContext.worldInfoEntries                                                                │
│                                                                                                                      │
│ Complete missing PromptBuilder features:                                                                             │
│ - scanWorldInfo() — add probability check (Math.random() * 100 <= entry.probability)                                 │
│ - scanWorldInfo() — add token budget enforcement (count tokens, stop when budget exceeded)                           │
│ - scanWorldInfo() — add recursive scanning pass (activated entries' content added to scan buffer, rescan)            │
│ - Add regex key matching (entry.key.startsWith("/") → treat as regex pattern)                                        │
│                                                                                                                      │
│ File: domain/prompt/PromptBuilder.kt (lines 491-574)                                                                 │
│                                                                                                                      │
│ ---                                                                                                                  │
│ Phase 6 — Extension System                                                                                           │
│                                                                                                                      │
│ Goal: Support ST extensions in the app.                                                                              │
│                                                                                                                      │
│ Native Extensions (Kotlin)                                                                                           │
│                                                                                                                      │
│ New files in extensions/native/:                                                                                     │
│ - QuickReplyExtension.kt — preset quick reply buttons, injected above input                                          │
│ - RegexExtension.kt — find/replace rules applied to AI output (mirrors ST regex extension)                           │
│ - TokenCounterExtension.kt — live token count display                                                                │
│ - ExpressionsExtension.kt — parse emotion from output, show character sprite overlay                                 │
│                                                                                                                      │
│ Extension Event Bus                                                                                                  │
│                                                                                                                      │
│ New file: extensions/ExtensionEventBus.kt                                                                            │
│ Mirrors ST's eventSource:                                                                                            │
│ enum class ExtensionEvent {                                                                                          │
│     MESSAGE_SENT, MESSAGE_RECEIVED, MESSAGE_EDITED,                                                                  │
│     GENERATION_STARTED, GENERATION_STOPPED,                                                                          │
│     CHAT_CHANGED, CHARACTER_CHANGED,                                                                                 │
│     PROMPT_PROCESSING_DONE                                                                                           │
│ }                                                                                                                    │
│ - emit(event, data) — notify all listeners                                                                           │
│ - on(event, handler) — subscribe                                                                                     │
│                                                                                                                      │
│ WebView Sandbox (for JS extensions)                                                                                  │
│                                                                                                                      │
│ New file: extensions/ExtensionWebView.kt                                                                             │
│ - Loads a minimal HTML shell that mimics ST's extension host environment                                             │
│ - Exposes getContext(), setExtensionPrompt(), eventSource via JS bridge (@JavascriptInterface)                       │
│ - Extension JS files loaded from /files/extensions/{name}/index.js                                                   │
│ - Communicates prompt injections back to PromptBuilder before each generation                                        │
│                                                                                                                      │
│ New screen: ui/screens/extensions/ExtensionsScreen.kt                                                                │
│ - List installed extensions (native + WebView)                                                                       │
│ - Enable/disable per extension                                                                                       │
│ - Install from URL (download JS files to /files/extensions/)                                                         │
│ - Per-extension settings UI                                                                                          │
│                                                                                                                      │
│ ---                                                                                                                  │
│ Phase 7 — Chat Completion Prompt Building                                                                            │
│                                                                                                                      │
│ Goal: Support cloud APIs (OpenAI, Claude, etc.) which use chat format.                                               │
│                                                                                                                      │
│ Modify PromptBuilder.kt:                                                                                             │
│ - Add buildChatCompletionMessages(): List<ChatMessage> alongside existing buildPrompt(): String                      │
│ - Maps: system prompt → role: system, character description → role: system, chat history → alternating               │
│ user/assistant, world info → injected as system messages at appropriate depth                                        │
│ - Used by LlmRepository when apiType == CHAT_COMPLETION                                                              │
│                                                                                                                      │
│ New domain model: OaiPromptConfig.kt                                                                                 │
│ - Mirrors ST's OAI prompt ordering system (which system blocks are enabled and in what order)                        │
│ - Stored in SettingsDataStore                                                                                        │
│                                                                                                                      │
│ ---                                                                                                                  │
│ Phase 8 — Polish & ST Import                                                                                         │
│                                                                                                                      │
│ Goal: Allow users to migrate from SillyTavern.                                                                       │
│                                                                                                                      │
│ New feature: ST Import Wizard                                                                                        │
│ - Enter ST server URL (optional, for migration only)                                                                 │
│ - Pull all characters, chats, lorebooks from ST server                                                               │
│ - Save to local storage                                                                                              │
│ - One-time migration, then server not needed                                                                         │
│                                                                                                                      │
│ Or: File-based import                                                                                                │
│ - Import a zip of ST's data/ directory                                                                               │
│ - Parse and copy characters, chats, worlds into app storage                                                          │
│                                                                                                                      │
│ ---                                                                                                                  │
│ Key Files Reference                                                                                                  │
│                                                                                                                      │
│ ┌───────────────────────────┬──────────────────────────────────────────────────────┐                                 │
│ │           What            │                      File Path                       │                                 │
│ ├───────────────────────────┼──────────────────────────────────────────────────────┤                                 │
│ │ Character PNG parse/write │ util/PngCharacterCard.kt (keep)                      │                                 │
│ ├───────────────────────────┼──────────────────────────────────────────────────────┤                                 │
│ │ Prompt building           │ domain/prompt/PromptBuilder.kt (extend)              │                                 │
│ ├───────────────────────────┼──────────────────────────────────────────────────────┤                                 │
│ │ Domain models             │ domain/model/*.kt (keep all)                         │                                 │
│ ├───────────────────────────┼──────────────────────────────────────────────────────┤                                 │
│ │ New local storage         │ data/local/CharacterStorage.kt (new)                 │                                 │
│ ├───────────────────────────┼──────────────────────────────────────────────────────┤                                 │
│ │ New local storage         │ data/local/ChatStorage.kt (new)                      │                                 │
│ ├───────────────────────────┼──────────────────────────────────────────────────────┤                                 │
│ │ New LLM backends          │ data/repository/LlmRepository.kt (new)               │                                 │
│ ├───────────────────────────┼──────────────────────────────────────────────────────┤                                 │
│ │ Room DB                   │ data/local/db/AppDatabase.kt (new)                   │                                 │
│ ├───────────────────────────┼──────────────────────────────────────────────────────┤                                 │
│ │ Extension bus             │ extensions/ExtensionEventBus.kt (new)                │                                 │
│ ├───────────────────────────┼──────────────────────────────────────────────────────┤                                 │
│ │ WebView sandbox           │ extensions/ExtensionWebView.kt (new)                 │                                 │
│ ├───────────────────────────┼──────────────────────────────────────────────────────┤                                 │
│ │ Bundled templates         │ app/src/main/assets/presets/**/*.json (copy from ST) │                                 │
│ ├───────────────────────────┼──────────────────────────────────────────────────────┤                                 │
│ │ Replace this              │ data/repository/SillyTavernRepository.kt (delete)    │                                 │
│ ├───────────────────────────┼──────────────────────────────────────────────────────┤                                 │
│ │ Replace this              │ data/remote/api/SillyTavernApi.kt (delete)           │                                 │
│ └───────────────────────────┴──────────────────────────────────────────────────────┘                                 │
│                                                                                                                      │
│ ---                                                                                                                  │
│ Phased Timeline Estimate                                                                                             │
│                                                                                                                      │
│ ┌──────────────────────┬─────────────────────┬──────────────────┐                                                    │
│ │        Phase         │        Scope        │ Estimated Effort │                                                    │
│ ├──────────────────────┼─────────────────────┼──────────────────┤                                                    │
│ │ 1 — Rename           │ Mechanical refactor │ 1 session        │                                                    │
│ ├──────────────────────┼─────────────────────┼──────────────────┤                                                    │
│ │ 2 — Local Storage    │ New data layer      │ 3-4 sessions     │                                                    │
│ ├──────────────────────┼─────────────────────┼──────────────────┤                                                    │
│ │ 3 — Direct LLM       │ Backend clients     │ 2-3 sessions     │                                                    │
│ ├──────────────────────┼─────────────────────┼──────────────────┤                                                    │
│ │ 4 — Bundle Templates │ Copy + wire up      │ 1 session        │                                                    │
│ ├──────────────────────┼─────────────────────┼──────────────────┤                                                    │
│ │ 5 — World Info       │ Fix + complete      │ 1-2 sessions     │                                                    │
│ ├──────────────────────┼─────────────────────┼──────────────────┤                                                    │
│ │ 6 — Extensions       │ New subsystem       │ 4-6 sessions     │                                                    │
│ ├──────────────────────┼─────────────────────┼──────────────────┤                                                    │
│ │ 7 — Chat Completions │ Prompt building     │ 2 sessions       │                                                    │
│ ├──────────────────────┼─────────────────────┼──────────────────┤                                                    │
│ │ 8 — ST Import        │ Migration wizard    │ 1-2 sessions     │                                                    │
│ └──────────────────────┴─────────────────────┴──────────────────┘                                                    │
│                                                                                                                      │
│ Start order: 1 → 2 → 3 → 4 → 5 (core is usable here) → 6 → 7 → 8                                                     │
│                                                                                                                      │
│ ---                                                                                                                  │
│ Verification                                                                                                         │
│                                                                                                                      │
│ After each phase, verify by:                                                                                         │
│ - Phase 2: Load a PNG character card from device storage, display in Characters screen                               │
│ - Phase 3: Send a message to KoboldCPP directly (no ST), receive streamed response                                   │
│ - Phase 4: Templates appear in FormattingScreen from bundled assets                                                  │
│ - Phase 5: Chat with Felarya card, confirm lorebook entries appear in debug log prompt                               │
│ - Phase 6: Install quick-reply extension, buttons appear above input in chat                                         │
│ - Phase 7: Connect to OpenAI/Claude API, receive response     