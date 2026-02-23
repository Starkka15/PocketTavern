/**
 * Scene Painter — PocketTavern Extension
 *
 * Generates images from chat context and inserts them into the conversation.
 * Accessed via the message long-press context menu (no headers).
 *
 * Menu items:
 *   - "Send background image in chat" — generates a scene/environment image
 *   - "Send a picture of yourself in chat" — generates a character portrait
 *
 * Uses PT.generateHidden() to create an image prompt from the message,
 * PT.generateImage() to generate the image, and PT.insertMessage() to
 * place it in chat as an image message.
 *
 * Features:
 *   - Per-character art style overrides
 *   - Configurable negative prompt and default style
 *   - Settings accessible via long-press → "Set Art Style" button on header
 */
(function () {
    'use strict';

    var EXT_ID = 'pt-scene-painter';

    // ── Settings ──────────────────────────────────────────────────────────────

    var DEFAULT_SETTINGS = {
        enabled:         true,
        defaultArtStyle: 'digital painting, highly detailed, dramatic lighting',
        negativePrompt:  'blurry, low quality, deformed, bad anatomy, text, watermark',
        artStyleByChar:  {}
    };

    function getSettings() {
        if (!PT.extension_settings[EXT_ID]) {
            PT.extension_settings[EXT_ID] = {};
        }
        var s = PT.extension_settings[EXT_ID];
        if (s.enabled         === undefined) s.enabled         = DEFAULT_SETTINGS.enabled;
        if (s.defaultArtStyle === undefined) s.defaultArtStyle = DEFAULT_SETTINGS.defaultArtStyle;
        if (s.negativePrompt  === undefined) s.negativePrompt  = DEFAULT_SETTINGS.negativePrompt;
        if (!s.artStyleByChar)               s.artStyleByChar  = {};
        return s;
    }

    function getCurrentCharName() {
        try {
            var ctx = PT.getContext();
            return (ctx && ctx.character && ctx.character.name) ? ctx.character.name : '__default';
        } catch (e) { return '__default'; }
    }

    function getArtStyle() {
        var s = getSettings();
        var charName = getCurrentCharName();
        return s.artStyleByChar[charName] || s.defaultArtStyle;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    var _isGenerating = false;
    var _lastLongPressedIndex = -1;

    // ── Message long-press handler ───────────────────────────────────────────

    function onMessageLongPressed(data) {
        var s = getSettings();
        if (!s.enabled) return;

        _lastLongPressedIndex = data.messageIndex;
        // Register our actions in the message context menu
        PT.registerMessageActions(EXT_ID, [
            { label: 'Send background image in chat',       action: 'sp_background' },
            { label: 'Send a picture of yourself in chat',  action: 'sp_portrait' }
        ]);
    }

    // ── Button handlers ──────────────────────────────────────────────────────

    function onButtonClicked(data) {
        if (data.action === 'sp_background') {
            paintScene(_lastLongPressedIndex, 'background');
        } else if (data.action === 'sp_portrait') {
            paintScene(_lastLongPressedIndex, 'portrait');
        } else if (data.action === 'sp_style') {
            handleSetStyle();
        }
    }

    // ── Core paint flow ──────────────────────────────────────────────────────

    function paintScene(messageIndex, mode) {
        if (_isGenerating) {
            PT.log('[ScenePainter] Already generating, skipping.');
            return;
        }

        var s = getSettings();
        if (!s.enabled) return;

        // Get the message text
        var messageText = '';
        var ctx = PT.getContext();
        if (ctx && ctx.recentMessages) {
            for (var i = ctx.recentMessages.length - 1; i >= 0; i--) {
                if (ctx.recentMessages[i].index === messageIndex) {
                    messageText = ctx.recentMessages[i].text;
                    break;
                }
            }
        }
        if (!messageText) {
            PT.log('[ScenePainter] No message text found for index ' + messageIndex);
            return;
        }

        _isGenerating = true;
        PT.log('[ScenePainter] Generating ' + mode + ' from message #' + messageIndex);

        var artStyle = getArtStyle();
        var charName = getCurrentCharName();

        // Build the analysis prompt based on mode
        var analysisPrompt;
        if (mode === 'portrait') {
            analysisPrompt =
                '[OOC: Do NOT continue the story. Do NOT write any narrative.\n' +
                'Based on the following message, create a detailed image generation prompt for a portrait/picture of ' + charName + '.\n\n' +
                'Message for context:\n' +
                '"""' + messageText + '"""\n\n' +
                'Art style: ' + artStyle + '\n\n' +
                'Create a portrait-focused Stable Diffusion prompt that describes:\n' +
                '- The character\'s appearance, expression, and pose\n' +
                '- What they are wearing\n' +
                '- Their current mood or action\n' +
                '- Background/setting (keep it simple, focus on the character)\n\n' +
                'Output ONLY the prompt text on a single line. No explanations, no tags, no formatting.]';
        } else {
            analysisPrompt =
                '[OOC: Do NOT continue the story. Do NOT write any narrative.\n' +
                'Based on the following message, create a detailed image generation prompt for the scene background/environment.\n\n' +
                'Message for context:\n' +
                '"""' + messageText + '"""\n\n' +
                'Art style: ' + artStyle + '\n\n' +
                'Create a landscape/environment Stable Diffusion prompt that describes:\n' +
                '- The setting and environment\n' +
                '- Time of day and weather\n' +
                '- Lighting and atmosphere\n' +
                '- Key visual details of the location\n' +
                '- Do NOT include any characters or people\n\n' +
                'Output ONLY the prompt text on a single line. No explanations, no tags, no formatting.]';
        }

        PT.generateHidden(analysisPrompt).then(function (sdPrompt) {
            if (!sdPrompt || !sdPrompt.trim()) {
                PT.log('[ScenePainter] Empty prompt from analysis');
                _isGenerating = false;
                return;
            }

            sdPrompt = sdPrompt.trim();
            // Clean up surrounding quotes the LLM might add
            if ((sdPrompt.charAt(0) === '"' && sdPrompt.charAt(sdPrompt.length - 1) === '"') ||
                (sdPrompt.charAt(0) === "'" && sdPrompt.charAt(sdPrompt.length - 1) === "'")) {
                sdPrompt = sdPrompt.substring(1, sdPrompt.length - 1);
            }

            PT.log('[ScenePainter] SD prompt (' + mode + '): ' + sdPrompt.substring(0, 100) + '...');

            var options = {};
            if (s.negativePrompt) {
                options.negativePrompt = s.negativePrompt;
            }
            // Portrait mode: use taller aspect ratio
            if (mode === 'portrait') {
                options.width = 512;
                options.height = 768;
            }

            PT.generateImage(sdPrompt, options).then(function (base64) {
                _isGenerating = false;

                if (!base64) {
                    PT.log('[ScenePainter] Image generation returned empty result');
                    return;
                }

                PT.log('[ScenePainter] Image generated (' + mode + '), inserting into chat');
                PT.insertMessage('', { type: 'image', imageBase64: base64 });
            });
        });
    }

    // ── Settings dialog ──────────────────────────────────────────────────────

    function handleSetStyle() {
        var s = getSettings();
        var charName = getCurrentCharName();
        var currentStyle = s.artStyleByChar[charName] || s.defaultArtStyle;
        var currentNeg = s.negativePrompt;

        PT.showEditDialog('Art Style — ' + charName, [
            { key: 'style',    label: 'Art Style',        value: currentStyle },
            { key: 'negative', label: 'Negative Prompt',  value: currentNeg }
        ]).then(function (result) {
            if (!result) return;
            if (result.style && result.style.trim()) {
                s.artStyleByChar[charName] = result.style.trim();
            }
            if (result.negative !== undefined) {
                s.negativePrompt = result.negative.trim();
            }
            PT.saveSettings();
            PT.log('[ScenePainter] Style updated for ' + charName);
        });
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    function onChatChanged() {
        _isGenerating = false;
    }

    function onCharacterChanged() {
        _isGenerating = false;
    }

    // ── Initialization ────────────────────────────────────────────────────────

    function init() {
        PT.log('[ScenePainter] Initializing...');

        getSettings();
        PT.saveSettings();

        // Register persistent message actions on init
        PT.registerMessageActions(EXT_ID, [
            { label: 'Send background image in chat',       action: 'sp_background' },
            { label: 'Send a picture of yourself in chat',  action: 'sp_portrait' }
        ]);

        PT.eventSource.on(PT.events.MESSAGE_LONG_PRESSED,  onMessageLongPressed);
        PT.eventSource.on(PT.events.CHAT_CHANGED,           onChatChanged);
        PT.eventSource.on(PT.events.CHARACTER_CHANGED,      onCharacterChanged);
        PT.eventSource.on(PT.events.BUTTON_CLICKED,         onButtonClicked);

        PT.log('[ScenePainter] Ready.');
    }

    init();

})();
