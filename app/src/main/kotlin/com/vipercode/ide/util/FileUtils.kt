package com.vipercode.ide.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.vipercode.ide.data.model.FileNode
import com.vipercode.ide.data.model.toFileNode
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure-Java bridge between Android storage layers and the rest of the app.
 *
 * Supports two storage modes:
 *  1. SAF (Storage Access Framework) — a folder URI picked by the user.
 *     Permission is persisted across launches; the URI is the source of
 *     truth.
 *  2. Local workspace — a folder under the app's external storage
 *     (`getExternalFilesDir(null)/workspace`). This requires no
 *     permissions and works fully offline out of the box, so the app is
 *     usable immediately without any picker.
 *
 * Every public function is suspend so it can be called from a ViewModel
 * without blocking the main thread. None of the functions cache
 * [DocumentFile] across calls — the URI is the single source of truth,
 * and a fresh [DocumentFile] is created per operation.
 */
object FileUtils {

    private const val MAX_FILE_BYTES = 5 * 1024 * 1024 // 5 MB safety cap for v0.0.2

    /** Name of the default offline workspace directory under the app's external storage. */
    const val LOCAL_WORKSPACE_DIR = "workspace"

    /**
     * Name of the directory that holds extracted ZIP projects. v0.0.6.
     *
     * Each uploaded ZIP gets its own subfolder under this root, named after
     * the ZIP's base name (without the `.zip` extension). Users can switch
     * between workspace and any extracted project via "Switch folder".
     */
    const val LOCAL_PROJECTS_DIR = "projects"

    /**
     * Returns a [File] pointing at the app-private workspace directory on
     * external storage. Creates the directory if it does not yet exist.
     *
     * Files written here survive across launches but are deleted if the
     * user clears app data — there is no need for runtime permissions on
     * Android 4.4+ (API 19) and above.
     */
    fun localWorkspaceRoot(context: Context): File {
        val base = context.getExternalFilesDir(null)
            ?: context.filesDir // fallback for devices without external storage
        val ws = File(base, LOCAL_WORKSPACE_DIR)
        if (!ws.exists()) ws.mkdirs()
        return ws
    }

    /**
     * Returns the directory that holds all extracted ZIP projects. v0.0.6.
     *
     * Each uploaded ZIP is extracted into a subfolder named after the ZIP's
     * base name (with a numeric suffix if a folder with the same name
     * already exists, so re-uploading the same ZIP never overwrites the
     * previously extracted copy).
     */
    fun localProjectsRoot(context: Context): File {
        val base = context.getExternalFilesDir(null)
            ?: context.filesDir
        val dir = File(base, LOCAL_PROJECTS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Lists all extracted project folders. v0.0.6. */
    fun listExtractedProjects(context: Context): List<File> {
        val root = localProjectsRoot(context)
        return root.listFiles { f -> f.isDirectory }?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * True if [uri] points inside the local workspace OR the extracted
     * projects tree.
     */
    fun isLocalWorkspaceUri(uri: Uri): Boolean {
        val s = uri.toString()
        if (!s.startsWith("file://")) return false
        val path = uri.path ?: return false
        return path.contains("/$LOCAL_WORKSPACE_DIR/") ||
            path.contains("/$LOCAL_PROJECTS_DIR/")
    }

    /**
     * Resolves [uri] to a [DocumentFile] and returns it, or null if the
     * document no longer exists (file deleted, permission revoked, etc.).
     *
     * Both SAF tree URIs and `file://` URIs (used by the local workspace)
     * are supported transparently.
     */
    fun resolve(context: Context, uri: Uri): DocumentFile? = when {
        uri.toString().startsWith("content://") -> {
            // Tree URIs come from the SAF picker and look like
            // content://com.android.externalstorage.documents/tree/primary%3AViperCode
            // or document URIs without a "tree" segment.
            val s = uri.toString()
            if (s.contains("/tree/")) {
                DocumentFile.fromTreeUri(context, uri)
            } else {
                DocumentFile.fromSingleUri(context, uri)
            }
        }
        uri.toString().startsWith("file://") -> {
            val path = uri.path ?: return null
            DocumentFile.fromFile(File(path))
        }
        else -> null
    }

    /**
     * Reads the file at [uri] as a UTF-8 string. If the file exceeds
     * [MAX_FILE_BYTES], the read is truncated and the [EditorTab] will
     * be marked read-only by the caller (handled at the repository layer).
     */
    suspend fun readText(context: Context, uri: Uri): String {
        return try {
            val resolver = context.contentResolver
            val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            val truncate = size in 1..Long.MAX_VALUE && size > MAX_FILE_BYTES
            resolver.openInputStream(uri)?.use { input ->
                val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
                if (!truncate) reader.readText() else {
                    val buf = CharArray(MAX_FILE_BYTES.toInt())
                    val read = reader.read(buf)
                    if (read <= 0) "" else String(buf, 0, read)
                }
            } ?: ""
        } catch (e: Throwable) {
            // Rethrow coroutine cancellation so structured concurrency works
            // correctly. Without this, a cancelled IO read would surface as
            // a generic IOException to the parent coroutine.
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw IOException("Read failed: ${e.message}", e)
        }
    }

    /**
     * Writes [text] back to [uri], replacing the previous content.
     * Honours the SAF take/persist permission that the user granted when
     * opening the folder, OR writes directly to the local file for
     * `file://` URIs.
     */
    suspend fun writeText(context: Context, uri: Uri, text: String) {
        try {
            val s = uri.toString()
            if (s.startsWith("file://")) {
                val path = uri.path ?: throw IOException("Invalid file uri: $uri")
                File(path).writeText(text, StandardCharsets.UTF_8)
                return
            }
            val resolver = context.contentResolver
            resolver.openOutputStream(uri, "wt")?.use { os ->
                OutputStreamWriter(os, StandardCharsets.UTF_8).use { it.write(text) }
            } ?: throw IOException("Open output stream returned null")
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw IOException("Write failed: ${e.message}", e)
        }
    }

    /**
     * Lists the children of [dirUri] as [FileNode]s. Returns an empty
     * list if the document is not a directory or has been removed.
     *
     * Directories are sorted first (alphabetical, case-insensitive),
     * then files follow in the same order — matches the UX of most
     * desktop file explorers.
     */
    suspend fun listChildren(context: Context, dirUri: Uri): List<FileNode> {
        return try {
            val dir = resolve(context, dirUri)
            if (dir == null || !dir.isDirectory) return emptyList()
            dir.listFiles().map { it.toFileNode(parent = dirUri) }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptyList()
        }
    }

    /**
     * Creates a new file inside [parentUri] with [name]. If a file with
     * the same name already exists, a numeric suffix is appended so the
     * call never overwrites existing data.
     *
     * v0.0.4 fix — extension duplication bug:
     *  The previous implementation always passed a derived MIME type
     *  (e.g. "text/html") to `DocumentFile.createFile(mime, displayName)`.
     *  On many Android versions the ExternalStorageProvider:
     *    1. strips the trailing extension that matches the MIME's
     *       primary extension (if any), then
     *    2. appends its own preferred extension derived from the MIME.
     *  When the provider's preferred extension differs from the one the
     *  user typed (e.g. user types ".html" but the provider maps
     *  "text/html" → ".htm"), the result is "hieu.html.htm".
     *  The same bug produces "gg.css.css" when the provider recognises
     *  "text/css" but still re-appends ".css" because the strip step
     *  looks for ".htm" (the wrong extension).
     *
     *  Fix: when the user already supplied an extension, pass MIME type
     *  "application/octet-stream" so SAF leaves the file name untouched.
     *  We only fall back to a derived MIME type when the user did NOT
     *  supply an extension (e.g. creating "README" → "README.txt").
     */
    suspend fun createFile(context: Context, parentUri: Uri, name: String): FileNode? {
        return try {
            val parent = resolve(context, parentUri) ?: return null
            val finalName = uniqueName(parent, name)
            // If the user supplied an extension, ask SAF NOT to alter the
            // name by passing a generic MIME type. Otherwise derive a
            // sensible MIME from the (extension-less) name.
            val dotIdx = finalName.lastIndexOf('.')
            val hasExtension = dotIdx > 0 && dotIdx < finalName.length - 1
            val mime = if (hasExtension) "application/octet-stream" else mimeFromName(finalName)
            val created = parent.createFile(mime, finalName) ?: return null
            created.toFileNode(parent = parentUri)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    suspend fun createDirectory(context: Context, parentUri: Uri, name: String): FileNode? {
        return try {
            val parent = resolve(context, parentUri) ?: return null
            val finalName = uniqueName(parent, name, isDir = true)
            val created = parent.createDirectory(finalName) ?: return null
            created.toFileNode(parent = parentUri)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    suspend fun rename(context: Context, uri: Uri, newName: String): Boolean {
        return try {
            resolve(context, uri)?.renameTo(newName) ?: false
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }

    suspend fun delete(context: Context, uri: Uri): Boolean {
        return try {
            resolve(context, uri)?.delete() ?: false
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }

    /**
     * Creates a copy of [srcUri] inside [parentUri] with the same name
     * (uniquified so it never overwrites). Recursively copies directories.
     *
     * v0.0.4.
     */
    suspend fun duplicate(context: Context, srcUri: Uri, parentUri: Uri): FileNode? {
        return try {
            val src = resolve(context, srcUri) ?: return null
            val parent = resolve(context, parentUri) ?: return null
            val baseName = src.name ?: "copy"
            val copyName = uniqueName(parent, baseName, isDir = src.isDirectory)
            val mime = if (src.isDirectory) null
            else {
                val dotIdx = copyName.lastIndexOf('.')
                val hasExt = dotIdx > 0 && dotIdx < copyName.length - 1
                if (hasExt) "application/octet-stream" else (src.type ?: "application/octet-stream")
            }
            val newDoc = if (src.isDirectory) {
                parent.createDirectory(copyName) ?: return null
            } else {
                parent.createFile(mime ?: "application/octet-stream", copyName) ?: return null
            }
            if (src.isDirectory) copyTree(context, src, newDoc)
            else copyFileContents(context, src, newDoc)
            newDoc.toFileNode(parent = parentUri)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    private suspend fun copyTree(context: Context, src: DocumentFile, dst: DocumentFile) {
        for (child in src.listFiles()) {
            val childName = child.name ?: continue
            val target = if (child.isDirectory) {
                dst.createDirectory(childName) ?: continue
            } else {
                val dotIdx = childName.lastIndexOf('.')
                val hasExt = dotIdx > 0 && dotIdx < childName.length - 1
                val mime = if (hasExt) "application/octet-stream" else (child.type ?: "application/octet-stream")
                dst.createFile(mime, childName) ?: continue
            }
            if (child.isDirectory) copyTree(context, child, target)
            else copyFileContents(context, child, target)
        }
    }

    private suspend fun copyFileContents(context: Context, src: DocumentFile, dst: DocumentFile) {
        try {
            val input = context.contentResolver.openInputStream(src.uri) ?: return
            val output = context.contentResolver.openOutputStream(dst.uri, "wt") ?: return
            input.use { i ->
                output.use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val r = i.read(buf)
                        if (r <= 0) break
                        o.write(buf, 0, r)
                    }
                }
            }
        } catch (_: Throwable) {
            // Best-effort copy — ignore failures on individual files.
        }
    }

    /**
     * Recursively walks [rootUri] and yields every file whose name or
     * content matches [query]. Used by the v0.0.4 "Search in files"
     * feature. The walk is breadth-first so shallow matches show first.
     *
     * The query is matched case-insensitively against both file names
     * and file contents. Files larger than [MAX_FILE_BYTES] are skipped
     * for content search to avoid OOM on huge binaries.
     *
     * Returns at most [limit] results so a huge workspace doesn't
     * starve the IO dispatcher.
     */
    suspend fun searchInFiles(
        context: Context,
        rootUri: Uri,
        query: String,
        limit: Int = 200,
    ): List<SearchHit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = query.trim()
        val qLower = q.lowercase()
        val results = mutableListOf<SearchHit>()
        val queue = ArrayDeque<Uri>()
        queue.addLast(rootUri)
        val visited = HashSet<Uri>()
        while (queue.isNotEmpty() && results.size < limit) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            val dir = resolve(context, current) ?: continue
            if (!dir.isDirectory) continue
            for (child in dir.listFiles()) {
                if (results.size >= limit) break
                val name = child.name ?: continue
                if (child.isDirectory) {
                    queue.addLast(child.uri)
                    continue
                }
                // Match by name first (cheap).
                if (name.contains(q, ignoreCase = true)) {
                    results.add(SearchHit(child.uri, name, 0, 0, matchedInName = true))
                    continue
                }
                // Skip files that are too large.
                val len = child.length()
                if (len <= 0 || len > MAX_FILE_BYTES) continue
                // Match by content (expensive).
                val text = try {
                    readText(context, child.uri)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    null
                } ?: continue
                val idx = text.lowercase().indexOf(qLower)
                if (idx >= 0) {
                    val (line, col) = lineColForOffset(text, idx)
                    results.add(SearchHit(child.uri, name, line, col, matchedInName = false))
                }
            }
        }
        results
    }

    private fun lineColForOffset(text: String, offset: Int): Pair<Int, Int> {
        var line = 1
        var col = 1
        var i = 0
        val end = offset.coerceAtMost(text.length)
        while (i < end) {
            if (text[i] == '\n') { line++; col = 1 } else col++
            i++
        }
        return line to col
    }

    /** A single search hit — used by [searchInFiles]. */
    data class SearchHit(
        val uri: Uri,
        val fileName: String,
        val line: Int,
        val column: Int,
        val matchedInName: Boolean,
    )

    private fun uniqueName(parent: DocumentFile, name: String, isDir: Boolean = false): String {
        if (parent.findFile(name) == null) return name
        val dot = name.lastIndexOf('.')
        val (base, ext) = when {
            isDir || dot < 0 -> name to ""
            else -> name.substring(0, dot) to name.substring(dot)
        }
        // Cap the loop to avoid infinite recursion on buggy providers.
        for (i in 1..1000) {
            val candidate = "$base ($i)$ext"
            if (parent.findFile(candidate) == null) return candidate
        }
        // Last-resort fallback — append a UUID suffix if all numeric
        // candidates somehow collided.
        return "$base (${java.util.UUID.randomUUID().toString().take(8)})$ext"
    }

    private fun mimeFromName(name: String): String {
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return when (ext) {
            "txt" -> "text/plain"
            "kt", "kts" -> "text/x-kotlin"
            "java" -> "text/x-java-source"
            "py" -> "text/x-python"
            "js", "mjs" -> "text/javascript"
            "ts" -> "application/typescript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "md" -> "text/markdown"
            "sh" -> "application/x-sh"
            "c", "h" -> "text/x-csrc"
            "cpp", "hpp" -> "text/x-c++src"
            "go" -> "text/x-go"
            "rs" -> "text/rust"
            "sql" -> "application/sql"
            "yaml", "yml" -> "application/yaml"
            "toml" -> "application/toml"
            else -> "application/octet-stream"
        }
    }

    fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }

    val DefaultCharset: Charset = StandardCharsets.UTF_8

    /**
     * Builds an initial URI for [androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree]
     * that points at the device's primary external storage root. v0.0.6.
     *
     * The SAF picker remembers the last-used location across launches, so
     * if the user previously picked a folder inside Termux (or any other
     * storage provider), subsequent invocations of
     * `OpenDocumentTree.launch(null)` reopen that same root — the user
     * sees "only Termux" and reports that the picker "doesn't show the
     * rest of the device". Passing this URI as the initial location
     * forces the picker to start at the primary shared storage root so
     * the user can navigate to any folder on the device from there.
     *
     * Returns null on devices / ROMs where the ExternalStorageProvider
     * root URI can't be built (shouldn't happen on any stock Android
     * 7.1+ device, but we still guard).
     */
    fun primaryStorageRootUri(): Uri? = runCatching {
        DocumentsContract.buildRootUri(
            "com.android.externalstorage.documents",
            "primary",
        )
    }.getOrNull()

    /**
     * Extracts a ZIP archive picked via SAF into a new subfolder under
     * [localProjectsRoot]. Returns the directory the archive was
     * extracted to (so the caller can switch the open folder to it).
     *
     * v0.0.6.
     *
     * Behaviour:
     *  - The new folder is named after the ZIP's base name (without the
     *    `.zip` extension). A numeric suffix is appended if a folder with
     *    the same name already exists, so re-uploading the same ZIP
     *    never overwrites the previously extracted copy.
     *  - Path traversal entries (`../`, absolute paths starting with
     *    `/`) are skipped — this is the standard ZipSlip mitigation.
     *  - Directory entries inside the ZIP are created as real directories
     *    (with `mkdirs()`), even when they have zero children.
     *  - Files inside the ZIP replace any existing file at the same path
     *    inside the destination folder.
     *  - The ZIP is streamed from the SAF URI via [ContentResolver];
     *    no intermediate copy on disk is required.
     */
    suspend fun extractZipToProjects(
        context: Context,
        zipUri: Uri,
        suggestedName: String? = null,
    ): File = withContext(Dispatchers.IO) {
        val baseName = (suggestedName ?: displayName(context, zipUri) ?: "project")
            .substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "project" }
        val projectsRoot = localProjectsRoot(context)
        val destDir = uniqueDirectory(projectsRoot, baseName)
        destDir.mkdirs()

        val resolver = context.contentResolver
        resolver.openInputStream(zipUri).use { input ->
            if (input == null) throw IOException("Cannot open ZIP input stream")
            ZipInputStream(input).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    // ZipSlip mitigation: refuse any entry that tries to
                    // escape the destination root.
                    val target = File(destDir, name).canonicalFile
                    val destCanon = destDir.canonicalFile
                    if (!target.path.startsWith(destCanon.path + File.separator) &&
                        target != destCanon
                    ) {
                        // Skip dangerous entry.
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { os ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val r = zis.read(buf)
                                if (r <= 0) break
                                os.write(buf, 0, r)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        destDir
    }

    /** Returns the display name of a SAF URI, or null if it can't be resolved. */
    private fun displayName(context: Context, uri: Uri): String? {
        return runCatching {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null,
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) it.getString(idx) else null
                } else null
            }
        }.getOrNull() ?: uri.lastPathSegment
    }

    /** Returns a non-existent directory name under [parent] based on [baseName]. */
    private fun uniqueDirectory(parent: File, baseName: String): File {
        if (!File(parent, baseName).exists()) return File(parent, baseName)
        for (i in 1..1000) {
            val candidate = File(parent, "$baseName ($i)")
            if (!candidate.exists()) return candidate
        }
        return File(parent, "$baseName (${java.util.UUID.randomUUID().toString().take(8)})")
    }
}
