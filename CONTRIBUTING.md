# Contributing to PocketTavern

Thanks for wanting to help. PocketTavern is a personal project that grew into something bigger, and contributions are what keep it moving forward.

---

## Ways to Contribute

- **Bug reports** — Open a GitHub Issue. Include Android version, device, steps to reproduce, and what you expected vs. what happened.
- **Feature requests** — Open an Issue with the `enhancement` label. Describe what you want and why — the use case matters more than the implementation.
- **Pull requests** — Fix a bug, improve performance, or add a feature. All PRs are reviewed and tested before merging.
- **Testing** — Try the latest release, especially on uncommon devices or Android versions, and report what breaks.

---

## Before You Open a PR

1. **Check existing issues and PRs** — your idea or fix might already be in progress.
2. **For large features**, open an Issue first to discuss the approach. It saves both of us time if the direction needs adjusting before code is written.
3. **For bug fixes**, a PR without a prior issue is fine — just describe what was broken and how you fixed it.

---

## Development Setup

### Prerequisites

- Android Studio (latest stable)
- JDK 17
- Android SDK 35 (targetSdk), minimum SDK 26 (Android 8.0)

### Clone and build

```bash
git clone https://github.com/Starkka15/PocketTavern.git
cd PocketTavern
```

Open in Android Studio and let Gradle sync. The debug build requires no signing config — just run on a device or emulator.

### Branching

- `main` — stable, release-ready
- `testing` — integration branch, PRs merge here first

**Base your branch off `testing`.** Name it something descriptive: `fix/chat-scroll-regression`, `feature/regex-lorebook-keys`, etc.

---

## Code Style

The project is Kotlin + Jetpack Compose. Follow the patterns already in the codebase:

- ViewModels hold state via `StateFlow`, screens observe with `collectAsStateWithLifecycle`
- Data flows through `LocalRepository` — don't reach into storage classes directly from UI
- Use `Result<T>` for fallible operations
- No comments explaining what code does — names should do that. Comments only for non-obvious *why*
- No unused imports, no dead code

If you're unsure about a pattern, look at how a similar screen is implemented and match it.

---

## Pull Request Guidelines

- **One thing per PR.** A bug fix and an unrelated refactor should be two PRs.
- **Write a clear description.** What changed, why, and how to test it.
- **Test on a real device** if you can — emulators miss edge cases around file I/O, permissions, and IME behavior.
- **Don't bump the version** — that's handled at release time.

All PRs are tested before merging. If something needs changes, feedback will be specific.

---

## What's Most Needed

These are areas where contributions are especially welcome:

- **Performance** — chat scroll, image loading, startup time
- **Backend compatibility** — if a new LLM API or sampler format isn't supported, PRs to add it are straightforward
- **Accessibility** — content descriptions, font scaling, contrast
- **Bug fixes from open Issues** — pick any open Issue tagged `bug` and go

---

## License

By contributing, you agree that your code will be released under the same [MIT + Commons Clause license](LICENSE) as the rest of the project. This means PocketTavern stays free and non-commercial — contributions don't change that.
