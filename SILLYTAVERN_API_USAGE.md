# SillyTavern API Usage

This document lists all SillyTavern API endpoints and components that PocketTavern uses.

## Authentication & Session

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/csrf-token` | GET | Retrieve CSRF token for authenticated requests |
| `/api/users/login` | POST | Multi-User Mode authentication |
| `/api/users/me` | GET | Get current user information |
| `/api/users/logout` | POST | End user session |
| `/api/users/change-password` | POST | Change user password |

## Characters

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/characters/all` | POST | List all characters |
| `/api/characters/get` | POST | Get single character details |
| `/api/characters/create` | POST | Create new character |
| `/api/characters/edit` | POST | Edit existing character |
| `/api/characters/edit-attribute` | POST | Edit single character attribute |
| `/api/characters/delete` | POST | Delete character |
| `/api/characters/import` | POST | Import character card (multipart) |
| `/api/characters/export` | POST | Export character as PNG |
| `/api/characters/chats` | POST | Get chat history list for character |
| `/thumbnail` | GET | Get character avatar thumbnail |

## Chat

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/chats/get` | POST | Get chat messages |
| `/api/chats/save` | POST | Save chat messages |
| `/api/chats/delete` | POST | Delete chat |

## Text Generation

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/backends/text-completions/generate` | POST | Generate AI response (text completion) |
| `/api/backends/text-completions/generate` | POST | Streaming generation (SSE) |
| `/api/backends/text-completions/abort` | POST | Abort ongoing generation |
| `/api/backends/text-completions/status` | POST | Get text completion backend status/models |
| `/api/backends/chat-completions/status` | POST | Get chat completion backend status/models |

## Settings & Presets

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/settings/get` | POST | Get server settings |
| `/api/settings/save` | POST | Save server settings |
| `/api/presets/save` | POST | Save generation preset |
| `/api/presets/delete` | POST | Delete generation preset |

## World Info / Lorebooks

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/worldinfo/list` | POST | List all lorebooks |
| `/api/worldinfo/get` | POST | Get lorebook entries |
| `/api/worldinfo/import` | POST | Import lorebook file (multipart) |

## User Personas / Avatars

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/avatars/get` | POST | List user avatars |
| `/api/avatars/upload` | POST | Upload user avatar (multipart) |
| `/api/avatars/delete` | POST | Delete user avatar |

## Groups

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/groups/all` | POST | List all groups |
| `/api/groups/create` | POST | Create new group |
| `/api/chats/group/get` | POST | Get group chat messages |
| `/api/chats/group/save` | POST | Save group chat messages |

---

## Features NOT Used

The following SillyTavern features are not currently implemented in PocketTavern:

- **Extensions** - Third-party extensions and their APIs
- **Advanced Formatting** - Custom prompt templates beyond instruct mode
- **Backgrounds** - Chat background images
- **Quick Replies** - Preset reply buttons
- **Tags Management** - Bulk tag operations
- **Regex Scripts** - Text replacement scripts
- **Vector Storage** - RAG/embedding features
- **Summarization** - Automatic chat summarization
- **TTS/STT** - Text-to-speech and speech-to-text
- **Image Generation** - In-chat image generation (Forge integration is separate)
- **Data Bank** - File attachments storage
- **Timelines** - Chat branching/timelines

---

## Required SillyTavern Configuration

For PocketTavern to function, SillyTavern must have:

1. **Network Access**: `listen: true` in config.yaml
2. **Multi-User Mode**: `enableUserAccounts: true` in config.yaml
3. **Valid User Account**: Created via the web interface

## API Compatibility

PocketTavern is tested against the official [SillyTavern](https://github.com/SillyTavern/SillyTavern) repository. Forked versions may have modified APIs and are not supported.
