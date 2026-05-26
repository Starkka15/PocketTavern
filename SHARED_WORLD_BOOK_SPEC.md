# Shared World Book — Spec

## Goal

A persistent, ever-growing shared context attached to a Group that flows
bidirectionally:
- **Group → Solo**: when {{user}} opens a solo chat with any member of the
  group, the group's world book is injected so the character knows what
  happened in group sessions and in other members' solo sessions.
- **Solo → Group**: when {{user}} chats solo with one member, lore entries
  added there are saved to the group world book and visible next time any
  character is chatted with (solo or group).

Characters can naturally reference events from sessions they "weren't in"
— Lilly can complain about Gulara going on a solo mission without her,
Gulara can mention something Lilly told her off-screen, etc.

---

## Data Model

### `Group` (domain/model/Group.kt)
Add one field:
```kotlin
val worldBook: String = ""
```
Stored in `groups.json` alongside existing fields. Serialized via
kotlinx.serialization — default empty string means zero migration needed
for existing groups.

---

## Storage Layer

### `GroupStorage` additions

```kotlin
/** Find all groups that contain this character fileName as a member. */
suspend fun getGroupsForCharacter(characterFileName: String): List<Group>

/** Append a timestamped lore entry to a group's world book and persist. */
suspend fun appendWorldBookEntry(groupId: String, entry: String)

/** Overwrite the world book entirely (for the editor UI). */
suspend fun saveWorldBook(groupId: String, content: String)
```

`appendWorldBookEntry` format:
```
[2026-05-25] Entry text here.
```
Entries separated by newlines. Prepended with ISO date so the LLM has
temporal context ("this happened before/after that").

---

## ChatViewModel (solo chat)

### New dependency
```kotlin
private val groupStorage: GroupStorage   // injected via Hilt
```

### New state
```kotlin
@Volatile private var _currentGroupId: String? = null
@Volatile private var _currentWorldBook: String = ""
```

### On character load
After resolving the character, query:
```kotlin
val groups = groupStorage.getGroupsForCharacter(character.avatar ?: "")
val group = groups.firstOrNull()
_currentGroupId = group?.id
_currentWorldBook = group?.worldBook ?: ""
```
If character is in multiple groups, use the most-recently-modified one
(fall back to first for now).

### Prompt injection
Pass `_currentWorldBook` to `PromptBuilder` alongside `_currentMemoryBlock`.
`PromptBuilder` already has a `memoryBlock` param — add a second param:
```kotlin
private val worldBook: String = ""
```
Injected as `[Shared World Book]\n$worldBook` at the same position as
memory (before char_description in OAI mode; in story string for text
completion). Only injected when non-blank.

### `/addlore` slash command
```
/addlore <text>
```
- Appends `[date] <text>` to the group's world book via
  `groupStorage.appendWorldBookEntry(_currentGroupId, text)`
- Updates `_currentWorldBook` in memory
- Inserts a narrator message: `* [World Book] Added: <text> *`
- No-ops silently if character isn't in a group

### `ChatUiState` additions
```kotlin
val groupName: String? = null          // name of linked group, for UI badge
val hasWorldBook: Boolean = false      // drives badge visibility
```

---

## GroupChatViewModel

### World book injection in `buildGroupPrompt`
After the `[scenario]` block:
```kotlin
if (group.worldBook.isNotBlank()) {
    append("[Shared World Book]\n${group.worldBook}\n\n")
}
```

### `/addlore` slash command
Same as solo — intercept in `sendMessage` before normal dispatch:
```kotlin
if (rawText.startsWith("/addlore ")) {
    val entry = rawText.removePrefix("/addlore ").trim()
    if (entry.isNotBlank()) {
        groupStorage.appendWorldBookEntry(group.id, entry)
        // reload group to pick up new worldBook
        val updated = groupStorage.loadGroups().first { it.id == group.id }
        _uiState.update { it.copy(group = updated) }
        // insert narrator message
        appendNarratorMessage("* [World Book] Added: $entry *")
    }
    _uiState.update { it.copy(inputText = "") }
    return
}
```

### World book editor UI
Reuse the existing `GroupPromptEditorDialog` pattern. Add a second menu
item "World Book" next to the existing "Group Prompt" menu item. Opens a
new `WorldBookEditorDialog` (identical structure, different title/hint).

New ViewModel functions:
```kotlin
fun showWorldBookEditor()
fun dismissWorldBookEditor()
fun updateWorldBookEditorText(text: String)
fun saveWorldBook()        // calls groupStorage.saveWorldBook(...)
```

New `GroupChatUiState` fields:
```kotlin
val showWorldBookEditor: Boolean = false
val worldBookEditorText: String = ""
```

---

## UI Changes

### Solo ChatScreen
- Small chip/badge below the character name in the top bar when a group
  world book is linked: `[Group: Sisters]` or similar.
- Tapping the chip opens a read-only world book viewer (AlertDialog with
  scrollable text). Not editable from solo — use `/addlore` to append.

### GroupChatScreen
- "World Book" menu item in the `⋮` overflow menu (alongside existing
  "Group Prompt").
- Opens `WorldBookEditorDialog` — full edit + save.
- Chip in the top bar showing entry count: `World Book (3)`.

---

## Injection Position in PromptBuilder

**OAI (chat completions):**
```
system: [combined system prompt]
system: [world info before_char]       ← existing
system: [Shared World Book]            ← NEW, after world info
system: [Memory]                       ← existing
user/assistant: [message examples]
...history...
user: [new message]
```

**Text completion:**
```
[story string]
[Memory]
[Shared World Book]                    ← NEW, after memory
[examples]
...history...
User: [new message]
```

---

## `/addlore` in both contexts — summary

| Context     | Command      | Effect                                              |
|-------------|--------------|-----------------------------------------------------|
| Solo chat   | `/addlore X` | Appends to linked group world book, narrator note   |
| Group chat  | `/addlore X` | Appends to this group's world book, narrator note   |
| No group    | `/addlore X` | Silent no-op (or toast: "No group linked")          |

---

## Files Changed

| File | Change |
|------|--------|
| `domain/model/Group.kt` | Add `worldBook: String = ""` |
| `data/local/GroupStorage.kt` | Add 3 methods |
| `domain/prompt/PromptBuilder.kt` | Add `worldBook` param, inject it |
| `ui/screens/chat/ChatViewModel.kt` | GroupStorage dep, world book load, `/addlore`, UI state |
| `ui/screens/chat/ChatScreen.kt` | Group badge chip, world book viewer dialog |
| `ui/screens/groups/GroupChatViewModel.kt` | World book in prompt, `/addlore`, editor state/fns |
| `ui/screens/groups/GroupChatScreen.kt` | World book menu item + editor dialog |

---

## Out of Scope (future)

- Multiple group membership (character in 2+ groups) — currently takes first
- Auto-summarization of world book when it grows too long
- Per-entry deletion UI
- World book search
