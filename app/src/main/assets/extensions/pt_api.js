/**
 * PocketTavern Extension API — pt_api.js
 *
 * Injected into the WebView sandbox before any extension code runs.
 * Extensions interact with PocketTavern via the global `PT` object.
 *
 * Quick-start:
 *
 *   PT.eventSource.on(PT.events.MESSAGE_RECEIVED, function(data) {
 *       PT.log('Got message: ' + JSON.stringify(data));
 *   });
 *
 *   PT.setExtensionPrompt('my-ext', 'Always respond in rhyme.', PT.INJECTION_POSITION.AFTER_CHAR_DEFS);
 */
(function () {
    'use strict';

    var _listeners = {};
    var _promptInjections = {};

    // ── Internal entry points called by Kotlin ────────────────────────────────

    /** Dispatch an event from Kotlin into all registered JS handlers. */
    window.__ptDispatchEvent = function (eventName, dataJson) {
        var data = dataJson ? JSON.parse(dataJson) : null;
        var handlers = _listeners[eventName] ? _listeners[eventName].slice() : [];
        for (var i = 0; i < handlers.length; i++) {
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
            CHARACTER_CHANGED:  'CHARACTER_CHANGED'
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
        }
    };

})();
