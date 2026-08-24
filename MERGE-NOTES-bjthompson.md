# Merge of bjthompson805/PocketTavern — 2026-08-24

## Special thanks

Huge thanks to **Brandon Thompson ([@bjthompson805](https://github.com/bjthompson805))**
for this work. He built on-device SDXL image generation end to end — vendoring MNN,
writing the JNI bridge and Kotlin engine layer, adding SDXL support to MNN's diffusion
engine itself (3 commits in his fork), and verifying the whole thing on real hardware
rather than assuming it worked. Along the way he found and fixed several real bugs in
*our* code that had nothing to do with his feature: engines never evicting each other
(5.4 GB -> 691 MB), the on-device model list never repopulating, and extension image-gen
errors being silently swallowed.

All of his commits are preserved here with him as the git author.

Branch: `bjthompson-sdxl-merge`, based on `testing` (`e76485f`).

## Taken (cherry-picked clean, no conflicts)

| Commit | What |
|---|---|
| `fd62477` | On-device SDXL image generation via MNN (his Phase 2+3) |
| `073bc96` | OnDeviceMemoryManager engine eviction + on-device model list refresh fix |
| `e4abda5` | Surface extension image-gen progress/errors in chat |
| `98a46d3` | Tap-to-zoom viewer for avatars and generated images |
| `13e40a9` | (ours) submodule HTTPS fix + pin bump |

His verification, on a Pixel 10 Pro XL — **not on our hardware**:
- real SDXL images through the app UI, ~18 min for 20-step/1024x1024 vs ~16 min
  on MNN's own CLI reference build (isolates his JNI layer from the engine)
- Native Heap PSS 5.4 GB -> 691 MB switching SDXL -> LLM
- no automated tests anywhere

## Deliberately SKIPPED: `a4829d2` (Phase 4 + persona fixes)

Conflicts with our multi-persona rework (`e76485f`), and **all four of his persona
bug fixes are already fixed on `testing` by different means**:

| His fix | Our status |
|---|---|
| `generateImage()` bypassed `ImageGenBackendType`, always hit SD WebUI | already fixed — goes through `imageGenRepository` |
| generation params hardcoded, ignoring saved settings | already fixed — built from `cfg.*` |
| `savePersonaEdit()` rebuilt the persona, dropping `avatarPath` | already fixed — `roster[idx].copy(...)` |
| persona role editable but never persisted | already fixed — `role = state.editRole.value` |

His `showEditDialog` avatar-preload is also unnecessary for us: he needed it because
his save always rewrote avatar bytes; ours never touches them.

Phase 4 itself (`SdxlModelManager` + `SdxlModelSection` download UI) was NOT taken,
because it is entangled with the persona changes in the same commit. **SDXL model
paths must therefore still be set by hand via the raw settings text field.**

## TODO — hand-ports still worth doing

1. **Avatar picker in the Edit persona dialog.** `PersonaScreen.kt` lines 438-607
   (`EditPersonaDialog`) contain no image/picker/generate UI at all — only
   `CreatePersonaDialog` has it. So a persona avatar can be set at creation and
   never changed. His approach — extract a shared `AvatarPickerSection` used by both
   dialogs — is right; port it against our roster/PersonaStorage model. ~30-60 lines.
2. **`PersonaViewModel.kt:428` calls `forgeRepository.interrupt()` directly.**
   Generation correctly routes through `imageGenRepository`, but Cancel still goes to
   Forge, so on any non-WebUI backend Cancel silently does nothing. His
   `ImageGenCapabilities.supportsCancel` handles this (grey the button + caption)
   — MNN's diffusion engine genuinely cannot be interrupted once started.
3. **Re-do Phase 4 SDXL download UI** on top of our persona code, or leave the raw
   path field.

## Risks / follow-ups

- **Submodule points at a personal fork** (`bjthompson805/MNN`). If it is renamed or
  deleted, builds break. Fork under `Starkka15` or get his 3 diffusion commits
  upstreamed to `alibaba/MNN`.
- **APK size / build time** from the MNN native build — not yet measured.
- **Practicality**: ~18 min/image on a Pixel 10 Pro XL. On a Moto G Power 2025 this
  is not a usable feature. Fine for flagship users.
- Pinned to 1024x1024 (SDXL UNet is `shapeMutable=false`), and cancel is impossible.

## Licensing — clean

`alibaba/MNN` and his fork are both **Apache-2.0**; PocketTavern is MIT + No-Commercial.
Apache-2.0 is permissive with no copyleft, so it composes fine, and as a *submodule*
MNN stays under its own licence rather than being absorbed. Apache-2.0 §4(b)
"state changes" is satisfied by the fork's git history; §4(d) NOTICE is moot —
upstream MNN ships no NOTICE file. Consistent with our policy that vendored deps keep
their upstream licence.
