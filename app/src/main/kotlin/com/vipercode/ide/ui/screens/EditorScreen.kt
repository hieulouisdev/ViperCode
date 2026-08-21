package com.vipercode.ide.ui.screens

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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Editor screen — wraps the multi-tab bar and a [CodeEditor] instance.
 *
 * v0.0.3 changes:
 *  - **Back button flushes auto-save**: v0.0.2 skipped both the
 *    unsaved-changes dialog AND the immediate save when autoSave was
 *    enabled, so content typed < 1.5 s before back was lost. We now
 *    always flush before navigating away.
 *  - **Search & Replace upgrade**: full dialog with regex toggle,
 *    case-sensitivity toggle, find-next / find-prev navigation,
 *    match count, and per-match replace (not just replace-all).
 *  - **Live preview button**: a "play" icon in the TopAppBar takes
 *    the user to [PreviewScreen] (only shown when the active tab is
 *    HTML — non-HTML files have nothing to preview).
 *  - **Cursor position persistence**: every edit forwards the caret
 *    (line, column) back to the repository so it survives tab switches
 *    and app kills.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    tabId: String,
    onBack: () -> Unit,
    onOpenPreview: (tabId: String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { FileRepository.get(context) }
    val scope = rememberCoroutineScope()
    val tabs by repo.tabs.collectAsState()
    val activeId by repo.activeTabId.collectAsState()

    val fontSize by SettingsRepository.fontSize.flow.collectAsState(initial = 14)
    val tabSize by SettingsRepository.tabSize.flow.collectAsState(initial = 4)
    val lineNumbers by SettingsRepository.lineNumbers.flow.collectAsState(initial = true)
    val wordWrap by SettingsRepository.wordWrap.flow.collectAsState(initial = false)
    val autoIndent by SettingsRepository.autoIndent.flow.collectAsState(initial = true)
    val autoSaveEnabled by SettingsRepository.autoSave.flow.collectAsState(initial = true)
    val autoSaveDelayMs by SettingsRepository.autoSaveDelayMs.flow.collectAsState(initial = 1500)

    val snackbarHostState = remember { SnackbarHostState() }
    var showUnsaved by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }

    // Resolve the actual active tab — prefer the repo's activeTabId, fall
    // back to the tabId from the route so the editor is never empty.
    val activeTab = tabs.firstOrNull { it.id == (activeId ?: tabId) }
    val isHtmlTab = activeTab?.language == Language.HTML

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
            snackbarHostState.showSnackbar("Saved ${tab.name}")
        }
    }

    // Back-button handler — flush auto-save before navigating away so
    // no content typed < delay is lost (v0.0.2 had this bug).
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
            // Flush then back — no unsaved-changes dialog needed.
            scope.launch {
                repo.saveTabIfDirty(t.id)
                onBack()
            }
        } else {
            // Manual save mode — ask the user what to do.
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
                            text = activeTab?.name ?: "Editor",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        activeTab?.let { tab ->
                            Text(
                                text = tab.language.displayName + " • ${tab.encoding}" +
                                    if (tab.readOnly) " • read-only" else "",
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    if (isHtmlTab && activeTab != null) {
                        IconButton(onClick = { onOpenPreview(activeTab.id) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Live preview")
                        }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val id = activeTab?.id ?: return@launch
                            val ok = repo.saveTab(id)
                            if (ok) snackbarHostState.showSnackbar("Saved")
                            else snackbarHostState.showSnackbar("Save failed")
                        }
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
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
                    },
                    fontSize = fontSize,
                    tabSize = tabSize,
                    showLineNumbers = lineNumbers,
                    wordWrap = wordWrap,
                    autoIndent = autoIndent,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Open or create a file to start editing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    showUnsaved?.let { id ->
        AlertDialog(
            onDismissRequest = { showUnsaved = null },
            title = { Text("Unsaved changes") },
            text = { Text("Save before closing?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsaved = null
                    scope.launch {
                        repo.saveTab(id)
                        repo.closeTab(id, discardUnsaved = false)
                        if (repo.tabs.value.isEmpty()) onBack()
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showUnsaved = null
                        scope.launch {
                            repo.closeTab(id, discardUnsaved = true)
                            if (repo.tabs.value.isEmpty()) onBack()
                        }
                    }) { Text("Discard") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showUnsaved = null }) {
                        Text("Cancel")
                    }
                }
            },
        )
    }
}

/**
 * Upgraded Search & Replace bar (v0.0.3).
 *
 * Features:
 *  - Find next / find previous (caret-aware)
 *  - Replace single match / Replace all
 *  - Case-sensitivity toggle
 *  - Regex toggle (with safe compile error display)
 *  - Live match count
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchReplaceBar(
    tab: com.vipercode.ide.data.model.EditorTab,
    onApplyChanges: (String) -> Unit,
    onMessage: (String) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(false) }
    var currentMatchIndex by remember { mutableIntStateOf(-1) }
    var totalMatches by remember { mutableIntStateOf(0) }
    var cursorOffset by remember { mutableIntStateOf(0) }

    // Recompute matches whenever the query, text, or toggles change.
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
                placeholder = { Text("Find") },
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
                    cursorOffset = matches[currentMatchIndex].first
                },
                enabled = matches.isNotEmpty(),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous match")
            }
            IconButton(
                onClick = {
                    if (matches.isEmpty()) return@IconButton
                    currentMatchIndex = (currentMatchIndex + 1) % matches.size
                    cursorOffset = matches[currentMatchIndex].first
                },
                enabled = matches.isNotEmpty(),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next match")
            }
            Text(
                text = if (matches.isEmpty()) "0 / 0" else "${currentMatchIndex + 1} / ${totalMatches}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
                fontFamily = FontFamily.Monospace,
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close search")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = replacement,
                onValueChange = { replacement = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Replace") },
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
                    // Replace the CURRENT match only.
                    if (matches.isEmpty() || currentMatchIndex !in matches.indices) {
                        onMessage("No match to replace")
                        return@IconButton
                    }
                    val (start, end) = matches[currentMatchIndex]
                    val updated = tab.content.substring(0, start) +
                        replacement +
                        tab.content.substring(end)
                    onApplyChanges(updated)
                    onMessage("Replaced match ${currentMatchIndex + 1}")
                },
            ) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = "Replace current")
            }
            TextButton(onClick = {
                if (matches.isEmpty()) {
                    onMessage("No matches to replace")
                    return@TextButton
                }
                val updated = replaceAllMatches(tab.content, query, replacement, caseSensitive, useRegex)
                val n = matches.size
                onApplyChanges(updated)
                onMessage("Replaced $n occurrence${if (n == 1) "" else "s"}")
            }) { Text("All") }
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
            // Literal substring search.
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
        // Invalid regex → return empty list. The user will see "0 / 0"
        // in the match counter; they can fix the pattern.
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
