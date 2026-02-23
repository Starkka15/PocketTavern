/**
 * PocketTavern Extension API — pt_api.js
 *
 * Injected into the WebView sandbox before any extension code runs.
 * Extensions interact with PocketTavern via the global `PT` object.
 *
 * Quick-start:
 *
 *   // React to a message
 *   PT.eventSource.on(PT.events.MESSAGE_RECEIVED, function(data) {
 *       // data = { text: "...", index: 5, isUser: false }
 *       var mood = (data.text.match(/\[mood: (\w+)\]/) || [])[1];
 *       if (mood) PT.setMessageHeader(data.index, '💭 Mood: ' + mood);
 *   });
 *
 *   // Add quick reply buttons
 *   PT.registerButtons('my-ext', [
 *       { label: 'Continue', message: 'Please continue.' },
 *       { label: 'Shorter',  message: 'Be more concise.' }
 *   ]);
 *
 *   // Send a message programmatically
 *   PT.sendMessage('Hello!');
 *
 *   // Inject a system prompt
 *   PT.setExtensionPrompt('my-ext', 'Always respond in rhyme.', PT.INJECTION_POSITION.AFTER_CHAR_DEFS);
 */
(function () {
    'use strict';

    var _listeners = {};
    var _promptInjections = {};

    // ── Internal entry points called by Kotlin ────────────────────────────────

    /** Dispatch an event from Kotlin into all registered JS handlers. */
    window.__ptDispatchEvent = function (eventName, dataJson) {
        var data = (dataJson !== null && dataJson !== undefined) ? dataJson : null;
        // dataJson may already be a parsed object (for structured events) or a string
        if (typeof data === 'string') {
            try { data = JSON.parse(data); } catch (e) { /* keep as string */ }
        }
        var handlers = _listeners[eventName] ? _listeners[eventName].slice() : [];
        var disabled = window.__ptDisabledExtensions || [];
        for (var i = 0; i < handlers.length; i++) {
            var extId = handlers[i].__ptExtId;
            if (extId && disabled.indexOf(extId) !== -1) continue;
            try {
                handlers[i](data);
            } catch (e) {
                if (window.PtBridge) PtBridge.log('[pt_api] handler error in ' + eventName + ': ' + e.message);
            }
        }
    };

    // ── eventSource ───────────────────────────────────────────────────────────

    var eventSource = {
        /**
         * Subscribe to a PocketTavern event.
         * @param {string}   eventName  - one of PT.events.*
         * @param {Function} callback   - called with event data (may be null)
         */
        on: function (eventName, callback) {
            // Tag the callback with the currently loading extension's ID
            // so per-character filtering can skip disabled extensions' handlers.
            callback.__ptExtId = window.__ptCurrentExtId || null;
            if (!_listeners[eventName]) _listeners[eventName] = [];
            _listeners[eventName].push(callback);
        },

        /**
         * Unsubscribe a previously registered handler.
         * @param {string}   eventName
         * @param {Function} callback
         */
        off: function (eventName, callback) {
            if (_listeners[eventName]) {
                _listeners[eventName] = _listeners[eventName].filter(function (f) {
                    return f !== callback;
                });
            }
        }
    };

    // ── Public PT object ──────────────────────────────────────────────────────

    window.PT = {

        /** Event name constants. */
        events: {
            MESSAGE_SENT:       'MESSAGE_SENT',
            MESSAGE_RECEIVED:   'MESSAGE_RECEIVED',
            MESSAGE_EDITED:     'MESSAGE_EDITED',
            MESSAGE_DELETED:    'MESSAGE_DELETED',
            GENERATION_STARTED: 'GENERATION_STARTED',
            GENERATION_STOPPED: 'GENERATION_STOPPED',
            CHAT_CHANGED:       'CHAT_CHANGED',
            CHARACTER_CHANGED:  'CHARACTER_CHANGED',
            BUTTON_CLICKED:       'BUTTON_CLICKED',
            HEADER_LONG_PRESSED:  'HEADER_LONG_PRESSED',
            MESSAGE_LONG_PRESSED: 'MESSAGE_LONG_PRESSED'
        },

        /** Where to inject prompt text relative to the character definition. */
        INJECTION_POSITION: {
            BEFORE_CHAR_DEFS: 0,
            AFTER_CHAR_DEFS:  1,
            IN_CHAT:          2
        },

        /** Subscribe / unsubscribe from PocketTavern events. */
        eventSource: eventSource,

        /**
         * Persistent settings object, keyed by your extension id.
         * Modify PT.extension_settings['your-id'] and call PT.saveSettings() to persist.
         */
        extension_settings: {},

        /**
         * Inject text into the prompt before the next generation.
         * Calling with null/empty text removes any previous injection.
         *
         * @param {string} extensionId  Your extension's unique id.
         * @param {string} text         Text to inject (null/'' to clear).
         * @param {number} [position]   PT.INJECTION_POSITION.* (default: AFTER_CHAR_DEFS).
         * @param {number} [depth]      Depth into chat history for IN_CHAT position (default: 0).
         */
        setExtensionPrompt: function (extensionId, text, position, depth) {
            var pos = (position !== undefined && position !== null) ? position : 1;
            var dep = (depth !== undefined && depth !== null) ? depth : 0;

            if (text && text.trim()) {
                _promptInjections[extensionId] = { text: text, position: pos, depth: dep };
            } else {
                delete _promptInjections[extensionId];
            }

            if (window.PtBridge) {
                PtBridge.setPromptInjection(extensionId, text || '', pos, dep);
            }
        },

        /**
         * Get the current chat context.
         * Returns an object with: character, recentMessages, personaName, apiType.
         *
         * @returns {object}
         */
        getContext: function () {
            if (!window.PtBridge) return {};
            try { return JSON.parse(PtBridge.getContext()); } catch (e) { return {}; }
        },

        /**
         * Persist PT.extension_settings to device storage.
         * Call after modifying PT.extension_settings[yourId].
         */
        saveSettings: function () {
            if (window.PtBridge) {
                PtBridge.saveAllSettings(JSON.stringify(PT.extension_settings));
            }
        },

        /**
         * Write a message to PocketTavern's debug log.
         * @param {*} message
         */
        log: function (message) {
            if (window.PtBridge) PtBridge.log(String(message));
        },

        // ── UI: Quick reply buttons ───────────────────────────────────────────

        /**
         * Register quick reply buttons above the chat input.
         * Replaces any buttons previously registered under the same id.
         *
         * Buttons can either send a message or trigger a callback action:
         *   - { label, message } — sends the message as a user chat message
         *   - { label, action }  — dispatches BUTTON_CLICKED event with { action, label }
         *
         * @param {string} extensionId  Unique id for this set of buttons.
         * @param {Array}  buttons      Array of { label: string, message?: string, action?: string }
         *
         * @example
         *   PT.registerButtons('my-ext', [
         *       { label: 'Continue', message: 'Please continue.' },
         *       { label: 'Edit',     action:  'edit' }
         *   ]);
         *
         *   PT.eventSource.on(PT.events.BUTTON_CLICKED, function(data) {
         *       if (data.action === 'edit') { // handle edit }
         *   });
         */
        registerButtons: function (extensionId, buttons) {
            if (window.PtBridge) {
                PtBridge.registerButtons(extensionId, JSON.stringify(buttons || []));
            }
        },

        /**
         * Remove all quick reply buttons registered under extensionId.
         * @param {string} extensionId
         */
        clearButtons: function (extensionId) {
            if (window.PtBridge) {
                PtBridge.clearButtons(extensionId);
            }
        },

        /**
         * Send a message as the user through the normal generation pipeline.
         * @param {string} text  The message text to send.
         */
        sendMessage: function (text) {
            if (window.PtBridge && text) {
                PtBridge.sendMessage(String(text));
            }
        },

        // ── UI: Message headers ───────────────────────────────────────────────

        /**
         * Set a header box that appears above the AI message at [messageIndex].
         * The box content updates whenever you call this again with the same index.
         * Pass empty string to remove the header.
         *
         * The message index is provided in MESSAGE_RECEIVED event data as data.index.
         *
         * @param {number} messageIndex      Index of the message to attach the header to.
         * @param {string} text              Text to display in the header box (always visible).
         * @param {string} [extensionId]     Your extension's id (used for long-press ownership).
         * @param {string} [collapsibleText] Optional text shown/hidden when user taps the header.
         *
         * @example
         *   PT.setMessageHeader(data.index, 'Time: 10:00 AM', 'my-ext', 'Alice\n  Outfit: Blue dress');
         */
        setMessageHeader: function (messageIndex, text, extensionId, collapsibleText) {
            if (window.PtBridge) {
                PtBridge.setMessageHeader(messageIndex, text || '', extensionId || '', collapsibleText || '');
            }
        },

        /**
         * Remove the header box for a specific message.
         * @param {number} messageIndex
         */
        clearMessageHeader: function (messageIndex) {
            if (window.PtBridge) {
                PtBridge.clearMessageHeader(messageIndex);
            }
        },

        /**
         * Remove all message headers (e.g. when CHAT_CHANGED fires).
         */
        clearAllHeaders: function () {
            if (window.PtBridge) {
                PtBridge.clearAllHeaders();
            }
        },

        /**
         * Get the persisted header entries for a specific message.
         * Returns an array of { text, extensionId } objects, or an empty array.
         *
         * Useful for reading back header data set by any extension (including
         * manual edits) without having to re-parse the raw message text.
         *
         * @param {number} messageIndex  Index of the message.
         * @returns {Array<{text: string, extensionId: string}>}
         *
         * @example
         *   var headers = PT.getMessageHeaders(3);
         *   var myHeader = headers.find(function(h) { return h.extensionId === 'my-ext'; });
         *   if (myHeader) console.log('Header text:', myHeader.text, 'Collapsible:', myHeader.collapsibleText);
         */
        getMessageHeaders: function (messageIndex) {
            if (!window.PtBridge) return [];
            try { return JSON.parse(PtBridge.getMessageHeaders(messageIndex)); } catch (e) { return []; }
        },

        // ── Header buttons & menus ───────────────────────────────────────────

        /**
         * Register inline buttons that render inside the header box.
         * Hidden by default; user long-presses the header to toggle show/hide.
         * Clicking a button dispatches BUTTON_CLICKED with { action, label }.
         *
         * @param {string} extensionId  Your extension's unique id.
         * @param {Array}  buttons      Array of { label: string, action: string }
         *
         * @example
         *   PT.registerHeaderButtons('my-ext', [
         *       { label: 'Edit',   action: 'edit_header' },
         *       { label: 'Regen',  action: 'regen_header' }
         *   ]);
         */
        registerHeaderButtons: function (extensionId, buttons) {
            if (window.PtBridge) {
                PtBridge.registerHeaderButtons(extensionId, JSON.stringify(buttons || []));
            }
        },

        /**
         * Remove inline header buttons for this extension.
         * @param {string} extensionId
         */
        clearHeaderButtons: function (extensionId) {
            if (window.PtBridge) {
                PtBridge.clearHeaderButtons(extensionId);
            }
        },

        /**
         * Pre-register a context menu shown as a popup when the user
         * long-presses a header owned by this extension.
         * Selecting an item dispatches BUTTON_CLICKED with { action, label }.
         *
         * @param {string} extensionId  Your extension's unique id.
         * @param {Array}  items        Array of { label: string, action: string }
         *
         * @example
         *   PT.registerHeaderMenu('my-ext', [
         *       { label: 'Review Notes',  action: 'review' },
         *       { label: 'Clear Notes',   action: 'clear' }
         *   ]);
         */
        registerHeaderMenu: function (extensionId, items) {
            if (window.PtBridge) {
                PtBridge.registerHeaderMenu(extensionId, JSON.stringify(items || []));
            }
        },

        /**
         * Remove the header context menu for this extension.
         * @param {string} extensionId
         */
        clearHeaderMenu: function (extensionId) {
            if (window.PtBridge) {
                PtBridge.clearHeaderMenu(extensionId);
            }
        },

        // ── Message context menu actions ─────────────────────────────────────

        /**
         * Register actions that appear in the message long-press context menu.
         * When the user long-presses any message, a MESSAGE_LONG_PRESSED event fires first,
         * then the menu shows with your registered actions after the built-in items.
         * Clicking an action dispatches BUTTON_CLICKED with { action, label }.
         *
         * @param {string} extensionId  Your extension's unique id.
         * @param {Array}  actions      Array of { label: string, action: string }
         *
         * @example
         *   PT.registerMessageActions('my-ext', [
         *       { label: 'Paint Scene', action: 'paint_scene' }
         *   ]);
         */
        registerMessageActions: function (extensionId, actions) {
            if (window.PtBridge) {
                PtBridge.registerMessageActions(extensionId, JSON.stringify(actions || []));
            }
        },

        /**
         * Remove message context menu actions for this extension.
         * @param {string} extensionId
         */
        clearMessageActions: function (extensionId) {
            if (window.PtBridge) {
                PtBridge.clearMessageActions(extensionId);
            }
        },

        // ── Output filters ───────────────────────────────────────────────────

        /**
         * Register a regex pattern to strip from displayed AI messages.
         * The matched text is removed before the message is shown in the chat bubble.
         * Useful for hiding metadata tags that your extension parses into headers.
         *
         * @param {string} extensionId  Your extension's unique id.
         * @param {string} pattern      Regex pattern string (will be applied with 'gi' flags).
         *
         * @example
         *   // Strip [mood: happy] tags from displayed text
         *   PT.registerOutputFilter('my-ext', '\\[mood:\\s*\\w+\\]');
         */
        registerOutputFilter: function (extensionId, pattern) {
            if (window.PtBridge) {
                PtBridge.registerOutputFilter(extensionId, pattern);
            }
        },

        /**
         * Remove a previously registered output filter.
         * @param {string} extensionId
         */
        clearOutputFilter: function (extensionId) {
            if (window.PtBridge) {
                PtBridge.clearOutputFilter(extensionId);
            }
        },

        // ── Dialogs ──────────────────────────────────────────────────────────

        /**
         * Show a native edit dialog with editable text fields.
         * Returns a Promise that resolves with { key: value } or null if cancelled.
         *
         * @param {string} title   Dialog title.
         * @param {Array}  fields  Array of { key: string, label: string, value: string }
         * @returns {Promise<object|null>}
         *
         * @example
         *   var result = await PT.showEditDialog('Edit Tracker', [
         *       { key: 'time',     label: 'Time',     value: '10:00:00' },
         *       { key: 'location', label: 'Location', value: 'Town Square' }
         *   ]);
         *   if (result) { console.log(result.time, result.location); }
         */
        showEditDialog: function (title, fields) {
            return new Promise(function (resolve) {
                var cbId = '__editCb_' + (++_callbackCounter);
                _pendingCallbacks[cbId] = resolve;
                if (window.PtBridge) {
                    PtBridge.showEditDialog(title || 'Edit', JSON.stringify(fields || []), cbId);
                } else {
                    resolve(null);
                }
            });
        },

        // ── Hidden generation ─────────────────────────────────────────────────

        /**
         * Send a prompt to the LLM without adding messages to the chat.
         * Useful for regenerating extension metadata (headers, tags) without
         * creating a new user/assistant message pair.
         *
         * @param {string} prompt  The prompt text to send.
         * @returns {Promise<string>}  The AI's response text.
         *
         * @example
         *   var tags = await PT.generateHidden('Re-output tracker tags for the current scene.');
         *   var parsed = parseTags(tags);
         */
        generateHidden: function (prompt) {
            return new Promise(function (resolve) {
                var cbId = '__genCb_' + (++_callbackCounter);
                _pendingCallbacks[cbId] = resolve;
                if (window.PtBridge) {
                    PtBridge.generateHidden(prompt || '', cbId);
                } else {
                    resolve('');
                }
            });
        },

        // ── Image generation ─────────────────────────────────────────────────

        /**
         * Generate an image using the app's configured image generation backend.
         * Returns a Promise that resolves with the base64-encoded image data,
         * or an empty string on failure.
         *
         * @param {string} prompt         The image generation prompt.
         * @param {object} [options]      Optional overrides: { width, height, negativePrompt, seed }
         * @returns {Promise<string>}     Base64 image data (PNG).
         *
         * @example
         *   var base64 = await PT.generateImage('a medieval tavern at sunset');
         *   if (base64) PT.insertMessage('', { type: 'image', imageBase64: base64 });
         */
        generateImage: function (prompt, options) {
            return new Promise(function (resolve) {
                var cbId = '__imgCb_' + (++_callbackCounter);
                _pendingCallbacks[cbId] = resolve;
                if (window.PtBridge) {
                    PtBridge.generateImage(prompt || '', JSON.stringify(options || {}), cbId);
                } else {
                    resolve('');
                }
            });
        },

        // ── Message insertion ────────────────────────────────────────────────

        /**
         * Insert a non-LLM message into the chat (narrator text or image).
         * Does not trigger AI generation — the message simply appears in chat.
         *
         * @param {string} content       Text content (for narrator messages).
         * @param {object} [options]     { type: 'narrator'|'image', imageBase64: '...' }
         *
         * @example
         *   // Insert narrator text
         *   PT.insertMessage('The sun sets over the mountains.');
         *
         *   // Insert an image
         *   PT.insertMessage('', { type: 'image', imageBase64: base64Data });
         */
        insertMessage: function (content, options) {
            if (window.PtBridge) {
                PtBridge.insertMessage(content || '', JSON.stringify(options || {}));
            }
        },

        // ── Per-character extension state ────────────────────────────────────

        /**
         * Check if an extension is currently enabled for the active character.
         * Useful for extensions to early-return from their logic when disabled.
         *
         * @param {string} extensionId  The extension's unique id.
         * @returns {boolean}  True if enabled (or no per-character filter active).
         */
        isEnabled: function (extensionId) {
            var disabled = window.__ptDisabledExtensions || [];
            return disabled.indexOf(extensionId) === -1;
        }
    };

    // ── Callback infrastructure for async bridge results ──────────────────

    var _callbackCounter = 0;
    var _pendingCallbacks = {};

    /** Called by Kotlin when the edit dialog is submitted or cancelled. */
    window.__ptEditDialogResult = function (callbackId, result) {
        var cb = _pendingCallbacks[callbackId];
        if (cb) {
            delete _pendingCallbacks[callbackId];
            cb(result);
        }
    };

    /** Called by Kotlin when a hidden generation completes. */
    window.__ptHiddenGenerateResult = function (callbackId, text) {
        var cb = _pendingCallbacks[callbackId];
        if (cb) {
            delete _pendingCallbacks[callbackId];
            cb(text || '');
        }
    };

    /** Called by Kotlin when image generation completes. */
    window.__ptImageGenerateResult = function (callbackId, base64) {
        var cb = _pendingCallbacks[callbackId];
        if (cb) {
            delete _pendingCallbacks[callbackId];
            cb(base64 || '');
        }
    };

})();
