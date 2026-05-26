# Scanlore — Spec

## Goal

`/scanlore` — on-demand command that sends recent chat messages to the LLM,
extracts lore-worthy events based on per-character tracking hints, and
presents a confirm/edit dialog before appending anything to the group's
shared world book.

Characters define what to track in their card via a "Lore Tracking" field.
This is PocketTavern-specific and lives in the card's `extensions` map
(`pockettavern_lore_hints`) so other tools ignore it silently.

---

## Character Card Changes

### `CharacterCardData` (util/PngCharacterCard.kt)
No structural change — lore hints stored in the existing `extensions` map:
```
extensions["pockettavern_lore_hints"] = JsonPrimitive("...")
```

### `Character` (domain/model/Character.kt)
Add one field:
```kotlin
val loreHints: String = ""
```
Plain text, user-written. Example for Gulara:
```
Track: weight and height changes after digesting a villain (note villain name
and estimated gain), villain captures (who was swallowed and when), prisoners
forgotten or accidentally digested, solo missions without Lilly, clothing or
measurement changes, exercise attempts.
```

### `CharacterEntity` (data/local/db/entity/CharacterEntity.kt)
Add column:
```kotlin
val loreHints: String = ""
```

### DB Migration (data/local/db/AppDatabase.kt)
Version 5 → 6:
```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE characters ADD COLUMN loreHints TEXT NOT NULL DEFAULT ''")
    }
}
```

### `CharacterStorage` converters
- `cardDataToEntity`: read `extensions["pockettavern_lore_hints"]` → `loreHints`
- `characterToEntity`: write `loreHints`
- `entityToCharacter`: map `loreHints`
- `readCharacterFile`: read from extensions
- `saveCharacter`: write to extensions via `CharacterCardData.extensions`

### `CreateCharacterViewModel` + `CreateCharacterScreen`
- Add `loreHints: String = ""` to `CreateCharacterUiState`
- Add `updateLoreHints(text)` function
- Add "Lore Tracking" `CharacterTextField` in the screen, after Creator Notes
- Load and save in edit mode alongside other fields

---

## `/scanlore` Command

Available in both solo chat and group chat. Uses the same LLM connection
as normal generation.

### Invocation
```
/scanlore          — scan last 30 messages
/scanlore 50       — scan last N messages
```

### Solo flow (ChatViewModel)
1. Parse message count (default 30)
2. Take last N messages from `_uiState.value.messages`
3. Collect `loreHints` from current character
4. If no group linked: show error narrator note, return
5. Build extraction prompt (see below)
6. Send to LLM as a hidden generation (no user bubble, no AI bubble)
7. Stream response into a `scanloreResult` state field
8. On complete, show `ScanloreConfirmDialog`

### Group flow (GroupChatViewModel)
Same, but:
- Gather `loreHints` from ALL enabled group members (concatenated, labelled by character)
- Uses group's LLM config

### Extraction Prompt
```
[INST] You are a lore extraction assistant. Read the following conversation
excerpt and extract notable events worth recording in a shared world log.

TRACKING CRITERIA (provided by the characters' cards):
{combined lore hints from all relevant characters}

CONVERSATION:
{last N messages formatted as "Name: content"}

OUTPUT FORMAT:
Return ONLY a numbered list of concise lore entries, one per line.
Each entry should be a single sentence in past tense, factual, no dialogue.
Only include events that actually occurred in the conversation.
If nothing notable happened, return "Nothing notable to record."

Do not include any preamble or explanation. Just the numbered list. [/INST]
```

### `ChatUiState` additions
```kotlin
val showScanloreDialog: Boolean = false
val scanloreEntries: List<String> = emptyList()   // parsed from LLM response
val scanloreLoading: Boolean = false
val scanloreError: String? = null
```

### `GroupChatUiState` additions
Same four fields.

---

## `ScanloreConfirmDialog` (shared composable)

Location: `ui/components/ScanloreConfirmDialog.kt`

```
┌─────────────────────────────────────┐
│ Scanlore Results                    │
│ ─────────────────────────────────── │
│ ☑  Gulara processed the Vashenko   │
│    brothers. +30 lbs, +1" height.  │
│                                     │
│ ☑  Lilly deployed warning stream   │
│    against an armed suspect.        │
│                                     │
│ ☐  {{user}} and Gulara discussed   │  ← user unchecked this one
│    the thermostat again.            │
│                                     │
│  [Cancel]              [Add to Book]│
└─────────────────────────────────────┘
```

- Each entry is a checkbox row — user can deselect any before confirming
- Entries are editable inline (tap to edit text)
- "Add to Book" appends all checked entries via `appendWorldBookEntry`
- Shows narrator note for each appended entry (or one combined note)

---

## LLM Call for Scanlore

Uses `llmRepository.generate()` / chat completions same as normal generation.
Sent as a **system-only prompt** (no character persona, no history injected —
just the extraction prompt and the raw message dump).

No streaming display needed — just accumulate the full response, then parse.
Parse by splitting on newlines, stripping leading numbers/punctuation
(`1. `, `- `, etc.), filtering blank lines and the "nothing notable" case.

---

## Files Changed

| File | Change |
|------|--------|
| `domain/model/Character.kt` | Add `loreHints: String = ""` |
| `data/local/db/entity/CharacterEntity.kt` | Add `loreHints` column |
| `data/local/db/AppDatabase.kt` | Version 5→6, MIGRATION_5_6 |
| `data/local/CharacterStorage.kt` | Read/write loreHints via extensions |
| `util/PngCharacterCard.kt` | No change — extensions map already exists |
| `ui/screens/createcharacter/CreateCharacterViewModel.kt` | Add loreHints field + update fn |
| `ui/screens/createcharacter/CreateCharacterScreen.kt` | Add Lore Tracking text field |
| `ui/components/ScanloreConfirmDialog.kt` | New composable |
| `ui/screens/chat/ChatViewModel.kt` | `/scanlore` handler, scanlore state |
| `ui/screens/chat/ChatScreen.kt` | Show ScanloreConfirmDialog |
| `ui/screens/groups/GroupChatViewModel.kt` | `/scanlore` handler, scanlore state |
| `ui/screens/groups/GroupChatScreen.kt` | Show ScanloreConfirmDialog |

---

## Out of Scope

- Auto-scanlore on a timer or message count
- Per-entry source attribution (which session it came from)
- Deduplication of world book entries
- Scanlore without a linked group (solo-only characters can't append anywhere)
