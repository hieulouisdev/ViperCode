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
  <img alt="CI" src="https://img.shields.io/github/actions/workflow/status/hieulouisdev/ViperCode/ci.yml?branch=main&label=CI&style=flat-square">
</p>

---

## Overview

**ViperCode** is a modern, performant code editor for Android, built for
developers who demand the class of perfection in every keystroke. It is
designed from the ground up with the Android Storage Access Framework,
Material 3 theming, and a fully Compose-native UI — no legacy view
system, no compromises on startup latency or rendering performance.

ViperCode v0.0.5 ships a complete rewrite of the live preview pipeline
(fixes the long-standing "preview không chạy hoàn toàn file" complaint),
adds **bracket auto-completion**, a **Markdown live preview**, a
**JavaScript console overlay**, a **recent files** row, a **share**
action, a **comment toggle**, an **editor status bar**, and a new CI
workflow that catches compile errors on every PR before they reach a
release.

## Features (v0.0.5)

### Editor core

- **Multi-language syntax highlighting** — built-in tokeniser for 30+
  languages including Kotlin, Java, Python, JavaScript/TypeScript, Go,
  Rust, C/C++, C#, Swift, Dart, Ruby, PHP, SQL, Scala, Groovy, Lua,
  YAML, TOML, Markdown, XML, JSON, HTML, CSS, and more.
- **Multi-tab editing** — open multiple files at once; dirty-state
  tracking prevents accidental data loss. Caret position is preserved
  across tab switches and restored on next launch.
- **Bracket auto-completion** (v0.0.5) — typing `(`, `[`, `{` auto-
  inserts the matching close; typing `"` or `'` does the same and is
  smart enough to NOT auto-close when the next char is alphanumeric.
  Typing the same close bracket again "skips over" the existing one
  instead of inserting a duplicate (matches VS Code default
  behaviour). Toggle in Settings → "Auto-close brackets".
- **Comment toggle** (v0.0.5) — a top-bar action that toggles line-
  comment on the current line or selection, picking the right comment
  syntax per language (`#` for Python, `//` for Kotlin/Java/JS, `--`
  for SQL/Lua, etc.).
- **Editor status bar** (v0.0.5) — slim bar at the bottom showing
  cursor line / column, total line count, word count, character count
  and selection length. Toggle in Settings → "Show status bar".
- **Share file** (v0.0.5) — the editor's top bar has a new share icon
  that exports the current file's content via Android's share sheet.
- **Recent files** (v0.0.5) — the home screen shows a horizontally
  scrollable "Recent" row above the file tree. Tap to re-open. Long
  press the trash icon to clear the list.
- **Live HTML/CSS/JS preview** — open any HTML file, tap the play icon
  in the editor's top bar, and a WebView renders the page with full
  JavaScript enabled. Auto-refreshes 600 ms after you stop typing. CSS
  and JS from sibling tabs are inlined automatically so a small
  multi-file project works out of the box.
- **Live Markdown preview** (v0.0.5) — `.md` and `.markdown` files
  now render in the preview screen with proper heading, list, code-
  block, link and emphasis support. GFM-style tables are rendered too.
- **JavaScript console overlay** (v0.0.5) — JS `console.log`, errors
  and warnings are surfaced in a toggleable bottom panel so you can
  debug your scripts without leaving the editor.
- **Find & Replace (upgraded)** — regex toggle, case-sensitivity
  toggle, find-next / find-prev navigation, live match counter, and
  per-match replace.
- **Syntax hints** — when the caret is adjacent to a bracket pair,
  both brackets get a subtle background highlight.
- **Auto-save** — dirty files are saved automatically after a short
  idle delay (configurable, 500 ms – 5 s).
- **Robust auto-indent** — Tab expands to spaces; Enter copies the
  previous line's indentation and adds an extra indent after `{`,
  `(`, `[`, `:` and `=>`.

### Preview pipeline (v0.0.5 rewrite)

The v0.0.4 preview had a number of long-standing bugs that caused
"preview không chạy hoàn toàn file" (the preview doesn't run the whole
file). v0.0.5 fixes all of them:

- **Viewport meta tag injection** — mobile pages now render at the
  correct width by default; the action injects
  `<meta name="viewport" content="width=device-width, initial-scale=1">`
  if the user's HTML doesn't already have one.
- **JavaScript console capture** — `console.log`, `console.error`,
  `console.warn` and friends are surfaced in a toggleable bottom
  panel with source file and line number.
- **Resource error capture** — failed `fetch()` / network errors are
  also surfaced in the console overlay so you can see why a script
  isn't loading.
- **`inlineCompanionAssets` rewrite** — the v0.0.4 fallback
  `replaceFirst(...).ifBlank { ... }` never triggered because
  `replaceFirst` always returns the input unchanged when no match is
  found. v0.0.5 uses a real "append if not present" check.
- **Link click handling** — clicking an `<a href>` inside the preview
  now opens the link in the system browser instead of hijacking the
  WebView's history stack.
- **Desktop / mobile viewport toggle** — simulate a 1280-px desktop
  viewport without rotating the device.
- **Share HTML / Open in browser** — the preview's top bar now has
  buttons to share the composed HTML or open the page in the system
  browser.

### Storage

- **Offline-first storage** — ViperCode ships with a default local
  workspace under the app's private external storage so it works the
  moment you install it, with no permission prompts and no internet.
- **Storage Access Framework integration** — open any folder on the
  device (internal or external storage, Google Drive, Nextcloud, etc.)
  via the system folder picker. Permissions are persisted across
  launches.

### UX

- **Material 3 dynamic theming** — automatically picks colours from
  the user's wallpaper on Android 12+; falls back to the ViperCode
  brand palette on older devices.
- **Optimised for Android 7.1.1+ (API 25)** — runs on over 99% of
  active Android devices worldwide.
- **Light / Dark / System theme modes** with full Material 3 component
  theming.
- **Editor preferences** — adjustable font size, tab size, font
  family, word wrap, line numbers, auto-indent, auto-close brackets,
  status bar visibility.
- **External file open** — tap any source file in your file manager
  and it opens directly in the editor.

## CI / CD (v0.0.5)

ViperCode ships TWO GitHub Actions workflows:

- **`.github/workflows/ci.yml`** — runs `lintDebug` + `assembleDebug`
  on every push and pull request so compile errors are caught BEFORE
  a release is published. The release workflow is then guaranteed to
  succeed because the same compile step has already passed.
- **`.github/workflows/build-release-apk.yml`** — triggered on
  release published (or via `workflow_dispatch`). Builds a signed
  release APK, signs it (v1+v2+v3) with either a long-lived keystore
  stored as a repo secret OR a freshly-generated debug-grade
  keystore, then attaches the APK to the release.

The release workflow reads release notes from `CHANGELOG.md` so the
user can edit rich text BEFORE publishing. If the release already has
a body (typed in the GitHub UI), the existing body is preserved.

## Fixes & changes since v0.0.4

### Critical bugs fixed

- **`Strings.applyMode` non-exhaustive `when`** — the v0.0.4 function
  was missing the `SYSTEM` branch, which would have failed to compile
  if any code had called it. v0.0.5 makes the function exhaustive.
- **Preview: HTML/CSS/JS no longer fails to "run completely"** —
  viewport meta tag injection, JavaScript console capture, proper
  `<base>` handling, link-click interception, resource error capture.
- **Preview: `inlineCompanionAssets` `ifBlank` bug** — fixed.
- **GitHub Actions: hardcoded "v0.0.3" release notes** — release
  notes now come from `CHANGELOG.md`.
- **GitHub Actions: `workflow_dispatch` default** — updated to
  `v0.0.5`.
- **GitHub Actions: missing v3 signing** — release APKs are now
  v1+v2+v3 signed.
- **GitHub Actions: `setup-android` missing platform-tools** — added.
- **Localisation: hardcoded "Create"/"Cancel"** — `NewNameDialog` now
  routes through `Strings.get`.
- **Gutter scroll sync** — the v0.0.4 magic density multiplier was
  wrong on most devices. v0.0.5 uses `LocalDensity` properly.

### New features

- Bracket auto-completion.
- Live Markdown preview.
- JavaScript console overlay.
- Comment toggle.
- Editor status bar.
- Recent files row.
- Share file / Share HTML / Open in browser.
- Desktop / mobile viewport toggle.
- New CI workflow + dynamic release notes.

## Tech stack

ViperCode is built with the latest stable versions of every dependency.

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
│   │   │   ├── data/prefs/               # SettingsRepository (DataStore),
│   │   │   │                              # RecentFiles
│   │   │   └── util/                     # LanguageDetector, FileUtils,
│   │   │                                  # Strings (i18n),
│   │   │                                  # MarkdownRenderer
│   │   └── res/                         # Launcher icons, themes, strings, XML config
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   ├── libs.versions.toml                # Centralised version catalog
│   └── wrapper/                          # Gradle wrapper
├── .github/workflows/                    # ci.yml + build-release-apk.yml
├── logoide.png                           # Original logo source
├── CHANGELOG.md                          # Release notes source
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
- **v0.0.2** — Offline-first, auto-save, search & replace, robust
  auto-indent, crash fixes.
- **v0.0.3** — Live HTML/CSS/JS preview, Find & Replace upgrade,
  syntax hints, FileExplorer virtualization, caret-persistence,
  GitHub Action auto-build.
- **v0.0.4** — Vietnamese language, editor + preview performance
  rewrite, file-extension duplication fix, folder context menu,
  Search-in-files, Quick-open, Go-to-line, Duplicate file,
  Hidden files toggle, Sort by, Configurable live preview.
- **v0.0.5** — Preview rewrite (viewport, console, link handling),
  bracket auto-completion, Markdown preview, comment toggle, recent
  files, share, status bar, new CI workflow, dynamic release notes.
- **v0.1.0** — Integrated terminal (Termux-compatible), LSP bridge for
  Kotlin/Java.

## Contributing

ViperCode is open source under the MIT License. Pull requests are
welcome — please open an issue first to discuss the scope of any
non-trivial change.

## License

[MIT](./LICENSE) — © 2026 hieulouisdev

