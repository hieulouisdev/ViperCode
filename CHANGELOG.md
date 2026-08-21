# Changelog

All notable changes to ViperCode are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [v0.0.5] - 2026-08-22

### Added
- **Bracket auto-completion** — typing `(`, `[`, `{`, `"`, `'` or `` ` ``
  automatically inserts the matching close and places the caret between
  the pair. Toggle in Settings → "Auto-close brackets".
- **Live Markdown preview** — `.md` and `.markdown` files now render
  in the preview screen with proper heading, list, code-block, link and
  emphasis support. GFM-style tables are rendered too.
- **JavaScript console overlay in preview** — JS `console.log`, errors
  and warnings are surfaced in a toggleable bottom panel so you can
  debug your scripts without leaving the editor. Tap the console icon
  in the preview's top bar to expand.
- **Recent files** — the home screen now shows a horizontally
  scrollable "Recent" row above the file tree. Tap to re-open. Long
  press the row to clear the list.
- **Share file** — the editor's top bar has a new share icon that
  exports the current file's content via Android's share sheet
  (email, messaging, GitHub Gist, etc.).
- **Share HTML / Open in browser** — the preview's top bar now has
  buttons to share the rendered HTML or open the page in the system
  browser.
- **Editor status bar** — a slim bar at the bottom of the editor
  shows line / column position, total line count, word count and
  character count. Toggle in Settings → "Show status bar".
- **Comment toggle** — a new top-bar action that toggles line-comment
  on the current line or selection, picking the right comment syntax
  per language (`#` for Python, `//` for Kotlin/Java/JS, `--` for
  SQL/Lua, etc.).
- **Desktop / mobile viewport toggle in preview** — simulate a
  desktop or mobile viewport without rotating the device.
- **Preview reload + open-external actions** — explicit reload
  button (works even when live refresh is off) and "open in browser"
  for full-page testing.
- **New CI workflow** — `.github/workflows/ci.yml` runs `lintDebug`
  + `assembleDebug` on every push and pull request so compile
  errors are caught BEFORE a release is published.

### Fixed
- **Critical: `Strings.applyMode` non-exhaustive `when`** — the v0.0.4
  function was missing the `SYSTEM` branch, which would have failed
  to compile if any code had called it. v0.0.5 makes the function
  exhaustive (and the MainActivity that does call it now goes through
  the corrected path).
- **Preview: HTML/CSS/JS no longer fails to "run completely"** — the
  WebView's base URL is now `file:///android_asset/` (with a proper
  `<base>` tag injected) so relative paths, fetch(), localStorage,
  ES modules and external CDNs all work. v0.0.4 used `about:blank`
  which silently broke every cross-document feature.
- **Preview: viewport meta tag** — mobile pages now render at the
  correct width by default; the action injects `<meta name="viewport"
  content="width=device-width, initial-scale=1">` if missing.
- **Preview: console errors visible** — JS exceptions are no longer
  swallowed; they appear in the new console overlay.
- **Preview: `inlineCompanionAssets` `ifBlank` bug** — the v0.0.4
  fallback `result.replaceFirst(...).ifBlank { ... }` never triggered
  because `replaceFirst` returns the input unchanged (never blank)
  when no match is found. v0.0.5 uses a real "append if not present"
  check.
- **Preview: shouldOverrideUrlLoading** — clicking links inside the
  preview now opens them in the system browser instead of hijacking
  the WebView's history stack.
- **GitHub Actions: hardcoded "v0.0.3" release notes** — the v0.0.4
  workflow always attached the v0.0.3 changelog to every release.
  v0.0.5 reads release notes from `CHANGELOG.md` and preserves any
  body the user typed into the GitHub UI.
- **GitHub Actions: `workflow_dispatch` default** — updated from
  `v0.0.3` to `v0.0.5`.
- **GitHub Actions: no CI on PRs** — added `ci.yml` that runs lint +
  debug build on every push/PR so the release workflow never fails
  due to a compile error introduced after the last release.
- **GitHub Actions: missing v3 signing** — release APKs are now
  v1+v2+v3 signed so they install cleanly on Android 9+ (v3 is the
  only scheme that supports key rotation).
- **GitHub Actions: `setup-android` missing platform-tools** — added
  `platform-tools` to the SDK package list so `apksigner` and
  `adb` are always present.
- **GitHub Actions: better error on missing APK** — the previous
  workflow just `exit 1`'d with a generic message when the build
  didn't produce an APK. v0.0.5 lists the build outputs directory
  before failing so the next-best guess is visible in the log.
- **Localisation: hardcoded "Create"/"Cancel"** — the v0.0.4
  `NewNameDialog` had hardcoded English button labels. v0.0.5 routes
  them through `Strings.get` so they flip with the language setting.
- **Gutter scroll sync** — the v0.0.4 magic density multiplier
  (`(fontSize + 6) * 2.0f`) was wrong on most devices, causing the
  line-number gutter to drift as the user scrolled. v0.0.5 uses
  `LocalDensity` to convert sp → px accurately.

### Changed
- The release workflow now uses `fetch-depth: 0` so `CHANGELOG.md` is
  always available even when the workflow is dispatched on a tag.
- The release notes step is conditional on `!github.event.release.body`
  so a user-typed release body in the GitHub UI is preserved.
- The `setup-android` action now installs `platform-tools` alongside
  the build-tools (used by `apksigner`).
- The `lintDebug` step is now part of the CI workflow so the release
  workflow only needs to build the release APK.

## [v0.0.4] - 2026-07-01

### Added
- **Vietnamese language (Tiếng Việt)** — pick between English and
  Vietnamese in Settings; the entire UI flips instantly without an
  Activity restart.
- **Massive editor performance rewrite** — syntax highlighter is now
  cached via `remember(text, language)`, line-number gutter is
  virtualised, paste on 10 000-line files no longer freezes the UI.
- **Massive preview performance rewrite** — `composedHtml` no longer
  recomputed on every keystroke, WebView reload is guarded by a
  content-equality check.
- **Search in files** — VS Code "Ctrl+Shift+F" style workspace-wide
  text search.
- **Quick open** — VS Code "Ctrl+P" style file picker.
- **Go to line** — top-bar action opens a dialog asking for a line
  number; the caret jumps there and the editor scrolls the line
  into view.
- **Duplicate file / folder** — long-press any file or folder to
  create a copy next to the original.
- **Hidden files toggle** — Settings → "Show hidden files" reveals
  dot-files in the file explorer.
- **Sort by** — Settings → Sort by Name / Size / Modified.
- **Configurable live preview** — Settings → "Live preview
  auto-refresh" toggle + delay slider (300 – 3000 ms).

### Fixed
- **File extension duplication bug** — creating `hieu.html` no longer
  produces `hieu.html.htm`.
- **Folder access after creation** — long-press context menu on every
  folder with "New file here", "New folder here", "Rename",
  "Duplicate", "Delete".

## [v0.0.3] - 2026-05-15

### Added
- **Live HTML/CSS/JS preview** — open an HTML file, tap the play icon
  in the editor's top bar, and the rendered output appears immediately.
- **Find & Replace upgrade** — regex toggle, case-sensitivity toggle,
  find-next / find-prev navigation, live match counter.
- **Multi-tab UX** — TabChip max-width capped at 180 dp.
- **Syntax hints** — bracket matching highlights the pair at the
  caret; unbalanced open brackets get a red underline.
- **FileExplorer rewrite** — flattened LazyColumn — full virtualisation
  for large workspaces.
- **Smart indent** — extra indent after `{`/`(`/`[` now respects the
  user's `tabSize` setting.
- **CI/CD** — GitHub Action auto-builds the APK on every new release.

## [v0.0.2] - 2026-04-01

### Added
- **Offline-first storage** — default local workspace under the app's
  private external storage.
- **Auto-save** — dirty files are saved automatically after a short
  idle delay.
- **Robust auto-indent** — Tab expands to spaces; Enter copies the
  previous line's indentation.
- **Search & Replace** — basic find and replace-all.

## [v0.0.1] - 2026-03-01

### Added
- Initial release.
- Multi-language syntax highlighting for 30+ languages.
- Multi-tab editing with dirty-state tracking.
- Storage Access Framework integration.
- Material 3 dynamic theming.
- Settings screen with theme, font, tab size, word wrap, line numbers,
  auto-indent preferences.
