package com.vipercode.ide.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.vipercode.ide.data.model.FileNode
import com.vipercode.ide.data.prefs.RecentFiles
import com.vipercode.ide.data.prefs.RecentFolders
import com.vipercode.ide.data.prefs.SettingsRepository
import com.vipercode.ide.data.prefs.SettingsRepository.SortBy
import com.vipercode.ide.data.repo.FileRepository
import com.vipercode.ide.data.repo.RepoResult
import com.vipercode.ide.ui.components.FileExplorer
import com.vipercode.ide.util.FileUtils
import com.vipercode.ide.util.Strings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home / Workspace screen.
 *
 * v0.0.6 changes:
 *  - **Removed the tagline subtitle from the top bar** so the title row
 *    no longer overflows on narrow screens ("workspace đẳng cấp hoàn hảo"
 *    → just the folder name). The tagline still lives on the splash and
 *    About screens where there's plenty of horizontal room.
 *  - **"Upload ZIP"** menu item — picks a `.zip` via SAF, extracts it
 *    into a new subfolder under `projects/`, and immediately switches
 *    the open folder to the extracted project so the user can start
 *    editing right away.
 *  - **"Switch folder"** bottom sheet — lists the workspace, all
 *    extracted projects, and every recently-opened SAF folder in one
 *    place so the user can jump between them without re-opening the
 *    SAF picker.
 *  - **"Browse device storage"** menu item — launches the SAF picker
 *    with an explicit initial URI pointing at the primary shared
 *    storage root. The v0.0.5 `OpenDocumentTree.launch(null)` reopens
 *    the last-used location, which on some devices (Termux, certain
 *    OEM ROMs) is the app's private storage instead of the device's
 *    shared storage. The explicit initial URI forces the picker to
 *    start at the device root so the user can actually navigate the
 *    whole device.
 *  - **"Use local workspace"** fix — the local workspace is now
 *    force-refreshed after the button is tapped (the v0.0.5 click
 *    handler set `lastFolderUri` and called `openFolder`, but the
 *    `LaunchedEffect(Unit)` had already consumed the initial open so
 *    the state never updated for the new directory). Also flips the
 *    `useLocalWorkspace` preference to `true` so the workspace
 *    persists on next launch.
 *  - The empty-state UI now shows an explicit hint ("This folder is
 *    empty — use the + button to create a new file") so users
 *    understand what to do when the local workspace has just been
 *    created and contains nothing.
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
    // v0.0.6 — recent folders list (for the switch-folder sheet).
    val recentFolderUris by RecentFolders.uris.collectAsState()

    var expanded by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var newFileDialog by remember { mutableStateOf<NewTarget?>(null) }
    var newFolderDialog by remember { mutableStateOf<NewTarget?>(null) }
    var longPressTarget by remember { mutableStateOf<FileNode?>(null) }
    // v0.0.6 — switch-folder sheet visibility.
    var showSwitchFolder by remember { mutableStateOf(false) }
    // v0.0.6 — extraction progress indicator.
    var extractingZip by remember { mutableStateOf(false) }
    // v0.0.6 — toast-like snackbar message (kept as a simple state so
    // we don't have to thread a SnackbarHost through the whole tree).
    var userMessage by remember { mutableStateOf<String?>(null) }

    // Restore the previous folder OR fall back to the local workspace.
    // v0.1.0 — FIX: previously keyed on `Unit`, so the effect ran once
    // per Activity lifetime. If the user closed the folder and came
    // back to the home screen, the restoration never re-fired and they
    // were left on the empty state until they manually picked a folder.
    // Now we key on `openFolder` (which becomes null after `closeFolder`)
    // AND a dedicated `folderRestoreToken` that we can bump to retry.
    // The early-return guard `openFolder != null` prevents the effect
    // from re-opening a folder that's already open.
    var folderRestoreToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(openFolder, folderRestoreToken) {
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
                    RecentFolders.add(uri)
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
                    RecentFolders.add(uri)
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
            RecentFolders.add(uri)
        }
    }

    // Auto-clear the user-message toast after a short delay.
    LaunchedEffect(userMessage) {
        if (userMessage != null) {
            kotlinx.coroutines.delay(2500)
            userMessage = null
        }
    }

    // v0.0.6 — explicit primary-storage root so the SAF picker starts
    // at the device's primary shared storage (instead of reopening the
    // last-used location, which on some devices points at Termux or
    // the app's private storage).
    val primaryRoot = remember { FileUtils.primaryStorageRootUri() }

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
                RecentFolders.add(uri)
            }
        }
    }

    // v0.0.6 — single-file picker used to pick a ZIP archive. We use
    // OpenDocument() (instead of GetContent) because OpenDocument grants
    // a persistable read permission we can use to stream the file
    // straight into the ZipInputStream without an intermediate copy.
    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                extractingZip = true
                // v0.0.8 — extractZipToProjects now returns
                // RepoResult<FileNode>; unwrap explicitly so the
                // compile error from v0.0.7 is fixed AND the user
                // sees the actual error message on failure.
                val result = runCatching {
                    repo.extractZipToProjects(uri)
                }.getOrNull()
                extractingZip = false
                val node = (result as? RepoResult.Success)?.value
                if (node != null) {
                    SettingsRepository.lastFolderUri.set(node.uri.toString())
                    RecentFolders.add(node.uri)
                    expanded = expanded + node.uri
                    userMessage = s.homeZipExtracted.format(node.name)
                } else {
                    val reason = (result as? RepoResult.Failure)?.message
                    userMessage = if (reason != null) "${s.homeZipExtractFailed}: $reason"
                                  else s.homeZipExtractFailed
                }
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
                    // v0.0.6 — title only, NO subtitle, so the bar never
                    // overflows. The tagline is shown on the splash and
                    // About screens where there's room.
                    Text(
                        text = openFolder?.name ?: "ViperCode",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                    // v0.0.6 — Switch folder sheet shortcut.
                    IconButton(onClick = { showSwitchFolder = true }) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = s.homeSwitchFolder)
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = s.commonMenu)
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
                        // v0.0.6 — explicit "Browse device storage" item that
                        // launches the SAF picker with an initial URI pointing
                        // at the primary shared storage root. The plain
                        // "Open folder (SAF)" item below it keeps the old
                        // behaviour (reopens the last-used location) for users
                        // who actually want that.
                        DropdownMenuItem(
                            text = { Text(s.homeBrowseDevice) },
                            onClick = {
                                menuOpen = false
                                folderPicker.launch(primaryRoot)
                            },
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(s.homeOpenFolderSaf) },
                            onClick = {
                                menuOpen = false
                                folderPicker.launch(null)
                            },
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(s.homeUploadZip) },
                            onClick = {
                                menuOpen = false
                                zipPicker.launch(arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                ))
                            },
                            leadingIcon = { Icon(Icons.Filled.Upload, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(s.homeUseLocalWorkspace) },
                            onClick = {
                                menuOpen = false
                                val local = FileUtils.localWorkspaceRoot(context)
                                val uri = Uri.fromFile(local)
                                scope.launch {
                                    // v0.0.6 — also flip the useLocalWorkspace
                                    // pref so the workspace persists on next
                                    // launch, force-refresh the directory so
                                    // the tree actually updates, and add the
                                    // folder to the recent-folders list so it
                                    // shows up in the switch-folder sheet.
                                    SettingsRepository.useLocalWorkspace.set(true)
                                    SettingsRepository.lastFolderUri.set(uri.toString())
                                    repo.openFolder(uri)
                                    repo.refreshDirectory(uri)
                                    expanded = expanded + uri
                                    RecentFolders.add(uri)
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.Folder, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(s.homeSwitchFolder) },
                            onClick = {
                                menuOpen = false
                                showSwitchFolder = true
                            },
                            leadingIcon = { Icon(Icons.Filled.SwapHoriz, null) },
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
            if (extractingZip) {
                // v0.0.6 — extraction in progress, show a spinner instead
                // of the FAB so the user gets visible feedback that
                // something is happening.
                FloatingActionButton(
                    onClick = {},
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else if (openFolder != null) {
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
                FloatingActionButton(onClick = { folderPicker.launch(primaryRoot) }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = s.homeOpenFolderSaf)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // v0.0.5 — Recent files row.
                if (recentUris.isNotEmpty()) {
                    RecentFilesRow(
                        uris = recentUris,
                        s = s,
                        onOpen = { uri ->
                            scope.launch {
                                // v0.0.7 — openFile now returns RepoResult.
                                val result = repo.openFile(uri)
                                val tab = (result as? com.vipercode.ide.data.repo.RepoResult.Success)?.value
                                if (tab != null) onOpenFile(tab.id)
                                else userMessage = s.editorOpenFileFailed
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
                            // v0.0.7 — openFile now returns RepoResult.
                            val result = repo.openFile(node.uri)
                            val tab = (result as? com.vipercode.ide.data.repo.RepoResult.Success)?.value
                            if (tab != null) onOpenFile(tab.id)
                            else userMessage = s.editorOpenFileFailed
                        }
                    },
                    onLongPress = { node -> longPressTarget = node },
                )
            }

            // v0.0.6 — toast-like message overlay.
            userMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }

    // v0.0.6 — Switch-folder bottom sheet.
    if (showSwitchFolder) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSwitchFolder = false },
            sheetState = sheetState,
        ) {
            SwitchFolderSheet(
                s = s,
                currentUri = openFolder?.uri,
                workspaceUri = remember { Uri.fromFile(FileUtils.localWorkspaceRoot(context)) },
                // v0.0.7 — re-key on `showSwitchFolder` so the project
                // list is refreshed every time the sheet opens. The
                // previous `remember { ... }` cached the list forever,
                // so a freshly-extracted ZIP didn't show up until the
                // app was restarted.
                projectUris = remember(showSwitchFolder, extractingZip) {
                    FileUtils.listExtractedProjects(context).map { Uri.fromFile(it) }
                },
                recentUris = recentFolderUris,
                onPick = { uri ->
                    scope.launch {
                        SettingsRepository.lastFolderUri.set(uri.toString())
                        val result = repo.openFolder(uri)
                        // v0.0.7 — `openFolder` now returns RepoResult;
                        // if it failed (e.g., SAF permission revoked)
                        // surface an error message. Previously a silent
                        // failure left the user with an empty workspace.
                        // `refreshDirectory` is now called inside
                        // `openFolder`, so we don't call it again here.
                        if (result is com.vipercode.ide.data.repo.RepoResult.Failure) {
                            userMessage = s.editorOpenFolderFailed
                        } else {
                            expanded = expanded + uri
                            RecentFolders.add(uri)
                        }
                    }
                    showSwitchFolder = false
                },
                onBrowseDevice = {
                    showSwitchFolder = false
                    folderPicker.launch(primaryRoot)
                },
                onOpenSaf = {
                    showSwitchFolder = false
                    folderPicker.launch(null)
                },
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
                            // v0.0.7 — openFile now returns RepoResult.
                            val result = repo.openFile(node.uri)
                            val tab = (result as? com.vipercode.ide.data.repo.RepoResult.Success)?.value
                            if (tab != null) onOpenFile(tab.id)
                            else userMessage = s.editorOpenFileFailed
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

/**
 * v0.0.6 — bottom-sheet content for "Switch folder".
 *
 * Lists the local workspace, all extracted ZIP projects, and every
 * recently-opened SAF folder in one place so the user can jump between
 * them without re-opening the SAF picker. Also exposes explicit
 * "Browse device storage" and "Open folder (SAF)" shortcuts at the
 * bottom.
 */
@Composable
private fun SwitchFolderSheet(
    s: Strings.T,
    currentUri: Uri?,
    workspaceUri: Uri,
    projectUris: List<Uri>,
    recentUris: List<Uri>,
    onPick: (Uri) -> Unit,
    onBrowseDevice: () -> Unit,
    onOpenSaf: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .verticalScroll(scrollState),
    ) {
        // Workspace section.
        SectionLabel(text = s.homeFolderSectionWorkspace)
        FolderRow(
            name = s.homeFolderSectionWorkspace,
            subtitle = workspaceUri.lastPathSegment ?: workspaceUri.toString(),
            isActive = currentUri == workspaceUri,
            icon = Icons.Filled.Workspaces,
            onClick = { onPick(workspaceUri) },
        )
        Spacer(Modifier.height(8.dp))

        // Extracted projects section.
        SectionLabel(text = s.homeFolderSectionProjects)
        if (projectUris.isEmpty()) {
            Text(
                text = s.homeNoProjects,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            projectUris.forEach { uri ->
                val name = uri.lastPathSegment ?: uri.toString()
                FolderRow(
                    name = name.substringAfterLast('/').ifBlank { name },
                    subtitle = uri.toString(),
                    isActive = currentUri == uri,
                    icon = Icons.Filled.Folder,
                    onClick = { onPick(uri) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Recent folders section.
        if (recentUris.isNotEmpty()) {
            SectionLabel(text = s.homeFolderSectionRecent)
            // Filter out URIs we've already shown above (workspace + projects).
            val alreadyShown = buildSet {
                add(workspaceUri.toString())
                addAll(projectUris.map { it.toString() })
            }
            recentUris.filterNot { it.toString() in alreadyShown }.forEach { uri ->
                val name = uri.lastPathSegment ?: uri.toString()
                FolderRow(
                    name = name.substringAfterLast('/').ifBlank { name },
                    subtitle = uri.toString(),
                    isActive = currentUri == uri,
                    icon = Icons.Filled.History,
                    onClick = { onPick(uri) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // Device section.
        SectionLabel(text = s.homeFolderSectionDevice)
        FolderRow(
            name = s.homeBrowseDevice,
            subtitle = null,
            isActive = false,
            icon = Icons.Filled.FolderOpen,
            onClick = onBrowseDevice,
        )
        FolderRow(
            name = s.homeOpenFolderSaf,
            subtitle = null,
            isActive = false,
            icon = Icons.Filled.FolderOpen,
            onClick = onOpenSaf,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun FolderRow(
    name: String,
    subtitle: String?,
    isActive: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isActive) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
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
