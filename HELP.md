# PocketTavern Help Guide

> **PocketTavern is a fully standalone Android app for chatting with AI characters — no server required.**
> Connect directly to your LLM backend of choice, manage characters and chats locally, and take your AI companions anywhere.

PocketTavern does not host or provide any AI models or content. You connect to your own LLM backends. Character cards are stored on your device.

---

## Table of Contents

- [Getting Started](#getting-started)
- [Home Screen](#home-screen)
- [Chat](#chat)
- [Characters & Cards](#characters--cards)
- [Group Chats](#group-chats)
- [LLM Backends](#llm-backends)
- [Image Generation](#image-generation)
- [Text-to-Speech (TTS)](#text-to-speech-tts)
- [World Info & Lorebooks](#world-info--lorebooks)
- [User Personas](#user-personas)
- [Prompt Building & Templates](#prompt-building--templates)
- [Text Generation Settings](#text-generation-settings)
- [CharaVault](#charavault)
- [Extensions](#extensions)
- [Themes & Appearance](#themes--appearance)
- [SillyTavern Import](#sillytavern-import)
- [Settings Overview](#settings-overview)
- [Frequently Asked Questions](#frequently-asked-questions)
- [Troubleshooting](#troubleshooting)

---

## Getting Started

### Step 1 — Get Some Characters

- **Import a PNG card** — Go to Characters, tap the import button, and select a `.png` character card from your device.
- **Create a character** — Tap "Create Character" on the home screen to build one from scratch with name, description, personality, scenario, and messages.
- **Browse CharaVault** — Tap CharaVault on the home screen to search and import community characters. Supports both CharaVault.net (public catalog) and self-hosted servers.

### Step 2 — Connect an LLM Backend

1. Open **Settings → API Configuration**. Select your API type from the dropdown.
2. **Local backends** (KoboldCpp, Ollama, LM Studio, etc.): Enter your PC's local IP address and port (e.g. `http://192.168.1.100:5001`). Your phone must be on the same Wi-Fi network.
3. **Cloud APIs** (OpenAI, Anthropic, Groq, etc.): Select Chat Completion, enter the API URL and your API key, then tap the refresh button to load models.
4. Tap **Test Connection** to verify. A green "Connected" indicator appears when successful.

### Step 3 — Start Chatting

1. Go to **Characters** and tap a character to open a new chat.
2. Type a message and tap **Send**. The AI response streams in real-time.
3. Your chat is saved automatically. Find it again under **Recent Chats** or by tapping the character.

---

## Home Screen

The home screen is your main hub with five navigation cards:

- **Characters** — Browse, import, and manage your character cards.
- **Recent Chats** — Shows your latest conversations sorted by most recently active, with a preview of the last message. Tap any chat to jump back in.
- **Create Character** — Jumps directly to the character creation form.
- **CharaVault** — Browse and import community characters and lorebooks.
- **Settings** — Access all app settings and configuration.

The bottom of the screen shows your current connection status (connected or disconnected).

If a new app version is available, an update dialog will appear with a Download button.

---

## Chat

### Sending Messages

- Type in the message box at the bottom and tap the **Send** button (arrow icon). The AI response streams in word-by-word with a live cursor.
- You can also use the keyboard's Send/Done action to send.
- The `/sys` prefix sends a narrator/system message that appears as centered italic text.

### Controls Below Messages

- **Stop** — While the AI is generating, a red Stop button appears. Tap it to abort generation (the partial response is kept).
- **Regenerate** — After an AI response, tap to delete the last AI message and generate a fresh one.
- **Continue** — Tap to append more text to the last AI response without starting a new message.

### Alternative Responses (Swipes)

- Swipe left or right on any AI message to cycle through alternative responses.
- A counter (e.g. "1/3") appears below AI messages with alternatives.
- Tap the left/right arrows to navigate. The refresh icon on the right generates a new alternative.
- All alternatives are saved — you can flip back and forth at any time.

### Long-Press Actions

Long-press any message bubble to open the action menu:

- **Edit Message** — Opens an editor to modify the message text.
- **Delete Message** — Removes just that message.
- **Delete From Here** — Removes this message and everything after it.
- **Regenerate** — (Last AI message only) Regenerates the response.
- **Generate Image** — Opens the image generation dialog using this message's context.
- **Play TTS / Stop TTS** — (When TTS is enabled) Speak or stop speaking this message.

### Top Bar Menu

Tap the three-dot menu (top right) to access:

- **Chat History** — View and switch between saved chats for this character.
- **New Chat** — Start a fresh conversation. If the character has alternate greetings, a greeting picker appears.
- **Character Settings** — Open per-character settings (lorebook, system prompt, TTS voice, etc.).
- **Edit Character** — Open the character editor.
- **Upload Background** — Pick an image from your device as the chat background.
- **Clear Background** — Remove the current chat background.
- **Debug Log** — View the app's debug log for troubleshooting.
- **Delete Chat / Delete Character** — Destructive actions (with confirmation).

### Status Bar

Above the input bar, a small status line shows your current API name and model (e.g. "OpenAI · gpt-4o").

### Quick Reply Buttons

When the Quick Reply extension is enabled, preset buttons appear above the input bar. Tap any button to instantly send that message.

---

## Characters & Cards

### Character List

- **Tap** a character to start or resume a chat.
- **Long-press** a character to open the action menu: Edit, Character Settings, Upload to CharaVault, or Delete.
- Tap the **+** button (top right) to create a new character.
- Tap the **import** button to import a `.png` character card from your device.
- Switch between **Characters** and **Groups** using the title dropdown.

### Creating / Editing a Character

The character editor has five tabs:

| Tab | Contents |
|-----|----------|
| **Basic** | Name (required) and avatar. Tap the avatar to choose from gallery or generate with AI. |
| **Personality** | Description, Personality, and Scenario fields. |
| **Messages** | First Message, Alternate Greetings (add unlimited), and Example Dialogue. |
| **Advanced** | Character-specific System Prompt (use `{{original}}` to include the default), Post-History Instructions, and embedded Character Lorebook info. |
| **Meta** | Creator name, Tags (type and tap Add), and Creator Notes. |

Tap **Save/Create** (top right) when done.

### Character Settings (Per-Character)

Open from the character's long-press menu or the chat overflow menu:

- **Favorite toggle** — Heart icon in the top bar. Favorites are pinned to the top of the character list.
- **Attached Lorebook** — Select a lorebook to attach to this character.
- **System Prompt** — Override the global system prompt for this character only.
- **Author's Note** — Per-character Author's Note with depth, role, and position settings.
- **Post-History Instructions** — Text injected after chat history.
- **Talkativeness** — Slider controlling how often this character speaks in group chats (0% = quiet, 100% = talkative).
- **TTS Voice** — Select a voice override for this character. "Default" uses the global TTS settings.

---

## Group Chats

Chat with multiple AI characters simultaneously.

### Creating a Group

1. Go to **Characters**, switch to the **Groups** tab (title dropdown), and tap **+**.
2. Enter a group name and select at least 2 characters.
3. Tap **Create**.

### Group Chat Controls

- The top bar shows the group name and member count.
- Tap the **three-dot menu** to change the **Activation Strategy**:
  - **Natural** — Characters respond in a natural conversational order.
  - **List (All respond)** — All characters respond in list order.
  - **Pooled** — Characters are drawn from a pool.
  - **Manual** — You choose which character responds each turn.
- Long-press a group in the list to delete it.

---

## LLM Backends

### API Configuration

Go to **Settings → API Configuration** to connect to your LLM:

1. Select the **API type** from the dropdown (Text Completion or Chat Completion).
2. Enter the **server URL** and **API key** (if required).
3. Tap the **refresh icon** to fetch available models from the server.
4. Select your **model** from the dropdown or type it manually.
5. Toggle **streaming** on/off.
6. Tap **Test Connection** to verify.

### Text Completion Backends

For local inference servers that use raw text prompts:

| Backend | Default URL / Notes |
|---------|-------------------|
| **KoboldCpp** | `http://[YOUR-IP]:5001` — start with `--host 0.0.0.0` |
| **llama.cpp** | `http://[YOUR-IP]:8080` |
| **Ollama** | `http://[YOUR-IP]:11434` — set `OLLAMA_HOST=0.0.0.0` |
| **Text Gen WebUI (Ooba)** | Oobabooga's text-generation-webui API |
| **vLLM** | High-throughput local inference |
| **Aphrodite** | Local — `/v1/completions` |
| **TabbyAPI** | ExllamaV2 backend |
| **Together AI** | Cloud |
| **OpenRouter** | Cloud (also available as chat completion) |
| **Infermatic AI** | Cloud |
| **Featherless** | Cloud |
| **Mancer** | Cloud |
| **DreamGen** | Cloud |
| **HuggingFace** | Cloud — Inference API |
| **Generic** | Any `/v1/completions`-compatible endpoint |

### Chat Completion Backends

For APIs that use the chat message format:

| Backend | Notes |
|---------|-------|
| **OpenAI** | GPT-3.5, GPT-4, GPT-4o, o1 |
| **Anthropic (Claude)** | Claude models via `/v1/messages` |
| **Google AI Studio** | Gemini models |
| **DeepSeek** | DeepSeek-V2, DeepSeek-Coder |
| **Mistral AI** | Mistral, Mixtral |
| **Groq** | Ultra-fast cloud inference |
| **Cohere** | Command models |
| **Perplexity** | Search-augmented generation |
| **OpenRouter** | Multi-provider gateway |
| **xAI (Grok)** | Grok models |
| **Fireworks** | Cloud inference |
| **AI21** | Jamba models |
| **Vertex AI** | Google Cloud |
| **Azure OpenAI** | Enterprise Azure endpoints |
| **Moonshot, NanoGPT, AIML API** | Cloud |
| **Pollinations** | Free text generation |
| **Chutes, ElectronHub, SiliconFlow, Z.AI** | Cloud |
| **Custom** | Any OpenAI-compatible endpoint via custom URL |

### Connection Profiles

Go to **Settings → Connection Profiles** to save multiple API configurations and switch between them instantly. Useful if you run different models for different characters or swap between local and cloud.

---

## Image Generation

### Supported Backends

| Backend | Auth | Notes |
|---------|------|-------|
| **SD WebUI / Forge** | URL | Local Stable Diffusion server (`--api` flag required) |
| **ComfyUI** | URL | Local ComfyUI node-graph server |
| **DALL-E (OpenAI)** | API Key | Models: dall-e-3, dall-e-2 |
| **Stability AI** | API Key | Stability AI REST API |
| **Pollinations** | API Key | Pollen credits (pay-as-you-go) — models: flux, flux-realism, flux-anime, flux-3d, turbo |
| **HuggingFace** | API Key | HF Inference API — configurable model ID |

### Generating from Chat

1. **Long-press** any message → **Generate Image**.
2. Choose a mode: **Background** (landscape scene) or **Character** (portrait).
3. Your LLM automatically generates an image prompt from the message context.
4. Edit the prompt if desired, then tap **Generate**.
5. When complete: **Save to Gallery** or **Set as Chat Background**.

### Per-Character Avatar Toggle

In Character mode, a "Use avatar as reference" switch appears. When enabled, the character's avatar is used as an img2img reference for more consistent results. Disable it for characters whose avatars don't contain a face or body. This setting is saved per-character.

### Avatar Generation

When creating or editing a character, tap the avatar area → **Generate with AI**. This uses your configured image generation backend to create an avatar.

If you're using KoboldCpp on the same machine as SD WebUI, PocketTavern automatically unloads the language model from VRAM before starting image generation, then reloads it afterwards.

### Image Generation Settings

Configure under **Settings → Image Generation**:

- **Backend selector** — Switch between all six backends.
- **URL / API Key** — Shown only when the active backend requires them.
- **Sampler and Model** — Fetched dynamically from SD WebUI and ComfyUI. Tap **Fetch Options** to refresh.
- **Steps** (1–150), **CFG Scale** (1–30), **Seed** (-1 = random).
- **Resolution presets** — Portrait 512x768, Landscape 768x512, Square 512x512, HD Portrait 768x1024, HD Landscape 1024x768, HD Square 1024x1024.
- **Negative prompt** — Text describing what to avoid in generation.
- **CLIP Skip** (SD WebUI / Forge only).
- **Test Connection** — Verify the backend is reachable.

---

## Text-to-Speech (TTS)

### Providers

| Provider | How it works |
|----------|-------------|
| **System TTS** | Uses Android's built-in speech engine. Works offline with your device's installed voices. |
| **OpenAI-Compatible** | Sends text to any server implementing `POST /v1/audio/speech`. Works with OpenAI, Kokoro, AllTalk, XTTS, and others. |

### Configuration

Go to **Settings → Text-to-Speech**:

- **Provider** — Choose System or OpenAI-Compatible.
- **Auto-play** — Automatically speak new AI messages as they arrive.
- **Speed** — Playback rate from 0.5x to 2.0x.
- **Voice** — Select from available voices (fetched from server for OpenAI-compatible).
- **Filter mode** — Control what gets spoken:
  - *All text* — Speaks everything (markdown stripped).
  - *Quotes only* — Only reads quoted dialogue (`"like this"`).
  - *No asterisks* — Skips action text (`*like this*`).

### Per-Character Voices

Each character can have its own voice override. Set it in the character's settings under **TTS Voice**. The global default is used when no override is set.

### Manual Playback

Long-press any message in chat to access **Play TTS** and **Stop TTS** options, regardless of auto-play settings.

---

## World Info & Lorebooks

Lorebooks inject relevant lore into the AI's context automatically based on what's being discussed in chat.

### How It Works

- Each lorebook entry has **keywords**. When those keywords appear in recent messages, the entry's content is injected into the prompt.
- **Secondary keys** provide an AND-filter — both primary and secondary keywords must match.
- **Constant entries** are always active and bypass keyword matching.
- **Recursive scanning** checks activated entries for additional keyword matches.
- **Regex keys** (`/pattern/flags`) enable advanced pattern matching.

### Entry Settings

- **Insertion position** — Before Char, After Char, Top/Bottom of Author's Note, or At Depth.
- **Probability** — Activation chance (0–100%).
- **Token budget** — Stops injecting once the budget is used up.
- **Entry groups** — Organize entries into named groups.
- **Selective flag** — Enables secondary keys for finer activation control.

### Attaching Lorebooks

- **Globally** — Go to Settings → World Info / Lorebooks.
- **Per-character** — Go to Character Settings → Attached Lorebook.
- **Per-persona** — Edit a persona and attach a lorebook.
- **Embedded** — Character cards with embedded `character_book` entries are loaded automatically.
- **From CharaVault** — Browse and import community lorebooks directly from the CharaVault screen.

---

## User Personas

Personas tell the AI who it's talking to. You can create multiple personas and switch between them.

### Creating a Persona

1. Go to **Settings → Personas** and tap the **+** button.
2. Choose an avatar (pick from gallery or generate with AI).
3. Enter a display name and optional description.
4. Tap **Create**.

### Editing a Persona

Tap the Edit icon on any persona to configure:

- **Description** — Injected into the prompt so characters know who you are.
- **Position** — Where the description appears: In System Prompt, In Chat at Depth, Top/Bottom of Author's Note.
- **Role** — System, User, or Assistant.
- **Depth** — (When position is "In Chat at Depth") How many messages from the end to inject.
- **Attached lorebook** — Attach a lorebook specific to this persona.

### Switching Personas

Tap any persona in the list to make it active. The current persona is shown with a checkmark and highlighted at the top.

---

## Prompt Building & Templates

PocketTavern ships with **96+ bundled templates** from SillyTavern's open-source library. You can also create your own.

### Instruct Templates (42 bundled)

Wraps each message in the correct tokens for your model family — ChatML, Llama 3, Mistral, DeepSeek, Alpaca, Command-R, Gemma, and many more. Go to **Settings → Formatting** to select one.

### Context Templates (34 bundled)

Controls how character description, persona, scenario, world info, and chat history are assembled into the final prompt.

### TextGen Presets (6 bundled)

Sampler parameter sets for text completion backends — temperature, top-p, top-k, repetition penalty, min-p, etc. Go to **Settings → Text Generation** to load/save presets.

### System Prompt Presets (14 bundled)

Ready-to-use system prompts: Roleplay - Immersive, Assistant - Expert, Chain of Thought, and more.

### Chat Completion Presets

For chat-completion APIs (OpenAI, Claude, etc.), go to **Settings → Chat Completion Presets**. Configure prompt order by dragging and reordering system prompt blocks, world info injection points, character description, and custom injections. Each block has a configurable role and injection position.

---

## Text Generation Settings

### Text Completion (Local Backends)

**Settings → Text Generation.** Key parameters:

| Parameter | Description |
|-----------|-------------|
| **Temperature** | Randomness of output (higher = more creative) |
| **Top-P, Top-K, Min-P, Top-A** | Sampling filters that control token selection |
| **Repetition Penalty** | Discourages the model from repeating itself |
| **Max New Tokens** | Maximum response length |
| **Context Size** | How much chat history fits in the prompt |
| **DRY Sampler** | Advanced repetition suppression |
| **Mirostat** | Alternative sampling mode with target perplexity |

Load and save named presets (e.g. Universal-Creative, Deterministic).

### Chat Completion (Cloud APIs)

**Settings → Chat Completion Presets.** Key parameters:

| Parameter | Description |
|-----------|-------------|
| **Temperature, Top-P, Top-K** | Same as above but for cloud APIs |
| **Max Tokens** | Maximum response length |
| **Frequency Penalty, Presence Penalty** | Repetition controls specific to OpenAI-style APIs |
| **Context Size, Seed** | Additional controls |

The **Prompt order editor** lets you drag and reorder prompt blocks, toggle them on/off, and set roles per block.

### Author's Note

**Settings → Context Settings.** Inject custom text into every conversation:

- **Content** — The text to inject.
- **Depth** — How many messages from the end to place it (lower = more influential).
- **Interval** — How often to inject (every N messages).
- **Position** — Before Char, After Char, Top/Bottom of Author's Note, At Depth.
- **Role** — System, User, or Assistant.

---

## CharaVault

Browse and import community characters and lorebooks from within the app.

### Modes

- **CharaVault.net** — Browse the public catalog. Guest browsing shows SFW content only. Log in with email/password for full access. NSFW content requires age verification (18+). 2FA/TOTP is supported.
- **Self-hosted** — Connect to your own CharaVault server by entering its URL.

### Browsing

- **Search** by name or description using the search bar.
- **Filter by tags** — Tap the tag icon to open the tag selector. Selected tags appear as chips above the results.
- **Content filter** — Tap the filter icon to toggle SFW/NSFW filtering.
- Switch between **Characters** and **Lorebooks** using the title dropdown.
- **Navigate pages** with the Previous/Next buttons. Tap the page number to jump to a specific page.

### Importing

- Tap any card to open a **preview** with full details.
- Tap **"Import to PocketTavern"** to download and save locally.
- Tap **tags** in the preview to add them to your search filter.

### Uploading

Long-press a character in the Characters list → **Upload to CharaVault** to share your character with the community.

---

## Extensions

### Built-in Extensions

Go to **Settings → Extensions** to enable/disable:

- **Quick Reply** — Preset buttons above the chat input. Tap any button to instantly send a message. Configure presets in Quick Reply Settings.
- **Regex** — Find-and-replace rules applied to AI output or user messages. Supports full regular expressions with capture groups.
- **Token Counter** — Shows a live estimated token count above the input while typing.

### JavaScript Extensions

Install third-party extensions that run in a sandboxed WebView:

1. Tap the **+** button in the JavaScript Extensions section.
2. **Install from URL** — Enter the URL of the extension's `index.js` or its parent folder.
3. **Install from Device** — Browse for a `.js` file or `.zip` bundle.
4. Toggle extensions on/off with the **switch** on each card.
5. Some extensions have configurable settings — tap **"Settings"** on the card to expand them.
6. Tap **"Uninstall"** (with confirmation) to remove an extension.

See the [README](README.md) for the full JavaScript Extension API documentation.

---

## Themes & Appearance

### Applying a Theme

Go to **Settings → Appearance**. Tap **"Apply"** on any theme to activate it.

### Built-in Themes

- **PocketTavern** (default) — Fire & Ice with animated particle effects.
- **Fire & Ice** — The default theme exported as editable JSON.
- **Midnight Plum** — Purple stars rising with slow-falling diamonds.
- **Ember** — Warm embers with bright spark accents.
- **Sand and Sea** — Warm sandy tones with ocean-blue accents.

### Importing Themes

- Tap **"Import Theme (.json or .zip)"** at the bottom of the Appearance screen.
- **JSON files** — SillyTavern theme exports or PocketTavern-native theme files.
- **ZIP bundles** — Include `theme.json` plus optional background image, logo, and music. Max 50 MB.

### Theme Features

- **Animated backgrounds** — GIF or animated WebP files play automatically.
- **Theme logos** — Replace the PocketTavern logo on the home screen.
- **Background music** — MP3, OGG, or WAV files play when the theme is active.
- **Particle effects** — Animated background particles (embers, snow, bubbles, rain, sparkles, or fully custom).
- **Delete** imported themes with the trash icon. Built-in themes cannot be deleted.

See the [README](README.md) for the full theme format specification and authoring guide.

---

## SillyTavern Import

Migrate your characters, chats, and lorebooks from SillyTavern.

### From Server

1. Go to **Settings → Import from SillyTavern**.
2. Enter your SillyTavern server URL and credentials.
3. Select what to import: characters, chats, lorebooks.
4. Tap **Import** — everything is pulled down and saved locally.

### From Folder

1. Copy your SillyTavern data directory to your Android device.
2. Choose **"Import from Folder"** and use the folder picker.
3. Characters, chats, and lorebooks are scanned and imported.

After import, PocketTavern works completely independently. Your SillyTavern server is no longer needed.

You can also import individual `.png` character cards at any time via the character list import button.

---

## Settings Overview

Settings are organized into five groups:

### Connection

- **API Configuration** — Select backend type, URL, API key, model, streaming toggle.
- **Connection Profiles** — Save and switch between multiple named API configurations.
- **Image Generation** — Configure image generation backends.

### Generation

- **Text Generation** — Sampler settings and presets for text completion backends. *(Dimmed when using chat completion APIs.)*
- **Chat Completion Presets** — Sampling presets and prompt order editor for OpenAI-compatible APIs. *(Dimmed when using text completion backends.)*
- **Formatting** — Instruct templates, context templates, and system prompt presets. *(Dimmed when using chat completion APIs.)*

### World & Characters

- **World Info / Lorebooks** — View and manage lorebook entries.
- **Context Settings** — Global Author's Note configuration.
- **Personas** — Create and manage user personas.

### Appearance & Audio

- **Appearance** — Import and apply themes (JSON or ZIP).
- **Text-to-Speech** — Voice synthesis settings.

### Utilities

- **Extensions** — Quick Reply, Regex, Token Counter, and JavaScript extensions.
- **Import from SillyTavern** — Migrate characters, chats, and lorebooks.
- **Help** — In-app help guide.

A connection status indicator at the bottom shows whether you're currently connected to your LLM backend.

---

## Frequently Asked Questions

<details>
<summary><b>Do I need a SillyTavern server to use PocketTavern?</b></summary>

No. PocketTavern is fully standalone. It connects directly to LLM backends (KoboldCpp, Ollama, OpenAI, etc.) with no middleman. SillyTavern is not required.
</details>

<details>
<summary><b>What character card formats are supported?</b></summary>

PocketTavern uses PNG character cards with embedded metadata (V2 spec) — the same format SillyTavern uses. Any `.png` card exported from SillyTavern, CharaVault, Chub.ai, or similar tools will work.
</details>

<details>
<summary><b>Can I use a local AI model on my phone?</b></summary>

PocketTavern doesn't run models directly on-device. It connects to LLM servers. Run a model on your PC (KoboldCpp, Ollama, LM Studio) and connect to it over Wi-Fi, or use a cloud API.
</details>

<details>
<summary><b>How do I connect to a local backend like KoboldCpp?</b></summary>

1. Start KoboldCpp on your PC with `--host 0.0.0.0` so it listens on the network.
2. Find your PC's local IP (e.g. `192.168.1.100`).
3. In PocketTavern: Settings → API Configuration → enter `http://192.168.1.100:5001`.
4. Your phone must be on the same Wi-Fi network.
</details>

<details>
<summary><b>Why can't I connect to my local backend?</b></summary>

Common causes:
- The backend isn't listening on the network (use `--host 0.0.0.0` for KoboldCpp, `OLLAMA_HOST=0.0.0.0` for Ollama).
- Using "localhost" or "127.0.0.1" instead of your PC's actual IP address.
- Phone and PC are on different Wi-Fi networks.
- Firewall is blocking the port.
- The URL has a trailing slash.
</details>

<details>
<summary><b>How do I use OpenAI / Claude / Groq?</b></summary>

Settings → API Configuration → select Chat Completion as the API type. Choose your provider, enter the API URL and your API key, then tap the refresh button to load models. Select a model and tap Test Connection.
</details>

<details>
<summary><b>What's the difference between Text Completion and Chat Completion?</b></summary>

**Text Completion** backends (KoboldCpp, llama.cpp, etc.) take a single text prompt. They need instruct templates and context templates to format the conversation correctly.

**Chat Completion** backends (OpenAI, Claude, etc.) take a list of messages with roles. They use the Chat Completion Presets and prompt order editor instead.
</details>

<details>
<summary><b>How do I get the AI to follow my instructions better?</b></summary>

- Put key instructions in the character's System Prompt (Character Settings).
- Use Author's Note (Settings → Context Settings) at a low depth for persistent steering.
- For text completion: make sure you're using the correct instruct template for your model.
- For chat completion: edit the Main Prompt in Chat Completion Presets.
</details>

<details>
<summary><b>Can I use multiple AI models?</b></summary>

Yes. Use **Connection Profiles** (Settings → Connection Profiles) to save different API configurations and switch between them instantly.
</details>

<details>
<summary><b>How does image generation work?</b></summary>

Long-press a message → Generate Image. Choose Background or Character mode. The LLM generates an image prompt from the message context, which is sent to your configured image backend. You can edit the prompt before generating. Configure your backend in Settings → Image Generation.
</details>

<details>
<summary><b>Do I need to pay for image generation?</b></summary>

SD WebUI / Forge and ComfyUI are free but require running a local server. Pollinations, DALL-E, Stability AI, and HuggingFace require paid API keys. Pollinations uses a pay-as-you-go "Pollen credits" system — get a key at pollinations.ai.
</details>

<details>
<summary><b>What are swipes?</b></summary>

Swipes are alternative AI responses. Swipe left/right on any AI message (or use the arrow buttons below it) to cycle through alternatives. The refresh button generates a new alternative. All alternatives are saved.
</details>

<details>
<summary><b>How do I import my SillyTavern data?</b></summary>

Settings → Import from SillyTavern. You can import from a running SillyTavern server (enter URL and credentials) or from a folder on your device containing SillyTavern data files.
</details>

<details>
<summary><b>Where are my files stored?</b></summary>

Characters, chats, lorebooks, themes, and settings are stored in PocketTavern's private app storage on your device. Chats use the SillyTavern-compatible `.jsonl` format and characters use standard PNG cards, so nothing is locked in.
</details>

<details>
<summary><b>How do I back up my data?</b></summary>

Chat files (`.jsonl`) and character cards (`.png`) are stored in the app's internal storage. You can export individual character cards. For a full backup, use Android's file manager or ADB to copy the app's data directory.
</details>

<details>
<summary><b>What are extensions?</b></summary>

Extensions add features to PocketTavern. Three are built-in (Quick Reply, Regex, Token Counter). You can also install JavaScript extensions from URLs that react to chat events, inject prompts, show custom UI, and more. Configure them in Settings → Extensions.
</details>

<details>
<summary><b>Can I use SillyTavern themes?</b></summary>

Yes. Export a theme from SillyTavern as a `.json` file, transfer it to your device, and import it via Settings → Appearance → Import Theme. PocketTavern reads the color fields and applies them. Web-specific CSS fields are ignored.
</details>

---

## Troubleshooting

### Can't connect to local backend

- Make sure the backend is running and listening on the network
- Use your PC's local IP address (e.g. `192.168.1.x`), not `localhost` or `127.0.0.1`
- Phone and PC must be on the same Wi-Fi network
- Check that your firewall allows the port (5001, 11434, etc.)
- For KoboldCpp: start with `--host 0.0.0.0` flag
- For Ollama: set `OLLAMA_HOST=0.0.0.0` before starting
- Make sure the URL does not have a trailing slash

### Cloud API not working

- Double-check your API key is correct and has credits/quota remaining
- Make sure the API URL does not have a trailing slash
- Tap the model refresh button after entering the URL and key
- Check the Debug Log (chat menu → Debug Log) for error details

### AI gives short or empty responses

- Increase Max Tokens / Max New Tokens in generation settings
- Check that your context size isn't too small for the conversation history
- Some models need a specific instruct template — make sure you've selected the right one in Formatting

### AI ignores instructions / Author's Note

- Try putting key instructions in the character's System Prompt instead
- Reduce Author's Note depth (lower = closer to the end = more influential)
- For chat completion: edit the Main Prompt in Chat Completion Presets
- Some models (especially Claude) anchor heavily to their initial system prompt

### Characters not loading or showing errors

- Check that the PNG file is a valid character card (exported from SillyTavern or similar)
- Try re-importing the PNG file
- Check the Debug Log for parse errors
- Some very old card formats (V1) may not include all fields — try a V2 card

### Image generation fails

- Verify the backend is running and reachable (use Test Connection in Image Generation settings)
- For SD WebUI / Forge: make sure it was started with the `--api` flag
- For cloud backends: check that your API key is valid and has credits
- Check the Debug Log for detailed error messages

### TTS not working

- For System TTS: make sure your device has a TTS engine installed (most Android devices do by default)
- For OpenAI-compatible: verify the server URL and that the server is running
- Try the Test Voice button in TTS settings
- Check that TTS is enabled in Settings → Text-to-Speech

### Theme not applying correctly

- Make sure the theme file is valid JSON
- For ZIP bundles: the ZIP must contain a `theme.json` at the root level
- ZIP bundles are limited to 50 MB
- Some SillyTavern theme fields (CSS, font scale) are not applicable to Android and are ignored
