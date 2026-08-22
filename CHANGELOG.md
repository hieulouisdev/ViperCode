# Changelog

All notable changes to ViperCode are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [v0.0.7] - 2026-08-22

### Added
- **RepoResult error surface** — `FileRepository.openFile`,
  `openExternalFile`, `openFolder`, `saveTab`, `saveTabIfDirty`,
  and `extractZipToProjects` now return a `RepoResult<T>` sealed
  type so failures propagate to the user instead of being silently
  swallowed. The editor and home screens surface a snackbar / toast
  on every failure with a real error message.
- **Truncated-file banner** — files larger than 5 MB now show a slim
  `errorContainer` banner explaining why edits are disabled and the
  content is truncated, instead of a silent read-only editor.
- **Settings → About** inline entry — Settings now has an inline
  "About ViperCode" row so the user can reach About without going
  back to the Home overflow menu.
- **Material 3 FilterChip** replaces the hardcoded `Aa` and `.*`
  labels in the Search & Replace bar; the chips are now properly
  i18n'd and accessible.
- **Tap-to-skip splash** — the splash screen now dismisses on tap,
  and the auto-advance delay was reduced from 1100 ms to 800 ms.
- **Loading spinners** in Search-in-files and Quick-open (was just
  `Text("…")`); empty results now show an icon + descriptive text
  instead of plain text.
- **Path display in Quick-open** — each result row shows the parent
  folder name so users can disambiguate same-named files (e.g.,
  multiple `index.js`).
- **Inline About in Settings** — common pattern; avoids forcing the
  user back to Home just to open About.
- **Toggle rows fully tappable** — the entire settings row now
  toggles the Switch (was only the Switch itself).
- **Animated dirty-tab indicator** — the dirty dot in the tab strip
  now gently pulses so unsaved changes are visually obvious.
- **Larger tap targets** — tab close button, file-explorer chevron,
  and recent-files clear all bumped to ≥ 48 dp (Material 3 minimum).
- **Dynamic gutter width** — the line-number gutter now grows from
  32 dp to 64 dp based on `lineCount.toString().length`, so 5+ digit
  line counts no longer clip.
- **All 18 about-features wired** — the About screen now lists all
  features declared in `Strings.T` (was only 14; the last 4 were
  dead fields).
- **All `Back` / `Menu` / `More` contentDescriptions i18n'd** —
  previously several were hardcoded English.

### Changed
- **Auto-save no longer corrupts files** — the save coroutine now
  lives on the editor's `rememberCoroutineScope` instead of the
  `LaunchedEffect`'s own scope. The previous setup cancelled the
  save mid-write on the next keystroke, leaving the file truncated
  because `FileUtils.writeText` opens with mode `"wt"`
  (truncate-then-write). This was a **critical data-loss bug**.
- **Font family setting now works** — `SettingsRepository.fontFamily`
  flows through to `CodeEditor(fontFamily = …)`. Previously the
  setting had no effect at all.
- **Rename updates the tab URI** — `FileRepository.rename` now
  re-resolves the renamed DocumentFile and updates the tab's `uri`
  field to the new URI SAF returns. The previous implementation kept
  the stale URI, so subsequent saves wrote to a non-existent file
  and silently failed.
- **`<` and `>` no longer matched as brackets** in non-HTML/XML
  languages. The previous `OPENERS` map included them, so
  `a < b > c` had `<` and `>` highlighted as a bracket pair —
  wrong in every language except HTML/XML.
- **Python triple-quoted strings** (`"""..."""` and `'''...'''`)
  are now highlighted correctly across multiple lines. The previous
  single-line scanner terminated at the first `\n` and highlighted
  the rest of the string body as code.
- **Markdown `#` heading detection** now only fires at the start of
  a line (after optional whitespace). Previously any `#` mid-paragraph
  was mis-highlighted as a heading.
- **`:` triggers extra indent only for Python** — JSON property
  colons, JS type annotations, YAML mappings, etc. no longer
  trigger an unwanted extra indent after Enter.
- **Auto-close quotes inside strings** — typing `"` or `'` inside
  an existing string literal no longer auto-closes (was producing
  `""` mid-string). String state is tracked from the start of the
  current line to the caret.
- **Multi-char numeric suffixes** — `123UL`, `0xFFL`, `1.5e10f` now
  consume all trailing `f/F/l/L/u/U/d/D` chars (was only one).
- **Pasted tabs expand to spaces** — previously only a single typed
  Tab was expanded; pasted `\t` characters stayed as `\t`.
- **Selection-wrap on bracket typing** — typing an opening bracket
  with a non-empty selection now wraps the selection with the
  open+close pair (was just replacing the selection).
- **Cursor preserved on external content sync** — when the
  underlying `tab.content` changes externally and the field text
  doesn't match, the caret + selection are coerced into the new
  text bounds instead of resetting to offset 0.
- **Throttled gutter scroll sync** — the line-number gutter scroll
  sync now flows through `snapshotFlow.distinctUntilChanged()` so
  fling scrolling only triggers one `scrollToItem` per visible-row
  change instead of one per pixel.
- **Binary-search line/column maths** — `lineColumnFromOffset` and
  `restoreOffset` use a precomputed `IntArray` of line starts →
  O(log n) per call instead of O(n). Big files (100k+ lines) no
  longer hitch on every keystroke.
- **Single `findAllMatches` computation** per keystroke in the
  Search & Replace bar (was two — one in `LaunchedEffect` and one
  in `remember`); per-match Replace now recomputes the matches
  synchronously so subsequent Replace clicks use correct offsets
  (was using stale indices and corrupting the document).
- **`copyFileContents` rethrows `CancellationException`** — the
  previous `catch (_: Throwable)` swallowed ALL throwables
  including `CancellationException`, which silently broke
  structured concurrency.
- **`FileUtils.searchInFiles` uses `indexOf(ignoreCase = true)`**
  instead of allocating a full lowercase copy of the file content
  (was 2× allocation per hit check on a 1 MB file).
- **`FileUtils.lineColForOffset` is now O(log n)** via binary
  search on a precomputed line-starts array (was O(offset) per call;
  for a 1 MB file a hit at the end was 1 M iterations).
- **`FileExplorer.flattenTree` is now `remember`-ed** so it isn't
  recomputed on every recomposition (was rebuilt on every keystroke
  in the editor if the editor shared state with the explorer).
- **`FileExplorer` human-size now `remember`-ed per node** so it
  isn't recomputed on every recomposition.
- **`EditorStatusBar.wordCount` regex now cached** via
  `remember { Regex("\\s+") }` (was allocated per recomposition).
- **Switch-folder sheet no longer wipes expanded folders** — the
  previous `expanded = setOf(uri)` collapsed every previously-expanded
  subtree; it now adds the picked URI to the existing expanded set.
- **`listExtractedProjects` re-keyed on `showSwitchFolder`** so a
  freshly-extracted ZIP now appears in the sheet immediately (was
  cached forever and required an app restart).
- **External `ACTION_VIEW` opens the editor directly** — the
  navigation graph now routes external file URIs to the EDITOR
  route (was HOME, so the just-opened file was invisible until the
  user manually found it in the file tree).
- **Search / Quick-open back stack preserved** — the previous
  `popUpTo(HOME)` destroyed the search back stack, so back from
  the editor went to Home instead of back to the search screen.
- **ProGuard rules tightened** — removed the overly broad
  `-keep class androidx.compose.** { *; }` and
  `-keep class androidx.datastore.** { *; }` rules that were
  bloating the release APK by preventing R8 from shrinking unused
  Compose / DataStore code. Both libraries ship their own consumer
  rules.

### Fixed
- **Critical: auto-save race condition** — the `LaunchedEffect` that
  re-keyed on `activeTab?.content` cancelled the in-flight save
  coroutine mid-write on the next keystroke, leaving the file
  truncated. v0.0.7 moves the save coroutine to a separate
  `rememberCoroutineScope` that survives the cancellation.
- **Critical: rename left tab URI stale** — subsequent saves wrote
  to the OLD URI which may not exist. v0.0.7 re-resolves the renamed
  DocumentFile and updates the tab's `uri`.
- **Critical: silent save failure** — `saveTab` returned a `Boolean`
  that the caller ignored on failure. v0.0.7 returns a `RepoResult`
  and the editor shows a snackbar with the failure message.
- **Critical: silent open failure** — `openFile` swallowed read
  errors and returned an empty-content tab. v0.0.7 returns a
  `RepoResult.Failure` with the underlying error message.
- **Critical: silent open-folder failure** — `openFolder` returned
  `Unit` and silently failed if the URI couldn't be resolved.
  v0.0.7 returns a `RepoResult.Failure` and the Home screen surfaces
  it via the toast overlay.
- **Cursor reset on external content sync** — `LaunchedEffect(tab.content)`
  reset the caret to offset 0 when `isDirty` flipped false. v0.0.7
  preserves the caret by coercing the selection into the new text
  bounds.
- **`computeExtraIndent` `:` rule** fired for JSON, JS, YAML, etc.
  v0.0.7 restricts it to Python only.
- **Auto-close quotes inside strings** — typing `"` mid-string
  produced `""`. v0.0.7 adds string-state tracking.
- **`<` and `>` matched as brackets** in non-HTML/XML languages.
  v0.0.7 removes them from the default `OPENERS` map.
- **Python triple-quoted strings** were mis-highlighted. v0.0.7
  detects `"""` / `'''` at scan start and spans multiple lines.
- **Markdown `#` heading detection** mis-highlighted mid-paragraph
  `#`. v0.0.7 only fires at line start.
- **Multi-char numeric suffixes** like `123UL` were mis-highlighted
  (only `U` was consumed, `L` became an identifier). v0.0.7 consumes
  all trailing `fFlLuUdD` chars.
- **`FileUtils.copyFileContents`** swallowed `CancellationException`,
  silently breaking structured concurrency. v0.0.7 rethrows it.
- **`FileUtils.searchInFiles`** allocated a full lowercase copy of
  each file content per hit check. v0.0.7 uses `indexOf(ignoreCase = true)`.
- **`FileUtils.lineColForOffset`** was O(offset) per call. v0.0.7 uses
  binary search on a precomputed line-starts array.
- **`FileExplorer.flattenTree`** was recomputed on every recomposition.
  v0.0.7 wraps it in `remember`.
- **Switch-folder sheet wiped expanded state** — the previous
  `expanded = setOf(uri)` collapsed every previously-expanded
  subtree. v0.0.7 adds the picked URI to the existing set.
- **`listExtractedProjects` ran on the main thread** and was never
  re-keyed, so a freshly-extracted ZIP didn't show up in the sheet
  until the app was restarted. v0.0.7 re-keys on `showSwitchFolder`.
- **External `ACTION_VIEW` opened HOME instead of EDITOR** — the
  just-opened file was invisible until the user found it in the
  file tree. v0.0.7 routes to EDITOR directly.
- **`popUpTo(HOME)` from Search / Quick-open** destroyed the search
  back stack. v0.0.7 removes the `popUpTo` so back from the editor
  returns to the search screen.
- **Hardcoded `contentDescription = "Back" / "Menu" / "More"`** in
  Settings, About, and Home — all now routed through `Strings.get()`.
- **Hardcoded `Text("Aa")` and `Text(".*")`** in the Search & Replace
  bar — replaced with Material 3 `FilterChip`s with i18n'd labels.
- **`Text("…")` loading state** in Search-in-files and Quick-open —
  replaced with `CircularProgressIndicator` + descriptive text.
- **Empty-results state** in Search-in-files and Quick-open — now
  shows an icon + descriptive text instead of plain text.
- **Tab close button tap target** was 20 dp (below the 48 dp
  Material 3 minimum). v0.0.7 bumps it to 48 dp.
- **File-explorer chevron tap target** was 20 dp. v0.0.7 bumps it
  to 48 dp.
- **Recent-files clear tap target** was 16 dp. v0.0.7 bumps it to
  48 dp.
- **`AboutScreen` "Material 3 (Material Components 1.12.0)"** was
  misleading — `com.google.android.material:material` is Material
  Components for Android (the View system), NOT Material 3. v0.0.7
  replaces with "Material 3 (Jetpack Compose Material3)".
- **`AboutScreen` listed only 14 features** despite `Strings.T`
  declaring 18. v0.0.7 lists all 18.
- **`SettingsScreen` preview section header** used `s.previewSubtitle`
  ("Live HTML/CSS/JS") as the section title — misleading. v0.0.7
  uses the dedicated `s.settingsPreviewSection` ("Live preview").
- **`SettingsScreen` preview-delay slider** was titled
  `s.settingsAutoSaveDelay` ("Auto-save delay") — confusing. v0.0.7
  uses the dedicated `s.settingsPreviewDelay`.
- **`SettingsScreen` ToggleRow** only the Switch was tappable.
  v0.0.7 makes the entire row tappable.
- **`SplashScreen`** had a fixed 1100 ms delay with no tap-to-skip.
  v0.0.7 reduces to 800 ms + tap-to-skip.
- **`AndroidManifest.xml`** had `VIBRATE` permission declared but
  never used. v0.0.7 removes it. Also `requestLegacyExternalStorage`
  flipped from `true` to `false` (was ignored on API 30+ anyway).
- **ProGuard rules** had overly broad `-keep class androidx.compose.** { *; }`
  and `-keep class androidx.datastore.** { *; }` rules that bloated
  the release APK. v0.0.7 removes both.

### Removed
- **Unused `VIBRATE` permission** from `AndroidManifest.xml`.
- **Overly broad ProGuard keep rules** for `androidx.compose.**` and
  `androidx.datastore.**` (both ship their own consumer rules).

## [v0.0.6] - 2026-08-22

### Added
- **Upload ZIP to extract** — pick a `.zip` archive from anywhere on
  the device (SAF picker) and ViperCode extracts it into a new
  subfolder under `projects/` (separate from the default `workspace/`
  folder). The extracted project is opened automatically as the
  current folder so you can start editing immediately. Re-uploading
  the same ZIP never overwrites the previously extracted copy (a
  numeric suffix is appended). Path-traversal entries are skipped
  (ZipSlip mitigation).
- **Switch folder** bottom sheet — a new icon in the top bar opens
  a sheet that lists the local workspace, every extracted project,
  and all recently-opened SAF folders in one place. Jump between
  them without re-opening the SAF picker every time.
- **Browse device storage** menu item — launches the SAF picker
  with an explicit initial URI pointing at the device's primary
  shared storage root. The plain "Open folder (SAF)" item keeps
  the old behaviour (reopens the last-used location) for users
  who actually want that.
- **Recent folders list** — every folder the user opens (workspace,
  extracted project, or SAF folder) is added to a recent list
  persisted across launches. The Switch-folder sheet consumes this
  list.
- **Empty-folder hint** — when the open folder is non-null but
  contains no files (e.g. a freshly-created local workspace), the
  explorer now shows an explicit "This folder is empty — use the +
  button to create a new file" hint instead of a blank tree.
- **Toast-style feedback** — extraction progress, success and
  failure are now surfaced via a snackbar-style overlay so the
  user always knows what happened.
- **Extraction spinner** — the FAB turns into a progress spinner
  while a ZIP is being extracted so the user gets visible feedback
  that the operation is in progress.

### Changed
- **Editor top bar condensed** — the 8+ icon buttons that used to
  overflow horizontally on narrow phones have been reorganised:
  Search, Save and Live-preview stay in the bar; Go-to-line,
  Comment toggle, Quick open, Search in files and Share moved
  into a "More" overflow menu.
- **Settings chip rows now wrap** — Theme, Language, Sort and Font
  family selectors used `Row` (no wrap), so on small screens a
  long chip label could push every subsequent chip off-screen.
  They now use `FlowRow` so chips wrap to the next line.
- **About screen** — tagline is now capped to a single line with
  ellipsis, BulletList items wrap to the next line, and InfoLine
  values ellipsize so long URLs no longer push labels off-screen.
- **Editor subtitle** — language/encoding/read-only string is now
  ellipsized to a single line so it never overflows the top bar.
- **Home top bar title** — the redundant tagline subtitle
  ("Đẳng cấp hoàn hảo" in Vietnamese, "The class of perfection"
  in English) has been removed so the title row no longer
  overflows on narrow screens. The tagline still lives on the
  splash and About screens where there's plenty of horizontal
  room.

### Fixed
- **"Dùng thư mục cục bộ" / "Use local workspace" did nothing** —
  the v0.0.5 click handler set `lastFolderUri` and called
  `openFolder`, but the initial `LaunchedEffect(Unit)` had already
  consumed the initial open so the new state never updated for the
  freshly-tapped directory. v0.0.6 force-refreshes the directory
  after the tap, also flips the `useLocalWorkspace` preference to
  `true` so the workspace persists on next launch, and adds the
  folder to the recent-folders list so it shows up in the
  switch-folder sheet.
- **"Mở thư mục" / "Open folder" only opened the Termux area** —
  the SAF picker remembers the last-used location across launches,
  so on devices where Termux was the most recently-used provider
  the user only saw Termux storage and reported "the picker doesn't
  show the rest of the device". v0.0.6 adds an explicit
  "Browse device storage" menu item that launches the SAF picker
  with an initial URI pointing at the device's primary shared
  storage root, so the picker starts at the device root and the
  user can navigate to any folder from there.
- **File explorer empty-state strings were hardcoded English** —
  "No folder opened" and "Pick a folder to start coding" were
  never routed through the i18n catalogue. v0.0.6 routes them
  through `Strings.get()` so the empty state honours the user's
  language preference.

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
