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
Material 3 theming, and a fully Compose-native UI — no WebView, no legacy
view system, no compromises on startup latency or rendering performance.

ViperCode v0.0.2 ships the editing core plus the offline-first
workflow improvements: auto-save, search & replace, an offline local
workspace, and a number of crash fixes that affected v0.0.1 on
certain devices.

## Features (v0.0.2)

- **Multi-language syntax highlighting** — built-in tokeniser for 30+
  languages including Kotlin, Java, Python, JavaScript/TypeScript, Go,
  Rust, C/C++, C#, Swift, Dart, Ruby, PHP, SQL, Scala, Groovy, Lua,
  YAML, TOML, Markdown, XML, JSON, HTML, CSS, and more.
- **Multi-tab editing** — open multiple files at once; dirty-state
  tracking prevents accidental data loss.
- **Auto-save** — dirty files are saved automatically after a short
  idle delay (configurable, 500 ms–5 s).
- **Search & Replace** — replace all occurrences within the active
  file from a compact inline bar.
- **Offline-first storage** — ViperCode ships with a default local
  workspace under the app's private external storage so it works the
  moment you install it, with no permission prompts and no internet.
  The Storage Access Framework picker remains available for opening
  any folder on the device.
- **Storage Access Framework integration** — open any folder on the
  device (internal or external storage, Google Drive, Nextcloud, etc.)
  via the system folder picker. Permissions are persisted across
  launches so your workspace is exactly where you left it.
- **Material 3 dynamic theming** — automatically picks colours from
  the user's wallpaper on Android 12+; falls back to the ViperCode
  brand palette on older devices.
- **Optimised for Android 7.1.1+ (API 25)** — runs on over 99% of
  active Android devices worldwide.
- **Light / Dark / System theme modes** with full Material 3 component
  theming.
- **Editor preferences** — adjustable font size, tab size, font
  family, word wrap, line numbers, auto-indent.
- **Auto-save to SAF / local file** — every save goes back to the
  original folder, whether it is a SAF tree URI or the local
  workspace; no copy step.
- **External file open** — tap any source file in your file manager and
  it opens directly in the editor.
- **Robust auto-indent** — Tab expands to spaces; Enter copies the
  previous line's indentation and adds an extra indent after `{`,
  `(`, `[`, `:` and `=>`, no matter where the caret is.

## Fixes since v0.0.1

- **Critical**: fixed the `UninitializedPropertyAccessException` that
  crashed the app on every launch. The root cause was an eagerly
  constructed `Flow` inside `SettingsRepository.Pref` that read a
  `lateinit` context before `init(context)` had run.
- Removed a duplicate `Language.JAVA → emptySet()` entry in
  `SyntaxHighlighter.KEYWORDS` that was shadowing the proper Java
  keyword set.
- Auto-indent now respects the caret position instead of assuming the
  user is always appending at the end of the buffer.
- Tab close no longer reads a stale `tabs` snapshot from Compose
  state — it queries the repository directly so the back-navigation
  check is correct.
- The Save toolbar button now saves the *active* tab, not the tab id
  that was first navigated to.
- Hardcoded version strings in the splash and About screens replaced
  with `BuildConfig.VERSION_NAME`.

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
- **v0.0.3** — Multi-file search, in-folder Git status display.
- **v0.1.0** — Integrated terminal (Termux-compatible), LSP bridge for
  Kotlin/Java.

## Contributing

ViperCode is open source under the MIT License. Pull requests are
welcome — please open an issue first to discuss the scope of any
non-trivial change.

## License

[MIT](./LICENSE) — © 2026 hieulouisdev
