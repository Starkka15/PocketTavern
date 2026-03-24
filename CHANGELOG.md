# Changelog

All notable changes to PocketTavern are documented here.

---

## [2.0.0] — 2026-02-21 — Standalone Release

### Overview

Version 2.0 is a complete architectural overhaul. PocketTavern is now a **fully standalone app** — no SillyTavern server is required.

We made this change because the original design created unnecessary friction. Users had to set up a Node.js server, configure network access, keep a PC running just to use the app, and debug connectivity issues before they could even send a message. That was the opposite of what we wanted PocketTavern to be: *simple*.

The new architecture talks directly to LLM backends (KoboldCPP, Ollama, OpenAI-compatible APIs, Anthropic, and more), stores all data locally on-device in SillyTavern-compatible formats, and ships with all the templates you'd expect built right in.

---

### What's New

#### Architecture
- **Removed SillyTavern server dependency entirely** — the app no longer needs a running ST instance
- Replaced `SillyTavernRepository` (3300-line server proxy) with a local-first data layer
- New package identifier: `com.pockettavern.app` (previously `com.stark.sillytavern`)
- App renamed from internal working title to **PocketTavern** throughout

#### Direct LLM Backends
- **KoboldCPP** — `POST /api/v1/generate` with streaming
- **Ollama** — `POST /api/generate` / `/api/chat` with streaming
- **OpenAI-Compatible** — `/v1/chat/completions` (covers LM Studio, TabbyAPI, vLLM, Aphrodite, TextGen WebUI OAI mode, OpenAI, Mistral, Groq, DeepSeek, and others)
- **LlamaCpp Server** — `POST /completion` with streaming
- **Anthropic** — Claude models via `/v1/messages`
- **NovelAI** — `/ai/generate-stream`
- All backends support token streaming via SSE/chunked transfer

#### Local Storage
- Characters stored as PNG files with embedded metadata (SillyTavern-compatible `.png` card format)
- Chats stored as JSONL files (SillyTavern-compatible format, one message per line)
- Lorebooks stored as JSON files (SillyTavern world info format)
- Room database for fast indexing, search, and filtering — file system remains the source of truth
- Recent Chats now sorts by actual last-modified time (not filename creation timestamp)
- Recent Chats now shows a preview of the last message

#### Bundled Templates (96+ total)
Copied from SillyTavern's open-source preset library:
- 42 instruct templates (Llama 3, Mistral V1–V7, ChatML, DeepSeek, Gemma, Command-R, Alpaca, and more)
- 34 context templates
- 14 system prompt presets
- 6 TextGen presets
- 3 OpenAI presets (Balanced, Creative, Precise)

#### Extension System
New native extension framework:
- **Quick Reply** — configurable preset message buttons above the input field
- **Regex Text Replacement** — find/replace rules applied to AI output, with full regex support
- **Token Counter** — live estimated token count display

#### OpenAI Prompt Order Editor
- Full drag-and-reorder prompt block system for chat-completion APIs
- Per-block role selection: `system` / `user` / `assistant`
- Per-block injection mode: in-order or injected at a specific depth into chat history
- Depth-0 injection ordering fixed (blocks now appear in their configured sequence)

#### Connection Profiles
- Save named API + model configurations
- Switch between profiles from the main screen

#### SillyTavern Import Wizard
- One-time migration tool: connects to a SillyTavern server, pulls characters, chats, and lorebooks, saves locally
- After import, the ST server is no longer needed
- Also supports direct PNG folder import (no server required)

#### World Info Improvements
- Character book entries (embedded in PNG cards) now automatically loaded into context
- Probability-based entry activation
- Token budget enforcement (stops injecting once budget is reached)
- Recursive scanning (activated entry content is scanned for further keyword matches)
- Regex key matching (`/pattern/flags` syntax)

#### Chat Improvements
- Author's Note depth and frequency controls
- Per-message edit and delete fully wired to local storage
- Alternative response system: swipe left/right on any AI message or tap the arrow buttons below it; `↺` button at the end generates a new alternative; all alternatives are stored alongside the original
- Group chat activation strategy selector
- Continue generation (append to last response without starting fresh)

---

### Removed

- **SillyTavernApi.kt** / **SillyTavernDtos.kt** — ST REST client no longer needed
- **SillyTavernRepository.kt** — replaced by `LocalRepository` + `LlmRepository`
- **AuthInterceptor / CsrfInterceptor** — no longer needed without an ST server
- **Server URL / Username / Password** settings — replaced with direct backend URL + API key
- "Test Connection" to ST server — replaced with per-backend connection test
- Setup requirement for SillyTavern Multi-User Mode

---

### Bug Fixes

- Recent Chats sorted by filename creation date instead of actual last activity — fixed
- Recent Chats "last message" preview always showed "Tap to continue chatting" — fixed
- Depth-0 system prompt injections appearing in reversed order — fixed
- GOT patching Thumb bit causing SIGILL in native layer — fixed

---

## [1.0.2] — Previous Release

- Add group chat activation strategy selector
- Bump version to 1.0.2

## [1.0.1]

- Add CharaVault login and mode switching
- Add group chat feature
- Add update check notification
- Fix version comparison with flavor suffix

## [1.0.0]

- Initial release
- SillyTavern companion app
- Characters, chats, CardVault, Forge, basic settings
