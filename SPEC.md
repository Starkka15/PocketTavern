# SPEC — PocketTavern: charx import + translation + sprite system

## §G Goal

Import `.charx` character cards (RisuAI format), auto-translate non-English cards via configured AI connection, display character expression sprites during chat.

---

## §C Constraints

- Android (Kotlin/Compose). No new dependencies unless unavoidable.
- `.charx` = JPEG prepended to ZIP. ZIP contains `card.json` (v3) + `assets/icon/image/iconx.png` + `assets/other/image/*.png` (sprites).
- `card.json` spec v3 fields are superset of v2. Existing `CharacterCardV2` + `ignoreUnknownKeys=true` handles parse.
- Translation uses whatever `ApiConfiguration` is active — no hardcoded endpoint.
- Sprites stored at `{filesDir}/characters/sprites/{characterName}/`. Name = sprite key (e.g. `angry`, `happy`).
- Sprite tag format in messages: `<img src=(name)>`. Must be stripped from displayed text.
- PNG cards: no sprites unless user manually drops files in sprite dir (future feature, no active task here).
- No breaking changes to existing import paths (CharaVault, ST import, JS bridge).

---

## §I Interfaces

| id | surface | notes |
|----|---------|-------|
| I1 | `CharxParser` (new util) | `parse(bytes): CharxResult` — card data + iconPng + sprites map |
| I2 | `SpriteStorage` (new, Hilt singleton) | `save(name, sprites)`, `getFile(name, sprite): File?`, `list(name): List<String>` |
| I3 | `TranslateCardUseCase` (new, Hilt) | `translate(data, fields, config): CharacterCardData` — collects LlmRepository flow |
| I4 | `CharacterStorage.importCharacterCard(uri)` | extended: charx branch alongside existing PNG branch |
| I5 | `CharactersViewModel.importLocalCharacter(uri)` | new fn — triggers import + lang detect + translation dialog |
| I6 | `CharactersScreen` file picker | accepts `image/png` and `*/*`; shows translation dialog post-import |
| I7 | `ChatViewModel` sprite state | `currentSpriteName: String?` in `ChatUiState`; scan incoming messages for `<img src=(…)>` |
| I8 | `ChatScreen` sprite panel | portrait above input bar, animates on sprite change |
| I9 | `ChatBubble / parseMarkdown` | strip `<img src=(…)>` from displayed text |

---

## §V Invariants

| id | invariant |
|----|----------|
| V1 | charx parse MUST skip prepended bytes by scanning for first `PK\x03\x04` magic — never assume ZIP starts at offset 0 |
| V2 | import MUST always produce valid PNG with embedded card JSON in `tEXt` chunk — charx and PNG paths converge before `CharacterStorage.saveRawPng()` |
| V3 | translation MUST preserve `{{user}}`, `{{char}}`, `{{original}}` template vars verbatim |
| V4 | translation MUST preserve `<img src=(…)>` sprite tags verbatim |
| V5 | translation MUST preserve markdown markers (`*`, `**`, `_`, `§…§`) verbatim |
| V6 | `<img src=(name)>` tags MUST be stripped from text before `formatMessage()` renders it |
| V7 | sprite lookup MUST be case-insensitive and strip `.png` suffix from key |
| V8 | translation dialog MUST only appear when non-ASCII ratio in `description` or `first_mes` exceeds 0.15 |
| V9 | failed translation MUST leave card untouched — partial writes forbidden |
| V10 | sprite panel MUST NOT block the message list or input bar — overlay only |

---

## §T Tasks

| id | status | description | cites |
|----|--------|-------------|-------|
| T1 | x | `util/CharxParser.kt` — find ZIP offset, unzip in-mem, return `CharxResult(cardData, iconPng, sprites)` | V1,I1 |
| T2 | x | `data/local/SpriteStorage.kt` — Hilt singleton, save/get/list sprites under `{filesDir}/characters/sprites/` | V7,I2 |
| T3 | x | `CharacterStorage.importCharacterCard(uri)` — detect charx (extension or ZIP magic), branch to charx path; call `CharxParser`, save sprites via `SpriteStorage`, converge to `saveRawPng()` | V1,V2,I4 |
| T4 | x | `domain/usecase/TranslateCardUseCase.kt` — take `CharacterCardData + List<field> + ApiConfiguration`, call `LlmRepository.generate()` per field, collect `StreamEvent.Complete`, return translated copy | V3,V4,V5,V9,I3 |
| T5 | x | `CharactersViewModel` — add `LlmRepository` + `SettingsDataStore` inject; add `importLocalCharacter(uri)`, lang-detect post-import, expose `showTranslateDialog`/`isTranslating`/`translateError` state | V8,I5 |
| T6 | x | `CharactersScreen` — add "Import" icon button to top bar; file picker `*/*`; `TranslationDialog` composable (field checkboxes + confirm); wire to `CharactersViewModel` | I5,I6 |
| T7 | x | `ChatBubble / parseMarkdown` — add `<img src=(…)>` case: strip tag, emit no segment | V6,I9 |
| T8 | x | `ChatViewModel` — add `currentSpriteName: String?` to `ChatUiState`; scan each incoming assistant message for `<img src=(…)>` regex; update state on match | I7 |
| T9 | x | `ChatScreen` — sprite panel: `AnimatedVisibility` above `ChatInput`, load image from `SpriteStorage.getFile(charName, spriteName)` via Coil, tap to dismiss | V10,I8 |

---

## §B Bugs

| id | date | cause | fix |
|----|------|-------|-----|

---

# SPEC — PocketTavern: Long-Term Memory


## §G Goal

Persist a compressed summary of old chat turns in the Room DB. Re-inject summary as a `[Memory]` block at the top of every prompt so the LLM retains facts from sessions too old to fit the context window.

---

## §C Constraints

- Android (Kotlin/Compose). No new libraries.
- Token counting uses `chars / 4` estimate — no tokenizer dependency.
- Summarization calls the active `ApiConfiguration`. If no connection configured, skip silently.
- Memory block stored in `ChatEntity` — survives app kill and reinstall (within same DB).
- Must not block the send flow — summarization runs after AI response is saved, in background.
- Summarize trigger: un-summarized turns whose combined char count exceeds 12 000 chars (~3 000 tokens).
- Memory block target size: ≤ 300 tokens (~1 200 chars). Prompt instructs LLM to stay within this.
- Feature can be toggled off in Settings. Default: on.

---

## §I Interfaces

| id | surface | notes |
|----|---------|-------|
| I1 | `ChatEntity` (extended) | add `memoryBlock: String`, `summarizedTurnCount: Int` — Room migration 4→5 |
| I2 | `ChatStorage.updateMemoryBlock(characterName, fileName, block, count)` | persist memory block + count to DB |
| I3 | `SummarizeHistoryUseCase` | `summarize(turns: List<ChatMessage>, config: ApiConfiguration): String` — returns bullet list |
| I4 | `PromptBuilder` (extended) | accept `memoryBlock: String` param; inject as system block before char description |
| I5 | `ChatViewModel` (extended) | after AI response saved: check threshold, call `SummarizeHistoryUseCase`, store via I2 |
| I6 | `SettingsDataStore` (extended) | add `memoryEnabled: Boolean` preference (default true) |
| I7 | Settings UI | toggle in Advanced section: "Long-Term Memory" on/off |

---

## §V Invariants

| id | invariant |
|----|----------|
| V1 | memory block MUST be injected as a system message before character description — never inside chat history |
| V2 | summarization MUST only run when an active `ApiConfiguration` exists — skip silently if none |
| V3 | summarization prompt MUST instruct the LLM to output only bullet facts, no roleplay, no prose filler |
| V4 | failed summarization MUST leave the existing memory block unchanged — no partial overwrite |
| V5 | turns already counted in `summarizedTurnCount` MUST NOT be included in next summarization batch |
| V6 | memory block MUST survive app restart — stored in Room `ChatEntity`, loaded with chat |
| V7 | token estimation MUST use `chars / 4` — no real tokenizer import |
| V8 | feature toggle off MUST suppress injection AND background summarization — not just injection |

---

## §T Tasks

| id | status | description | cites |
|----|--------|-------------|-------|
| T10 | x | `ChatEntity` add `memoryBlock TEXT NOT NULL DEFAULT ''` + `summarizedTurnCount INTEGER NOT NULL DEFAULT 0`; `AppDatabase` version 4→5, `MIGRATION_4_5`; `AppModule` registers migration | V6,I1 |
| T11 | x | `ChatStorage.updateMemoryBlock(characterName, fileName, block, count)` — upsert only these two fields without rewriting JSONL; also load `memoryBlock`/`summarizedTurnCount` into `Chat` model when parsing | V6,I2 |
| T12 | x | `SummarizeHistoryUseCase` — inject `LlmRepository`; build system prompt: "Summarize key facts, relationship events, and plot threads from this conversation excerpt as concise bullet points (max 300 tokens). Output only bullets."; stream turns as user/assistant pairs; collect `StreamEvent.Complete`; return trimmed string | V2,V3,V4,I3 |
| T13 | x | `PromptBuilder` accept optional `memoryBlock: String` param; in `buildChatCompletionMessages` emit as role=system message "[Memory]\n{block}" immediately before `char_description` block when non-blank; same for `buildInstructPrompt` / `buildSimplePrompt` | V1,I4 |
| T14 | x | `ChatViewModel` — after AI response appended: compute char sum of turns from index `summarizedTurnCount` onward; if > 12 000 and `memoryEnabled` and connection active: launch `viewModelScope.launch(Dispatchers.IO)` → `SummarizeHistoryUseCase.summarize(batch, config)` → `ChatStorage.updateMemoryBlock(...)` → update local `chat` state | V2,V5,V8,I5 |
| T15 | x | `SettingsDataStore` add `memoryEnabled: Boolean` (default true); `SettingsRepository` expose as `Flow<Boolean>`; `SettingsScreen` toggle in Advanced section labelled "Long-Term Memory" with subtitle "Summarize old messages so the AI remembers past sessions" | V8,I6,I7 |

---

## §B Bugs

| id | date | cause | fix |
|----|------|-------|-----|

---

# SPEC — PocketTavern: Macro System (ST-Compatible)

## §G Goal

Full SillyTavern-compatible macro substitution for all character card fields and prompt text. Add missing macros, pass chat history into substitution so message macros resolve, and expose a `beforePromptSend` JS bridge hook for extension-level custom macros.

---

## §C Constraints

- Android (Kotlin/Compose). No new libraries.
- All macros case-insensitive (existing behavior).
- `substituteMacros` refactored to accept optional `history: List<ChatMessage>` and `newMessage: String` — callers inside build methods already have these.
- Dice roll (`{{roll:NdN}}`) uses `kotlin.random.Random` — no math library needed.
- `{{idle_duration}}` reads last message timestamp from history; formats as human-readable ("5 minutes ago", "2 hours ago").
- `{{time_UTC±N}}` / `{{time::UTC+N}}` — both ST legacy and new `::` format accepted.
- JS `beforePromptSend` hook receives assembled prompt string, returns modified string; timeout 500ms, failure falls back to original.
- Group chat macros (`{{group}}`, `{{groupNotMuted}}`) out of scope — no group chat feature.

---

## §I Interfaces

| id | surface | notes |
|----|---------|-------|
| I1 | `PromptBuilder.substituteMacros(text, history, newMessage)` | adds history + newMessage params; all internal callers updated |
| I2 | `PromptBuilder` — new macros | `{{newline}}`, `{{noop}}`, `{{isotime}}`, `{{isodate}}`, `{{time_UTC±N}}`, `{{roll:NdN}}`, `{{idle_duration}}`, `{{lastMessage}}`, `{{lastUserMessage}}`, `{{lastCharMessage}}`, `{{input}}`, `{{creatorNotes}}`, `{{charPrompt}}`, `{{charInstruction}}` |
| I3 | JS bridge `beforePromptSend` event | payload `{prompt: String}`, extension returns `{prompt: String}`; called in `ChatViewModel` after `PromptBuilder` but before `LlmRepository.generate()` |

---

## §V Invariants

| id | invariant |
|----|----------|
| V1 | all macros MUST be case-insensitive — use `ignoreCase = true` or `RegexOption.IGNORE_CASE` |
| V2 | `{{roll:NdN}}` MUST clamp N dice 1–100, sides 1–1000 — no unbounded loop |
| V3 | `{{idle_duration}}` MUST return "just now" if history empty or last message < 60s ago |
| V4 | `{{lastMessage}}` / `{{lastUserMessage}}` / `{{lastCharMessage}}` MUST return empty string if no matching message exists — never crash |
| V5 | `{{input}}` MUST resolve to the current `newMessage` value at substitution time |
| V6 | `beforePromptSend` JS hook MUST NOT block send if extension throws or times out — fallback to original prompt |
| V7 | `substituteMacros` signature change MUST NOT break existing call sites — default params `history = emptyList()`, `newMessage = ""` |

---

## §T Tasks

| id | status | description | cites |
|----|--------|-------------|-------|
| T16 | x | Refactor `substituteMacros(text)` → `substituteMacros(text, history: List<ChatMessage> = emptyList(), newMessage: String = "")` in `PromptBuilder`; update all internal call sites in `buildChatCompletionMessages`, `buildInstructPrompt`, `buildSimplePrompt` to pass `chatHistory` + `newMessage` | V7,I1 |
| T17 | x | Add formatting macros: `{{newline}}` → `\n`; `{{newline::N}}` → N newlines; `{{space}}` → ` `; `{{space::N}}` → N spaces; `{{noop}}` → `""` | V1,I2 |
| T18 | x | Add time macros: `{{isotime}}` → `HH:mm`; `{{isodate}}` → `yyyy-MM-dd`; `{{time_UTC±N}}` and `{{time::UTC+N}}` → current time adjusted by offset (both formats); `{{idle_duration}}` / `{{idleDuration}}` — format as "X minutes ago" / "X hours ago" / "just now" from last history message timestamp | V1,V3,I2 |
| T19 | x | Add dice macro: `{{roll:NdN}}` and `{{roll::NdN}}` (both formats) — parse N dice + M sides, clamp, sum rolls, substitute result as string | V1,V2,I2 |
| T20 | x | Add `{{random::a::b::c}}` double-colon format alongside existing `{{random:a,b,c}}` comma format | V1,I2 |
| T21 | x | Add message macros using history param: `{{lastMessage}}` → last message content regardless of sender; `{{lastUserMessage}}` → last `isUser=true` content; `{{lastCharMessage}}` → last `isUser=false && !isNarrator` content; `{{input}}` → `newMessage` param | V1,V4,V5,I2 |
| T22 | x | Add character card field aliases: `{{charDescription}}` → `character.description`; `{{charPersonality}}` → `character.personality`; `{{charScenario}}` → `character.scenario`; `{{charPrompt}}` → `character.systemPrompt`; `{{charInstruction}}` / `{{charJailbreak}}` → `character.postHistoryInstructions`; `{{creatorNotes}}` / `{{charCreatorNotes}}` → `character.creatorNotes` | V1,I2 |
| T23 | x | Add `beforePromptSend` JS bridge hook in `ChatViewModel`: after `PromptBuilder` produces prompt/messages, call `extensionBridge.fireEvent("beforePromptSend", payload)` with 500ms timeout; apply returned prompt if valid, else use original | V6,I3 |

---

## §B Bugs

| id | date | cause | fix |
|----|------|-------|-----|
