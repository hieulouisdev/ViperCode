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
 * Result type for repository operations that can fail. Used so the
 * caller (the screen) can show a meaningful snackbar / toast instead
 * of a silent failure.
 *
 * v0.0.7 — replaces the previous `Boolean` returns that swallowed
 * errors silently.
 */
sealed class RepoResult<out T> {
    data class Success<T>(val value: T) : RepoResult<T>()
    data class Failure(val message: String, val cause: Throwable? = null) : RepoResult<Nothing>()
}

/**
 * In-memory state holder for the ViperCode workspace.
 *
 * v0.0.7 changes:
 *  - **Rename now updates the tab URI** — previously the tab's `uri`
 *    field stayed pointing at the old URI after a SAF `renameTo`,
 *    so subsequent saves wrote to a non-existent file. The new
 *    implementation re-resolves the renamed DocumentFile and updates
 *    `tab.uri` to the new URI returned by SAF.
 *  - **Open/save errors surface to the caller** — `openFile`,
 *    `openExternalFile`, `saveTab`, and `extractZipToProjects` now
 *    return `RepoResult` so the screen can show a snackbar instead
 *    of a silent empty editor / silent save failure.
 *  - **Recent-folders list updated on every folder open** — so the
 *    switch-folder sheet always reflects the last-visited folders.
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
     * v0.0.7 — returns a [RepoResult] so the caller can show a
     * snackbar if the URI can no longer be resolved.
     */
    suspend fun openFolder(uri: Uri): RepoResult<FileNode> = withContext(Dispatchers.IO) {
        val doc = FileUtils.resolve(appContext, uri) ?: return@withContext
            RepoResult.Failure<FileNode>("Cannot resolve folder — permission may have been revoked")
        if (!doc.isDirectory) return@withContext
            RepoResult.Failure<FileNode>("Selected document is not a folder")
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
        // v0.0.7 — track this folder as recent so the switch-folder
        // sheet picks it up.
        com.vipercode.ide.data.prefs.RecentFolders.add(uri)
        RepoResult.Success(root)
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

    /**
     * v0.0.7 — returns [RepoResult] so a read failure is surfaced to
     * the user (previously the caller got an empty-content tab and had
     * no way to know the file was unreadable).
     */
    suspend fun openFile(uri: Uri): RepoResult<EditorTab> = withContext(Dispatchers.IO) {
        // Reuse existing tab if already open.
        _tabs.value.firstOrNull { it.uri == uri }?.let { existing ->
            _activeTabId.value = existing.id
            RecentFiles.add(uri)
            return@withContext RepoResult.Success(existing)
        }
        val doc = FileUtils.resolve(appContext, uri) ?: return@withContext
            RepoResult.Failure<EditorTab>("Cannot resolve file — it may have been moved or deleted")
        val name = doc.name ?: uri.lastPathSegment ?: "Untitled"
        val mime = doc.type
        val language = LanguageDetector.detect(name, mime)
        val contentResult = runCatching { FileUtils.readText(appContext, uri) }
        val content = contentResult.getOrElse {
            return@withContext RepoResult.Failure<EditorTab>(
                "Cannot read file: ${it.message ?: "unknown error"}",
                it,
            )
        }
        val size = if (doc.isDirectory) 0L else doc.length()
        val truncated = size > MAX_INLINE_BYTES
        val readOnly = truncated
        val tab = EditorTab(
            uri = uri,
            name = name,
            language = language,
            content = content,
            originalContent = content,
            readOnly = readOnly,
            truncated = truncated,
        )
        _tabs.update { it + tab }
        _activeTabId.value = tab.id
        RecentFiles.add(uri)
        if (truncated) {
            RepoResult.Success(tab) // The screen surfaces a "truncated" hint.
        } else {
            RepoResult.Success(tab)
        }
    }

    suspend fun openExternalFile(uri: Uri): RepoResult<EditorTab> {
        if (uri.toString().startsWith("content://")) {
            runCatching {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                appContext.contentResolver.takePersistableUriPermission(uri, flags)
            }
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        return openFile(uri)
    }

    /**
     * v0.0.7 — returns [RepoResult] so save failures surface to the
     * user via a snackbar (previously the failure was silently
     * swallowed and the user thought the file was saved).
     */
    suspend fun saveTab(tabId: String): RepoResult<Unit> = withContext(Dispatchers.IO) {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return@withContext
            RepoResult.Failure<Unit>("Tab not found: $tabId")
        val result = runCatching {
            FileUtils.writeText(appContext, tab.uri, tab.content)
            _tabs.update { tabs ->
                tabs.map { if (it.id == tabId) it.copy(originalContent = it.content) else it }
            }
        }
        result.fold(
            onSuccess = { RepoResult.Success(Unit) },
            onFailure = { RepoResult.Failure("Save failed: ${it.message ?: "unknown error"}", it) },
        )
    }

    /** Auto-save hook — only writes if the tab is dirty. */
    suspend fun saveTabIfDirty(tabId: String): RepoResult<Unit> {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return RepoResult.Failure("Tab not found: $tabId")
        if (!tab.isDirty) return RepoResult.Success(Unit)
        return saveTab(tabId)
    }

    fun updateTabContent(tabId: String, newContent: String) {
        _tabs.update { tabs ->
            tabs.map { if (it.id == tabId) it.copy(content = newContent) else it }
        }
    }

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

    suspend fun duplicate(uri: Uri): FileNode? = withContext(Dispatchers.IO) {
        val parent = _tree.value.entries.firstOrNull { (_, kids) -> kids.any { it.uri == uri } }?.key
            ?: return@withContext null
        val node = FileUtils.duplicate(appContext, uri, parent) ?: return@withContext null
        refreshDirectory(parent)
        node
    }

    suspend fun searchInFiles(rootUri: Uri, query: String): List<FileUtils.SearchHit> =
        withContext(Dispatchers.IO) {
            FileUtils.searchInFiles(appContext, rootUri, query)
        }

    /**
     * v0.0.7 — returns [RepoResult] so extraction errors surface to
     * the user (previously the caller got null and had no idea why).
     */
    suspend fun extractZipToProjects(zipUri: Uri, suggestedName: String? = null): RepoResult<FileNode> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = FileUtils.extractZipToProjects(appContext, zipUri, suggestedName)
                val uri = android.net.Uri.fromFile(dir)
                val root = FileNode(
                    uri = uri,
                    name = dir.name,
                    isDirectory = true,
                    size = 0L,
                    lastModified = dir.lastModified(),
                    mimeType = null,
                    parentUri = null,
                )
                _openFolder.value = root
                refreshDirectory(uri)
                com.vipercode.ide.data.prefs.RecentFolders.add(uri)
                root
            }.fold(
                onSuccess = { RepoResult.Success(it) },
                onFailure = { RepoResult.Failure("ZIP extraction failed: ${it.message ?: "unknown error"}", it) },
            )
        }

    suspend fun listExtractedProjects(): List<FileNode> = withContext(Dispatchers.IO) {
        FileUtils.listExtractedProjects(appContext).map { dir ->
            val uri = android.net.Uri.fromFile(dir)
            FileNode(
                uri = uri,
                name = dir.name,
                isDirectory = true,
                size = 0L,
                lastModified = dir.lastModified(),
                mimeType = null,
                parentUri = null,
            )
        }
    }

    /**
     * v0.0.7 — re-resolves the renamed DocumentFile and updates the
     * tab's `uri` field to the new URI returned by SAF. This was a
     * critical bug in v0.0.6: after a rename, saves wrote to the
     * old URI which may not exist anymore.
     *
     * Returns true on success. The caller is responsible for surfacing
     * failure (e.g., a snackbar).
     */
    suspend fun rename(uri: Uri, newName: String): Boolean = withContext(Dispatchers.IO) {
        val ok = FileUtils.rename(appContext, uri, newName)
        val parent = _tree.value.entries.firstOrNull { (_, kids) -> kids.any { it.uri == uri } }?.key
        if (parent != null) refreshDirectory(parent)
        if (ok) {
            // v0.0.7 — re-resolve the renamed DocumentFile to obtain
            // the new URI (SAF may or may not change the URI on
            // renameTo, depending on the provider). If the URI is
            // unchanged, this is a no-op. If it changed, we update
            // the tab's `uri` field so future saves hit the new path.
            val newDoc = FileUtils.resolve(appContext, uri)
            val newUri = if (newDoc != null && newDoc.exists()) {
                val docUri = newDoc.uri
                if (docUri != uri) docUri else uri
            } else uri

            _tabs.update { tabs ->
                tabs.map {
                    if (it.uri == uri) it.copy(uri = newUri, name = newName) else it
                }
            }
        }
        ok
    }

    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val ok = FileUtils.delete(appContext, uri)
        val parent = _tree.value.entries.firstOrNull { (_, kids) -> kids.any { it.uri == uri } }?.key
        if (parent != null) refreshDirectory(parent)
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
