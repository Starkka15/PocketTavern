# SillyTavern Android

A native Android client for SillyTavern chat interface.

## Features

### Connection
- Connect to a SillyTavern server via URL
- Automatic reconnection on app launch
- Connection status indicator

### Characters
- View list of available characters from server
- Character avatars displayed in list
- Create new characters with:
  - Name
  - Description
  - Personality
  - First message
  - Avatar image (optional)
- **Long-press character name to delete**
- Character cards created in chara_card_v2 format (PNG with embedded data)

### Chat
- Real-time chat with selected character
- Message history display
- User and character message bubbles
- Bold text formatting support (`**text**`)
- Send messages with Enter key or send button

### Technical Details
- Built with Kotlin and Jetpack Compose
- MVVM architecture with Hilt dependency injection
- Retrofit for API communication
- DataStore for local settings persistence
- PNG character card embedding for avatar uploads

## Requirements
- Android 8.0 (API 26) or higher
- Network access to SillyTavern server
