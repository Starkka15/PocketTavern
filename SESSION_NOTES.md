# Session Notes - 2026-02-07

## Group Chat Feature (Branch: feature/group-chats)

### What Was Implemented

1. **Group List Screen** (`GroupsScreen.kt`)
   - Lists all groups from SillyTavern
   - Shows stacked member avatars
   - Create group dialog with character selection
   - Requires minimum 2 members

2. **Group Chat Screen** (`GroupChatScreen.kt`)
   - Message bubbles with character avatars and names
   - User messages on right, AI messages on left
   - Auto-scroll to latest message
   - Input bar with send button

3. **ViewModels**
   - `GroupsViewModel.kt` - handles group list and creation
   - `GroupChatViewModel.kt` - handles chat loading/saving

4. **Navigation**
   - Added `Route.Groups` and `Route.GroupChat(groupId)` routes
   - Groups accessible from main screen

5. **Domain Models**
   - `GroupChatMessage` in `ChatMessage.kt` - message data class

6. **Repository Methods** (in `SillyTavernRepository.kt`)
   - `getAllGroups()` - fetch all groups
   - `getGroupChat(chatId)` - load messages (fixed: uses chatId, not groupId)
   - `saveGroupChat(chatId, messages)` - save messages
   - `createGroup(name, members)` - create new group
   - `editGroup(groupId, chatId, chats)` - update group (NEW)
   - `buildGroupMemberAvatarUrl(memberAvatar)` - avatar URLs

7. **API Endpoints** (in `SillyTavernApi.kt`)
   - `POST api/groups/all` - get all groups
   - `POST api/groups/create` - create group
   - `POST api/groups/edit` - edit group (NEW)
   - `POST api/chats/group/get` - get chat messages
   - `POST api/chats/group/save` - save chat messages

8. **DTOs** (in `SillyTavernDtos.kt`)
   - `GroupDto` - group data
   - `CreateGroupRequest` - create group request
   - `EditGroupRequest` - edit group request (NEW)
   - `GetGroupChatRequest` - get chat request
   - `SaveGroupChatRequest` - save chat request
   - `GroupChatMessageDto` - message DTO

### Bug Fixed

**Issue:** "File not found: data\default-user\group chats\{groupId}.jsonl"

**Root Cause:** API was being called with `groupId` instead of `chatId`. SillyTavern stores group chats in files named by `chatId`, not `groupId`. New groups don't have a `chatId` until the first message.

**Fix:**
- Track `currentChatId` separately in ViewModel
- When loading: if `chatId` is null, show empty messages (new group)
- When sending first message: generate new `chatId` (timestamp), update group via `editGroup()`, then save chat

### Not Yet Implemented

- AI response generation for groups (requires generate endpoint + character selection logic)
- Group settings/editing
- Delete group
- Multiple chat sessions per group

---

## CardVault Windows Path Fix

### Issue
Windows users with multiple directories couldn't use CARD_DIRS properly because `:` is used both as path separator AND in Windows drive letters (C:, D:, etc.).

### Fix (pushed to github.com/Starkka15/cardvault)
- Added `parse_path_list()` helper function in `server.py`
- Windows users should use `;` (semicolon) as separator
- Smart detection of drive letters when `:` is used
- Updated documentation in README.md and server.py header

### Example
```
# Windows (use semicolon)
CARD_DIRS=C:/Characters/folder1;D:/Characters/folder2

# Linux/macOS (use colon)
CARD_DIRS=/data/cards:/mnt/more-cards
```

---

## Files Modified (PocketTavern)

### New Files
- `app/src/main/kotlin/com/stark/sillytavern/ui/screens/groups/GroupsScreen.kt`
- `app/src/main/kotlin/com/stark/sillytavern/ui/screens/groups/GroupsViewModel.kt`
- `app/src/main/kotlin/com/stark/sillytavern/ui/screens/groups/GroupChatScreen.kt`
- `app/src/main/kotlin/com/stark/sillytavern/ui/screens/groups/GroupChatViewModel.kt`

### Modified Files
- `app/src/main/kotlin/com/stark/sillytavern/ui/navigation/Routes.kt` - added group routes
- `app/src/main/kotlin/com/stark/sillytavern/ui/navigation/NavGraph.kt` - added group navigation
- `app/src/main/kotlin/com/stark/sillytavern/ui/screens/main/MainScreen.kt` - added Groups card
- `app/src/main/kotlin/com/stark/sillytavern/domain/model/ChatMessage.kt` - added GroupChatMessage
- `app/src/main/kotlin/com/stark/sillytavern/domain/model/Group.kt` - group model
- `app/src/main/kotlin/com/stark/sillytavern/data/repository/SillyTavernRepository.kt` - group methods
- `app/src/main/kotlin/com/stark/sillytavern/data/remote/api/SillyTavernApi.kt` - group endpoints
- `app/src/main/kotlin/com/stark/sillytavern/data/remote/dto/st/SillyTavernDtos.kt` - group DTOs

---

## Next Steps

1. Test group chat more thoroughly
2. Consider adding AI response generation for groups
3. Merge feature/group-chats branch when ready
4. Consider group management features (edit, delete, add/remove members)
