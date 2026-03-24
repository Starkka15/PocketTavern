/**
 * Scene Painter — PocketTavern Extension
 *
 * Generates images from chat context and inserts them into the conversation.
 * Accessed via the message long-press context menu.
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
 *   - Per-character art style, negative prompt, sampler via Character Settings
 *   - Avatar face reference for portrait consistency (img2img)
 */
(function () {
    'use strict';

    var EXT_ID = 'pt-scene-painter';

    // ── Defaults (used when no per-character setting is saved) ────────────

    var DEFAULTS = {
        artStyle:       'digital painting, highly detailed, dramatic lighting',
        negativePrompt: 'blurry, low quality, deformed, bad anatomy, text, watermark',
        enableFaceRef:  'false'
    };

    // ── Helpers ──────────────────────────────────────────────────────────

    function getSetting(key) {
        var v = PT.getCharacterSetting(key);
        return v || DEFAULTS[key] || '';
    }

    function getCurrentCharName() {
        try {
            var ctx = PT.getContext();
            return (ctx && ctx.character && ctx.character.name) ? ctx.character.name : 'the character';
        } catch (e) { return 'the character'; }
    }

    // ── State ─────────────────────────────────────────────────────────────

    var _isGenerating = false;
    var _lastLongPressedIndex = -1;

    // ── Message long-press handler ───────────────────────────────────────

    function onMessageLongPressed(data) {
        if (!PT.isEnabled(EXT_ID)) return;
        _lastLongPressedIndex = data.messageIndex;
    }

    // ── Button handlers ──────────────────────────────────────────────────

    function onButtonClicked(data) {
        if (data.action === 'sp_background') {
            paintScene(_lastLongPressedIndex, 'background');
        } else if (data.action === 'sp_portrait') {
            paintScene(_lastLongPressedIndex, 'portrait');
        }
    }

    // ── Core paint flow ──────────────────────────────────────────────────

    function paintScene(messageIndex, mode) {
        if (_isGenerating) {
            PT.log('[ScenePainter] Already generating, skipping.');
            return;
        }

        if (!PT.isEnabled(EXT_ID)) return;

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

        var artStyle = getSetting('artStyle');
        var charName = getCurrentCharName();

        // Build the analysis prompt based on mode
        // Instructs the LLM to output SD-style comma-separated tags, NOT prose
        var analysisPrompt;
        if (mode === 'portrait') {
            analysisPrompt =
                '[OOC: Do NOT continue the story. Do NOT write any narrative.\n' +
                'Extract visual tags from the message below for a Stable Diffusion portrait of ' + charName + '.\n\n' +
                'Message:\n"""' + messageText + '"""\n\n' +
                'Output ONLY comma-separated Stable Diffusion tags. No sentences, no prose, no quotes.\n' +
                'Keep under 40 tags. Focus on: hair color/style, eye color, expression, clothing, pose, simple background.\n' +
                'Example format: 1girl, green eyes, long brown hair, messy bun, red dress, smiling, sitting, tavern interior, warm lighting\n' +
                'Tags:]';
        } else {
            analysisPrompt =
                '[OOC: Do NOT continue the story. Do NOT write any narrative.\n' +
                'Extract visual tags from the message below for a Stable Diffusion landscape/environment image.\n\n' +
                'Message:\n"""' + messageText + '"""\n\n' +
                'Output ONLY comma-separated Stable Diffusion tags. No sentences, no prose, no quotes.\n' +
                'Keep under 30 tags. Focus on: setting, time of day, weather, lighting, atmosphere. NO people or characters.\n' +
                'Example format: tavern interior, wooden tables, candlelight, warm atmosphere, medieval, night, cozy\n' +
                'Tags:]';
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

            // Prepend art style directly to the SD prompt so it's always included
            // (the LLM may or may not echo it from the analysis prompt)
            sdPrompt = artStyle + ', ' + sdPrompt;

            PT.log('[ScenePainter] SD prompt (' + mode + '): ' + sdPrompt.substring(0, 100) + '...');

            var negativePrompt = getSetting('negativePrompt');
            var options = {};
            if (negativePrompt) {
                options.negativePrompt = negativePrompt;
            }

            // Per-character sampler override
            var sampler = getSetting('sampler');
            if (sampler) {
                options.sampler = sampler;
            }

            // Per-character model override (e.g. anime model for anime characters)
            var model = getSetting('model');
            if (model) {
                options.model = model;
            }

            // Portrait mode: use taller aspect ratio
            if (mode === 'portrait') {
                options.width = 512;
                options.height = 768;

                // Face reference: use avatar face as img2img init for consistency
                var faceRefEnabled = getSetting('enableFaceRef') === 'true';
                if (faceRefEnabled) {
                    var faceBase64 = PT.getCharacterFace();
                    if (faceBase64) {
                        PT.log('[ScenePainter] Using avatar face reference for img2img');
                        options.initImage = faceBase64;
                        options.denoisingStrength = 0.9;
                    } else {
                        PT.log('[ScenePainter] Face ref enabled but no face detected in avatar');
                    }
                }
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

    // ── Event handlers ────────────────────────────────────────────────────

    function onChatChanged() {
        _isGenerating = false;
    }

    function onCharacterChanged() {
        _isGenerating = false;
    }

    // ── Initialization ────────────────────────────────────────────────────

    function init() {
        PT.log('[ScenePainter] Initializing...');

        // Register per-character settings (rendered in Character Settings UI)
        PT.registerCharacterSettings(EXT_ID, [
            { key: 'artStyle',       label: 'Art Style',       type: 'select', source: 'sd_styles',
              default: DEFAULTS.artStyle },
            { key: 'model',          label: 'Model',           type: 'select', source: 'sd_models' },
            { key: 'negativePrompt', label: 'Negative Prompt', type: 'text',
              default: DEFAULTS.negativePrompt },
            { key: 'sampler',        label: 'Sampler',         type: 'select', source: 'sd_samplers' },
            { key: 'enableFaceRef',  label: 'Avatar Face Reference (portraits)', type: 'toggle',
              default: DEFAULTS.enableFaceRef }
        ]);

        // Register message context menu actions
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
