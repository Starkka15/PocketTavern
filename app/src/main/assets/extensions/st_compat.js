/**
 * st_compat.js — SillyTavern → PocketTavern compatibility shim
 *
 * Injected after pt_api.js so that ST extensions run in PocketTavern's WebView
 * sandbox without modification. Maps ST globals to PT equivalents.
 *
 * Injected before any extension code runs; extensions load after this file.
 */
(function () {
    'use strict';

    // ── extension_settings ────────────────────────────────────────────────────
    // ST extensions store their settings under extension_settings['ext-id'].
    // PT's extension_settings is the same object — just expose it directly.
    window.extension_settings = PT.extension_settings;

    // ── saveSettingsDebounced ─────────────────────────────────────────────────
    var _saveTimer = null;
    window.saveSettingsDebounced = function () {
        clearTimeout(_saveTimer);
        _saveTimer = setTimeout(function () { PT.saveSettings(); }, 500);
    };

    // ── event_types ───────────────────────────────────────────────────────────
    // ST event name constants mapped to PT equivalents.
    // ST events with no PT equivalent are kept as their own name — they'll
    // be registered but never fired, which is safe.
    window.event_types = {
        // Direct mappings
        MESSAGE_SENT:                   PT.events.MESSAGE_SENT,
        MESSAGE_RECEIVED:               PT.events.MESSAGE_RECEIVED,
        MESSAGE_EDITED:                 PT.events.MESSAGE_EDITED,
        MESSAGE_DELETED:                PT.events.MESSAGE_DELETED,
        CHAT_CHANGED:                   PT.events.CHAT_CHANGED,
        CHARACTER_CHANGED:              PT.events.CHARACTER_CHANGED,
        GENERATION_STARTED:             PT.events.GENERATION_STARTED,
        GENERATION_STOPPED:             PT.events.GENERATION_STOPPED,

        // Near-equivalent mappings
        MESSAGE_SWIPED:                 PT.events.MESSAGE_RECEIVED,
        CHAT_COMPLETION_PROMPT_READY:   PT.events.GENERATION_STARTED,
        GENERATE_BEFORE_COMBINE_PROMPTS: PT.events.GENERATION_STARTED,
        GENERATE_AFTER_COMBINE_PROMPTS: PT.events.GENERATION_STARTED,

        // ST-only events — registered but never fired in PT
        APP_READY:                      'APP_READY',
        SETTINGS_UPDATED:               'SETTINGS_UPDATED',
        GROUP_UPDATED:                  'GROUP_UPDATED',
        WORLDINFO_SETTINGS_UPDATED:     'WORLDINFO_SETTINGS_UPDATED',
        WORLDINFO_UPDATED:              'WORLDINFO_UPDATED',
        CHARACTER_DELETED:              'CHARACTER_DELETED',
        USER_MESSAGE_RENDERED:          'USER_MESSAGE_RENDERED',
        AI_RESPONSE_FINISHED:           PT.events.MESSAGE_RECEIVED,
        FORCE_SET_BACKGROUND:           'FORCE_SET_BACKGROUND',
        CHAT_DELETED:                   'CHAT_DELETED',
        CHAT_CREATED:                   'CHAT_CREATED',
        GROUP_CHAT_DELETED:             'GROUP_CHAT_DELETED',
        GROUP_CHAT_CREATED:             'GROUP_CHAT_CREATED',
        MOVABLE_PANELS_RESET:           'MOVABLE_PANELS_RESET',
        SETTINGS_LOADED_BEFORE:         'SETTINGS_LOADED_BEFORE',
        SETTINGS_LOADED_AFTER:          'SETTINGS_LOADED_AFTER',
        BIAS_UPDATED:                   'BIAS_UPDATED',
        SMOOTH_STREAMING_THROTTLE:      'SMOOTH_STREAMING_THROTTLE',
        EXTENSION_SETTINGS_LOADED:      'EXTENSION_SETTINGS_LOADED',
        IMPULSE_PROMPT_READY:           'IMPULSE_PROMPT_READY',
        LLM_PROMPT_TOKENS_COUNTED:      'LLM_PROMPT_TOKENS_COUNTED',
        CHARACTER_PAGE_LOADED:          'CHARACTER_PAGE_LOADED',
        CHARACTER_FIRST_MESSAGE_SELECTED: 'CHARACTER_FIRST_MESSAGE_SELECTED',
    };

    // ── eventSource ───────────────────────────────────────────────────────────
    // Delegates to PT.eventSource. emit() is a no-op (PT doesn't support JS-side emit).
    window.eventSource = {
        on:         function (ev, cb) { PT.eventSource.on(ev, cb); },
        off:        function (ev, cb) { PT.eventSource.off(ev, cb); },
        emit:       function (ev, data) { if (window.__ptDispatchEvent) __ptDispatchEvent(ev, data !== undefined ? JSON.stringify(data) : null); },
        makeFirst:  function (ev, cb) { PT.eventSource.on(ev, cb); },
        removeLast: function (ev, cb) { PT.eventSource.off(ev, cb); }
    };

    // ── toastr ────────────────────────────────────────────────────────────────
    window.toastr = {
        success: function (msg) { PT._showToast('success', msg); },
        info:    function (msg) { PT._showToast('info',    msg); },
        warning: function (msg) { PT._showToast('warning', msg); },
        error:   function (msg) { PT._showToast('error',   msg); }
    };

    // ── importCharacterFile ───────────────────────────────────────────────────
    // Primary function BotBrowser calls to import a card by URL.
    window.importCharacterFile = function (url, data) {
        var filename = (url || '').split('/').pop().split('?')[0] || 'character.png';
        if (!filename.endsWith('.png')) filename += '.png';
        return PT.importCharacter(url, filename);
    };

    // ── getRequestHeaders ─────────────────────────────────────────────────────
    // Returns auth headers for CharaVault API calls.
    window.getRequestHeaders = function () {
        var ctx = PT.getContext();
        var token = ctx.apiToken || ctx.authToken || '';
        return token ? { 'Authorization': 'Bearer ' + token } : {};
    };

    // ── characters / getCharacters ────────────────────────────────────────────
    window.characters = [];
    window.getCharacters = function () { return []; };

    // ── Misc ST globals used by various extensions ────────────────────────────
    window.callPopup = function (html, type, inputValue) {
        return Promise.resolve(inputValue || '');
    };
    window.importWorldInfo       = function () { return Promise.resolve(); };
    window.selectCharacterById   = function () {};
    window.substituteParams      = function (str) { return str || ''; };
    window.getTokenCount         = function (str) { return Math.ceil((str || '').length / 4); };
    window.escapeHtml            = function (str) {
        return String(str)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    };
    window.getContext            = function () { return PT.getContext(); };
    window.reloadCurrentChat     = function () {};
    window.sendMessageAsUser     = function (text) { PT.sendMessage(text); };
    window.Generate              = function () {};
    window.is_send_press         = false;
    window.main_api              = 'openai';   // most extensions key off this for compat checks
    window.power_user            = {};
    window.chat_metadata         = {};
    window.this_chid             = null;       // current character id (unknown in PT, null is safe)

    // ── Minimal jQuery stub ───────────────────────────────────────────────────
    // Only installed if real jQuery isn't already present (e.g. from jquery.min.js).
    // Covers the patterns BotBrowser and similar extensions actually use:
    //   $(selector), $(document).ready(), $.ajax(), $.get(),
    //   .on(), .find(), .html(), .val(), .append(), .text(), .attr(), .css(),
    //   .show(), .hide(), .empty(), .length, .each()
    if (!window.jQuery) {
        var _jq = function (selector) {
            var els = [];
            if (!selector) {
                // no-op
            } else if (typeof selector === 'function') {
                // jQuery(fn) — same as $(document).ready(fn)
                if (document.readyState !== 'loading') {
                    try { selector(); } catch (e) { console.error('[st_compat] jQuery(fn) error: ' + e); }
                } else {
                    document.addEventListener('DOMContentLoaded', function () {
                        try { selector(); } catch (e) { console.error('[st_compat] jQuery(fn) error: ' + e); }
                    });
                }
                return { _els: [], length: 0 };
            } else if (selector === document || selector === window) {
                els = [selector];
            } else if (typeof selector === 'string') {
                try { els = Array.prototype.slice.call(document.querySelectorAll(selector)); }
                catch (e) { els = []; }
            } else if (selector && selector.nodeType) {
                els = [selector];
            } else if (Array.isArray(selector)) {
                els = selector;
            }

            var wrap = {
                _els: els,
                length: els.length,

                each: function (fn) {
                    els.forEach(function (el, i) { fn.call(el, i, el); });
                    return wrap;
                },

                on: function (event, selectorOrFn, fn) {
                    var handler = typeof selectorOrFn === 'function' ? selectorOrFn : fn;
                    els.forEach(function (el) {
                        if (el && el.addEventListener) el.addEventListener(event, handler);
                    });
                    return wrap;
                },

                off: function (event, fn) {
                    els.forEach(function (el) {
                        if (el && el.removeEventListener) el.removeEventListener(event, fn);
                    });
                    return wrap;
                },

                find: function (sel) {
                    var found = [];
                    els.forEach(function (el) {
                        if (el && el.querySelectorAll) {
                            found = found.concat(Array.prototype.slice.call(el.querySelectorAll(sel)));
                        }
                    });
                    return _jq(found);
                },

                html: function (val) {
                    if (val === undefined) return els[0] ? els[0].innerHTML : '';
                    els.forEach(function (el) { if (el) el.innerHTML = val; });
                    return wrap;
                },

                text: function (val) {
                    if (val === undefined) return els[0] ? els[0].textContent : '';
                    els.forEach(function (el) { if (el) el.textContent = val; });
                    return wrap;
                },

                val: function (val) {
                    if (val === undefined) return els[0] ? els[0].value : '';
                    els.forEach(function (el) { if (el) el.value = val; });
                    return wrap;
                },

                append: function (content) {
                    els.forEach(function (el) {
                        if (!el) return;
                        if (typeof content === 'string') {
                            el.insertAdjacentHTML('beforeend', content);
                        } else if (content && content._els) {
                            content._els.forEach(function (c) { el.appendChild(c); });
                        } else if (content && content.nodeType) {
                            el.appendChild(content);
                        }
                        // Auto-register panel when an extension appends to #extensions_settings
                        if (el.id === 'extensions_settings' && el.innerHTML.trim()) {
                            var extId = window.__ptCurrentExtId || 'extension';
                            var titleEl = el.querySelector('b, strong, h4, .inline-drawer-toggle-title, [data-i18n]');
                            var title = (titleEl && titleEl.textContent.trim()) || extId;
                            PT.registerPanel({ title: title, html: el.innerHTML, css: '' });
                        }
                    });
                    return wrap;
                },

                prepend: function (content) {
                    els.forEach(function (el) {
                        if (!el) return;
                        if (typeof content === 'string') {
                            el.insertAdjacentHTML('afterbegin', content);
                        }
                    });
                    return wrap;
                },

                attr: function (name, val) {
                    if (val === undefined) return els[0] ? els[0].getAttribute(name) : null;
                    els.forEach(function (el) { if (el) el.setAttribute(name, val); });
                    return wrap;
                },

                css: function (prop, val) {
                    if (typeof prop === 'object') {
                        els.forEach(function (el) {
                            if (el) Object.assign(el.style, prop);
                        });
                    } else if (val === undefined) {
                        return els[0] ? els[0].style[prop] : '';
                    } else {
                        els.forEach(function (el) { if (el) el.style[prop] = val; });
                    }
                    return wrap;
                },

                show: function () {
                    els.forEach(function (el) { if (el) el.style.display = ''; });
                    return wrap;
                },

                hide: function () {
                    els.forEach(function (el) { if (el) el.style.display = 'none'; });
                    return wrap;
                },

                empty: function () {
                    els.forEach(function (el) { if (el) el.innerHTML = ''; });
                    return wrap;
                },

                remove: function () {
                    els.forEach(function (el) { if (el && el.parentNode) el.parentNode.removeChild(el); });
                    return wrap;
                },

                addClass: function (cls) {
                    els.forEach(function (el) { if (el) el.classList.add(cls); });
                    return wrap;
                },

                removeClass: function (cls) {
                    els.forEach(function (el) { if (el) el.classList.remove(cls); });
                    return wrap;
                },

                hasClass: function (cls) {
                    return els.length > 0 && els[0] && els[0].classList.contains(cls);
                },

                prop: function (name, val) {
                    if (val === undefined) return els[0] ? els[0][name] : undefined;
                    els.forEach(function (el) { if (el) el[name] = val; });
                    return wrap;
                },

                trigger: function (event) {
                    els.forEach(function (el) {
                        if (el) el.dispatchEvent(new Event(event, { bubbles: true }));
                    });
                    return wrap;
                },

                ready: function (fn) {
                    if (document.readyState !== 'loading') fn();
                    else document.addEventListener('DOMContentLoaded', fn);
                    return wrap;
                },

                get: function (i) { return els[i]; }
            };
            return wrap;
        };

        // $.ajax / $.get / $.post
        _jq.ajax = function (opts) {
            opts = opts || {};
            var method = (opts.method || opts.type || 'GET').toUpperCase();
            var headers = opts.headers || {};
            if (opts.contentType) headers['Content-Type'] = opts.contentType;
            var body = null;
            if (opts.data && method !== 'GET') {
                body = typeof opts.data === 'string' ? opts.data : JSON.stringify(opts.data);
            }
            fetch(opts.url, { method: method, headers: headers, body: body })
                .then(function (r) { return r.text(); })
                .then(function (text) {
                    var data = text;
                    try { data = JSON.parse(text); } catch (e) {}
                    if (opts.success) opts.success(data);
                    if (opts.complete) opts.complete({ responseText: text }, 'success');
                })
                .catch(function (err) {
                    if (opts.error) opts.error({ responseText: '' }, 'error', err);
                    if (opts.complete) opts.complete({}, 'error');
                });
        };

        _jq.get = function (url, data, success) {
            if (typeof data === 'function') { success = data; data = undefined; }
            _jq.ajax({ url: url, method: 'GET', success: success });
        };

        _jq.post = function (url, data, success) {
            _jq.ajax({ url: url, method: 'POST', data: data, success: success });
        };

        _jq.extend = function (target) {
            var sources = Array.prototype.slice.call(arguments, 1);
            sources.forEach(function (src) { if (src) Object.assign(target, src); });
            return target;
        };

        _jq.noop = function () {};
        _jq.isFunction = function (fn) { return typeof fn === 'function'; };
        _jq.trim = function (str) { return String(str).trim(); };
        _jq.parseJSON = function (str) { return JSON.parse(str); };
        _jq.fn = _jq.prototype;

        window.jQuery = _jq;
        window.$ = _jq;
    }

    // ── #extensions_settings container ───────────────────────────────────────
    // ST extensions append their settings UI here. We create it so the DOM
    // query succeeds, and the jQuery append() intercept above auto-registers
    // the content as a PT panel.
    (function () {
        var existing = document.getElementById('extensions_settings');
        if (!existing) {
            var div = document.createElement('div');
            div.id = 'extensions_settings';
            div.style.display = 'none';
            document.body.appendChild(div);
        }
    })();

})();
