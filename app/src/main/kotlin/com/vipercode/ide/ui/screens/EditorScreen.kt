package com.vipercode.ide.ui.screens

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ContentReplace
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Editor screen — wraps the multi-tab bar and a [CodeEditor] instance.
 *
 * The TopAppBar exposes the active file's name + language tag + a save
 * action. v0.0.2 adds:
 *  - Auto-save: a debounced save fires after the user stops typing
 *    (delay defined by [SettingsRepository.autoSaveDelayMs]).
 *  - Search & Replace: a togglable inline bar above the editor.
 *  - Search/Replace within a single file (multi-file search is on the
 *    v0.0.3 roadmap).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    tabId: String,
    onBack: () -> Unit,
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

    // If the route's tabId is stale (e.g., the tab was closed via a
    // different code path) but other tabs are still open, promote the
    // first available tab to active so the user always sees an editor
    // instead of the empty state.
    LaunchedEffect(tabs, activeId, tabId) {
        if (tabs.isEmpty()) return@LaunchedEffect
        if (activeId == null || tabs.none { it.id == activeId }) {
            val fallback = tabs.firstOrNull { it.id == tabId } ?: tabs.first()
            repo.setActiveTab(fallback.id)
        }
    }

    // Pop the editor when the last tab is closed — but only check this
    // against the latest repo value, not the captured Compose state,
    // because Compose state can be a frame behind the StateFlow.
    LaunchedEffect(tabs.size) {
        if (tabs.isEmpty()) onBack()
    }

    // ── Auto-save (debounced) ──────────────────────────────────────
    // We track the latest content snapshot of the active tab and arm a
    // delayed save whenever it changes. If the user keeps typing, the
    // previous launch is cancelled implicitly because we re-key the
    // effect on tab.id + tab.content.
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
                    IconButton(onClick = {
                        val t = activeTab
                        if (t != null && t.isDirty && !autoSaveEnabled) showUnsaved = t.id
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
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
                            // Auto-save the tab if enabled, then close.
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
                    onReplaceAll = { needle, replacement ->
                        val t = activeTab ?: return@SearchReplaceBar
                        if (needle.isNotEmpty()) {
                            val updated = t.content.replace(needle, replacement)
                            repo.updateTabContent(t.id, updated)
                            scope.launch { snackbarHostState.showSnackbar("Replaced all occurrences") }
                        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchReplaceBar(
    onReplaceAll: (needle: String, replacement: String) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Find") },
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
        IconButton(onClick = { onReplaceAll(query, replacement) }) {
            Icon(Icons.Filled.ContentReplace, contentDescription = "Replace all")
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Close search")
        }
    }
}
