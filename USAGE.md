# SillyTavern Android - User Guide

A mobile client for SillyTavern that lets you chat with AI characters on your Android device.

> **Note:** This app does not host or provide any content. All characters and data come from your own self-hosted SillyTavern server.

## Requirements

- Android device (Android 8.0+)
- SillyTavern server running on your local network
- Server URL (e.g., `http://192.168.1.100:8000`)

## Setup

### 1. Install the App

Install the APK on your Android device. You may need to enable "Install from unknown sources" in your device settings.

### 2. Configure Server Connection

1. Open the app and tap the **gear icon** (Settings) in the bottom status bar
2. Enter your **SillyTavern Server URL** (e.g., `http://192.168.1.100:8000`)
3. If your server requires authentication, enter your **Username** and **Password**
4. Tap **Test Connection** to verify it works
5. Tap **Save** to store your settings

### 3. Optional: Stable Diffusion Forge

If you have a Stable Diffusion Forge server for generating character avatars:
1. Enter the **Forge URL** in settings (e.g., `http://192.168.1.100:7860`)

## Features

### Character List

The main screen shows all characters from your SillyTavern server:
- Tap a character to start or continue chatting
- Long-press a character to delete them
- Tap the **Refresh** button to reload the list
- Tap the **+** button to create a new character

The character list automatically refreshes when you return from importing or creating characters.

### Chat

The chat screen provides a full conversation interface:

**Top Bar Actions:**
- **Back arrow** - Return to character list
- **History** (clock icon) - View and switch between chat sessions
- **New Chat** (+) - Start a fresh conversation
- **Delete** (trash icon) - Delete the current chat session

**Messaging:**
- Type messages in the input field at the bottom
- Tap **Send** to send your message
- Messages stream in real-time as the AI generates them
- The chat automatically scrolls to show new content

**Chat History:**
- Tap the **History** button to see all saved conversations
- Each chat shows the date/time it was created
- Tap a chat to switch to it
- The current chat is highlighted

**Text Formatting:**
The app renders markdown formatting in messages:
- **Bold** text with `**text**`
- *Italic* text with `*text*` or `_text_`
- ***Bold and italic*** with `***text***`
- `Code` with backticks

### Create Character

Create custom characters directly in the app:
1. Tap the **+** button on the main screen
2. Fill in the character details:
   - Name (required)
   - Description
   - Personality
   - Scenario
   - First Message (greeting)
3. Optionally generate an avatar using Stable Diffusion Forge
4. Tap **Create** to save the character

### Browse Chub.ai (Full version only)

Import new characters directly from Chub.ai:

1. Enable Chub.ai in Settings
2. Tap **Chub.ai** on the main screen
3. Use the search bar to find characters
4. Use the **Sort** dropdown to change ordering:
   - Downloads (most popular)
   - Recent (newest)
   - Rating (highest rated)
   - Random
5. Tap a character to preview details
6. Tap **Import to SillyTavern** to add the character

The character will be downloaded and imported directly to your SillyTavern server. The character list refreshes automatically when you return.

## Troubleshooting

### "Connection failed" error
- Verify your SillyTavern server is running
- Check that your device is on the same network as the server
- Ensure the server URL is correct (include `http://` and the port)
- If using authentication, verify credentials are correct

### Characters not loading
- Tap the Refresh button to reload the list
- Check server connection in Settings
- Verify SillyTavern has characters imported

### Chub.ai search not working
- Ensure you have internet connectivity
- Try different search terms
- Clear the search and tap Search to browse all characters

### Chat not responding
- Check that your SillyTavern server has an API configured
- Verify the AI backend (KoboldCpp, etc.) is running and connected
- Check server logs for errors

### Streaming not working
- Ensure your AI backend supports streaming
- Check that the server URL is correct
- Try restarting the SillyTavern server

## Tips

- The app supports streaming responses - you'll see the AI's reply appear word-by-word as it's generated
- Your chat history is stored on the SillyTavern server, so you can continue conversations from any device
- Character avatars are cached locally for faster loading
- Use the chat history feature to maintain multiple conversation threads with the same character
- Long conversations are automatically saved to the server
