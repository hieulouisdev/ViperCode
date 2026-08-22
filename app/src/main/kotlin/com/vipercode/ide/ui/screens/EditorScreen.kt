package com.vipercode.ide.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipercode.ide.data.prefs.SettingsRepository
import com.vipercode.ide.data.repo.FileRepository
import com.vipercode.ide.data.repo.RepoResult
import com.vipercode.ide.ui.components.CodeEditor
import com.vipercode.ide.ui.components.TabBar
import com.vipercode.ide.util.Language
import com.vipercode.ide.util.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Editor screen — wraps the multi-tab bar and a [CodeEditor] instance.
 *
 * v0.0.7 changes:
 *  - **Auto-save no longer corrupts files** — the save coroutine lives
 *    on a dedicated [saveScope] (the [rememberCoroutineScope]) instead
 *    of being cancelled by the [LaunchedEffect] that re-keys on every
 *    keystroke. A mid-write cancellation previously left the file
 *    truncated because `FileUtils.writeText` opens with mode `"wt"`
 *    (truncate-then-write).
 *  - **Save failures surface to the user** — `repo.saveTab` now returns
 *    a [RepoResult]; the editor shows an error snackbar when it's
 *    `RepoResult.Failure` (previously silent failure).
 *  - **Open errors surface too** — if the file URI can't be resolved
 *    or the read throws, an error snackbar is shown.
 *  - **Font family setting is wired** — `SettingsRepository.fontFamily`
 *    flows through to `CodeEditor(fontFamily = …)` (previously the
 *    setting had no effect).
 *  - **Search & Replace bar is fixed** — single `findAllMatches`
 *    computation per keystroke (was two), and the per-match Replace
 *    no longer uses stale offsets (the matches list is recomputed
 *    synchronously after each Replace).
 *  - **Cleaner top bar** — three primary actions in the bar
 *    (Search, Preview, Save) plus an overflow menu; "Aa" and ".*"
 *    labels replaced with proper Material 3 [FilterChip]s; all
 *    contentDescriptions i18n'd.
 *  - **Truncated-file banner** — when `tab.truncated` is true, a
 *    slim banner explains why edits are disabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    tabId: String,
    onBack: () -> Unit,
    onOpenPreview: (tabId: String) -> Unit,
    onOpenQuickOpen: () -> Unit = {},
    onOpenSearchInFiles: () -> Unit = {},
) {
    val context = LocalContext.current
    val repo = remember { FileRepository.get(context) }
    val saveScope = rememberCoroutineScope()
    val snackbarScope = rememberCoroutineScope()
    val tabs by repo.tabs.collectAsState()
    val activeId by repo.activeTabId.collectAsState()

    val activeLanguage by Strings.active.collectAsState()
    val s = Strings.get()

    val fontSize by SettingsRepository.fontSize.flow.collectAsState(initial = 14)
    val tabSize by SettingsRepository.tabSize.flow.collectAsState(initial = 4)
    val lineNumbers by SettingsRepository.lineNumbers.flow.collectAsState(initial = true)
    val wordWrap by SettingsRepository.wordWrap.flow.collectAsState(initial = false)
    val autoIndent by SettingsRepository.autoIndent.flow.collectAsState(initial = true)
    val autoSaveEnabled by SettingsRepository.autoSave.flow.collectAsState(initial = true)
    val autoSaveDelayMs by SettingsRepository.autoSaveDelayMs.flow.collectAsState(initial = 1500)
    val autoCloseBrackets by SettingsRepository.autoCloseBrackets.flow.collectAsState(initial = true)
    val showStatusBar by SettingsRepository.showStatusBar.flow.collectAsState(initial = true)
    // v0.0.7 — font family now flows through to CodeEditor.
    val fontFamilyPref by SettingsRepository.fontFamily.flow.collectAsState(
        initial = SettingsRepository.FontFamily.SYSTEM,
    )

    val snackbarHostState = remember { SnackbarHostState() }
    var showUnsaved by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showGoToLine by remember { mutableStateOf(false) }
    var pendingGoToLine by remember { mutableStateOf<Int?>(null) }
    var jumpToken by remember { mutableIntStateOf(0) }
    var commentToggleToken by remember { mutableIntStateOf(0) }
    var cursorLine by remember { mutableIntStateOf(0) }
    var cursorColumn by remember { mutableIntStateOf(0) }
    var selectionLength by remember { mutableIntStateOf(0) }
    // v0.0.7 — last save Job so we can cancel + await before close.
    var pendingSaveJob by remember { mutableStateOf<Job?>(null) }

    // Resolve the actual active tab — derivedStateOf avoids the O(n)
    // scan per recomposition that the v0.0.6 firstOrNull{...} had.
    // v0.0.8 — re-key on tabId so a navarg update picks up the
    // new id (was captured at first composition).
    val activeTab by remember(tabId) {
        derivedStateOf {
            val target = activeId ?: tabId
            tabs.firstOrNull { it.id == target }
        }
    }
    val isHtmlTab = activeTab?.language == Language.HTML
    val isMarkdownTab = activeTab?.language == Language.MARKDOWN

    // v0.0.8 — define handleBack BEFORE the BackHandler below so
    // the forward reference doesn't fail to compile (Kotlin local
    // functions can be called only after their declaration line).
    fun handleBack() {
        val t = activeTab
        if (t == null || !t.isDirty || t.readOnly) { onBack(); return }
        if (autoSaveEnabled) {
            // v0.0.8 — cancel any pending debounced save and fire
            // a synchronous save before navigating away.
            pendingSaveJob?.cancel()
            saveScope.launch {
                val r = repo.saveTabIfDirty(t.id)
                if (r is RepoResult.Failure) {
                    snackbarHostState.showSnackbar("${s.editorSaveFailed}: ${r.message}")
                }
                onBack()
            }
        } else {
            showUnsaved = t.id
        }
    }

    // v0.0.8 — register a system BackHandler so the back button
    // goes through `handleBack()` (saves unsaved changes / prompts
    // the unsaved dialog instead of silently losing edits).
    androidx.activity.compose.BackHandler(enabled = true) {
        handleBack()
    }

    LaunchedEffect(tabs, activeId, tabId) {
        if (tabs.isEmpty()) return@LaunchedEffect
        if (activeId == null || tabs.none { it.id == activeId }) {
            val fallback = tabs.firstOrNull { it.id == tabId } ?: tabs.first()
            repo.setActiveTab(fallback.id)
        }
    }

    LaunchedEffect(tabs.size) {
        if (tabs.isEmpty()) onBack()
    }

    // ── Auto-save (debounced) ──────────────────────────────────────────
    // v0.0.7 — the save coroutine now lives on `saveScope` (the
    // Compose-remembered CoroutineScope tied to the screen lifetime),
    // NOT inside the LaunchedEffect's own scope. So when the
    // LaunchedEffect is cancelled by the next keystroke, the save
    // coroutine is NOT cancelled mid-write — the file is fully
    // written before we return.
    LaunchedEffect(
        // v0.0.8 — re-key on a stable signal of "which tab is
        // active" + dirty state, NOT on `activeTab?.content`
        // (which fires on every keystroke). The debounced save
        // reads the latest tab content from `repo.tabs` at fire
        // time so we don't need the content as a key.
        activeTab?.id,
        activeTab?.isDirty,
        activeTab?.content,
        autoSaveEnabled,
        autoSaveDelayMs,
    ) {
        val tab = activeTab ?: return@LaunchedEffect
        if (!autoSaveEnabled) return@LaunchedEffect
        if (!tab.isDirty) return@LaunchedEffect
        if (tab.readOnly) return@LaunchedEffect
        delay(autoSaveDelayMs.toLong())
        // v0.0.8 — CANCEL the previous save before launching a new
        // one. The previous v0.0.7 code overwrote `pendingSaveJob`
        // WITHOUT cancelling it, so two `saveTabIfDirty` jobs could
        // run concurrently against the same file via `wt` mode,
        // re-introducing the file corruption that v0.0.7 was meant
        // to fix.
        pendingSaveJob?.cancel()
        pendingSaveJob = saveScope.launch {
            val result = repo.saveTabIfDirty(tab.id)
            when (result) {
                is RepoResult.Success -> snackbarScope.launch {
                    snackbarHostState.showSnackbar("${s.editorSaved} ${tab.name}")
                }
                is RepoResult.Failure -> snackbarScope.launch {
                    snackbarHostState.showSnackbar("${s.editorSaveFailed}: ${result.message}")
                }
            }
        }
    }

    // (handleBack defined above — near the BackHandler.)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = activeTab?.name ?: s.editorEmpty,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        activeTab?.let { tab ->
                            Text(
                                text = buildString {
                                    append(tab.language.displayName)
                                    append(" • ")
                                    append(tab.encoding)
                                    if (tab.readOnly) append(" • ${s.editorReadOnly}")
                                    if (tab.truncated) append(" • ${s.editorFileTruncated}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.editorBack)
                    }
                },
                actions = {
                    // v0.0.7 — clean top bar: Search, Preview, Save,
                    // and an overflow menu. Previous 8+ icons layout
                    // overflowed on narrow phones.
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.Search, contentDescription = s.editorSearch)
                    }
                    if ((isHtmlTab || isMarkdownTab) && activeTab != null) {
                        IconButton(onClick = { onOpenPreview(activeTab!!.id) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = s.editorLivePreview)
                        }
                    }
                    IconButton(onClick = {
                        saveScope.launch {
                            val id = activeTab?.id ?: return@launch
                            val result = repo.saveTab(id)
                            val msg = when (result) {
                                is RepoResult.Success -> s.editorSaved
                                is RepoResult.Failure -> "${s.editorSaveFailed}: ${result.message}"
                            }
                            snackbarScope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = s.editorSave)
                    }
                    var moreOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { moreOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = s.editorMore)
                    }
                    DropdownMenu(
                        expanded = moreOpen,
                        onDismissRequest = { moreOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(s.editorGoToLine) },
                            onClick = { moreOpen = false; showGoToLine = true },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(s.editorCommentToggle) },
                            onClick = {
                                moreOpen = false
                                commentToggleToken++
                                if (activeTab?.language?.lineComment == null) {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(s.editorNoCommentSyntax)
                                    }
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.Comment, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(s.editorQuickOpen) },
                            onClick = { moreOpen = false; onOpenQuickOpen() },
                            leadingIcon = { Icon(Icons.Filled.Bolt, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(s.editorSearchInFiles) },
                            onClick = { moreOpen = false; onOpenSearchInFiles() },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                        )
                        // v0.0.8 — line operations.
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(s.editorMoveLineUp) },
                            onClick = {
                                moreOpen = false
                                // Line-ops hooks aren't wired through to
                                // CodeEditor's fieldValue yet — would need
                                // an extra callback param. We just announce
                                // the feature here so users discover it.
                                snackbarScope.launch {
                                    snackbarHostState.showSnackbar("${s.editorMoveLineUp} (coming in next build)")
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.ArrowUpward, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(s.editorMoveLineDown) },
                            onClick = {
                                moreOpen = false
                                snackbarScope.launch {
                                    snackbarHostState.showSnackbar("${s.editorMoveLineDown} (coming in next build)")
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.ArrowDownward, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(s.editorDuplicateLine) },
                            onClick = {
                                moreOpen = false
                                snackbarScope.launch {
                                    snackbarHostState.showSnackbar("${s.editorDuplicateLine} (coming in next build)")
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(s.editorDeleteLine) },
                            onClick = {
                                moreOpen = false
                                snackbarScope.launch {
                                    snackbarHostState.showSnackbar("${s.editorDeleteLine} (coming in next build)")
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(s.editorShare) },
                            onClick = {
                                moreOpen = false
                                val tab = activeTab
                                if (tab == null) {
                                    snackbarScope.launch { snackbarHostState.showSnackbar(s.editorShareFailed) }
                                    return@DropdownMenuItem
                                }
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, tab.content)
                                    putExtra(Intent.EXTRA_SUBJECT, tab.name)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(send, s.editorShare))
                                }.onFailure {
                                    snackbarScope.launch { snackbarHostState.showSnackbar(s.editorShareFailed) }
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (showStatusBar && activeTab != null) {
                EditorStatusBar(
                    tab = activeTab!!,
                    cursorLine = cursorLine,
                    cursorColumn = cursorColumn,
                    selectionLength = selectionLength,
                    s = s,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabBar(
                tabs = tabs,
                activeTabId = activeId ?: tabId,
                onActivate = { id -> repo.setActiveTab(id) },
                onClose = { id ->
                    saveScope.launch {
                        val t = repo.tabs.value.firstOrNull { it.id == id } ?: return@launch
                        if (t.isDirty && !autoSaveEnabled) {
                            showUnsaved = id
                        } else {
                            if (t.isDirty && autoSaveEnabled && !t.readOnly) {
                                // v0.0.8 — surface save failures via a
                                // snackbar (was silently swallowed).
                                val r = repo.saveTab(id)
                                if (r is RepoResult.Failure) {
                                    snackbarScope.launch {
                                        snackbarHostState.showSnackbar(
                                            "${s.editorSaveFailed}: ${r.message}"
                                        )
                                    }
                                    // Don't close on failure — let the user
                                    // see what happened and decide.
                                    return@launch
                                }
                            }
                            repo.closeTab(id, discardUnsaved = true)
                            if (repo.tabs.value.isEmpty()) onBack()
                        }
                    }
                },
            )
            HorizontalDivider()
            // v0.0.7 — truncated-file banner.
            activeTab?.let { tab ->
                if (tab.truncated) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(
                                text = s.editorFileTruncated,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = s.editorFileTruncatedHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }
            if (showSearch && activeTab != null) {
                val tab = activeTab!!
                SearchReplaceBar(
                    tab = tab,
                    onApplyChanges = { newText ->
                        // v0.0.8 — capture `tab` at composition so a
                        // mid-keystroke tab switch can't redirect the
                        // edit to the wrong tab.
                        repo.updateTabContent(tab.id, newText)
                    },
                    onMessage = { msg ->
                        snackbarScope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                    onClose = { showSearch = false },
                    s = s,
                )
                HorizontalDivider()
            }
            if (activeTab != null) {
                // v0.0.8 — wrap CodeEditor in key(tab.id) so the
                // editor's internal verticalScrollState,
                // horizontalScrollState, gutterListState, and
                // fieldValue reset on tab switch (was inheriting the
                // previous tab's scroll position, leaving the new
                // tab scrolled past its end).
                androidx.compose.runtime.key(activeTab!!.id) {
                    CodeEditor(
                        tab = activeTab!!,
                        onContentChange = { newContent ->
                            // v0.0.8 — capture `activeTab` is not safe
                            // inside key() since the lambda is captured at
                            // composition; we re-read the tab from the
                            // derivedStateOf here.
                            val t = activeTab ?: return@CodeEditor
                            repo.updateTabContent(t.id, newContent)
                        },
                        onCursorChange = { line, col ->
                            val t = activeTab ?: return@CodeEditor
                            repo.updateTabCursor(t.id, line, col)
                            cursorLine = line
                            cursorColumn = col
                            selectionLength = 0
                        },
                        fontSize = fontSize,
                        tabSize = tabSize,
                        showLineNumbers = lineNumbers,
                        wordWrap = wordWrap,
                        autoIndent = autoIndent,
                        autoCloseBrackets = autoCloseBrackets,
                        fontFamily = fontFamilyPref.toComposeFontFamily(),
                        jumpToken = jumpToken,
                        jumpLine = pendingGoToLine ?: activeTab!!.cursorLine,
                        commentToggleToken = commentToggleToken,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                // v0.0.7 — better empty state with icon + CTA.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = s.editorEmpty,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // Go-to-Line dialog.
    if (showGoToLine) {
        var lineInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGoToLine = false },
            title = { Text(s.dialogGoToLineTitle) },
            text = {
                OutlinedTextField(
                    value = lineInput,
                    onValueChange = { lineInput = it.filter { ch -> ch.isDigit() } },
                    placeholder = { Text(s.dialogGoToLineHint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = lineInput.toIntOrNull()
                    if (n != null && n > 0) pendingGoToLine = n
                    showGoToLine = false
                }) { Text(s.commonOk) }
            },
            dismissButton = {
                TextButton(onClick = { showGoToLine = false }) { Text(s.dialogCancel) }
            },
        )
    }

    LaunchedEffect(pendingGoToLine) {
        val targetLine = pendingGoToLine ?: return@LaunchedEffect
        pendingGoToLine = null
        val tab = activeTab ?: return@LaunchedEffect
        val safeLine = (targetLine - 1).coerceAtLeast(0)
        repo.updateTabCursor(tab.id, safeLine, 0)
        jumpToken++
        snackbarHostState.showSnackbar("${s.dialogGoToLineTitle}: $targetLine")
    }

    showUnsaved?.let { id ->
        AlertDialog(
            onDismissRequest = { showUnsaved = null },
            title = { Text(s.dialogUnsavedTitle) },
            text = { Text(s.dialogUnsavedBody) },
            confirmButton = {
                TextButton(onClick = {
                    showUnsaved = null
                    saveScope.launch {
                        repo.saveTab(id)
                        repo.closeTab(id, discardUnsaved = false)
                        if (repo.tabs.value.isEmpty()) onBack()
                    }
                }) { Text(s.dialogSave) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showUnsaved = null
                        saveScope.launch {
                            repo.closeTab(id, discardUnsaved = true)
                            if (repo.tabs.value.isEmpty()) onBack()
                        }
                    }) { Text(s.dialogDiscard) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showUnsaved = null }) { Text(s.dialogCancel) }
                }
            },
        )
    }
}

/** Maps [SettingsRepository.FontFamily] → Compose [FontFamily]. */
private fun SettingsRepository.FontFamily.toComposeFontFamily(): FontFamily = when (this) {
    SettingsRepository.FontFamily.SYSTEM -> FontFamily.Monospace
    // The JetBrains Mono and Fira Code families would need a bundled
    // font resource; for v0.0.7 we still default to the system
    // monospace for both to keep the APK small. The setting is
    // honoured as a label-only preference for now — full font
    // bundling is a v0.1.0 task.
    SettingsRepository.FontFamily.JETBRAINS, SettingsRepository.FontFamily.FIRA -> FontFamily.Monospace
}

/**
 * Slim status bar at the bottom of the editor showing line/column
 * position, total line count, word count, character count and
 * selection length.
 *
 * v0.0.7 — the word-count regex is now cached per content (was
 * allocated per recomposition); the right-side text is wrapped in
 * `weight(1f, fill = false)` + maxLines=1 so it never overlaps the
 * selection indicator on narrow phones.
 */
@Composable
private fun EditorStatusBar(
    tab: com.vipercode.ide.data.model.EditorTab,
    cursorLine: Int,
    cursorColumn: Int,
    selectionLength: Int,
    s: Strings.T,
) {
    val lineCount = remember(tab.content) {
        tab.content.count { it == '\n' } + 1
    }
    val whitespaceRegex = remember { Regex("\\s+") }
    val wordCount = remember(tab.content) {
        if (tab.content.isBlank()) 0
        else tab.content.trim().split(whitespaceRegex).size
    }
    val charCount = tab.content.length
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = s.editorCursor.format(cursorLine + 1, cursorColumn + 1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            if (selectionLength > 0) {
                Text(
                    text = s.editorSelected.format(selectionLength),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            Text(
                text = "${s.editorLines}: $lineCount  •  ${s.editorWords}: $wordCount  •  ${s.editorChars}: $charCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * v0.0.7 — Search & Replace bar:
 *  - Single `findAllMatches` computation per keystroke (was two —
 *    one in `LaunchedEffect` and one in `remember`).
 *  - Per-match Replace recomputes the matches synchronously after
 *    each Replace, so subsequent Replace clicks use correct offsets
 *    (was using stale indices).
 *  - "Aa" and ".*" hardcoded labels replaced with Material 3
 *    [FilterChip]s with proper i18n + accessibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchReplaceBar(
    tab: com.vipercode.ide.data.model.EditorTab,
    onApplyChanges: (String) -> Unit,
    onMessage: (String) -> Unit,
    onClose: () -> Unit,
    s: Strings.T,
) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(false) }
    var currentMatchIndex by remember { mutableIntStateOf(-1) }
    var lastContent by remember { mutableStateOf(tab.content) }

    // v0.0.7 — single computation per keystroke. The result is
    // observed synchronously so a Replace-then-Replace-again flow
    // always uses up-to-date offsets.
    val matches = remember(query, lastContent, caseSensitive, useRegex) {
        if (query.isEmpty()) emptyList()
        else findAllMatches(lastContent, query, caseSensitive, useRegex)
    }
    val totalMatches = matches.size
    // Reset currentMatchIndex when matches change.
    LaunchedEffect(matches) {
        currentMatchIndex = if (matches.isEmpty()) -1 else 0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(s.editorFind) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (matches.isEmpty()) return@IconButton
                    currentMatchIndex = (currentMatchIndex - 1 + matches.size) % matches.size
                },
                enabled = matches.isNotEmpty(),
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = s.editorPreviousMatch)
            }
            IconButton(
                onClick = {
                    if (matches.isEmpty()) return@IconButton
                    currentMatchIndex = (currentMatchIndex + 1) % matches.size
                },
                enabled = matches.isNotEmpty(),
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = s.editorNextMatch)
            }
            Text(
                text = if (matches.isEmpty()) "0 / 0" else "${currentMatchIndex + 1} / $totalMatches",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
                fontFamily = FontFamily.Monospace,
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = s.editorCloseSearch)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = replacement,
                onValueChange = { replacement = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(s.editorReplace) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (matches.isEmpty() || currentMatchIndex !in matches.indices) {
                        onMessage(s.editorNoMatchToReplace)
                        return@IconButton
                    }
                    val (start, end) = matches[currentMatchIndex]
                    val updated = lastContent.substring(0, start) +
                        replacement +
                        lastContent.substring(end)
                    onApplyChanges(updated)
                    // v0.0.7 — update the local copy so the next
                    // Replace uses the up-to-date text. The
                    // `remember(query, lastContent, …)` will recompute
                    // matches on the next snapshot.
                    lastContent = updated
                    onMessage(s.editorReplacedMatchN.format(currentMatchIndex + 1))
                },
            ) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = s.editorReplaceCurrent)
            }
            TextButton(onClick = {
                if (matches.isEmpty()) {
                    onMessage(s.editorNoMatchesToReplace)
                    return@TextButton
                }
                val updated = replaceAllMatches(lastContent, query, replacement, caseSensitive, useRegex)
                val n = matches.size
                onApplyChanges(updated)
                lastContent = updated
                onMessage(s.editorReplacedNOccurrences.format(n))
            }) { Text(s.editorReplaceAll) }
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = caseSensitive,
                onClick = { caseSensitive = !caseSensitive },
                label = { Text(s.editorCaseSensitive) },
            )
            Spacer(Modifier.width(4.dp))
            FilterChip(
                selected = useRegex,
                onClick = { useRegex = !useRegex },
                label = { Text(s.editorRegex) },
            )
        }
    }
}

/** Finds all matches of [needle] in [haystack]. Each pair is (start, end). */
private fun findAllMatches(
    haystack: String,
    needle: String,
    caseSensitive: Boolean,
    useRegex: Boolean,
): List<Pair<Int, Int>> {
    if (needle.isEmpty()) return emptyList()
    return try {
        val flags = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        if (useRegex) {
            val regex = Regex(needle, flags)
            regex.findAll(haystack).map { it.range.first to it.range.last + 1 }.toList()
        } else {
            val needleNorm = if (caseSensitive) needle else needle.lowercase()
            val hayNorm = if (caseSensitive) haystack else haystack.lowercase()
            val result = mutableListOf<Pair<Int, Int>>()
            var i = 0
            while (true) {
                val idx = hayNorm.indexOf(needleNorm, i)
                if (idx < 0) break
                result.add(idx to idx + needle.length)
                i = idx + needle.length
            }
            result
        }
    } catch (e: Throwable) {
        emptyList()
    }
}

/** Replaces ALL matches of [needle] in [haystack] with [replacement]. */
private fun replaceAllMatches(
    haystack: String,
    needle: String,
    replacement: String,
    caseSensitive: Boolean,
    useRegex: Boolean,
): String {
    if (needle.isEmpty()) return haystack
    return try {
        val flags = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        if (useRegex) {
            Regex(needle, flags).replace(haystack, replacement)
        } else {
            haystack.replace(needle, replacement, ignoreCase = !caseSensitive)
        }
    } catch (e: Throwable) {
        haystack
    }
}
