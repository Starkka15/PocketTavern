# PocketTavern

**⚠️ NOT FOR COMMERCIAL USE — See [LICENSE](LICENSE) for details**

> **PocketTavern is a fully standalone Android app for chatting with AI characters — no server required.**
> Connect directly to your LLM backend of choice, manage characters and chats locally, and take your AI companions anywhere.

[![Discord](https://img.shields.io/badge/Discord-Join%20Server-ff247f?style=for-the-badge&logoColor=white)](https://stt.gg/49Bfn8bA)
[![Release](https://img.shields.io/github/v/release/Starkka15/PocketTavern?style=for-the-badge&color=7c3aed)](https://github.com/Starkka15/PocketTavern/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3ddc84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Starkka15/PocketTavern/releases/latest)

---

## Why We Went Standalone

PocketTavern started as a companion app for SillyTavern — you needed a running SillyTavern server on your home PC, and the app just connected to it.

**We removed that requirement entirely.**

The original approach had real friction: setting up a Node.js server, configuring network access, keeping a PC running just to chat from your phone. It made the app harder to use than it needed to be, and we kept hearing the same question: *"Why can't it just work on its own?"*

So we built our own backend layer. PocketTavern now talks directly to LLM APIs — KoboldCPP, Ollama, OpenAI-compatible endpoints, Anthropic, and more — without a SillyTavern middleman. Characters, chats, and settings all live on your device in a fully SillyTavern-compatible format, so nothing is locked in.

**The goal was simplicity: one app, no setup server, just chat.**

---

## Screenshots

<p align="center">
  <img src="screenshots/01_home.png" width="180" alt="Home Screen"/>
  <img src="screenshots/02_characters.png" width="180" alt="Characters"/>
  <img src="screenshots/04_chat.png" width="180" alt="Chat"/>
  <img src="screenshots/05_settings.png" width="180" alt="Settings"/>
</p>

<p align="center">
  <img src="screenshots/03_character_options.png" width="180" alt="Character Options"/>
  <img src="screenshots/06_cardvault_cards.png" width="180" alt="CardVault Cards"/>
  <img src="screenshots/07_cardvault_lorebooks.png" width="180" alt="CardVault Lorebooks"/>
</p>

---

## Quick Start

1. Download the APK from the [Releases](https://github.com/Starkka15/PocketTavern/releases/latest) page
2. Enable **Install from unknown sources** on your Android device
3. Open the APK to install
4. Open PocketTavern → **Settings** → **API Configuration**
5. Enter your LLM backend URL and select your model
6. Head to **Characters**, import a PNG card or browse CharaVault — and chat

No Node.js. No PC. No SillyTavern server.

---

## Features

<details>
<summary><b>Chat</b></summary>

### Chat Interface

PocketTavern's chat screen is built around a natural, responsive conversation flow:

- **Streaming responses** — AI output appears word-by-word as it's generated
- **Alternative responses (swipes)** — Swipe left/right on any AI message, or use the `‹ 1/2 ›` arrows that appear below it. The `↺` button at the end of the list generates a fresh alternative. All alternatives are saved alongside the original so you can flip back and forth
- **Edit messages** — Tap any message to edit it directly
- **Delete messages** — Remove individual messages from history
- **Continue** — Append to the last AI response without starting a new one
- **Author's Note** — Inject custom text into the context at a configurable depth and frequency
- **Character backgrounds** — Display per-character background images behind the chat

### Group Chat

Chat with multiple AI characters simultaneously:

- Create groups with any combination of your local characters
- Configure reply order (sequential, random, or activation-based)
- Each character maintains its own persona, description, and world info
- Narrator mode for injecting scene-setting messages

</details>

<details>
<summary><b>LLM Backends</b></summary>

PocketTavern connects directly to your LLM without any intermediary server. Configure your endpoint once under **Settings → API Configuration**.

| Backend | Type | Notes |
|---------|------|-------|
| **KoboldCPP** | Local | `POST /api/v1/generate` — streaming via `/extra/stream` |
| **Ollama** | Local | `POST /api/generate` or `/api/chat` with streaming |
| **OpenAI-Compatible** | Local / Cloud | Works with LM Studio, TabbyAPI, vLLM, Aphrodite, TextGen WebUI, OpenAI, Mistral, Groq, DeepSeek, and any service following the `/v1/chat/completions` spec |
| **LlamaCpp Server** | Local | `POST /completion` with streaming |
| **Anthropic** | Cloud | Claude models via `POST /v1/messages` |
| **NovelAI** | Cloud | Subscription-based creative writing models |

### Connection Profiles

Save multiple backend configurations and switch between them instantly — useful if you run different models for different characters, or swap between local and cloud depending on your connection.

</details>

<details>
<summary><b>Characters & Cards</b></summary>

### Local Character Storage

Characters are stored as PNG files with embedded metadata — the same format SillyTavern uses. Every card you import or create stays on your device in `/files/characters/`, and can be exported at any time.

- Import any `.png` character card by tapping the import button or sharing a card to PocketTavern
- Create characters from scratch with name, description, personality, first message, scenario, and example dialogues
- Edit any character's details at any time
- Assign a background image per character

### Character Settings (per-character)

Each character can have its own overrides:

- Custom instruct/context template
- System prompt override
- Attached lorebook / world info file
- Token allocation adjustments

### Browse CharaVault & Forge

Browse thousands of community characters directly in the app:

- **CharaVault** — Search by name, tag, or description; preview full card details; import with one tap
- **Forge** — Community character browser with tag filtering

</details>

<details>
<summary><b>Chats & History</b></summary>

Chats are stored as `.jsonl` files in SillyTavern-compatible format — one metadata line followed by one JSON message per line.

- **Recent Chats** home screen — shows your latest conversations sorted by most recently active, with a preview of the last message
- **Multiple chats per character** — start fresh or continue any previous conversation
- **Full chat history** — scroll back through your entire conversation
- **Export** — chats are plain files you can copy/backup at any time

</details>

<details>
<summary><b>World Info & Lorebooks</b></summary>

World Info (lorebooks) inject relevant lore into the AI's context automatically based on what's being discussed.

- Attach lorebooks globally or per-character
- Character cards with embedded `character_book` entries are automatically loaded
- Entries activate when their keywords appear in recent messages
- **Probability** — entries have a configurable activation chance
- **Token budget** — stops injecting once the context budget is used up
- **Recursive scanning** — activated entry content is scanned for additional keyword matches
- **Regex keys** — use `/pattern/flags` as entry keys for advanced matching

</details>

<details>
<summary><b>Prompt Building & Templates</b></summary>

PocketTavern ships with **96+ bundled templates** copied directly from SillyTavern's open-source preset library. You can also create and save your own.

### Instruct Templates (42 bundled)
Instruct format wraps each message in the correct tokens for your model family — ChatML, Llama 3, Mistral, DeepSeek, Alpaca, Command-R, Gemma, and many more.

### Context Templates (34 bundled)
Controls how the character description, persona, scenario, world info, and chat history are assembled into the final prompt.

### TextGen Presets (6 bundled)
Sampler parameter sets — temperature, top-p, top-k, repetition penalty, min-p, etc. Includes Universal-Creative, Deterministic, and others.

### System Prompt Presets (14 bundled)
Ready-to-use system prompts: Roleplay - Immersive, Assistant - Expert, Chain of Thought, and more.

### OpenAI / Chat Completion Presets
For chat-completion APIs (OpenAI, Claude, etc.), configure a prompt order: drag and reorder system prompt blocks, world info injection points, character description, chat history, and custom injections. Each block has configurable role (system / user / assistant) and injection position (in-order or at a specific depth into chat history).

</details>

<details>
<summary><b>Extensions</b></summary>

PocketTavern ships with three built-in native extensions and a **JavaScript extension API** that lets developers build and install their own.

### Built-in Extensions

#### Quick Reply
Pre-defined response buttons appear above the text input. Tap one to instantly send a preset message — useful for common phrases, commands, or choices. Configure sets of buttons per-character or globally.

#### Regex Text Replacement
Apply find-and-replace rules to AI output (and optionally to user messages). Rules support full regular expressions with capture groups. Use cases: strip unwanted tokens, reformat text, clean up model artifacts.

#### Token Counter
Displays a live estimated token count for the current chat context. Useful for knowing when you're approaching your model's context limit.

---

### JavaScript Extension API

PocketTavern includes a WebView sandbox that runs JavaScript extensions. Extensions are installed from a URL and loaded at startup. They can react to chat events, inject text into the prompt, and persist their own settings.

#### Installing an extension

Go to **Settings → Extensions → JavaScript Extensions** and tap **+**. Enter the URL of the extension's `index.js` or its parent folder — PocketTavern downloads the file and reloads the sandbox automatically.

#### The `PT` global object

Every extension has access to the `PT` global object:

| API | Description |
|-----|-------------|
| `PT.events` | Event name constants (see below) |
| `PT.INJECTION_POSITION` | `BEFORE_CHAR_DEFS`, `AFTER_CHAR_DEFS`, `IN_CHAT` |
| `PT.eventSource.on(event, fn)` | Subscribe to a PocketTavern event |
| `PT.eventSource.off(event, fn)` | Unsubscribe |
| `PT.setExtensionPrompt(id, text, position, depth)` | Inject text into the prompt before the next generation |
| `PT.getContext()` | Returns `{ character, recentMessages, personaName, apiType }` |
| `PT.extension_settings` | Persistent settings object keyed by extension id |
| `PT.saveSettings()` | Persist `PT.extension_settings` to device storage |
| `PT.log(message)` | Write to PocketTavern's debug log |

#### Events

| Constant | Fires when… |
|----------|-------------|
| `PT.events.MESSAGE_SENT` | The user sends a message |
| `PT.events.MESSAGE_RECEIVED` | An AI response completes |
| `PT.events.MESSAGE_EDITED` | A message is edited |
| `PT.events.MESSAGE_DELETED` | A message is deleted |
| `PT.events.GENERATION_STARTED` | Generation begins |
| `PT.events.GENERATION_STOPPED` | Generation ends or is aborted |
| `PT.events.CHAT_CHANGED` | The active chat changes |
| `PT.events.CHARACTER_CHANGED` | The active character changes |

#### Example extension

```javascript
// manifest.json (optional, hosted alongside index.js):
// { "id": "word-count", "name": "Word Count", "version": "1.0.0",
//   "description": "Logs the word count of every AI response.",
//   "author": "you" }

(function () {
    var EXT_ID = 'word-count';

    // Initialise settings with defaults
    PT.extension_settings[EXT_ID] = PT.extension_settings[EXT_ID] || {
        enabled: true
    };

    // React to incoming AI messages
    PT.eventSource.on(PT.events.MESSAGE_RECEIVED, function (message) {
        if (!PT.extension_settings[EXT_ID].enabled) return;
        var words = message ? message.trim().split(/\s+/).length : 0;
        PT.log('[word-count] Response was ' + words + ' words.');
    });

    // Inject a reminder into the prompt before every generation
    PT.eventSource.on(PT.events.GENERATION_STARTED, function () {
        var ctx = PT.getContext();
        PT.setExtensionPrompt(
            EXT_ID,
            'Keep responses concise — aim for under 200 words.',
            PT.INJECTION_POSITION.AFTER_CHAR_DEFS
        );
    });

    PT.log('[word-count] loaded');
})();
```

#### Extension file layout

```
my-extension/
├── index.js       ← required — the extension code
└── manifest.json  ← optional — name, version, description, author, id
```

`manifest.json` format:
```json
{
  "id": "my-extension",
  "name": "My Extension",
  "version": "1.0.0",
  "description": "What it does.",
  "author": "your-name"
}
```

Host both files anywhere accessible by URL (GitHub raw, a web server, etc.). The install URL can point directly to `index.js` or to the folder — PocketTavern appends `/index.js` automatically.

</details>

<details>
<summary><b>User Persona</b></summary>

Set up a persona to tell the AI who it's talking to:

- **Display name** — shown in chat bubbles
- **Description** — injected into the system prompt so characters know who you are
- **Avatar** — your profile picture in the chat interface
- Multiple personas — create different personas for different roleplay scenarios and switch between them

</details>

<details>
<summary><b>Settings & Configuration</b></summary>

### API Configuration
- Select backend type (KoboldCPP, Ollama, OpenAI-compatible, Anthropic, etc.)
- Enter endpoint URL and API key (if required)
- Pick your model from a fetched list or enter manually
- Streaming toggle

### Connection Profiles
- Save multiple named API + model configurations
- Switch profiles from the main screen

### Text Generation Parameters
- Temperature, top-p, top-k, min-p, repetition penalty, context size, response length
- Load/save named presets

### Formatting
- Select instruct template
- Select context template
- Configure system prompt
- Enable/disable individual prompt sections

### OpenAI Preset Editor
- Full drag-and-reorder prompt block editor
- Per-block role and injection mode controls
- Works with any chat-completion-style API

### General Settings
- Stable Diffusion Forge URL (for avatar generation)
- CardVault / CharaVault server URL
- Theme preferences
- Debug logging toggle

</details>

<details>
<summary><b>Stable Diffusion Avatar Generation</b></summary>

Generate character avatars using your local Stable Diffusion Forge server.

1. Install [Stable Diffusion WebUI Forge](https://github.com/lllyasviel/stable-diffusion-webui-forge) with `--api` flag
2. Note the server address (e.g., `http://192.168.1.100:7860`)
3. Enter it in **Settings → Stable Diffusion Forge**

Avatar generation is available in the Create Character and Edit Character screens.

</details>

<details>
<summary><b>SillyTavern Import (Migration)</b></summary>

Already have characters and chats on a SillyTavern server? Migrate everything to PocketTavern in one step:

1. Go to **Settings → Import from SillyTavern**
2. Enter your SillyTavern server URL and credentials
3. Select what to import: characters, chats, lorebooks
4. Tap **Import** — everything is pulled down and saved locally

After import, PocketTavern works completely independently. Your SillyTavern server is no longer needed.

Alternatively, you can import a folder of `.png` character cards directly via the file picker — no server required.

</details>

---

## Content & Legal

> **Content Disclaimer:** PocketTavern does not host, store, or provide any character content. All characters come from your own device or optional third-party services (CharaVault, Forge) that you configure. We have no visibility into what characters or content you use.

> **Personal Use:** This app is designed strictly for personal use. The app ID and package name (`com.pockettavern.app`) are independent of SillyTavern. PocketTavern is not affiliated with, endorsed by, or derived from any commercial product.

---

## Credits

- **[Starkka15](https://github.com/Starkka15)** — Lead Developer
- **[Kuma3D](https://github.com/Kuma3D)** — UI / Graphical Layout
- **[SillyTavern](https://github.com/SillyTavern/SillyTavern)** — Instruct/context/textgen preset templates bundled under their open-source license
