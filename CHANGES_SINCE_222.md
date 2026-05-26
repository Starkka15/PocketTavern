# Changes since v2.2.2

All changes are uncommitted modifications on top of the v2.2.2 tag.

---

## 1. SPRITE_REGEX group extraction fix
**File:** `ui/components/ChatBubble.kt`

Groups 4–6 of `SPRITE_REGEX` were silently ignored. The name extraction chain now falls through all six capture groups before giving up, so sprite tags with unusual formatting resolve correctly.

---

## 2. Full macro substitution in user messages
**File:** `domain/prompt/PromptBuilder.kt`

Added `applyUserMacros(text, history)` — a public method that runs the full `substituteMacros()` pipeline on outgoing user messages. Previously only `{{char}}` and `{{user}}` were substituted in user turns; now all macros (`{{persona}}`, `{{time}}`, `{{date}}`, `{{random}}`, etc.) resolve correctly.

**File:** `ui/screens/chat/ChatViewModel.kt`

User messages are passed through `applyUserMacros()` before being stored and sent.

---

## 3. Shared World Book

### Data model
**File:** `domain/model/Group.kt`
- Added `worldBook: String = ""` field to `Group`

**File:** `data/local/GroupStorage.kt`
- `getGroupsForCharacter(fileName)` — find all groups a character belongs to
- `appendWorldBookEntry(groupId, entry)` — prepend `[YYYY-MM-DD]` timestamp, append to world book, persist
- `saveWorldBook(groupId, content)` — full overwrite (editor UI)

### Prompt injection
**File:** `domain/prompt/PromptBuilder.kt`
- Added `worldBook: String = ""` constructor parameter
- OAI mode: injects `[Shared World Book]\n$worldBook` as a system message immediately before `char_description`
- Text completion mode: injects the same block into the story string

### Solo chat wiring
**File:** `ui/screens/chat/ChatViewModel.kt`
- On character load, queries `GroupStorage.getGroupsForCharacter()` to find the first linked group
- Caches `_currentGroupId` and `_currentWorldBook`; passes world book to `PromptBuilder`
- `ChatUiState` gains `linkedGroupName` and `hasWorldBook`

### Solo chat top bar
**File:** `ui/screens/chat/ChatScreen.kt`
- When a character belongs to a group, a subtitle `Group: <name>` appears in the top bar in primary color

### Group chat wiring
**File:** `ui/screens/groups/GroupChatViewModel.kt`
- `buildGroupPrompt` injects `[Shared World Book]` block after scenario
- `buildGroupOaiMessages` (see §6) includes world book in the system message

### World Book editor (group chat overflow menu)
**File:** `ui/screens/groups/GroupChatScreen.kt`
- "World Book" menu item (MenuBook icon) added to overflow
- `WorldBookEditorDialog` composable — free-text editor, taller than prompt editor, saves on confirm

### `/addlore` slash command
Available in **both** solo and group chat.
`/addlore <text>` — appends a timestamped entry to the linked group's world book, then posts a narrator system message confirming the addition. Silently ignored if no group is linked (solo chat).

---

## 4. Lore Tracking field (character cards)

### DB migration
**File:** `data/local/db/AppDatabase.kt` — version 5 → 6
**File:** `data/local/db/entity/CharacterEntity.kt` — `loreHints: String = ""`
**File:** `di/AppModule.kt` — `MIGRATION_5_6` registered

### Card storage
**File:** `data/local/CharacterStorage.kt`
- `saveCharacter`: writes `loreHints` into `extensions["pockettavern_lore_hints"]` (ignored by SillyTavern and other tools)
- `cardDataToEntity` / `readCharacterFile`: reads `pockettavern_lore_hints` from extensions map

### Domain model
**File:** `domain/model/Character.kt` — `loreHints: String = ""`

### Character editor UI
**File:** `ui/screens/createcharacter/CreateCharacterViewModel.kt` — `loreHints` state + `updateLoreHints()`
**File:** `ui/screens/createcharacter/CreateCharacterScreen.kt` — "Lore Tracking (PocketTavern)" field after Creator Notes (minLines 3, maxLines 8)

---

## 5. `/scanlore` command + ScanloreConfirmDialog

### Dialog component
**File:** `ui/components/ScanloreConfirmDialog.kt` *(new file)*
- Shows loading spinner while the LLM runs
- On results: checkbox list with inline editable `OutlinedTextField` per entry
- "Add to Book" confirms selected/edited entries → `appendWorldBookEntry`
- Works in both solo and group chat

### Solo chat
**File:** `ui/screens/chat/ChatViewModel.kt`
- `/scanlore [N]` — scans last N messages (default 30)
- Builds extraction prompt using character's `loreHints` as tracking criteria
- Calls LLM via `generate()` with proper OAI messages when on a chat completions backend
- `runScanlore()`, `parseScanloreResponse()`, `dismissScanlore()`, `confirmScanlore()`
- Error if character has no lore hints or no linked group

**File:** `ui/screens/chat/ChatScreen.kt` — `ScanloreConfirmDialog` shown when `uiState.showScanloreDialog`

### Group chat
**File:** `ui/screens/groups/GroupChatViewModel.kt`
- Same `/scanlore [N]` command in group chat
- Gathers `loreHints` from **all** enabled group members, labels each block by character name
- `runScanlore()`, `parseScanloreResponse()`, `dismissScanlore()`, `confirmScanlore()`

**File:** `ui/screens/groups/GroupChatScreen.kt` — `ScanloreConfirmDialog` shown when `uiState.showScanloreDialog`

---

## 6. Group chat: proper OAI chat completions support

**File:** `ui/screens/groups/GroupChatViewModel.kt`

Previously, group chat built a flat text-completion prompt and wrapped it in a single `user` message when calling a chat completions backend. DeepSeek R1 and similar models would echo the instructions back instead of responding in character.

Three new message builders added, each returning `List<PromptMessage>`:

- **`buildGroupOaiMessages`** — `system`: character card + rules + scenario + world book + style. `user`: conversation transcript + "Respond now as X."
- **`buildNarratorOaiMessages`** — `user`: character list + narration request (± hint)
- **`buildFirstMessageOaiMessages`** — `system`: character card + rules. `user`: narrator scene + cue to address user

All three generation functions (`generateForCharacter`, `generateNarratorMessage`, `generateFirstMessageFor`) now:
1. Pass the appropriate OAI messages when `config.usesChatCompletions`
2. Fetch and pass `getCurrentOaiPreset()` so temperature, max tokens, top_p, etc. are respected

---

## 7. `/sysauto` command (group chat narrator)

**File:** `ui/screens/groups/GroupChatViewModel.kt`

`/sysauto [optional hint]` — generates a narrator scene description mid-conversation.
- With hint: `"The chase ends in the parking garage"` → LLM writes that scene
- Without hint: LLM invents something based on characters and group name

Triggers `generateNarratorMessage()` which posts the result as an italic narrator bubble.

---

## 8. Group chat: card content truncation raised

**File:** `ui/screens/groups/GroupChatViewModel.kt`

All four prompt builders (text completion + three OAI builders) now send full card content:

| Field | Before | After |
|-------|--------|-------|
| description | 600 chars | 3000 chars |
| personality | 300 chars | 1200 chars |
| scenario | 300 chars | 800 chars |

Both cards (Gulara Hunter, Lilly Hunter) are ~750–850 tokens each — well within DeepSeek R1 0528's 128K context window.

---

## Character cards created

Both PNG files embed the full V2 card spec with `extensions["pockettavern_lore_hints"]`.

### Gulara Hunter (`~/Desktop/Gulara.png`)
- Class-1 vore superhero; digests villains, gains weight
- Weight gain mechanics: compulsory per villain processed, exercise recovery is real but slow (fraction of gain)
- Works with {{user}} (police detective), lives with him under state supervision
- Lore hints: weight changes, villain roster, digestion events

### Lilly Hunter (`~/Desktop/Lilly_Hunter.png`)
- Class-3 Hazardous Output Asset; toxic urine power, engineered containment diaper
- Graduated power: warning stream → corrosive → full dissolution
- Hold-too-long mechanic: toxin absorbs back → weight gain
- **Unbirth mechanic**: complete dissolution → Lilly can reabsorb everything; further weight gain
- Immune to own output; Gulara is resistant and likes the taste
- Sisters; both live with {{user}}
- Lore hints: weight changes from all sources (absorption, unbirth events), power use log
