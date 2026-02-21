# PocketTavern — Extension System Plan

## Overview

PocketTavern's extension system mirrors SillyTavern's but implemented natively in Kotlin,
with an optional WebView sandbox for third-party JS extensions.

---

## How SillyTavern Extensions Work (Research Summary)

### Directory Structure
Each extension lives in `/public/scripts/extensions/{name}/`:
- `manifest.json` — metadata, entry point, optional `generate_interceptor`
- `index.js` — ES module, the extension code
- `style.css` — optional styling
- `html/`, `locales/`, `src/` — optional

### manifest.json Format
```json
{
  "display_name": "Quick Reply",
  "loading_order": 1,
  "js": "index.js",
  "css": "style.css",
  "author": "ST Team",
  "version": "1.0.0",
  "generate_interceptor": "quickReplyInterceptor"
}
```

### Event System
```javascript
eventSource.on(event_types.MESSAGE_RECEIVED, handler);
eventSource.emit(event_types.GENERATION_STARTED, data);
```

Available events (100+): MESSAGE_SENT, MESSAGE_RECEIVED, MESSAGE_EDITED, MESSAGE_DELETED,
CHAT_CHANGED, CHARACTER_CHANGED, GENERATION_STARTED, GENERATION_STOPPED,
SETTINGS_SAVED, PROMPT_PROCESSING_STARTED, PROMPT_PROCESSING_DONE, MESSAGE_SWIPED,
CHAT_CREATED, GROUP_CHAT_CHANGED, etc.

### getContext() API
Extensions call `getContext()` to read app state:
- `character` — current character
- `chat` — current messages array
- `group` — current group (if any)
- `chatIndex` — current chat index

### Prompt Injection
```javascript
setExtensionPrompt(extensionName, prompt, position, role);
// position: IN_PROMPT(0), IN_CHAT(1), BEFORE_PROMPT(2)
// role: SYSTEM(0), USER(1), ASSISTANT(2)
```

### Settings Persistence
```javascript
extension_settings.myExtension = { enabled: true, rules: [] };
saveSettingsDebounced();
```

### Generation Interceptor
Manifest field `generate_interceptor` → global function name:
```javascript
window.myInterceptor = async function(chat, contextSize, abort, type) {
    // Modify chat array before generation
    // Call abort() to cancel generation
}
```

### Built-in Extensions in ST
| Extension | What it does |
|-----------|-------------|
| `quick-reply` | Preset buttons above input, injects text on click |
| `regex` | Find/replace rules applied to AI output (and optionally input) |
| `expressions` | Parses emotion from output, overlays character sprite |
| `memory` | Summarizes old messages to free context |
| `token-counter` | Live token count display |
| `stable-diffusion` | Generates images from chat |
| `vectors` | RAG/embeddings for long-term memory |
| `tts` | Text-to-speech |
| `translate` | Message translation |

---

## PocketTavern Extension Architecture

### File Structure (New)
```
extensions/
├── ExtensionEventBus.kt       ← Global event bus (mirrors ST's eventSource)
├── ExtensionManager.kt        ← Discovers, loads, manages all extensions
├── NativeExtension.kt         ← Base interface for native Kotlin extensions
├── native/
│   ├── QuickReplyExtension.kt ← Quick reply preset buttons
│   ├── RegexExtension.kt      ← Find/replace rules on messages
│   ├── TokenCounterExtension.kt ← Live token count estimate
│   └── ExpressionsExtension.kt  ← Emotion detection + sprite overlay
└── webview/
    └── ExtensionWebView.kt    ← WebView sandbox for JS extensions (Tier 3)

domain/model/
├── QuickReplyPreset.kt        ← Data: preset name + list of buttons
├── QuickReplyButton.kt        ← Data: label + message template
└── RegexRule.kt               ← Data: pattern, replacement, enabled, flags

ui/screens/extensions/
├── ExtensionsScreen.kt        ← Hub: list extensions, enable/disable
├── ExtensionsViewModel.kt
├── quickreply/
│   ├── QuickReplySettingsScreen.kt
│   └── QuickReplySettingsViewModel.kt
└── regex/
    ├── RegexSettingsScreen.kt
    └── RegexSettingsViewModel.kt
```

### Domain Models

#### QuickReplyButton
```kotlin
data class QuickReplyButton(
    val id: String,       // UUID
    val label: String,    // Display text on button
    val message: String   // Text to send (supports {{user}}, {{char}} macros)
)
```

#### QuickReplyPreset
```kotlin
data class QuickReplyPreset(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val buttons: List<QuickReplyButton>
)
```

#### RegexRule
```kotlin
data class RegexRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val pattern: String,       // regex or plain string
    val isRegex: Boolean,
    val replacement: String,   // can use $1, $2 capture groups
    val applyToOutput: Boolean = true,
    val applyToInput: Boolean = false,
    val caseInsensitive: Boolean = false
)
```

### ExtensionEventBus
```kotlin
enum class ExtensionEvent {
    MESSAGE_SENT,
    MESSAGE_RECEIVED,
    MESSAGE_EDITED,
    MESSAGE_DELETED,
    GENERATION_STARTED,
    GENERATION_STOPPED,
    CHAT_CHANGED,
    CHARACTER_CHANGED,
    PROMPT_ABOUT_TO_BUILD   // prompt injection hook
}

object ExtensionEventBus {
    fun emit(event: ExtensionEvent, data: Any? = null)
    fun on(event: ExtensionEvent, handler: suspend (Any?) -> Unit): () -> Unit
}
```

### NativeExtension Interface
```kotlin
interface NativeExtension {
    val id: String
    val displayName: String
    var enabled: Boolean
    fun onEvent(event: ExtensionEvent, data: Any?)
    fun getPromptInjection(): String?   // called before each generation
}
```

### ExtensionManager (Hilt Singleton)
```kotlin
@Singleton
class ExtensionManager @Inject constructor(
    private val quickReplyExtension: QuickReplyExtension,
    private val regexExtension: RegexExtension,
    private val tokenCounterExtension: TokenCounterExtension
) {
    val extensions: List<NativeExtension>
    fun processOutputMessage(text: String): String   // applies regex rules
    fun processInputMessage(text: String): String    // applies input regex rules
    fun getPromptInjections(): List<String>          // collects from all extensions
    fun emit(event: ExtensionEvent, data: Any? = null)
}
```

---

## Implementation Plan

### Step 1 — ExtensionEventBus + NativeExtension interface
Files: `extensions/ExtensionEventBus.kt`, `extensions/NativeExtension.kt`

### Step 2 — Domain Models
Files: `domain/model/QuickReplyPreset.kt`, `domain/model/QuickReplyButton.kt`, `domain/model/RegexRule.kt`

### Step 3 — QuickReplyExtension (native)
File: `extensions/native/QuickReplyExtension.kt`
- Stores list of presets
- Exposes enabled presets' buttons to the chat UI
- Sends button tap as a message (calls ChatViewModel.sendMessage)

### Step 4 — RegexExtension (native)
File: `extensions/native/RegexExtension.kt`
- Stores list of rules (persisted in DataStore/JSON)
- `processOutput(text)` — apply all enabled output rules
- `processInput(text)` — apply all enabled input rules

### Step 5 — ExtensionManager (Hilt Singleton)
File: `extensions/ExtensionManager.kt`
- Aggregates all native extensions
- Called from ChatViewModel to process messages

### Step 6 — Wire into ChatViewModel
- After receiving streamed text → `extensionManager.processOutputMessage(text)`
- Before sending → `extensionManager.processInputMessage(text)`
- Emit `MESSAGE_RECEIVED`, `MESSAGE_SENT`, `GENERATION_STARTED`, `GENERATION_STOPPED`

### Step 7 — Quick Reply UI in ChatScreen
- Row of buttons above the message input
- Only shown when at least one preset has `enabled = true`
- Tapping a button calls `viewModel.sendMessage(button.message)`

### Step 8 — Extensions Hub Screen
File: `ui/screens/extensions/ExtensionsScreen.kt`
- Cards for each extension with enable toggle
- Navigate to per-extension settings

### Step 9 — Quick Reply Settings Screen
File: `ui/screens/extensions/quickreply/QuickReplySettingsScreen.kt`
- List presets with add/edit/delete
- Each preset: name field + list of buttons (label + message)

### Step 10 — Regex Settings Screen
File: `ui/screens/extensions/regex/RegexSettingsScreen.kt`
- List rules with add/edit/delete/reorder
- Each rule: name, pattern, replacement, flags, apply-to toggles
- Live preview: test pattern against sample text

### Step 11 — Token Counter
File: `extensions/native/TokenCounterExtension.kt`
- Simple char/4 heuristic
- Displayed as a chip in ChatScreen toolbar or above input
- Updates on every message change

### Step 12 — Routes + Navigation
Add `Extensions`, `QuickReplySettings`, `RegexSettings` routes
Wire in NavGraph and SettingsHubScreen

---

## Persistence

Extension settings stored in `DataStore` + JSON files:
- `extension_quick_reply_presets` → JSON array of QuickReplyPreset
- `extension_regex_rules` → JSON array of RegexRule
- `extension_{name}_enabled` → Boolean per extension

---

## Deferred (Tier 3)

- **Expressions** — emotion detection + sprite overlay (needs per-character image set)
- **WebView Sandbox** — JS extension loading (for third-party ST extensions)
  - Loads extension JS in hidden WebView
  - Exposes getContext(), setExtensionPrompt(), eventSource via @JavascriptInterface
  - Returns prompt injections to PromptBuilder
  - Install from URL (git or direct download)
- **TTS** — Text-to-speech (Android TTS API + optional external service)
- **Memory** — Auto-summarize old messages (requires LLM call)
