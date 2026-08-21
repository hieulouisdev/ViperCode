package com.vipercode.ide.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.vipercode.ide.data.model.FileNode
import com.vipercode.ide.data.model.toFileNode
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Pure-Java bridge between the Android Storage Access Framework (SAF)
 * tree and the rest of the app.
 *
 * Every public function is suspend so it can be called from a ViewModel
 * without blocking the main thread. None of the functions cache
 * [DocumentFile] across calls — the URI is the single source of truth,
 * and a fresh [DocumentFile] is created per operation.
 */
object FileUtils {

    private const val MAX_FILE_BYTES = 5 * 1024 * 1024 // 5 MB safety cap for v0.0.1

    /**
     * Resolves [uri] to a [DocumentFile] and returns it, or null if the
     * document no longer exists (file deleted, permission revoked, etc.).
     */
    fun resolve(context: Context, uri: Uri): DocumentFile? = when {
        uri.toString().startsWith("content://") -> DocumentFile.fromSingleUri(context, uri)
        uri.toString().startsWith("file://") -> DocumentFile.fromFile(java.io.File(uri.path ?: ""))
        else -> null
    }

    /**
     * Reads the file at [uri] as a UTF-8 string. If the file exceeds
     * [MAX_FILE_BYTES], the read is truncated and the [EditorTab] will
     * be marked read-only by the caller (handled at the repository layer).
     */
    suspend fun readText(context: Context, uri: Uri): String = runCatching {
        val resolver = context.contentResolver
        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1
        val truncate = size in 1..Long.MAX_VALUE && size > MAX_FILE_BYTES
        resolver.openInputStream(uri)?.use { input ->
            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
            if (!truncate) reader.readText() else {
                val buf = CharArray(MAX_FILE_BYTES.toInt())
                val read = reader.read(buf)
                String(buf, 0, read)
            }
        } ?: ""
    }.getOrElse { throw IOException("Read failed: ${it.message}", it) }

    /**
     * Writes [text] back to [uri], replacing the previous content.
     * Honours the SAF take/persist permission that the user granted when
     * opening the folder.
     */
    suspend fun writeText(context: Context, uri: Uri, text: String): Unit = runCatching {
        val resolver = context.contentResolver
        resolver.openOutputStream(uri, "wt")?.use { os ->
            OutputStreamWriter(os, StandardCharsets.UTF_8).use { it.write(text) }
        } ?: throw IOException("Open output stream returned null")
    }.getOrElse { throw IOException("Write failed: ${it.message}", it) }

    /**
     * Lists the children of [dirUri] as [FileNode]s. Returns an empty
     * list if the document is not a directory or has been removed.
     *
     * Directories are sorted first (alphabetical, case-insensitive),
     * then files follow in the same order — matches the UX of most
     * desktop file explorers.
     */
    suspend fun listChildren(context: Context, dirUri: Uri): List<FileNode> = runCatching {
        val dir = DocumentFile.fromTreeUri(context, dirUri)
            ?: DocumentFile.fromSingleUri(context, dirUri)
            ?: return emptyList()
        if (!dir.isDirectory) return emptyList()
        dir.listFiles().map { it.toFileNode(parent = dirUri) }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }.getOrElse { emptyList() }

    /**
     * Creates a new file inside [parentUri] with [name]. If a file with
     * the same name already exists, a numeric suffix is appended so the
     * call never overwrites existing data.
     */
    suspend fun createFile(context: Context, parentUri: Uri, name: String): FileNode? =
        runCatching {
            val parent = DocumentFile.fromTreeUri(context, parentUri) ?: return null
            val finalName = uniqueName(parent, name)
            val mime = mimeFromName(name)
            val created = parent.createFile(mime, finalName) ?: return null
            created.toFileNode(parent = parentUri)
        }.getOrNull()

    suspend fun createDirectory(context: Context, parentUri: Uri, name: String): FileNode? =
        runCatching {
            val parent = DocumentFile.fromTreeUri(context, parentUri) ?: return null
            val finalName = uniqueName(parent, name, isDir = true)
            val created = parent.createDirectory(finalName) ?: return null
            created.toFileNode(parent = parentUri)
        }.getOrNull()

    suspend fun rename(context: Context, uri: Uri, newName: String): Boolean = runCatching {
        DocumentFile.fromSingleUri(context, uri)?.renameTo(newName) ?: false
    }.getOrDefault(false)

    suspend fun delete(context: Context, uri: Uri): Boolean = runCatching {
        DocumentFile.fromSingleUri(context, uri)?.delete() ?: false
    }.getOrDefault(false)

    private fun uniqueName(parent: DocumentFile, name: String, isDir: Boolean = false): String {
        if (parent.findFile(name) == null) return name
        val dot = name.lastIndexOf('.')
        val (base, ext) = when {
            isDir || dot < 0 -> name to ""
            else -> name.substring(0, dot) to name.substring(dot)
        }
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (parent.findFile(candidate) == null) return candidate
            i++
        }
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
}
