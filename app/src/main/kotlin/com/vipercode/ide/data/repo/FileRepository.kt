package com.vipercode.ide.data.repo

import android.content.Context
import android.net.Uri
import com.vipercode.ide.data.model.EditorTab
import com.vipercode.ide.data.model.FileNode
import com.vipercode.ide.data.prefs.RecentFiles
import com.vipercode.ide.data.prefs.SettingsRepository
import com.vipercode.ide.util.FileUtils
import com.vipercode.ide.util.LanguageDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * In-memory state holder for the ViperCode workspace.
 *
 * v0.0.2 keeps the open folder / file tree / open tabs in memory but
 * adds **offline-first** persistence:
 *
 *  - The last opened folder URI is persisted in [SettingsRepository].
 *  - A default local workspace (`getExternalFilesDir/workspace`) is used
 *    the first time the app launches so the user can start editing
 *    immediately without picking a SAF folder.
 *  - All settings, including the last folder URI, are stored locally
 *    in DataStore — no network access is ever required.
 *
 * Auto-save: dirty tabs are saved automatically after a configurable
 * idle delay (see [SettingsRepository.autoSaveDelayMs]). The trigger
 * lives in [com.vipercode.ide.ui.screens.EditorScreen]; this repository
 * exposes [saveTabIfDirty] for it to call.
 *
 * The repository is safe to share across ViewModels — every mutation is
 * performed on [Dispatchers.IO] and the public state is read-only via
 * [StateFlow].
 */
class FileRepository(private val appContext: Context) {

    private val _openFolder = MutableStateFlow<FileNode?>(null)
    val openFolder: StateFlow<FileNode?> = _openFolder.asStateFlow()

    private val _tree = MutableStateFlow<Map<Uri, List<FileNode>>>(emptyMap())
    val tree: StateFlow<Map<Uri, List<FileNode>>> = _tree.asStateFlow()

    private val _tabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val tabs: StateFlow<List<EditorTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    /**
     * Opens the given SAF tree URI or `file://` URI as the workspace.
     * Falls back silently if the URI can no longer be resolved (e.g.
     * the user revoked permission).
     */
    suspend fun openFolder(uri: Uri) = withContext(Dispatchers.IO) {
        val doc = FileUtils.resolve(appContext, uri) ?: return@withContext
        if (!doc.isDirectory) return@withContext
        val root = FileNode(
            uri = uri,
            name = doc.name ?: displayNameForLocal(uri),
            isDirectory = true,
            size = 0L,
            lastModified = doc.lastModified(),
            mimeType = doc.type,
            parentUri = null,
        )
        _openFolder.value = root
        refreshDirectory(uri)
    }

    /** Closes the current workspace but keeps the tabs in memory. */
    fun closeFolder() {
        _openFolder.value = null
        _tree.value = emptyMap()
    }

    suspend fun refreshDirectory(uri: Uri) = withContext(Dispatchers.IO) {
        val children = FileUtils.listChildren(appContext, uri)
        _tree.update { it.toMutableMap().apply { put(uri, children) } }
    }

    suspend fun openFile(uri: Uri): EditorTab? = withContext(Dispatchers.IO) {
        // Reuse existing tab if already open.
        _tabs.value.firstOrNull { it.uri == uri }?.let { existing ->
            _activeTabId.value = existing.id
            // v0.0.5 — track recent files even on tab re-activation.
            RecentFiles.add(uri)
            return@withContext existing
        }
        val doc = FileUtils.resolve(appContext, uri) ?: return@withContext null
        val name = doc.name ?: uri.lastPathSegment ?: "Untitled"
        val mime = doc.type
        val language = LanguageDetector.detect(name, mime)
        val content = runCatching { FileUtils.readText(appContext, uri) }.getOrElse {
            // Failed to read — surface an empty tab so the user can see
            // the error toast instead of a blank screen.
            ""
        }
        val size = if (doc.isDirectory) 0L else doc.length()
        val readOnly = size > MAX_INLINE_BYTES
        val tab = EditorTab(
            uri = uri,
            name = name,
            language = language,
            content = content,
            originalContent = content,
            readOnly = readOnly,
        )
        _tabs.update { it + tab }
        _activeTabId.value = tab.id
        // v0.0.5 — add the file to the recent-files list.
        RecentFiles.add(uri)
        tab
    }

    suspend fun openExternalFile(uri: Uri): EditorTab? {
        // External URIs (content://) arrive via ACTION_VIEW intents. The
        // granted read permission is transient and tied to the Activity
        // — without persisting it, saving later will throw
        // SecurityException.
        //
        // Not every content provider supports persistable permissions, so
        // we wrap the takePersistableUriPermission call in runCatching
        // and continue even if it fails (the user can still view the
        // file; only future writes will fail).
        if (uri.toString().startsWith("content://")) {
            runCatching {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                appContext.contentResolver.takePersistableUriPermission(uri, flags)
            }
            // Some providers throw if WRITE isn't granted; fall back to
            // READ-only so at least the file can be displayed.
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        return openFile(uri)
    }

    suspend fun saveTab(tabId: String): Boolean = withContext(Dispatchers.IO) {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return@withContext false
        runCatching {
            FileUtils.writeText(appContext, tab.uri, tab.content)
            _tabs.update { tabs ->
                tabs.map { if (it.id == tabId) it.copy(originalContent = it.content) else it }
            }
            true
        }.getOrElse { false }
    }

    /** Auto-save hook — only writes if the tab is dirty. */
    suspend fun saveTabIfDirty(tabId: String): Boolean {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return false
        if (!tab.isDirty) return true
        return saveTab(tabId)
    }

    fun updateTabContent(tabId: String, newContent: String) {
        _tabs.update { tabs ->
            tabs.map { if (it.id == tabId) it.copy(content = newContent) else it }
        }
    }

    /**
     * Updates the saved caret position (line + column, 0-indexed) of a
     * tab without touching its content. Called by [CodeEditor] on every
     * edit so the caret survives tab switches and process death.
     */
    fun updateTabCursor(tabId: String, line: Int, column: Int) {
        _tabs.update { tabs ->
            tabs.map {
                if (it.id == tabId) it.copy(cursorLine = line, cursorColumn = column) else it
            }
        }
    }

    fun setActiveTab(tabId: String?) {
        _activeTabId.value = tabId
    }

    suspend fun closeTab(tabId: String, discardUnsaved: Boolean): Boolean = withContext(Dispatchers.IO) {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return@withContext false
        if (tab.isDirty && !discardUnsaved) return@withContext false
        _tabs.update { tabs -> tabs.filterNot { it.id == tabId } }
        if (_activeTabId.value == tabId) {
            _activeTabId.value = _tabs.value.lastOrNull()?.id
        }
        true
    }

    suspend fun createFile(parentUri: Uri, name: String): FileNode? =
        withContext(Dispatchers.IO) {
            val node = FileUtils.createFile(appContext, parentUri, name) ?: return@withContext null
            refreshDirectory(parentUri)
            node
        }

    suspend fun createDirectory(parentUri: Uri, name: String): FileNode? =
        withContext(Dispatchers.IO) {
            val node = FileUtils.createDirectory(appContext, parentUri, name) ?: return@withContext null
            refreshDirectory(parentUri)
            node
        }

    /** v0.0.4 — duplicates a file or folder next to its original. */
    suspend fun duplicate(uri: Uri): FileNode? = withContext(Dispatchers.IO) {
        val parent = _tree.value.entries.firstOrNull { (_, kids) -> kids.any { it.uri == uri } }?.key
            ?: return@withContext null
        val node = FileUtils.duplicate(appContext, uri, parent) ?: return@withContext null
        refreshDirectory(parent)
        node
    }

    /** v0.0.4 — workspace-wide text search. */
    suspend fun searchInFiles(rootUri: Uri, query: String): List<FileUtils.SearchHit> =
        withContext(Dispatchers.IO) {
            FileUtils.searchInFiles(appContext, rootUri, query)
        }

    suspend fun rename(uri: Uri, newName: String): Boolean = withContext(Dispatchers.IO) {
        val ok = FileUtils.rename(appContext, uri, newName)
        val parent = _tree.value.entries.firstOrNull { (_, kids) -> kids.any { it.uri == uri } }?.key
        if (parent != null) refreshDirectory(parent)
        // Also rename the tab if it is open so the title bar updates.
        if (ok) {
            _tabs.update { tabs ->
                tabs.map { if (it.uri == uri) it.copy(name = newName) else it }
            }
        }
        ok
    }

    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val ok = FileUtils.delete(appContext, uri)
        val parent = _tree.value.entries.firstOrNull { (_, kids) -> kids.any { it.uri == uri } }?.key
        if (parent != null) refreshDirectory(parent)
        // Close any open tab whose URI is below the deleted node.
        if (ok) {
            _tabs.update { tabs -> tabs.filterNot { it.uri == uri || it.uri.toString().startsWith(uri.toString() + "/") } }
            if (_activeTabId.value?.let { id -> _tabs.value.none { it.id == id } } == true) {
                _activeTabId.value = _tabs.value.lastOrNull()?.id
            }
        }
        ok
    }

    private fun displayNameForLocal(uri: Uri): String {
        val path = uri.path ?: return "Workspace"
        val f = File(path)
        return f.name.ifBlank { "Workspace" }
    }

    companion object {
        private const val MAX_INLINE_BYTES = 5L * 1024 * 1024 // 5 MB

        @Volatile private var instance: FileRepository? = null
        fun get(context: Context): FileRepository = instance ?: synchronized(this) {
            instance ?: FileRepository(context.applicationContext).also { instance = it }
        }
    }
}
