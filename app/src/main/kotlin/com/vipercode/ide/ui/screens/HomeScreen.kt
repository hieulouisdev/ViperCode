package com.vipercode.ide.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.vipercode.ide.data.model.FileNode
import com.vipercode.ide.data.prefs.RecentFiles
import com.vipercode.ide.data.prefs.SettingsRepository
import com.vipercode.ide.data.prefs.SettingsRepository.SortBy
import com.vipercode.ide.data.repo.FileRepository
import com.vipercode.ide.ui.components.FileExplorer
import com.vipercode.ide.util.FileUtils
import com.vipercode.ide.util.Strings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home / Workspace screen.
 *
 * v0.0.4 changes:
 *  - All strings routed through [Strings.get] so the screen flips to
 *    Vietnamese when the user picks Tiếng Việt in Settings.
 *  - Long-press on a folder opens a context menu with "New file here",
 *    "New folder here", "Rename", "Duplicate", "Delete" — fixes the
 *    v0.0.3 complaint that the user couldn't create files inside a
 *    sub-folder (the FAB only ever created at the workspace root).
 *  - Newly-created folders are auto-added to the expanded set so the
 *    user can immediately see and "access" them.
 *  - Sort by Name / Size / Modified, and a hidden-files toggle, both
 *    driven by new Settings prefs.
 *  - Top-bar overflow menu exposes "Search in files" and "Quick open"
 *    shortcuts that hand off to the Editor screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenFile: (tabId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSearchInFiles: () -> Unit = {},
    onOpenQuickOpen: () -> Unit = {},
) {
    val context = LocalContext.current
    val repo = remember { FileRepository.get(context) }
    val scope = rememberCoroutineScope()
    val openFolder by repo.openFolder.collectAsState()
    val tree by repo.tree.collectAsState()

    // Observe the Strings catalogue so we re-render when the language
    // flips. Reading `active` as state is enough — the actual T is
    // fetched via Strings.get() below.
    val activeLanguage by Strings.active.collectAsState()
    val s = Strings.get()

    val showHidden by SettingsRepository.showHiddenFiles.flow
        .collectAsState(initial = SettingsRepository.showHiddenFiles.default)
    val sortBy by SettingsRepository.sortBy.flow
        .collectAsState(initial = SettingsRepository.sortBy.default)

    // v0.0.5 — recent files list.
    val recentUris by RecentFiles.uris.collectAsState()

    var expanded by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var newFileDialog by remember { mutableStateOf<NewTarget?>(null) }
    var newFolderDialog by remember { mutableStateOf<NewTarget?>(null) }
    var longPressTarget by remember { mutableStateOf<FileNode?>(null) }

    // Restore the previous folder OR fall back to the local workspace.
    LaunchedEffect(Unit) {
        if (openFolder != null) return@LaunchedEffect
        val saved = SettingsRepository.lastFolderUri.first()
        if (saved.isNotBlank()) {
            val restored = runCatching {
                val uri = Uri.parse(saved)
                if (uri.toString().startsWith("file://") ||
                    FileUtils.isLocalWorkspaceUri(uri)
                ) {
                    repo.openFolder(uri)
                    expanded = expanded + uri
                    true
                } else {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    }
                    repo.openFolder(uri)
                    expanded = expanded + uri
                    true
                }
            }.getOrDefault(false)
            if (restored) return@LaunchedEffect
        }
        if (SettingsRepository.useLocalWorkspace.first()) {
            val local = FileUtils.localWorkspaceRoot(context)
            val uri = Uri.fromFile(local)
            SettingsRepository.lastFolderUri.set(uri.toString())
            repo.openFolder(uri)
            expanded = expanded + uri
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            scope.launch {
                SettingsRepository.lastFolderUri.set(uri.toString())
                repo.openFolder(uri)
                expanded = expanded + uri
            }
        }
    }

    /**
     * Filters and re-sorts the file-tree children map according to the
     * user's [showHidden] + [sortBy] preferences. v0.0.3 always showed
     * every child, including `.git/` etc.
     */
    val filteredTree = remember(tree, showHidden, sortBy, activeLanguage) {
        tree.mapValues { (_, kids) ->
            val visible = if (showHidden) kids
            else kids.filterNot { it.name.startsWith('.') }
            when (sortBy) {
                SortBy.NAME -> visible.sortedWith(
                    compareBy({ !it.isDirectory }, { it.name.lowercase() })
                )
                SortBy.SIZE -> visible.sortedWith(
                    compareBy({ !it.isDirectory }, { it.size })
                )
                SortBy.MODIFIED -> visible.sortedWith(
                    compareBy({ !it.isDirectory }, { -it.lastModified })
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = openFolder?.name ?: "ViperCode",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = s.tagline,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            openFolder?.uri?.let { repo.refreshDirectory(it) }
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = s.homeRefresh)
                    }
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = s.commonSortByName)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            SettingsRepository.showHiddenFiles.set(!showHidden)
                        }
                    }) {
                        Icon(
                            imageVector = if (showHidden) Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff,
                            contentDescription = if (showHidden) s.commonHideHiddenFiles
                            else s.commonShowHiddenFiles,
                        )
                    }
                    IconButton(onClick = onOpenSearchInFiles) {
                        Icon(Icons.Filled.Search, contentDescription = s.editorSearchInFiles)
                    }
                    IconButton(onClick = onOpenQuickOpen) {
                        Icon(Icons.Filled.Bolt, contentDescription = s.editorQuickOpen)
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        properties = PopupProperties(),
                    ) {
                        DropdownMenuItem(
                            text = { Text(s.homeSettings) },
                            onClick = { menuOpen = false; onOpenSettings() },
                            leadingIcon = { Icon(Icons.Filled.Settings, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(s.homeAbout) },
                            onClick = { menuOpen = false; onOpenAbout() },
                            leadingIcon = { Icon(Icons.Filled.Info, null) },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(s.homeOpenFolderSaf) },
                            onClick = {
                                menuOpen = false
                                folderPicker.launch(null)
                            },
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(s.homeUseLocalWorkspace) },
                            onClick = {
                                menuOpen = false
                                val local = FileUtils.localWorkspaceRoot(context)
                                val uri = Uri.fromFile(local)
                                scope.launch {
                                    SettingsRepository.lastFolderUri.set(uri.toString())
                                    repo.openFolder(uri)
                                    expanded = expanded + uri
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.Folder, null) },
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(s.commonSortByName) },
                            onClick = {
                                sortMenuOpen = false
                                scope.launch { SettingsRepository.sortBy.set(SortBy.NAME) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(s.commonSortBySize) },
                            onClick = {
                                sortMenuOpen = false
                                scope.launch { SettingsRepository.sortBy.set(SortBy.SIZE) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(s.commonSortByModified) },
                            onClick = {
                                sortMenuOpen = false
                                scope.launch { SettingsRepository.sortBy.set(SortBy.MODIFIED) }
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            if (openFolder != null) {
                Column {
                    FloatingActionButton(
                        onClick = { newFolderDialog = NewTarget(openFolder!!.uri) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = s.homeNewFolder)
                    }
                    FloatingActionButton(onClick = { newFileDialog = NewTarget(openFolder!!.uri) }) {
                        Icon(Icons.Filled.Add, contentDescription = s.homeNewFile)
                    }
                }
            } else {
                FloatingActionButton(onClick = { folderPicker.launch(null) }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = s.homeOpenFolderSaf)
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // v0.0.5 — Recent files row.
            if (recentUris.isNotEmpty()) {
                RecentFilesRow(
                    uris = recentUris,
                    s = s,
                    onOpen = { uri ->
                        scope.launch {
                            val tab = repo.openFile(uri)
                            if (tab != null) onOpenFile(tab.id)
                        }
                    },
                    onClear = { RecentFiles.clear() },
                )
                HorizontalDivider()
            }
            FileExplorer(
                root = openFolder,
                children = filteredTree,
                expanded = expanded,
                onToggleFolder = { uri ->
                    expanded = if (uri in expanded) expanded - uri else expanded + uri
                    scope.launch { repo.refreshDirectory(uri) }
                },
                onOpenFile = { node ->
                    scope.launch {
                        val tab = repo.openFile(node.uri)
                        if (tab != null) onOpenFile(tab.id)
                    }
                },
                onLongPress = { node -> longPressTarget = node },
            )
        }
    }

    // Resolve the right parent for "New file / New folder" actions:
    // if the user long-pressed a folder, create inside it; otherwise
    // create at the workspace root.
    newFileDialog?.let { target ->
        NewNameDialog(
            title = s.dialogNewFileTitle,
            hint = s.dialogNewFileHint,
            s = s,
            onConfirm = { name ->
                newFileDialog = null
                if (name.isNotBlank()) {
                    scope.launch {
                        val node = repo.createFile(target.parentUri, name)
                        if (node != null) {
                            val tab = repo.openFile(node.uri)
                            if (tab != null) onOpenFile(tab.id)
                        }
                    }
                }
            },
            onDismiss = { newFileDialog = null },
        )
    }
    newFolderDialog?.let { target ->
        NewNameDialog(
            title = s.dialogNewFolderTitle,
            hint = s.dialogNewFolderHint,
            s = s,
            onConfirm = { name ->
                newFolderDialog = null
                if (name.isNotBlank()) {
                    scope.launch {
                        val node = repo.createDirectory(target.parentUri, name)
                        // Auto-expand the new folder so the user can
                        // immediately "access" it — fixes the v0.0.3
                        // complaint that newly-created folders appeared
                        // inert until the user manually re-clicked them.
                        if (node != null) {
                            expanded = expanded + node.uri
                            scope.launch { repo.refreshDirectory(node.uri) }
                        }
                    }
                }
            },
            onDismiss = { newFolderDialog = null },
        )
    }
    longPressTarget?.let { target ->
        FileActionsDialog(
            node = target,
            s = s,
            onNewFileHere = {
                longPressTarget = null
                newFileDialog = NewTarget(target.uri)
            },
            onNewFolderHere = {
                longPressTarget = null
                newFolderDialog = NewTarget(target.uri)
            },
            onRename = { newName ->
                scope.launch {
                    repo.rename(target.uri, newName)
                    longPressTarget = null
                }
            },
            onDuplicate = {
                scope.launch {
                    repo.duplicate(target.uri)
                    longPressTarget = null
                }
            },
            onDelete = {
                scope.launch {
                    repo.delete(target.uri)
                    longPressTarget = null
                }
            },
            onDismiss = { longPressTarget = null },
        )
    }
}

/** Carries the target parent URI through the new-file / new-folder dialog. */
private data class NewTarget(val parentUri: Uri)

/**
 * v0.0.5 — horizontal "Recent files" row shown above the file tree.
 *
 * Tapping a chip re-opens the file in the editor. Long-pressing the
 * row's title area clears the list.
 */
@Composable
private fun RecentFilesRow(
    uris: List<Uri>,
    s: Strings.T,
    onOpen: (Uri) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = s.homeRecent,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = s.homeClearRecent,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        ) {
            items(uris, key = { it.toString() }) { uri ->
                val name = uri.lastPathSegment ?: uri.toString()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.widthIn(max = 160.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { onOpen(uri) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewNameDialog(
    title: String,
    hint: String,
    s: Strings.T,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(hint) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text(s.dialogCreate) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.dialogCancel) }
        },
    )
}

@Composable
private fun FileActionsDialog(
    node: FileNode,
    s: Strings.T,
    onNewFileHere: () -> Unit,
    onNewFolderHere: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(node.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(node.name) },
        text = {
            if (renaming) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                )
            } else {
                Column {
                    Text(s.dialogChooseAction)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (node.isDirectory) {
                        ActionRow(icon = Icons.Filled.Add, label = s.homeNewFileHere, onClick = onNewFileHere)
                        ActionRow(icon = Icons.Filled.CreateNewFolder, label = s.homeNewFolderHere, onClick = onNewFolderHere)
                        ActionRow(icon = Icons.Filled.ContentCopy, label = s.dialogDuplicate, onClick = onDuplicate)
                    } else {
                        ActionRow(icon = Icons.Filled.ContentCopy, label = s.dialogDuplicate, onClick = onDuplicate)
                    }
                    ActionRow(icon = Icons.Filled.DriveFileRenameOutline, label = s.dialogRename, onClick = { renaming = true })
                    ActionRow(icon = Icons.Filled.Delete, label = s.dialogDelete, onClick = onDelete)
                }
            }
        },
        confirmButton = {
            if (renaming) {
                TextButton(onClick = { onRename(newName) }) { Text(s.dialogRename) }
            } else {
                TextButton(onClick = onDismiss) { Text(s.commonOk) }
            }
        },
        dismissButton = {
            if (renaming) {
                TextButton(onClick = { renaming = false; onDismiss() }) { Text(s.dialogCancel) }
            }
        },
    )
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
