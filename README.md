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
  <img alt="Language" src="https://img.shields.io/badge/i18n-EN%20%2F%20Ti%E1%BA%BFng%20Vi%E1%BB%87t-2196F3?style=flat-square">
</p>

---

## Overview

**ViperCode** is a modern, performant code editor for Android, built for
developers who demand the class of perfection in every keystroke. It is
designed from the ground up with the Android Storage Access Framework,
Material 3 theming, and a fully Compose-native UI — no legacy view
system, no compromises on startup latency or rendering performance.

ViperCode v0.0.4 ships the editing core plus a major performance
rewrite of the editor (kills the paste / scroll lag of v0.0.3), adds a
**Vietnamese interface** (Tiếng Việt), fixes the long-standing
file-extension duplication bug (`hieu.html` → `hieu.html.htm`), and
adds new features: workspace-wide search, quick-open, go-to-line,
duplicate file, sort by name/size/modified, hidden-files toggle, and a
configurable live preview.

## Features (v0.0.4)

### Editor core

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
  both brackets get a subtle background highlight.
- **Auto-save** — dirty files are saved automatically after a short
  idle delay (configurable, 500 ms – 5 s). Back button now flushes
  auto-save so nothing is lost if you navigate away within the delay
  window.
- **Robust auto-indent** — Tab expands to spaces; Enter copies the
  previous line's indentation and adds an extra indent after `{`,
  `(`, `[`, `:` and `=>`. Extra indent respects the user's `tabSize`.

### New in v0.0.4

- **Vietnamese language (Tiếng Việt)** — pick between English and
  Vietnamese in Settings; the entire UI flips instantly without an
  Activity restart. The `Strings.kt` catalogue is the single source
  of truth — adding more languages is a one-file change.
- **Massive editor performance rewrite** — the syntax highlighter is
  now cached via `remember(text, language)` instead of running on
  every keystroke. The line-number gutter is virtualised via a
  `LazyColumn` synced to the editor's scroll state. Pasting a 10 000-
  line file used to freeze the UI for several seconds; it now feels
  instant.
- **Massive preview performance rewrite** — `composedHtml` is no
  longer recomputed on every keystroke (only on refresh tick), the
  WebView reload is guarded by a content-equality check so spurious
  recompositions don't nuke the DOM, and a configurable debounce +
  live-refresh toggle give the user fine control.
- **Fixed: file extension duplication bug** — creating `hieu.html`
  no longer produces `hieu.html.htm`, and `gg.css` no longer produces
  `gg.css.css`. The fix passes `application/octet-stream` as the
  MIME type when the user supplied an extension, so SAF leaves the
  file name untouched.
- **Fixed: folder access after creation** — the FAB only ever created
  files at the workspace root. v0.0.4 adds a long-press context menu
  on every folder with "New file here", "New folder here", "Rename",
  "Duplicate", "Delete". Newly-created folders are auto-expanded.
- **Search in files** — VS Code "Ctrl+Shift+F" style workspace-wide
  text search. Walks the entire open folder (case-insensitive
  substring match on both file name and content), capped at 200 hits.
  Tap a result to open it in the editor at the matching line.
- **Quick open** — VS Code "Ctrl+P" style file picker. Walks the
  workspace once on first open, then filters the cached list as you
  type. Files whose name starts with the query are ranked first.
- **Go to line** — top-bar action opens a dialog asking for a line
  number; the caret jumps there and the editor scrolls the line into
  view.
- **Duplicate file / folder** — long-press any file or folder to
  create a copy next to the original. Directories are duplicated
  recursively.
- **Hidden files toggle** — Settings → "Show hidden files" reveals
  dot-files (e.g. `.gitignore`, `.env`) in the file explorer.
- **Sort by** — Settings → Sort by Name / Size / Modified. Available
  directly from the home screen's sort icon too.
- **Configurable live preview** — Settings → "Live preview
  auto-refresh" toggle + a delay slider (300 – 3000 ms). The
  preview's top bar also has a lightning-bolt icon to flip live
  refresh on/off without leaving the screen.

### Storage

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

### UX

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
  it opens directly in the editor.

## Fixes & changes since v0.0.3

### Critical bugs

- **File extension duplication (`hieu.html.htm`, `gg.css.css`)**:
  the v0.0.3 `FileUtils.createFile` passed a derived MIME type
  (e.g. `text/html`) to `DocumentFile.createFile(mime, displayName)`.
  The ExternalStorageProvider then stripped the user's trailing
  extension and appended its own preferred extension — which on many
  Android versions differs from what the user typed (e.g. user types
  `.html`, provider appends `.htm`). v0.0.4 passes
  `application/octet-stream` whenever the user supplied an extension,
  so SAF leaves the file name untouched.
- **Folder access after creation**: the v0.0.3 FAB only ever created
  files at the workspace root — there was no way to create a file
  inside a sub-folder. v0.0.4 adds a long-press context menu on every
  folder with "New file here" / "New folder here" / "Rename" /
  "Duplicate" / "Delete". Newly-created folders are auto-expanded so
  the user can immediately access them.
- **Editor paste + scroll lag**: the v0.0.3 `CodeEditor` ran
  `SyntaxHighlighter.highlight` (an O(n) tokeniser) and
  `SyntaxHints.findUnbalancedBrackets` (another O(n) scan) on EVERY
  recomposition — i.e. on every keystroke and every caret move. For a
  5 000-line paste this froze the UI for several seconds. v0.0.4
  caches the highlighted `AnnotatedString` in a `remember(text,
  language)` block, replaces the full-document bracket scan with an
  O(distance) caret-aware walker, and swaps the eager
  `Column { repeat(lineCount) { Text(...) } }` gutter for a
  virtualised `LazyColumn` whose scroll state is synced to the
  editor's `ScrollState`.
- **Preview lag**: the v0.0.3 `composedHtml` was keyed on
  `activeTab?.content`, so the regex-based companion asset inliner ran
  on every keystroke even though the WebView only reloaded after the
  600 ms debounce. v0.0.4 keys `composedHtml` on `refreshKey` only,
  guards the WebView reload with a content-equality check so spurious
  recompositions don't nuke the DOM, and adds a configurable delay +
  live-refresh toggle.

### UX

- **Vietnamese language support**: the entire UI flows through a
  centralised `Strings.kt` catalogue. Flip the language in Settings
  and the whole app recomposes without an Activity restart.
- **Search in files / Quick open / Go to line**: three new
  editor-flow shortcuts accessible from both the home screen's
  top bar and the editor's top bar.
- **Hidden files + Sort by**: two new preferences that affect the
  file explorer's rendering.
- **Configurable live preview**: a toggle + delay slider, plus a
  lightning-bolt icon on the preview's top bar for an instant flip.

### New features

- **`Strings.kt`** — centralised i18n catalogue with EN + VN.
- **`SearchInFilesScreen`** — workspace-wide text search.
- **`QuickOpenScreen`** — VS Code "Ctrl+P" style file picker.
- **`FileUtils.duplicate`** + **`FileUtils.searchInFiles`** — pure
  utility functions reused by the repository layer.
- **`FileActionsDialog`** — long-press context menu on every file /
  folder with Rename / Delete / Duplicate / New file here /
  New folder here.
- **`LanguageMode`** + **`SortBy`** + **`showHiddenFiles`** +
  **`livePreview`** + **`previewDelayMs`** prefs in
  `SettingsRepository`.

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
│   │   │   ├── ui/screens/               # Splash, Home, Editor, Preview,
│   │   │   │                              # Settings, About, SearchInFiles,
│   │   │   │                              # QuickOpen
│   │   │   ├── ui/components/            # CodeEditor, FileExplorer, TabBar,
│   │   │   │                              # SyntaxHighlighter, SyntaxHints
│   │   │   ├── data/model/               # FileNode, EditorTab
│   │   │   ├── data/repo/                # FileRepository
│   │   │   ├── data/prefs/               # SettingsRepository (DataStore)
│   │   │   └── util/                     # LanguageDetector, FileUtils,
│   │   │                                  # Strings (i18n)
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
- **v0.0.4** — Vietnamese language, massive editor + preview
  performance rewrite, file-extension duplication fix, folder context
  menu, Search-in-files, Quick-open, Go-to-line, Duplicate file,
  Hidden files toggle, Sort by, Configurable live preview.
- **v0.1.0** — Integrated terminal (Termux-compatible), LSP bridge for
  Kotlin/Java.

## Contributing

ViperCode is open source under the MIT License. Pull requests are
welcome — please open an issue first to discuss the scope of any
non-trivial change.

## License

[MIT](./LICENSE) — © 2026 hieulouisdev
