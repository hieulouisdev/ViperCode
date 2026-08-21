package com.vipercode.ide.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipercode.ide.data.prefs.SettingsRepository
import com.vipercode.ide.data.repo.FileRepository
import com.vipercode.ide.ui.components.CodeEditor
import com.vipercode.ide.ui.components.TabBar
import kotlinx.coroutines.launch

/**
 * Editor screen — wraps the multi-tab bar and a [CodeEditor] instance.
 *
 * The TopAppBar exposes the active file's name + language tag + a save
 * action. v0.0.1 save is manual — auto-save is planned for v0.0.2.
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

    val snackbarHostState = remember { SnackbarHostState() }
    var showUnsaved by remember { mutableStateOf<String?>(null) }

    val activeTab = tabs.firstOrNull { it.id == (activeId ?: tabId) }
    LaunchedEffect(activeId, tabs) {
        if (activeTab == null && tabs.isEmpty()) onBack()
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
                                text = tab.language.displayName + " • ${tab.encoding}",
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
                        if (t != null && t.isDirty) showUnsaved = t.id
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val ok = repo.saveTab(tabId)
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
                        val t = tabs.firstOrNull { it.id == id } ?: return@launch
                        if (t.isDirty) showUnsaved = id
                        else {
                            repo.closeTab(id, discardUnsaved = true)
                            if (tabs.size <= 1) onBack()
                        }
                    }
                },
            )
            HorizontalDivider()
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
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUnsaved = null },
            title = { Text("Unsaved changes") },
            text = { Text("Save before closing?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showUnsaved = null
                    scope.launch {
                        repo.saveTab(id)
                        repo.closeTab(id, discardUnsaved = false)
                        if (tabs.size <= 1) onBack()
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    androidx.compose.material3.TextButton(onClick = {
                        showUnsaved = null
                        scope.launch {
                            repo.closeTab(id, discardUnsaved = true)
                            if (tabs.size <= 1) onBack()
                        }
                    }) { Text("Discard") }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.TextButton(onClick = { showUnsaved = null }) {
                        Text("Cancel")
                    }
                }
            },
        )
    }
}
