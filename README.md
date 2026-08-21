<p align="center">
  <img src="logoide.png" alt="ViperCode logo" width="160" height="160">
</p>

<h1 align="center">ViperCode</h1>

<p align="center"><em>The class of perfection</em></p>

<p align="center">
  <a href="https://github.com/hieulouisdev/ViperCode/releases"><img alt="Latest release" src="https://img.shields.io/github/v/release/hieulouisdev/ViperCode?style=flat-square"></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square">
  <img alt="Min SDK" src="https://img.shields.io/badge/min%20SDK-25%20(Android%207.1.1)-blue?style=flat-square">
  <img alt="Target SDK" src="https://img.shields.io/badge/target%20SDK-35%20(Android%2015)-blue?style=flat-square">
  <img alt="License" src="https://img.shields.io/github/license/hieulouisdev/ViperCode?style=flat-square">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square">
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-2024.12.01-4285F4?style=flat-square">
</p>

---

## Overview

**ViperCode** is a modern, performant code editor for Android, built for
developers who demand the class of perfection in every keystroke. It is
designed from the ground up with the Android Storage Access Framework,
Material 3 theming, and a fully Compose-native UI — no legacy view
system, no compromises on startup latency or rendering performance.

ViperCode v0.0.3 ships the editing core plus a major upgrade:
a live HTML/CSS/JS preview screen, a full Find & Replace with regex
and case toggle, syntax hints (bracket matching + unbalanced-bracket
underlines), a virtualized file tree, and a long list of bug fixes
that affected v0.0.2's editor on long editing sessions.

## Features (v0.0.3)

- **Multi-language syntax highlighting** — built-in tokeniser for 30+
  languages including Kotlin, Java, Python, JavaScript/TypeScript, Go,
  Rust, C/C++, C#, Swift, Dart, Ruby, PHP, SQL, Scala, Groovy, Lua,
  YAML, TOML, Markdown, XML, JSON, HTML, CSS, and more.
- **Multi-tab editing** — open multiple files at once; dirty-state
  tracking prevents accidental data loss. Caret position is preserved
  across tab switches and restored on next launch.
- **Live HTML/CSS/JS preview** — open any HTML file, tap the play icon
  in the editor's top bar, and a WebView renders the page with full
  JavaScript enabled. Auto-refreshes 600 ms after you stop typing. CSS
  and JS from sibling tabs are inlined automatically so a small
  multi-file project works out of the box.
- **Find & Replace (upgraded)** — regex toggle, case-sensitivity
  toggle, find-next / find-prev navigation, live match counter, and
  per-match replace (not just replace-all).
- **Syntax hints** — when the caret is adjacent to a bracket pair,
  both brackets get a subtle background highlight. Unbalanced open
  brackets get a red underline so you can spot missing closes as you
  type. `@` followed by a non-identifier is no longer
  mis-highlighted as an annotation.
- **Auto-save** — dirty files are saved automatically after a short
  idle delay (configurable, 500 ms–5 s). Back button now flushes
  auto-save so nothing is lost if you navigate away within the delay
  window.
- **Offline-first storage** — ViperCode ships with a default local
  workspace under the app's private external storage so it works the
  moment you install it, with no permission prompts and no internet.
  The Storage Access Framework picker remains available for opening
  any folder on the device.
- **Storage Access Framework integration** — open any folder on the
  device (internal or external storage, Google Drive, Nextcloud, etc.)
  via the system folder picker. Permissions are persisted across
  launches (READ + WRITE) so your workspace is exactly where you left
  it and saves never fail with `SecurityException`.
- **Material 3 dynamic theming** — automatically picks colours from
  the user's wallpaper on Android 12+; falls back to the ViperCode
  brand palette on older devices.
- **Optimised for Android 7.1.1+ (API 25)** — runs on over 99% of
  active Android devices worldwide.
- **Light / Dark / System theme modes** with full Material 3 component
  theming.
- **Editor preferences** — adjustable font size, tab size, font
  family, word wrap, line numbers, auto-indent.
- **External file open** — tap any source file in your file manager and
  it opens directly in the editor. Subsequent ACTION_VIEW intents now
  navigate correctly thanks to the fixed `pendingExternalUri`
  StateFlow + `launchMode="singleTop"`.
- **Robust auto-indent** — Tab expands to spaces; Enter copies the
  previous line's indentation and adds an extra indent after `{`,
  `(`, `[`, `:` and `=>`. Extra indent now respects the user's
  `tabSize` setting instead of being hardcoded to 4 spaces.

## Fixes & changes since v0.0.2

### Critical bugs

- **Highlight overlay drift (showstopper)**: the v0.0.2 editor rendered
  syntax highlighting via a `decorationBox` overlay `Text` composable
  that had its own `verticalScroll` state — never synced with the
  `BasicTextField`'s internal scroll. Highlight colours drifted out of
  alignment within seconds of typing. v0.0.3 replaces the overlay with
  a `VisualTransformation` applied to the field itself (single layout
  pass, always aligned with the caret).
- **Line numbers don't scroll with editor**: `LineNumberGutter` had
  its own `LazyColumn` + `rememberLazyListState` — never received
  scroll updates from the editor. v0.0.3 shares the same `ScrollState`
  between the gutter and the editor so they scroll in lock-step.
- **Caret jumps to 0 on tab switch**: `remember(tab.id)` re-initialised
  the `TextFieldValue` to `TextRange(0)` every time the user switched
  tab. v0.0.3 captures the caret (line + column) on every edit and
  restores it via the new `EditorTab.cursorLine/cursorColumn` fields.
- **Subsequent ACTION_VIEW intents don't navigate**:
  `MainActivity.pendingExternalUri` was a plain `@Volatile var` in the
  companion — Compose had no way to observe it. v0.0.3 converts it to
  a `MutableStateFlow` collected via `collectAsState`. The manifest
  also now sets `launchMode="singleTop"` so `onNewIntent` fires for
  new view intents instead of spawning a fresh Activity.
- **`SettingsRepository.now()` blocks main thread**: `MainActivity.setContent`
  and `HomeScreen.LaunchedEffect` both called the blocking `now()`.
  v0.0.3 adds a non-blocking `Pref.first()` suspend variant and uses
  the `default` value for `collectAsState(initial = ...)` so the main
  thread is never blocked.
- **HomeScreen only takes READ permission**: v0.0.2's restore path
  called `takePersistableUriPermission(uri, FLAG_GRANT_READ)` — saves
  silently failed with `SecurityException` after relaunch. v0.0.3
  takes `READ or WRITE` (with a READ-only fallback for restrictive
  providers).
- **`FileRepository.openExternalFile` doesn't persist permission**:
  for `content://` URIs from ACTION_VIEW the granted permission was
  transient and tied to the Activity. v0.0.3 calls
  `takePersistableUriPermission` (wrapped in `runCatching` for
  providers that reject persist).
- **Back button loses unsaved content**: with `autoSaveEnabled = true`,
  the v0.0.2 back button navigated away immediately, skipping the
  unsaved-changes dialog AND skipping the immediate save — content
  typed < 1.5 s before back was lost. v0.0.3 always flushes
  `saveTabIfDirty` before navigating back.
- **Splash screen bypassed**: `ViperNavHost` had an unconditional
  `LaunchedEffect(Unit) { navigate(HOME) }` that fired on first
  composition, so `SplashScreen`'s 1.1 s delay never got to run.
  v0.0.3 removed the auto-navigate — `SplashScreen.onContinue` is now
  the only trigger.
- **`SettingsRepository` enum decode crash**: stored enum names that
  no longer matched any variant (e.g. after a rename) threw
  `NoSuchElementException`. v0.0.3 uses `firstOrNull ?: default`.
- **`FileUtils` swallows `CancellationException`**: `runCatching`
  catches every `Throwable`, including `CancellationException`, which
  broke structured concurrency (a cancelled IO read surfaced as a
  generic `IOException` to the parent coroutine). v0.0.3 explicitly
  rethrows `CancellationException`.
- **`SyntaxHighlighter` `@`-annotation guard**: the v0.0.2 guard
  `end > i + 1` was always true because `scanIdentifier(...) + 1`
  was at least `i + 1`. v0.0.3 requires `end > i + 2` so `@` followed
  by a non-identifier (e.g. `@!foo`) is no longer mis-highlighted.
- **Auto-indent hardcoded 4 spaces**: `computeExtraIndent` ignored
  the user's `tabSize` setting. v0.0.3 takes an `indentUnit` parameter
  so the extra indent after `{`/`(`/`[`/`:`/`=>` matches the user's
  tab width.
- **`FileUtils.uniqueName` infinite loop**: the v0.0.2 loop was
  `while (true)` — a buggy content provider could cause it to spin
  forever. v0.0.3 caps at 1000 iterations and falls back to a UUID
  suffix.

### UX

- **FileExplorer virtualization**: v0.0.2 used eager recursion inside
  `AnimatedVisibility { Column { sub.forEach { FileRow(...) } } }` —
  thousands of composables for large workspaces. v0.0.3 flattens the
  tree into a single `List<FlatRow>` and uses a `LazyColumn` with
  virtualization.
- **TabBar max width**: `TabChip` now caps at 180 dp so long file
  names no longer push other chips off-screen.
- **About screen build type**: hardcoded `"(release)"` replaced with
  `BuildConfig.BUILD_TYPE` so the About screen shows the real variant.
- **Manifest cleanup**: removed unused `MANAGE_EXTERNAL_STORAGE` and
  `POST_NOTIFICATIONS` permissions. Added `INTERNET` + `VIBRATE`
  for the live preview WebView.

### New features

- **Live HTML/CSS/JS preview** (`PreviewScreen`): WebView-backed
  preview with full JavaScript support. Auto-refresh 600 ms after
  typing stops. Sibling CSS/JS tabs auto-inlined.
- **Find & Replace upgrade**: regex toggle, case-sensitivity toggle,
  find-next / find-prev navigation, live match counter, per-match
  replace.
- **Syntax hints** (`SyntaxHints.kt`): bracket pair highlighting,
  unbalanced bracket underlines, and a safer `@`-annotation guard.
- **Cursor persistence**: `EditorTab.cursorLine/cursorColumn` are now
  actually written and read so the caret survives tab switches.
- **GitHub Action** (`.github/workflows/build-release-apk.yml`):
  auto-builds a signed release APK on every new release publish and
  attaches it as a release asset.

## Tech stack

ViperCode is built with the latest stable versions of every dependency.
The stack is deliberately conservative — no experimental frameworks,
no preview-quality libraries — to keep the upgrade path smooth.

| Layer              | Technology                                     | Version        |
|--------------------|------------------------------------------------|----------------|
| Language           | Kotlin                                         | 2.0.21         |
| UI toolkit         | Jetpack Compose (BOM)                          | 2024.12.01     |
| Design system      | Material 3 (Material Components)              | 1.12.0         |
| Build system       | Android Gradle Plugin                          | 8.7.3          |
| Build tool         | Gradle (Kotlin DSL)                            | 8.11.1         |
| Navigation         | AndroidX Navigation Compose                    | 2.8.5          |
| Persistence        | AndroidX DataStore Preferences                 | 1.1.1          |
| File access        | AndroidX DocumentFile + Storage Access Framework | built-in     |
| Splash screen      | AndroidX Core SplashScreen                     | 1.0.1          |
| Min SDK            | Android 7.1.1 (API 25)                         |                |
| Target / Compile   | Android 15 (API 35)                            |                |

## Project structure

```
ViperCode/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/vipercode/ide/
│   │   │   ├── ViperCodeApp.kt           # Application entry
│   │   │   ├── MainActivity.kt           # Single Activity host
│   │   │   ├── ui/theme/                 # Material 3 theme, colour, typography
│   │   │   ├── ui/navigation/            # Compose Navigation graph
│   │   │   ├── ui/screens/               # Splash, Home, Editor, Settings, About
│   │   │   ├── ui/components/            # CodeEditor, FileExplorer, TabBar, SyntaxHighlighter
│   │   │   ├── data/model/               # FileNode, EditorTab
│   │   │   ├── data/repo/                # FileRepository
│   │   │   ├── data/prefs/               # SettingsRepository (DataStore)
│   │   │   └── util/                     # LanguageDetector, FileUtils
│   │   └── res/                         # Launcher icons, themes, strings, XML config
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   ├── libs.versions.toml                # Centralised version catalog
│   └── wrapper/                          # Gradle wrapper
├── logoide.png                           # Original logo source
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Build

### Prerequisites

- Android Studio Iguana or later (or just the Android SDK command-line tools)
- JDK 17+
- Android SDK with platform `android-35` and `build-tools;35.0.0`

### Build a debug APK

```bash
git clone https://github.com/hieulouisdev/ViperCode.git
cd ViperCode
./gradlew assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Build a release APK

For a signed release you'll need to provide your own keystore. A
minimal release build (unsigned) can be produced via:

```bash
./gradlew assembleRelease
```

The unsigned APK is generated at
`app/build/outputs/apk/release/app-release-unsigned.apk`. To install it
on a device you must sign it with `apksigner`:

```bash
apksigner sign --ks your.keystore --out app-release.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

## Roadmap

The v0.0.x line focuses on the editing experience:

- **v0.0.1** — Foundation: file explorer, multi-tab editor, syntax
  highlighting for 30+ languages, Material 3 theming, settings.
- **v0.0.2** — Offline local workspace, auto-save, search & replace,
  robust auto-indent, crash fixes.
- **v0.0.3** — Live HTML/CSS/JS preview, Find & Replace upgrade
  (regex + case toggle + find-next), syntax hints (bracket matching +
  unbalanced-bracket underlines), FileExplorer virtualization,
  caret-persistence across tab switches, GitHub Action auto-build.
- **v0.0.4** — Multi-file search across the workspace, incremental
  highlighting for large files, custom font bundling (JetBrains Mono
  / Fira Code).
- **v0.1.0** — Integrated terminal (Termux-compatible), LSP bridge for
  Kotlin/Java.

## Contributing

ViperCode is open source under the MIT License. Pull requests are
welcome — please open an issue first to discuss the scope of any
non-trivial change.

## License

[MIT](./LICENSE) — © 2026 hieulouisdev
