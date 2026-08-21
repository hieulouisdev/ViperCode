package com.vipercode.ide.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.vipercode.ide.ui.components.CodeEditor
import com.vipercode.ide.ui.components.TabBar
import com.vipercode.ide.util.Language
import com.vipercode.ide.util.Strings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Editor screen — wraps the multi-tab bar and a [CodeEditor] instance.
 *
 * v0.0.5 changes:
 *  - **Comment toggle** button in the top bar — bumps
 *    [commentToggleToken] which the [CodeEditor] picks up via
 *    `LaunchedEffect` to toggle line-comment on the current
 *    selection. Picks the right comment syntax per language
 *    (`#` for Python, `//` for Kotlin/Java/JS, `--` for SQL/Lua,
 *    etc.).
 *  - **Share file** button — exports the current file's content via
 *    Android's share sheet.
 *  - **Editor status bar** — slim bar at the bottom showing cursor
 *    line / column, total line count, word count and character
 *    count. Toggleable in Settings.
 *  - **Auto-close brackets** setting passed through to [CodeEditor].
 *  - All UI strings routed through [Strings.get] (Vietnamese support).
 *  - **Go to line**: top-bar action asks for a line number and
 *    restores the caret there. Lives in [showGoToLine].
 *  - **Quick open / Search in files**: top-bar shortcuts that hand
 *    off to dedicated screens.
 *  - **Tab title shows the file path** in the subtitle for easier
 *    orientation in big workspaces.
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
    val scope = rememberCoroutineScope()
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
    // v0.0.5 — new editor prefs.
    val autoCloseBrackets by SettingsRepository.autoCloseBrackets.flow.collectAsState(initial = true)
    val showStatusBar by SettingsRepository.showStatusBar.flow.collectAsState(initial = true)

    val snackbarHostState = remember { SnackbarHostState() }
    var showUnsaved by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showGoToLine by remember { mutableStateOf(false) }
    var pendingGoToLine by remember { mutableStateOf<Int?>(null) }
    var jumpToken by remember { mutableIntStateOf(0) }
    // v0.0.5 — comment-toggle hook.
    var commentToggleToken by remember { mutableIntStateOf(0) }
    // v0.0.5 — cursor position for the status bar.
    var cursorLine by remember { mutableIntStateOf(0) }
    var cursorColumn by remember { mutableIntStateOf(0) }
    var selectionLength by remember { mutableIntStateOf(0) }

    // Resolve the actual active tab — prefer the repo's activeTabId,
    // fall back to the tabId from the route so the editor is never
    // empty.
    val activeTab = tabs.firstOrNull { it.id == (activeId ?: tabId) }
    val isHtmlTab = activeTab?.language == Language.HTML
    val isMarkdownTab = activeTab?.language == Language.MARKDOWN

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

    // ── Auto-save (debounced) ──────────────────────────────────────
    LaunchedEffect(
        activeTab?.id,
        activeTab?.content,
        autoSaveEnabled,
        autoSaveDelayMs,
    ) {
        val tab = activeTab ?: return@LaunchedEffect
        if (!autoSaveEnabled) return@LaunchedEffect
        if (!tab.isDirty) return@LaunchedEffect
        if (tab.readOnly) return@LaunchedEffect
        delay(autoSaveDelayMs.toLong())
        val ok = repo.saveTabIfDirty(tab.id)
        if (ok) {
            snackbarHostState.showSnackbar("${s.editorSaved} ${tab.name}")
        }
    }

    // Back-button handler — flush auto-save before navigating away so
    // no content typed < delay is lost.
    fun handleBack() {
        val t = activeTab
        if (t == null) {
            onBack()
            return
        }
        if (!t.isDirty || t.readOnly) {
            onBack()
            return
        }
        if (autoSaveEnabled) {
            scope.launch {
                repo.saveTabIfDirty(t.id)
                onBack()
            }
        } else {
            showUnsaved = t.id
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeTab?.name ?: s.editorEmpty,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        activeTab?.let { tab ->
                            Text(
                                text = tab.language.displayName + " • ${tab.encoding}" +
                                    if (tab.readOnly) " • ${s.editorReadOnly}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.Search, contentDescription = s.editorSearch)
                    }
                    IconButton(onClick = { showGoToLine = true }) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = s.editorGoToLine)
                    }
                    // v0.0.5 — comment toggle.
                    IconButton(onClick = { commentToggleToken++ }) {
                        Icon(Icons.Filled.Comment, contentDescription = s.editorCommentToggle)
                    }
                    IconButton(onClick = onOpenQuickOpen) {
                        Icon(Icons.Filled.Bolt, contentDescription = s.editorQuickOpen)
                    }
                    IconButton(onClick = onOpenSearchInFiles) {
                        Icon(Icons.Filled.Search, contentDescription = s.editorSearchInFiles)
                    }
                    // v0.0.5 — share file content via Android share sheet.
                    IconButton(onClick = {
                        val tab = activeTab
                        if (tab == null) {
                            scope.launch { snackbarHostState.showSnackbar(s.editorShareFailed) }
                            return@IconButton
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, tab.content)
                            putExtra(Intent.EXTRA_SUBJECT, tab.name)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(send, s.editorShare))
                        }.onFailure {
                            scope.launch { snackbarHostState.showSnackbar(s.editorShareFailed) }
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = s.editorShare)
                    }
                    if ((isHtmlTab || isMarkdownTab) && activeTab != null) {
                        IconButton(onClick = { onOpenPreview(activeTab.id) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = s.editorLivePreview)
                        }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val id = activeTab?.id ?: return@launch
                            val ok = repo.saveTab(id)
                            if (ok) snackbarHostState.showSnackbar(s.editorSaved)
                            else snackbarHostState.showSnackbar(s.editorSaveFailed)
                        }
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = s.editorSave)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            // v0.0.5 — editor status bar.
            if (showStatusBar && activeTab != null) {
                EditorStatusBar(
                    tab = activeTab,
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
                    scope.launch {
                        val t = repo.tabs.value.firstOrNull { it.id == id } ?: return@launch
                        if (t.isDirty && !autoSaveEnabled) {
                            showUnsaved = id
                        } else {
                            if (t.isDirty && autoSaveEnabled && !t.readOnly) {
                                repo.saveTab(id)
                            }
                            repo.closeTab(id, discardUnsaved = true)
                            if (repo.tabs.value.isEmpty()) onBack()
                        }
                    }
                },
            )
            HorizontalDivider()
            if (showSearch && activeTab != null) {
                SearchReplaceBar(
                    tab = activeTab,
                    onApplyChanges = { newText ->
                        repo.updateTabContent(activeTab.id, newText)
                    },
                    onMessage = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                    onClose = { showSearch = false },
                )
                HorizontalDivider()
            }
            if (activeTab != null) {
                CodeEditor(
                    tab = activeTab,
                    onContentChange = { newContent ->
                        repo.updateTabContent(activeTab.id, newContent)
                    },
                    onCursorChange = { line, col ->
                        repo.updateTabCursor(activeTab.id, line, col)
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
                    jumpToken = jumpToken,
                    jumpLine = pendingGoToLine ?: activeTab.cursorLine,
                    commentToggleToken = commentToggleToken,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = s.editorEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    if (n != null && n > 0) {
                        pendingGoToLine = n
                    }
                    showGoToLine = false
                }) { Text(s.commonOk) }
            },
            dismissButton = {
                TextButton(onClick = { showGoToLine = false }) { Text(s.dialogCancel) }
            },
        )
    }

    // Apply the requested go-to-line by storing the cursor on the tab
    // AND bumping `jumpToken` so the CodeEditor's `LaunchedEffect`
    // picks up the new line and moves the caret + scroll position.
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
                    scope.launch {
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
                        scope.launch {
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

/**
 * v0.0.5 — slim status bar at the bottom of the editor showing
 * line/column position, total line count, word count, character count
 * and selection length.
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
    val wordCount = remember(tab.content) {
        if (tab.content.isBlank()) 0
        else tab.content.trim().split(Regex("\\s+")).size
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
                )
            }
            Text(
                text = "${s.editorLines}: $lineCount  •  ${s.editorWords}: $wordCount  •  ${s.editorChars}: $charCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * Upgraded Search & Replace bar (v0.0.3, i18n'd in v0.0.4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchReplaceBar(
    tab: com.vipercode.ide.data.model.EditorTab,
    onApplyChanges: (String) -> Unit,
    onMessage: (String) -> Unit,
    onClose: () -> Unit,
) {
    val activeLanguage by Strings.active.collectAsState()
    val s = Strings.get()

    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(false) }
    var currentMatchIndex by remember { mutableIntStateOf(-1) }
    var totalMatches by remember { mutableIntStateOf(0) }

    LaunchedEffect(query, tab.content, caseSensitive, useRegex) {
        if (query.isEmpty()) {
            totalMatches = 0
            currentMatchIndex = -1
            return@LaunchedEffect
        }
        val matches = findAllMatches(tab.content, query, caseSensitive, useRegex)
        totalMatches = matches.size
        currentMatchIndex = if (matches.isEmpty()) -1 else 0
    }

    val matches = remember(query, tab.content, caseSensitive, useRegex) {
        if (query.isEmpty()) emptyList()
        else findAllMatches(tab.content, query, caseSensitive, useRegex)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(s.editorFind) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = if (caseSensitive) KeyboardCapitalization.Sentences
                    else KeyboardCapitalization.None,
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
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = s.editorPreviousMatch)
            }
            IconButton(
                onClick = {
                    if (matches.isEmpty()) return@IconButton
                    currentMatchIndex = (currentMatchIndex + 1) % matches.size
                },
                enabled = matches.isNotEmpty(),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = s.editorNextMatch)
            }
            Text(
                text = if (matches.isEmpty()) "0 / 0" else "${currentMatchIndex + 1} / ${totalMatches}",
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
                    val updated = tab.content.substring(0, start) +
                        replacement +
                        tab.content.substring(end)
                    onApplyChanges(updated)
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
                val updated = replaceAllMatches(tab.content, query, replacement, caseSensitive, useRegex)
                val n = matches.size
                onApplyChanges(updated)
                onMessage(s.editorReplacedNOccurrences.format(n))
            }) { Text(s.editorReplaceAll) }
            Spacer(Modifier.width(4.dp))
            Text("Aa", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Switch(checked = caseSensitive, onCheckedChange = { caseSensitive = it })
            Spacer(Modifier.width(4.dp))
            Text(".*", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Switch(checked = useRegex, onCheckedChange = { useRegex = it })
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
            val from = if (caseSensitive) needle else needle.lowercase()
            val hay = if (caseSensitive) haystack else haystack.lowercase()
            if (from.isEmpty()) haystack
            else haystack.replace(from, replacement, ignoreCase = !caseSensitive)
        }
    } catch (e: Throwable) {
        haystack
    }
}
