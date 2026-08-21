package com.vipercode.ide.data.repo

import android.content.Context
import android.net.Uri
import com.vipercode.ide.data.model.EditorTab
import com.vipercode.ide.data.model.FileNode
import com.vipercode.ide.util.FileUtils
import com.vipercode.ide.util.LanguageDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * In-memory state holder for the ViperCode workspace.
 *
 * v0.0.1 keeps everything in memory: the currently open folder, the
 * cached file tree and the list of open editor tabs. Persistence is
 * handled by [com.vipercode.ide.data.prefs.SettingsRepository] for the
 * last-used folder URI; future versions will persist open tabs too.
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

    suspend fun openFolder(uri: Uri) = withContext(Dispatchers.IO) {
        val doc = FileUtils.resolve(appContext, uri) ?: return@withContext
        if (!doc.isDirectory) return@withContext
        val root = FileNode(
            uri = uri,
            name = doc.name ?: "Workspace",
            isDirectory = true,
            size = 0L,
            lastModified = doc.lastModified(),
            mimeType = doc.type,
            parentUri = null,
        )
        _openFolder.value = root
        refreshDirectory(uri)
    }

    suspend fun refreshDirectory(uri: Uri) = withContext(Dispatchers.IO) {
        val children = FileUtils.listChildren(appContext, uri)
        _tree.update { it.toMutableMap().apply { put(uri, children) } }
    }

    suspend fun openFile(uri: Uri): EditorTab? = withContext(Dispatchers.IO) {
        // Reuse existing tab if already open.
        _tabs.value.firstOrNull { it.uri == uri }?.let { existing ->
            _activeTabId.value = existing.id
            return@withContext existing
        }
        val doc = FileUtils.resolve(appContext, uri) ?: return@withContext null
        val name = doc.name ?: "Untitled"
        val mime = doc.type
        val language = LanguageDetector.detect(name, mime)
        val content = runCatching { FileUtils.readText(appContext, uri) }.getOrElse {
            // Failed to read — surface an empty tab so the user can see
            // the error toast instead of a blank screen.
            ""
        }
        val tab = EditorTab(
            uri = uri,
            name = name,
            language = language,
            content = content,
            originalContent = content,
            readOnly = false,
        )
        _tabs.update { it + tab }
        _activeTabId.value = tab.id
        tab
    }

    suspend fun openExternalFile(uri: Uri): EditorTab? = openFile(uri)

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

    fun updateTabContent(tabId: String, newContent: String) {
        _tabs.update { tabs ->
            tabs.map { if (it.id == tabId) it.copy(content = newContent) else it }
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

    suspend fun rename(uri: Uri, newName: String): Boolean = withContext(Dispatchers.IO) {
        val ok = FileUtils.rename(appContext, uri, newName)
        val parent = _tree.value.entries.firstOrNull { (_, kids) -> kids.any { it.uri == uri } }?.key
        if (parent != null) refreshDirectory(parent)
        ok
    }

    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val ok = FileUtils.delete(appContext, uri)
        val parent = _tree.value.entries.firstOrNull { (_, kids) -> kids.any { it.uri == uri } }?.key
        if (parent != null) refreshDirectory(parent)
        ok
    }

    companion object {
        @Volatile private var instance: FileRepository? = null
        fun get(context: Context): FileRepository = instance ?: synchronized(this) {
            instance ?: FileRepository(context.applicationContext).also { instance = it }
        }
    }
}
