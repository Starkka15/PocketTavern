package com.pockettavern.app.ui.screens.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockettavern.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupGuideScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Introduction ────────────────────────────────────────
            Text(
                text = "Welcome to PocketTavern",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "PocketTavern is a standalone AI character chat app for Android. Connect directly to any LLM backend, manage characters and chats locally, and take your AI companions anywhere — no server or PC required.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "PocketTavern does not host or provide any AI models or content. You connect to your own LLM backends. Character cards are stored on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Getting Started ─────────────────────────────────────
            HelpDropdown(title = "Getting Started") {
                SectionHeading("Step 1 — Get Some Characters")
                BulletItem("Import a PNG card — Go to Characters, tap the import button, and select a .png character card from your device.")
                BulletItem("Create a character — Tap \"Create Character\" on the home screen to build one from scratch with name, description, personality, scenario, and messages.")
                BulletItem("Browse CharaVault — Tap CharaVault on the home screen to search and import community characters. Supports both CharaVault.net (public catalog) and self-hosted servers.")

                VerticalSpacer()
                SectionHeading("Step 2 — Connect an LLM Backend")
                BulletItem("Open Settings → API Configuration. Select your API type from the dropdown.")
                BulletItem("Local backends (KoboldCpp, Ollama, LM Studio, etc.): Enter your PC's local IP address and port (e.g. http://192.168.1.100:5001). Your phone must be on the same Wi-Fi network.")
                BulletItem("Cloud APIs (OpenAI, Anthropic, Groq, etc.): Select Chat Completion, enter the API URL and your API key, then tap the refresh button to load models.")
                BulletItem("Tap Test Connection to verify. A green \"Connected\" indicator appears when successful.")

                VerticalSpacer()
                SectionHeading("Step 3 — Start Chatting")
                BulletItem("Go to Characters and tap a character to open a new chat.")
                BulletItem("Type a message and tap Send. The AI response streams in real-time.")
                BulletItem("Your chat is saved automatically. Find it again under Recent Chats or by tapping the character.")
            }

            // ── Home Screen ─────────────────────────────────────────
            HelpDropdown(title = "Home Screen") {
                HelpText("The home screen is your main hub with five navigation cards:")
                BulletItem("Characters — Browse, import, and manage your character cards.")
                BulletItem("Recent Chats — Shows your latest conversations sorted by most recently active, with a preview of the last message. Tap any chat to jump back in.")
                BulletItem("Create Character — Jumps directly to the character creation form.")
                BulletItem("CharaVault — Browse and import community characters and lorebooks.")
                BulletItem("Settings — Access all app settings and configuration.")
                VerticalSpacer()
                HelpText("The bottom of the screen shows your current connection status (connected or disconnected).")
                HelpText("If a new app version is available, an update dialog will appear with a Download button.")
            }

            // ── Chat ────────────────────────────────────────────────
            HelpDropdown(title = "Chat") {
                SectionHeading("Sending Messages")
                BulletItem("Type in the message box at the bottom and tap the Send button (arrow icon). The AI response streams in word-by-word with a live cursor.")
                BulletItem("You can also use the keyboard's Send/Done action to send.")
                BulletItem("The /sys prefix sends a narrator/system message that appears as centered italic text.")

                VerticalSpacer()
                SectionHeading("Controls Below Messages")
                BulletItem("Stop — While the AI is generating, a red Stop button appears. Tap it to abort generation (the partial response is kept).")
                BulletItem("Regenerate — After an AI response, tap to delete the last AI message and generate a fresh one.")
                BulletItem("Continue — Tap to append more text to the last AI response without starting a new message.")

                VerticalSpacer()
                SectionHeading("Alternative Responses (Swipes)")
                BulletItem("Swipe left or right on any AI message to cycle through alternative responses.")
                BulletItem("A counter (e.g. \"1/3\") appears below AI messages with alternatives.")
                BulletItem("Tap the left/right arrows to navigate. The refresh icon on the right generates a new alternative.")
                BulletItem("All alternatives are saved — you can flip back and forth at any time.")

                VerticalSpacer()
                SectionHeading("Long-Press Actions")
                HelpText("Long-press any message bubble to open the action menu:")
                BulletItem("Edit Message — Opens an editor to modify the message text.")
                BulletItem("Delete Message — Removes just that message.")
                BulletItem("Delete From Here — Removes this message and everything after it.")
                BulletItem("Regenerate — (Last AI message only) Regenerates the response.")
                BulletItem("Generate Image — Opens the image generation dialog using this message's context.")
                BulletItem("Play TTS / Stop TTS — (When TTS is enabled) Speak or stop speaking this message.")

                VerticalSpacer()
                SectionHeading("Top Bar Menu")
                HelpText("Tap the three-dot menu (top right) to access:")
                BulletItem("Chat History — View and switch between saved chats for this character.")
                BulletItem("New Chat — Start a fresh conversation. If the character has alternate greetings, a greeting picker appears.")
                BulletItem("Character Settings — Open per-character settings (lorebook, system prompt, TTS voice, etc.).")
                BulletItem("Edit Character — Open the character editor.")
                BulletItem("Upload Background — Pick an image from your device as the chat background.")
                BulletItem("Clear Background — Remove the current chat background.")
                BulletItem("Debug Log — View the app's debug log for troubleshooting.")
                BulletItem("Delete Chat / Delete Character — Destructive actions (with confirmation).")

                VerticalSpacer()
                SectionHeading("Status Bar")
                HelpText("Above the input bar, a small status line shows your current API name and model (e.g. \"OpenAI · gpt-4o\").")

                VerticalSpacer()
                SectionHeading("Quick Reply Buttons")
                HelpText("When the Quick Reply extension is enabled, preset buttons appear above the input bar. Tap any button to instantly send that message.")
            }

            // ── Characters ──────────────────────────────────────────
            HelpDropdown(title = "Characters & Cards") {
                SectionHeading("Character List")
                BulletItem("Tap a character to start or resume a chat.")
                BulletItem("Long-press a character to open the action menu: Edit, Character Settings, Upload to CharaVault, or Delete.")
                BulletItem("Tap the + button (top right) to create a new character.")
                BulletItem("Tap the import button to import a .png character card from your device.")
                BulletItem("Switch between Characters and Groups using the title dropdown.")

                VerticalSpacer()
                SectionHeading("Creating / Editing a Character")
                HelpText("The character editor has five tabs:")
                BulletItem("Basic — Name (required) and avatar. Tap the avatar to choose from gallery or generate with AI.")
                BulletItem("Personality — Description, Personality, and Scenario fields.")
                BulletItem("Messages — First Message, Alternate Greetings (add unlimited), and Example Dialogue.")
                BulletItem("Advanced — Character-specific System Prompt (use {{original}} to include the default), Post-History Instructions, and embedded Character Lorebook info.")
                BulletItem("Meta — Creator name, Tags (type and tap Add), and Creator Notes.")
                VerticalSpacer()
                HelpText("Tap Save/Create (top right) when done.")

                VerticalSpacer()
                SectionHeading("Character Settings (Per-Character)")
                HelpText("Open from the character's long-press menu or the chat overflow menu:")
                BulletItem("Favorite toggle — Heart icon in the top bar. Favorites are pinned to the top of the character list.")
                BulletItem("Attached Lorebook — Select a lorebook to attach to this character.")
                BulletItem("System Prompt — Override the global system prompt for this character only.")
                BulletItem("Author's Note — Per-character Author's Note with depth, role, and position settings.")
                BulletItem("Talkativeness — Slider controlling how often this character speaks in group chats.")
                BulletItem("TTS Voice — Select a voice override for this character. \"Default\" uses the global TTS settings.")
            }

            // ── Group Chat ──────────────────────────────────────────
            HelpDropdown(title = "Group Chats") {
                HelpText("Chat with multiple AI characters simultaneously.")
                VerticalSpacer()
                SectionHeading("Creating a Group")
                BulletItem("Go to Characters, switch to the Groups tab (title dropdown), and tap +.")
                BulletItem("Enter a group name and select at least 2 characters.")
                BulletItem("Tap Create.")

                VerticalSpacer()
                SectionHeading("Group Chat Controls")
                BulletItem("The top bar shows the group name and member count.")
                BulletItem("Tap the three-dot menu to change the Activation Strategy:")
                BulletItem("  Natural — Characters respond in a natural conversational order.")
                BulletItem("  List (All respond) — All characters respond in list order.")
                BulletItem("  Pooled — Characters are drawn from a pool.")
                BulletItem("  Manual — You choose which character responds each turn.")
                VerticalSpacer()
                BulletItem("Long-press a group in the list to delete it.")
            }

            // ── LLM Backends ────────────────────────────────────────
            HelpDropdown(title = "LLM Backends") {
                SectionHeading("API Configuration")
                HelpText("Go to Settings → API Configuration to connect to your LLM.")
                BulletItem("Select the API type from the dropdown (Text Completion or Chat Completion).")
                BulletItem("Enter the server URL and API key (if required).")
                BulletItem("Tap the refresh icon to fetch available models from the server.")
                BulletItem("Select your model from the dropdown or type it manually.")
                BulletItem("Toggle streaming on/off.")
                BulletItem("Tap Test Connection to verify.")

                VerticalSpacer()
                SectionHeading("Text Completion Backends")
                HelpText("For local inference servers that use raw text prompts:")
                BulletItem("KoboldCpp — http://[YOUR-IP]:5001 (start with --host 0.0.0.0)")
                BulletItem("llama.cpp — http://[YOUR-IP]:8080")
                BulletItem("Ollama — http://[YOUR-IP]:11434 (set OLLAMA_HOST=0.0.0.0)")
                BulletItem("Text Gen WebUI (Ooba), vLLM, Aphrodite, TabbyAPI")
                BulletItem("Cloud: Together AI, OpenRouter, Infermatic AI, Featherless, Mancer, DreamGen, HuggingFace")

                VerticalSpacer()
                SectionHeading("Chat Completion Backends")
                HelpText("For APIs that use the chat message format:")
                BulletItem("OpenAI, Anthropic (Claude), Google AI Studio (Gemini)")
                BulletItem("DeepSeek, Mistral AI, Groq, Cohere, Perplexity")
                BulletItem("OpenRouter, xAI (Grok), Fireworks, AI21, Vertex AI, Azure OpenAI")
                BulletItem("Pollinations (free), and many more")
                BulletItem("Custom — any OpenAI-compatible endpoint via custom URL")

                VerticalSpacer()
                SectionHeading("Connection Profiles")
                HelpText("Go to Settings → Connection Profiles to save multiple API configurations and switch between them instantly. Useful if you run different models for different characters or swap between local and cloud.")
            }

            // ── Image Generation ────────────────────────────────────
            HelpDropdown(title = "Image Generation") {
                SectionHeading("Supported Backends")
                BulletItem("SD WebUI / Forge — Local server (requires --api flag). Enter URL in settings.")
                BulletItem("ComfyUI — Local node-graph server. Enter URL in settings.")
                BulletItem("DALL-E (OpenAI) — Cloud API. Enter API key. Supports dall-e-2 and dall-e-3.")
                BulletItem("Stability AI — Cloud API. Enter API key.")
                BulletItem("Pollinations (Free) — No authentication or server required.")
                BulletItem("HuggingFace — Cloud API. Enter API key and model ID.")

                VerticalSpacer()
                SectionHeading("Generating from Chat")
                BulletItem("Long-press any message → Generate Image.")
                BulletItem("Choose a mode: Background (landscape scene) or Character (portrait).")
                BulletItem("Your LLM automatically generates an image prompt from the message context.")
                BulletItem("Edit the prompt if desired, then tap Generate.")
                BulletItem("When complete: Save to Gallery or Set as Chat Background.")

                VerticalSpacer()
                SectionHeading("Per-Character Avatar Toggle")
                HelpText("In Character mode, a \"Use avatar as reference\" switch appears. When enabled, the character's avatar is used as an img2img reference for more consistent results. Disable it for characters whose avatars don't contain a face or body. This setting is saved per-character.")

                VerticalSpacer()
                SectionHeading("Avatar Generation")
                HelpText("When creating or editing a character, tap the avatar area to access Generate with AI. This uses your configured image generation backend to create an avatar.")

                VerticalSpacer()
                SectionHeading("Settings")
                HelpText("Configure under Settings → Image Generation:")
                BulletItem("Backend selector — Switch between all six backends.")
                BulletItem("URL / API Key — Shown only when the active backend requires them.")
                BulletItem("Sampler and Model — Fetched dynamically from SD WebUI and ComfyUI. Tap Fetch Options to refresh.")
                BulletItem("Steps (1–150), CFG Scale (1–30), Seed (-1 = random).")
                BulletItem("Resolution presets: Portrait, Landscape, Square, HD Portrait, HD Landscape, HD Square.")
                BulletItem("Negative prompt — Text describing what to avoid in generation.")
                BulletItem("CLIP Skip (SD WebUI / Forge only).")
                BulletItem("Test Connection — Verify the backend is reachable.")
            }

            // ── Text-to-Speech ──────────────────────────────────────
            HelpDropdown(title = "Text-to-Speech (TTS)") {
                SectionHeading("Providers")
                BulletItem("System TTS — Uses Android's built-in speech engine. Works offline with your device's installed voices.")
                BulletItem("OpenAI-Compatible — Sends text to any server implementing POST /v1/audio/speech. Works with OpenAI, Kokoro, AllTalk, XTTS, and others.")

                VerticalSpacer()
                SectionHeading("Configuration")
                HelpText("Go to Settings → Text-to-Speech:")
                BulletItem("Provider — Choose System or OpenAI-Compatible.")
                BulletItem("Auto-play — Automatically speak new AI messages as they arrive.")
                BulletItem("Speed — Playback rate from 0.5x to 2.0x.")
                BulletItem("Voice — Select from available voices (fetched from server for OpenAI-compatible).")
                BulletItem("Filter mode — Control what gets spoken: All text, Quotes only (\"dialogue\"), or No asterisks (*actions*).")

                VerticalSpacer()
                SectionHeading("Per-Character Voices")
                HelpText("Each character can have its own voice override. Set it in the character's settings under TTS Voice. The global default is used when no override is set.")

                VerticalSpacer()
                SectionHeading("Manual Playback")
                HelpText("Long-press any message in chat to access Play TTS and Stop TTS options, regardless of auto-play settings.")
            }

            // ── World Info ──────────────────────────────────────────
            HelpDropdown(title = "World Info & Lorebooks") {
                HelpText("Lorebooks inject relevant lore into the AI's context automatically based on what's being discussed in chat.")
                VerticalSpacer()
                SectionHeading("How It Works")
                BulletItem("Each lorebook entry has keywords. When those keywords appear in recent messages, the entry's content is injected into the prompt.")
                BulletItem("Secondary keys provide an AND-filter — both primary and secondary keywords must match.")
                BulletItem("Constant entries are always active and bypass keyword matching.")
                BulletItem("Recursive scanning checks activated entries for additional keyword matches.")
                BulletItem("Regex keys (/pattern/flags) enable advanced pattern matching.")

                VerticalSpacer()
                SectionHeading("Entry Settings")
                BulletItem("Insertion position — Before Char, After Char, Top/Bottom of Author's Note, or At Depth.")
                BulletItem("Probability — Activation chance (0–100%).")
                BulletItem("Token budget — Stops injecting once the budget is used up.")
                BulletItem("Entry groups — Organize entries into named groups.")
                BulletItem("Selective flag — Enables secondary keys for finer activation control.")

                VerticalSpacer()
                SectionHeading("Attaching Lorebooks")
                BulletItem("Globally — Go to Settings → World Info / Lorebooks.")
                BulletItem("Per-character — Go to Character Settings → Attached Lorebook.")
                BulletItem("Per-persona — Edit a persona and attach a lorebook.")
                BulletItem("Embedded — Character cards with embedded character_book entries are loaded automatically.")
                BulletItem("From CharaVault — Browse and import community lorebooks directly from the CharaVault screen.")
            }

            // ── Personas ────────────────────────────────────────────
            HelpDropdown(title = "User Personas") {
                HelpText("Personas tell the AI who it's talking to. You can create multiple personas and switch between them.")
                VerticalSpacer()
                SectionHeading("Creating a Persona")
                BulletItem("Go to Settings → Personas and tap the + button.")
                BulletItem("Choose an avatar (pick from gallery or generate with AI).")
                BulletItem("Enter a display name and optional description.")
                BulletItem("Tap Create.")

                VerticalSpacer()
                SectionHeading("Editing a Persona")
                HelpText("Tap the Edit icon on any persona to configure:")
                BulletItem("Description — Injected into the prompt so characters know who you are.")
                BulletItem("Position — Where the description appears: In System Prompt, In Chat at Depth, Top/Bottom of Author's Note.")
                BulletItem("Role — System, User, or Assistant.")
                BulletItem("Depth — (When position is \"In Chat at Depth\") How many messages from the end to inject.")

                VerticalSpacer()
                SectionHeading("Switching Personas")
                HelpText("Tap any persona in the list to make it active. The current persona is shown with a checkmark and highlighted at the top.")
            }

            // ── Prompt Building ──────────────────────────────────────
            HelpDropdown(title = "Prompt Building & Templates") {
                HelpText("PocketTavern ships with 96+ bundled templates from SillyTavern's open-source library. You can also create your own.")
                VerticalSpacer()
                SectionHeading("Instruct Templates (42 bundled)")
                HelpText("Wraps each message in the correct tokens for your model family — ChatML, Llama 3, Mistral, DeepSeek, Alpaca, Command-R, Gemma, and many more. Go to Settings → Formatting to select one.")

                VerticalSpacer()
                SectionHeading("Context Templates (34 bundled)")
                HelpText("Controls how character description, persona, scenario, world info, and chat history are assembled into the final prompt.")

                VerticalSpacer()
                SectionHeading("TextGen Presets (6 bundled)")
                HelpText("Sampler parameter sets for text completion backends — temperature, top-p, top-k, repetition penalty, min-p, etc. Go to Settings → Text Generation to load/save presets.")

                VerticalSpacer()
                SectionHeading("System Prompt Presets (14 bundled)")
                HelpText("Ready-to-use system prompts: Roleplay - Immersive, Assistant - Expert, Chain of Thought, and more.")

                VerticalSpacer()
                SectionHeading("Chat Completion Presets")
                HelpText("For chat-completion APIs (OpenAI, Claude, etc.), go to Settings → Chat Completion Presets. Configure prompt order by dragging and reordering system prompt blocks, world info injection points, character description, and custom injections. Each block has a configurable role and injection position.")
            }

            // ── Generation Settings ─────────────────────────────────
            HelpDropdown(title = "Text Generation Settings") {
                SectionHeading("Text Completion (Local Backends)")
                HelpText("Settings → Text Generation. Key parameters:")
                BulletItem("Temperature — Randomness of output (higher = more creative).")
                BulletItem("Top-P, Top-K, Min-P, Top-A — Sampling filters that control token selection.")
                BulletItem("Repetition Penalty — Discourages the model from repeating itself.")
                BulletItem("Max New Tokens — Maximum response length.")
                BulletItem("Context Size — How much chat history fits in the prompt.")
                BulletItem("DRY Sampler — Advanced repetition suppression.")
                BulletItem("Mirostat — Alternative sampling mode with target perplexity.")
                BulletItem("Load and save named presets (e.g. Universal-Creative, Deterministic).")

                VerticalSpacer()
                SectionHeading("Chat Completion (Cloud APIs)")
                HelpText("Settings → Chat Completion Presets. Key parameters:")
                BulletItem("Temperature, Top-P, Top-K — Same as above but for cloud APIs.")
                BulletItem("Max Tokens — Maximum response length.")
                BulletItem("Frequency Penalty, Presence Penalty — Repetition controls specific to OpenAI-style APIs.")
                BulletItem("Context Size, Seed — Additional controls.")
                BulletItem("Prompt order editor — Drag and reorder prompt blocks, toggle them on/off, set roles per block.")

                VerticalSpacer()
                SectionHeading("Author's Note")
                HelpText("Settings → Context Settings. Inject custom text into every conversation:")
                BulletItem("Content — The text to inject.")
                BulletItem("Depth — How many messages from the end to place it (lower = more influential).")
                BulletItem("Interval — How often to inject (every N messages).")
                BulletItem("Position — Before Char, After Char, Top/Bottom of Author's Note, At Depth.")
                BulletItem("Role — System, User, or Assistant.")
            }

            // ── CharaVault ──────────────────────────────────────────
            HelpDropdown(title = "CharaVault") {
                HelpText("Browse and import community characters and lorebooks from within the app.")
                VerticalSpacer()
                SectionHeading("Modes")
                BulletItem("CharaVault.net — Browse the public catalog. Guest browsing shows SFW content only. Log in with email/password for full access. NSFW content requires age verification (18+). 2FA/TOTP is supported.")
                BulletItem("Self-hosted — Connect to your own CharaVault server by entering its URL.")

                VerticalSpacer()
                SectionHeading("Browsing")
                BulletItem("Search by name or description using the search bar.")
                BulletItem("Filter by tags — Tap the tag icon to open the tag selector. Selected tags appear as chips above the results.")
                BulletItem("Content filter — Tap the filter icon to toggle SFW/NSFW filtering.")
                BulletItem("Switch between Characters and Lorebooks using the title dropdown.")
                BulletItem("Navigate pages with the Previous/Next buttons. Tap the page number to jump to a specific page.")

                VerticalSpacer()
                SectionHeading("Importing")
                BulletItem("Tap any card to open a preview with full details.")
                BulletItem("Tap \"Import to PocketTavern\" to download and save locally.")
                BulletItem("Tap tags in the preview to add them to your search filter.")

                VerticalSpacer()
                SectionHeading("Uploading")
                HelpText("Long-press a character in the Characters list → Upload to CharaVault to share your character with the community.")
            }

            // ── Extensions ──────────────────────────────────────────
            HelpDropdown(title = "Extensions") {
                SectionHeading("Built-in Extensions")
                HelpText("Go to Settings → Extensions to enable/disable:")
                BulletItem("Quick Reply — Preset buttons above the chat input. Tap any button to instantly send a message. Configure presets in Quick Reply Settings.")
                BulletItem("Regex — Find-and-replace rules applied to AI output or user messages. Supports full regular expressions with capture groups.")
                BulletItem("Token Counter — Shows a live estimated token count above the input while typing.")

                VerticalSpacer()
                SectionHeading("JavaScript Extensions")
                HelpText("Install third-party extensions that run in a sandboxed WebView:")
                BulletItem("Tap the + button in the JavaScript Extensions section.")
                BulletItem("Install from URL — Enter the URL of the extension's index.js or its parent folder.")
                BulletItem("Install from Device — Browse for a .js file or .zip bundle.")
                BulletItem("Toggle extensions on/off with the switch on each card.")
                BulletItem("Some extensions have configurable settings — tap \"Settings\" on the card to expand them.")
                BulletItem("Tap \"Uninstall\" (with confirmation) to remove an extension.")
            }

            // ── Themes ──────────────────────────────────────────────
            HelpDropdown(title = "Themes & Appearance") {
                SectionHeading("Applying a Theme")
                HelpText("Go to Settings → Appearance. Tap \"Apply\" on any theme to activate it.")
                VerticalSpacer()
                SectionHeading("Built-in Themes")
                BulletItem("PocketTavern (default) — Fire & Ice with animated particle effects.")
                BulletItem("Fire & Ice — The default theme exported as editable JSON.")
                BulletItem("Midnight Plum — Purple stars rising with slow-falling diamonds.")
                BulletItem("Ember — Warm embers with bright spark accents.")
                BulletItem("Sand and Sea — Warm sandy tones with ocean-blue accents.")

                VerticalSpacer()
                SectionHeading("Importing Themes")
                BulletItem("Tap \"Import Theme (.json or .zip)\" at the bottom of the Appearance screen.")
                BulletItem("JSON files — SillyTavern theme exports or PocketTavern-native theme files.")
                BulletItem("ZIP bundles — Include theme.json plus optional background image, logo, and music. Max 50 MB.")

                VerticalSpacer()
                SectionHeading("Theme Features")
                BulletItem("Animated backgrounds — GIF or animated WebP files play automatically.")
                BulletItem("Theme logos — Replace the PocketTavern logo on the home screen.")
                BulletItem("Background music — MP3, OGG, or WAV files play when the theme is active.")
                BulletItem("Particle effects — Animated background particles (embers, snow, bubbles, rain, sparkles, or fully custom).")
                BulletItem("Delete imported themes with the trash icon. Built-in themes cannot be deleted.")
            }

            // ── SillyTavern Import ──────────────────────────────────
            HelpDropdown(title = "SillyTavern Import") {
                HelpText("Migrate your characters, chats, and lorebooks from SillyTavern.")
                VerticalSpacer()
                SectionHeading("From Server")
                BulletItem("Go to Settings → Import from SillyTavern.")
                BulletItem("Enter your SillyTavern server URL and credentials.")
                BulletItem("Select what to import: characters, chats, lorebooks.")
                BulletItem("Tap Import — everything is pulled down and saved locally.")

                VerticalSpacer()
                SectionHeading("From Folder")
                BulletItem("Copy your SillyTavern data directory to your Android device.")
                BulletItem("Choose \"Import from Folder\" and use the folder picker.")
                BulletItem("Characters, chats, and lorebooks are scanned and imported.")

                VerticalSpacer()
                HelpText("After import, PocketTavern works completely independently. Your SillyTavern server is no longer needed.")
            }

            // ── Settings Overview ───────────────────────────────────
            HelpDropdown(title = "Settings Overview") {
                HelpText("Settings are organized into five groups:")
                VerticalSpacer()
                SectionHeading("Connection")
                BulletItem("API Configuration — Select backend type, URL, API key, model, streaming toggle.")
                BulletItem("Connection Profiles — Save and switch between multiple named API configurations.")
                BulletItem("Image Generation — Configure image generation backends (SD WebUI, ComfyUI, DALL-E, Stability AI, Pollinations, HuggingFace).")

                VerticalSpacer()
                SectionHeading("Generation")
                BulletItem("Text Generation — Sampler settings and presets for text completion backends (KoboldCpp, llama.cpp, etc.). Dimmed when using chat completion APIs.")
                BulletItem("Chat Completion Presets — Sampling presets and prompt order editor for OpenAI-compatible APIs. Dimmed when using text completion backends.")
                BulletItem("Formatting — Instruct templates, context templates, and system prompt presets. Dimmed when using chat completion APIs.")

                VerticalSpacer()
                SectionHeading("World & Characters")
                BulletItem("World Info / Lorebooks — View and manage lorebook entries.")
                BulletItem("Context Settings — Global Author's Note configuration.")
                BulletItem("Personas — Create and manage user personas. Shows the current persona name.")

                VerticalSpacer()
                SectionHeading("Appearance & Audio")
                BulletItem("Appearance — Import and apply themes (JSON or ZIP).")
                BulletItem("Text-to-Speech — Voice synthesis settings (System TTS or OpenAI-compatible).")

                VerticalSpacer()
                SectionHeading("Utilities")
                BulletItem("Extensions — Quick Reply, Regex, Token Counter, and JavaScript extensions.")
                BulletItem("Import from SillyTavern — Migrate characters, chats, and lorebooks.")
                BulletItem("Help — This screen.")
                VerticalSpacer()
                HelpText("A connection status indicator at the bottom shows whether you're currently connected to your LLM backend.")
            }

            // ── FAQ ─────────────────────────────────────────────────
            HelpDropdown(title = "Frequently Asked Questions") {
                FaqItem(
                    question = "Do I need a SillyTavern server to use PocketTavern?",
                    answer = "No. PocketTavern is fully standalone. It connects directly to LLM backends (KoboldCpp, Ollama, OpenAI, etc.) with no middleman. SillyTavern is not required."
                )
                FaqItem(
                    question = "What character card formats are supported?",
                    answer = "PocketTavern uses PNG character cards with embedded metadata (V2 spec) — the same format SillyTavern uses. Any .png card exported from SillyTavern, CharaVault, Chub.ai, or similar tools will work."
                )
                FaqItem(
                    question = "Can I use a local AI model on my phone?",
                    answer = "PocketTavern doesn't run models directly on-device. It connects to LLM servers. Run a model on your PC (KoboldCpp, Ollama, LM Studio) and connect to it over Wi-Fi, or use a cloud API."
                )
                FaqItem(
                    question = "How do I connect to a local backend like KoboldCpp?",
                    answer = "1) Start KoboldCpp on your PC with --host 0.0.0.0 so it listens on the network.\n2) Find your PC's local IP (e.g. 192.168.1.100).\n3) In PocketTavern: Settings → API Configuration → enter http://192.168.1.100:5001.\n4) Your phone must be on the same Wi-Fi network."
                )
                FaqItem(
                    question = "Why can't I connect to my local backend?",
                    answer = "Common causes:\n• The backend isn't listening on the network (use --host 0.0.0.0 for KoboldCpp, OLLAMA_HOST=0.0.0.0 for Ollama).\n• Using \"localhost\" or \"127.0.0.1\" instead of your PC's actual IP address.\n• Phone and PC are on different Wi-Fi networks.\n• Firewall is blocking the port.\n• The URL has a trailing slash."
                )
                FaqItem(
                    question = "How do I use OpenAI / Claude / Groq?",
                    answer = "Settings → API Configuration → select Chat Completion as the API type. Choose your provider, enter the API URL and your API key, then tap the refresh button to load models. Select a model and tap Test Connection."
                )
                FaqItem(
                    question = "What's the difference between Text Completion and Chat Completion?",
                    answer = "Text Completion backends (KoboldCpp, llama.cpp, etc.) take a single text prompt. They need instruct templates and context templates to format the conversation correctly.\n\nChat Completion backends (OpenAI, Claude, etc.) take a list of messages with roles. They use the Chat Completion Presets and prompt order editor instead."
                )
                FaqItem(
                    question = "How do I get the AI to follow my instructions better?",
                    answer = "• Put key instructions in the character's System Prompt (Character Settings).\n• Use Author's Note (Settings → Context Settings) at a low depth for persistent steering.\n• For text completion: make sure you're using the correct instruct template for your model.\n• For chat completion: edit the Main Prompt in Chat Completion Presets."
                )
                FaqItem(
                    question = "Can I use multiple AI models?",
                    answer = "Yes. Use Connection Profiles (Settings → Connection Profiles) to save different API configurations and switch between them instantly."
                )
                FaqItem(
                    question = "How does image generation work?",
                    answer = "Long-press a message → Generate Image. Choose Background or Character mode. The LLM generates an image prompt from the message context, which is sent to your configured image backend. You can edit the prompt before generating. Configure your backend in Settings → Image Generation."
                )
                FaqItem(
                    question = "Do I need to pay for image generation?",
                    answer = "Pollinations is completely free with no API key required. SD WebUI / Forge and ComfyUI are free but require running a local server. DALL-E, Stability AI, and HuggingFace require paid API keys."
                )
                FaqItem(
                    question = "What are swipes?",
                    answer = "Swipes are alternative AI responses. Swipe left/right on any AI message (or use the arrow buttons below it) to cycle through alternatives. The refresh button generates a new alternative. All alternatives are saved."
                )
                FaqItem(
                    question = "How do I import my SillyTavern data?",
                    answer = "Settings → Import from SillyTavern. You can import from a running SillyTavern server (enter URL and credentials) or from a folder on your device containing SillyTavern data files."
                )
                FaqItem(
                    question = "Where are my files stored?",
                    answer = "Characters, chats, lorebooks, themes, and settings are stored in PocketTavern's private app storage on your device. Chats use the SillyTavern-compatible .jsonl format and characters use standard PNG cards, so nothing is locked in."
                )
                FaqItem(
                    question = "How do I back up my data?",
                    answer = "Chat files (.jsonl) and character cards (.png) are stored in the app's internal storage. You can export individual character cards. For a full backup, use Android's file manager or ADB to copy the app's data directory."
                )
                FaqItem(
                    question = "What are extensions?",
                    answer = "Extensions add features to PocketTavern. Three are built-in (Quick Reply, Regex, Token Counter). You can also install JavaScript extensions from URLs that react to chat events, inject prompts, show custom UI, and more. Configure them in Settings → Extensions."
                )
                FaqItem(
                    question = "Can I use SillyTavern themes?",
                    answer = "Yes. Export a theme from SillyTavern as a .json file, transfer it to your device, and import it via Settings → Appearance → Import Theme. PocketTavern reads the color fields and applies them. Web-specific CSS fields are ignored."
                )
            }

            // ── Troubleshooting ─────────────────────────────────────
            HelpDropdown(title = "Troubleshooting") {
                TroubleshootBlock(
                    problem = "Can't connect to local backend",
                    solutions = listOf(
                        "Make sure the backend is running and listening on the network",
                        "Use your PC's local IP address (e.g. 192.168.1.x), not localhost or 127.0.0.1",
                        "Phone and PC must be on the same Wi-Fi network",
                        "Check that your firewall allows the port (5001, 11434, etc.)",
                        "For KoboldCpp: start with --host 0.0.0.0 flag",
                        "For Ollama: set OLLAMA_HOST=0.0.0.0 before starting",
                        "Make sure the URL does not have a trailing slash"
                    )
                )
                TroubleshootBlock(
                    problem = "Cloud API not working",
                    solutions = listOf(
                        "Double-check your API key is correct and has credits/quota remaining",
                        "Make sure the API URL does not have a trailing slash",
                        "Tap the model refresh button after entering the URL and key",
                        "Check the Debug Log (chat menu → Debug Log) for error details"
                    )
                )
                TroubleshootBlock(
                    problem = "AI gives short or empty responses",
                    solutions = listOf(
                        "Increase Max Tokens / Max New Tokens in generation settings",
                        "Check that your context size isn't too small for the conversation history",
                        "Some models need a specific instruct template — make sure you've selected the right one in Formatting"
                    )
                )
                TroubleshootBlock(
                    problem = "AI ignores instructions / Author's Note",
                    solutions = listOf(
                        "Try putting key instructions in the character's System Prompt instead",
                        "Reduce Author's Note depth (lower = closer to the end = more influential)",
                        "For chat completion: edit the Main Prompt in Chat Completion Presets",
                        "Some models (especially Claude) anchor heavily to their initial system prompt"
                    )
                )
                TroubleshootBlock(
                    problem = "Characters not loading or showing errors",
                    solutions = listOf(
                        "Check that the PNG file is a valid character card (exported from SillyTavern or similar)",
                        "Try re-importing the PNG file",
                        "Check the Debug Log for parse errors",
                        "Some very old card formats (V1) may not include all fields — try a V2 card"
                    )
                )
                TroubleshootBlock(
                    problem = "Image generation fails",
                    solutions = listOf(
                        "Verify the backend is running and reachable (use Test Connection in Image Generation settings)",
                        "For SD WebUI / Forge: make sure it was started with the --api flag",
                        "For cloud backends: check that your API key is valid and has credits",
                        "Check the Debug Log for detailed error messages"
                    )
                )
                TroubleshootBlock(
                    problem = "TTS not working",
                    solutions = listOf(
                        "For System TTS: make sure your device has a TTS engine installed (most Android devices do by default)",
                        "For OpenAI-compatible: verify the server URL and that the server is running",
                        "Try the Test Voice button in TTS settings",
                        "Check that TTS is enabled in Settings → Text-to-Speech"
                    )
                )
                TroubleshootBlock(
                    problem = "Theme not applying correctly",
                    solutions = listOf(
                        "Make sure the theme file is valid JSON",
                        "For ZIP bundles: the ZIP must contain a theme.json at the root level",
                        "ZIP bundles are limited to 50 MB",
                        "Some SillyTavern theme fields (CSS, font scale) are not applicable to Android and are ignored"
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Reusable Components ─────────────────────────────────────────────

@Composable
private fun HelpDropdown(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = if (expanded) MaterialTheme.shapes.medium else MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun HelpText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun BulletItem(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "\u2022 ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun VerticalSpacer() {
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier
        .fillMaxWidth()
        .clickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                text = answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }
        if (!expanded) {
            Spacer(modifier = Modifier.height(4.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun TroubleshootBlock(problem: String, solutions: List<String>) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = problem,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        solutions.forEach { solution ->
            BulletItem(solution)
        }
    }
}
