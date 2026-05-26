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
        enabled:           true,
        defaultArtStyle:   'digital painting, highly detailed, dramatic lighting',
        negativePrompt:    'blurry, low quality, deformed, bad anatomy, text, watermark',
        promptPrefix:      '',     // prepended to every prompt (e.g. Pony XL quality tags)
        artStyleByChar:    {},
        portraitImg2Img:   true,   // use character avatar as img2img source for portraits
        denoisingStrength: 0.55    // 0.0 = identical, 1.0 = ignore source image
    };

    function getSettings() {
        if (!PT.extension_settings[EXT_ID]) {
            PT.extension_settings[EXT_ID] = {};
        }
        var s = PT.extension_settings[EXT_ID];
        if (s.enabled             === undefined) s.enabled             = DEFAULT_SETTINGS.enabled;
        if (s.defaultArtStyle     === undefined) s.defaultArtStyle     = DEFAULT_SETTINGS.defaultArtStyle;
        if (s.negativePrompt      === undefined) s.negativePrompt      = DEFAULT_SETTINGS.negativePrompt;
        if (s.promptPrefix        === undefined) s.promptPrefix        = DEFAULT_SETTINGS.promptPrefix;
        if (!s.artStyleByChar)                   s.artStyleByChar      = {};
        if (s.portraitImg2Img     === undefined) s.portraitImg2Img     = DEFAULT_SETTINGS.portraitImg2Img;
        if (s.denoisingStrength   === undefined) s.denoisingStrength   = DEFAULT_SETTINGS.denoisingStrength;
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
            { label: 'Send a picture of yourself in chat',  action: 'sp_portrait' },
            { label: 'Set art style...',                    action: 'sp_style' }
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
                'Task: Generate a Stable Diffusion image prompt for a portrait of ' + charName + '.\n\n' +
                'You have the character\'s full description in your context. Use it to extract exact physical appearance.\n\n' +
                'Message (for pose/expression/setting):\n' +
                '"""' + messageText + '"""\n\n' +
                'Output a single line of comma-separated SD tags:\n' +
                '- Physical appearance FIRST: hair color, hair style, eye color, skin tone, body type, bust size, distinguishing features\n' +
                '- Then: current clothing or state of undress from the message\n' +
                '- Then: pose, expression, action\n' +
                '- Then: setting (brief)\n\n' +
                'Rules: comma-separated tags only, no prose, no art style tags (added separately), no explanations.\n' +
                'Output ONLY the single prompt line.]';
        } else {
            analysisPrompt =
                '[OOC: Do NOT continue the story. Do NOT write any narrative.\n' +
                'Task: Generate a Stable Diffusion environment/background prompt from this message.\n\n' +
                'Message:\n' +
                '"""' + messageText + '"""\n\n' +
                'Output a single line of comma-separated SD tags:\n' +
                '- Setting and location, time of day, weather, lighting, atmosphere, key visual details\n' +
                '- No characters or people, no art style tags (added separately)\n\n' +
                'Output ONLY the single prompt line.]';
        }

        PT.generateHidden(analysisPrompt).then(function (sdPrompt) {
            if (!sdPrompt || !sdPrompt.trim()) {
                PT.log('[ScenePainter] Empty prompt from analysis');
                _isGenerating = false;
                return;
            }

            // LLM may output reasoning steps — take the last non-empty line as the actual prompt
            var lines = sdPrompt.trim().split('\n').map(function(l) { return l.trim(); }).filter(function(l) { return l.length > 0; });
            sdPrompt = lines[lines.length - 1];

            // Strip surrounding quotes
            if ((sdPrompt.charAt(0) === '"' && sdPrompt.charAt(sdPrompt.length - 1) === '"') ||
                (sdPrompt.charAt(0) === "'" && sdPrompt.charAt(sdPrompt.length - 1) === "'")) {
                sdPrompt = sdPrompt.substring(1, sdPrompt.length - 1);
            }

            // Prepend model prefix and art style — guaranteed to be first regardless of LLM output
            var prefix = (s.promptPrefix || '').trim();
            var style  = artStyle.trim();
            if (prefix) sdPrompt = prefix + ', ' + sdPrompt;
            if (style)  sdPrompt = style  + ', ' + sdPrompt;

            PT.log('[ScenePainter] SD prompt (' + mode + '): ' + sdPrompt.substring(0, 150) + '...');

            var options = {};
            if (s.negativePrompt) {
                options.negativePrompt = s.negativePrompt;
            }
            // Portrait mode: taller aspect ratio + optional img2img from character avatar
            if (mode === 'portrait') {
                options.width = 512;
                options.height = 768;
                if (s.portraitImg2Img) {
                    var avatarBase64 = PT.getCharacterAvatar();
                    if (avatarBase64) {
                        options.sourceImageBase64 = avatarBase64;
                        options.denoisingStrength = s.denoisingStrength;
                        PT.log('[ScenePainter] Using img2img with avatar, denoising=' + s.denoisingStrength);
                    } else {
                        PT.log('[ScenePainter] No avatar found, falling back to txt2img');
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
            }).catch(function(e) {
                PT.log('[ScenePainter] generateImage error: ' + e);
                _isGenerating = false;
            });
        }).catch(function(e) {
            PT.log('[ScenePainter] generateHidden error: ' + e);
            _isGenerating = false;
        });
    }

    // ── Presets ──────────────────────────────────────────────────────────────

    var PRESETS = {
        'SD 1.5': {
            prefix:   '',
            style:    'digital painting, highly detailed, dramatic lighting',
            negative: 'blurry, low quality, deformed, bad anatomy, text, watermark, ugly'
        },
        'SDXL': {
            prefix:   '',
            style:    'digital painting, concept art, highly detailed, vibrant colors, sharp focus',
            negative: 'blurry, low quality, deformed, bad anatomy, text, watermark, ugly, amateur'
        },
        'Pony XL': {
            prefix:   'score_9, score_8_up, score_7_up, score_6_up',
            style:    'digital painting, highly detailed',
            negative: 'score_4, score_3, score_2, score_1, blurry, low quality, text, watermark, bad anatomy'
        },
        'Illustrious': {
            prefix:   '',
            style:    'anime style, highly detailed, vibrant colors, clean linework',
            negative: 'lowres, bad anatomy, bad hands, missing fingers, extra digit, fewer digits, cropped, worst quality, low quality'
        },
        'Flux Dev': {
            prefix:   '',
            style:    'photorealistic, cinematic lighting, highly detailed, 8k',
            negative: ''
        },
        'Flux Schnell': {
            prefix:   '',
            style:    'photorealistic, vibrant, detailed',
            negative: ''
        }
    };

    var PRESET_NONE = '-- Custom --';

    // ── Settings dialog ──────────────────────────────────────────────────────

    function handleSetStyle() {
        var s = getSettings();
        var charName = getCurrentCharName();
        var currentStyle = s.artStyleByChar[charName] || s.defaultArtStyle;

        PT.getAvailableModels().then(function (models) {
            var modelOptions = ['-- keep current --'].concat(models || []);
            var presetOptions = [PRESET_NONE].concat(Object.keys(PRESETS));

            return PT.showEditDialog('Art Style — ' + charName, [
                { key: 'preset',    label: 'Preset (overrides Style / Prefix / Negative)', type: 'select', options: presetOptions,  value: PRESET_NONE },
                { key: 'model',     label: 'Model',                                        type: 'select', options: modelOptions,   value: modelOptions[0] },
                { key: 'prefix',    label: 'Prompt Prefix (model tags)',                   value: s.promptPrefix || '' },
                { key: 'style',     label: 'Art Style',                                    value: currentStyle },
                { key: 'negative',  label: 'Negative Prompt',                              value: s.negativePrompt || '' },
                { key: 'denoising', label: 'Portrait Denoising (0.0–1.0)',            value: String(s.denoisingStrength) }
            ]);
        }).then(function (result) {
            if (!result) return;

            var preset = result.preset;
            if (preset && preset !== PRESET_NONE && PRESETS[preset]) {
                var p = PRESETS[preset];
                s.promptPrefix             = p.prefix;
                s.artStyleByChar[charName] = p.style;
                s.negativePrompt           = p.negative;
            } else {
                if (result.prefix !== undefined)           s.promptPrefix             = result.prefix.trim();
                if (result.style && result.style.trim())   s.artStyleByChar[charName] = result.style.trim();
                if (result.negative !== undefined)         s.negativePrompt           = result.negative.trim();
            }

            var d = parseFloat(result.denoising);
            if (!isNaN(d) && d >= 0 && d <= 1) {
                s.denoisingStrength = d;
            }

            PT.saveSettings();
            PT.log('[ScenePainter] Style updated for ' + charName +
                (preset && preset !== PRESET_NONE ? ' (preset: ' + preset + ')' : ''));

            var selectedModel = result.model;
            if (selectedModel && selectedModel !== '-- keep current --') {
                PT.setCurrentModel(selectedModel).then(function (ok) {
                    PT.log('[ScenePainter] Model ' + (ok ? 'changed to: ' : 'change FAILED: ') + selectedModel);
                }).catch(function (e) {
                    PT.log('[ScenePainter] setCurrentModel error: ' + e);
                });
            }
        }).catch(function (e) {
            PT.log('[ScenePainter] handleSetStyle error: ' + e);
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
            { label: 'Send a picture of yourself in chat',  action: 'sp_portrait' },
            { label: 'Set art style...',                    action: 'sp_style' }
        ]);

        PT.eventSource.on(PT.events.MESSAGE_LONG_PRESSED,  onMessageLongPressed);
        PT.eventSource.on(PT.events.CHAT_CHANGED,           onChatChanged);
        PT.eventSource.on(PT.events.CHARACTER_CHANGED,      onCharacterChanged);
        PT.eventSource.on(PT.events.BUTTON_CLICKED,         onButtonClicked);

        PT.log('[ScenePainter] Ready.');
    }

    init();

})();
