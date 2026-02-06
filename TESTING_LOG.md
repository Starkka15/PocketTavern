# Testing Log - SillyTavern Android

## 2026-01-27

### Test: Simple text chat with chub.ai

**Android:**
- Status: Computer crashed during testing
- Notes: Test interrupted, returning to Android testing

**webOS:**
- Status: Tested successfully (no crash)

---

### Bug Found: Message duplication in chat UI

**Symptom:** Messages displayed twice (e.g., "test" shows as "testtest")

**Root Cause:** Bug in `ChatBubble.kt` `formatMessage()` function.
When no bold formatting exists, text was appended twice due to overlapping conditions.

**File:** `app/src/main/kotlin/com/stark/sillytavern/ui/components/ChatBubble.kt`

**Fix:** Removed redundant `if (lastIndex == 0)` block that duplicated text.

**Note:** Data was correct (verified on touchpad/web) - issue was display-only on Android.

---

### Bug Found: Cannot delete characters

**Symptom:** No way to delete characters from the UI

**Root Cause:** `CharacterListItem` only had `onClick` - no gesture to trigger `showDeleteConfirmation()`

**Fix:** Added `onLongClick` using `combinedClickable` to trigger delete dialog

**Files Modified:**
- `CharacterListItem.kt` - Added `onLongClick` parameter
- `MainScreen.kt` - Pass delete handler to list items

---

### Bug Found: Character creation fails with "Input must be string"

**Symptom:** Server error when creating character: `sanitize-filename` fails

**Root Cause:** Android was sending `avatar: ""` (empty string) which the server tried to sanitize as a filename. webOS doesn't send the avatar field at all when empty.

**Fix:**
- Removed `avatar` field entirely from `CreateCharacterRequest`
- Matched webOS request format exactly (no avatar in create)
- Added `explicitNulls = false` to JSON config

**Solution:** Implemented PNG character card embedding directly in Android (like webOS proxy):
- Create chara_card_v2 format PNG with tEXt chunk containing base64-encoded character JSON
- Send to `/api/characters/import` as multipart upload

**Files Created:**
- `util/PngCharacterCard.kt` - PNG embedding utility (tEXt chunk with "chara" keyword)

**Files Modified:**
- `SillyTavernApi.kt` - Added multipart import endpoint
- `SillyTavernRepository.kt` - Creates PNG card when avatar provided, uses import endpoint
- `SillyTavernDtos.kt` - Updated CreateCharacterRequest fields
- `NetworkModule.kt` - Don't serialize nulls
- `CreateCharacterViewModel.kt` - Passes avatar to repository

---

*Currently: Resuming Android testing*
