package com.vipercode.ide.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.PopupProperties
import com.vipercode.ide.data.model.FileNode
import com.vipercode.ide.data.prefs.SettingsRepository
import com.vipercode.ide.data.repo.FileRepository
import com.vipercode.ide.ui.components.FileExplorer
import com.vipercode.ide.util.FileUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home / Workspace screen.
 *
 * Shows the open folder's file tree. If no folder is open yet:
 *  - v0.0.2: automatically opens a default local workspace under the
 *    app's external storage (`getExternalFilesDir/workspace`) so the
 *    app is immediately usable offline. The user can still switch to a
 *    SAF-picked folder via the overflow menu.
 *
 * The picked folder URI (or local workspace path) is persisted in
 * [SettingsRepository.lastFolderUri] so the same workspace is restored
 * on next launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenFile: (tabId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { FileRepository.get(context) }
    val scope = rememberCoroutineScope()
    val openFolder by repo.openFolder.collectAsState()
    val tree by repo.tree.collectAsState()

    var expanded by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var menuOpen by remember { mutableStateOf(false) }
    var newFileDialog by remember { mutableStateOf(false) }
    var newFolderDialog by remember { mutableStateOf(false) }
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
                    // SAF URI — re-grant BOTH READ and WRITE persistable
                    // permissions. v0.0.2 only took READ, which meant
                    // saves silently failed with SecurityException after
                    // relaunch. Many providers reject WRITE for tree URIs
                    // that were originally picked with the SAF picker,
                    // so the call is wrapped in runCatching.
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
        // Fall back to the local offline workspace.
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
                            text = "The class of perfection",
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
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
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
                            text = { Text("Settings") },
                            onClick = { menuOpen = false; onOpenSettings() },
                            leadingIcon = { Icon(Icons.Filled.Settings, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("About ViperCode") },
                            onClick = { menuOpen = false; onOpenAbout() },
                            leadingIcon = { Icon(Icons.Filled.Info, null) },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Open folder (SAF)") },
                            onClick = {
                                menuOpen = false
                                folderPicker.launch(null)
                            },
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Use local workspace") },
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
                        onClick = { newFolderDialog = true },
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                    }
                    FloatingActionButton(onClick = { newFileDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "New file")
                    }
                }
            } else {
                FloatingActionButton(onClick = { folderPicker.launch(null) }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = "Open folder")
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            FileExplorer(
                root = openFolder,
                children = tree,
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

    if (newFileDialog) {
        NewNameDialog(
            title = "New file",
            hint = "e.g. main.kt",
            onConfirm = { name ->
                newFileDialog = false
                if (name.isNotBlank() && openFolder != null) {
                    scope.launch {
                        val node = repo.createFile(openFolder!!.uri, name)
                        if (node != null) {
                            val tab = repo.openFile(node.uri)
                            if (tab != null) onOpenFile(tab.id)
                        }
                    }
                }
            },
            onDismiss = { newFileDialog = false },
        )
    }
    if (newFolderDialog) {
        NewNameDialog(
            title = "New folder",
            hint = "e.g. src",
            onConfirm = { name ->
                newFolderDialog = false
                if (name.isNotBlank() && openFolder != null) {
                    scope.launch { repo.createDirectory(openFolder!!.uri, name) }
                }
            },
            onDismiss = { newFolderDialog = false },
        )
    }
    longPressTarget?.let { target ->
        FileActionsDialog(
            node = target,
            onRename = { newName ->
                scope.launch {
                    repo.rename(target.uri, newName)
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

@Composable
private fun NewNameDialog(
    title: String,
    hint: String,
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
            TextButton(onClick = { onConfirm(name) }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun FileActionsDialog(
    node: FileNode,
    onRename: (String) -> Unit,
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
                Text("Choose an action")
            }
        },
        confirmButton = {
            if (renaming) {
                TextButton(onClick = { onRename(newName) }) { Text("Rename") }
            } else {
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        },
        dismissButton = {
            if (renaming) {
                TextButton(onClick = { renaming = false; onDismiss() }) { Text("Cancel") }
            } else {
                TextButton(onClick = { renaming = true }) { Text("Rename") }
            }
        },
    )
}
