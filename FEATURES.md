# PocketTavern Feature Status

Last updated: 2026-02-20

## Already Implemented

| Feature | Location |
|---|---|
| Message Swipes | `ChatViewModel.kt:847-947` — full left/right with alternatives |
| Author's Note | `ChatViewModel.kt:685-709` — depth/position/interval |
| Backgrounds | `BackgroundRepository.kt` — per-character, displayed in chat |
| Message Editing | `ChatViewModel.kt:761-793` |
| Message Deletion | `ChatViewModel.kt:466-475` |
| Character Tags | `Character.kt` — stored (filtering UI unverified) |
| User Personas | `Persona.kt` + `PersonaScreen.kt` — full prompt injection |
| Macros | `PromptBuilder.kt:838-860` — {{char}}, {{user}}, {{date}} etc. |
| Quick Reply buttons | `extensions/builtin/QuickReplyExtension.kt` |
| Regex extension | `extensions/builtin/RegexExtension.kt` |
| Token Counter | `extensions/builtin/TokenCounterExtension.kt` |
| World Info / Lorebook | `PromptBuilder.kt` — scan, recursive, probability, token budget |
| Instruct templates | `PresetStorage.kt` — bundled + user |
| Context templates | `PresetStorage.kt` — bundled + user |
| TextGen presets | `PresetStorage.kt` — bundled + user |
| System Prompt presets | `PresetStorage.kt` — bundled + user, editable in-app |
| Group chat | `GroupChatViewModel.kt` |
| Streaming | `LlmRepository.kt` — SSE/chunked for all backends |
| Direct LLM backends | KoboldCPP, Ollama, OpenAI-compat, LlamaCpp, Anthropic, NovelAI |
| Character browsing | Chub, CardVault, Forge |

## Partial

| Feature | Gap |
|---|---|
| Quick Reply auto-triggers | Events emitted but buttons don't react to them yet |
| Narrator/System messages | Group chats only — not wired for 1-on-1 |

## Missing (Prioritized)

| Feature | Priority | Notes |
|---|---|---|
| Auto-Continue | High | Auto-regenerate if response ends below target length |
| Connection Profiles | High | Save named API+model+preset combos, switch instantly |
| TTS | Medium | Android native TextToSpeech — zero dependencies for basic version |
| Chat Bookmarks/Branching | Medium | Fork conversation at any message into a new chat |
| File Attachments | Medium | Attach text/PDF to messages, inject content into context |
| Character Expressions/Sprites | Medium | Sprite overlay with emotion detection from AI output |
| Slash Commands | Low | /gen, /send, /continue etc. |
| Translation | Low | Auto-translate via Google/DeepL API |
| Chat Backups | Low | Auto-backup and restore chat states |
