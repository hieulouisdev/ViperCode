package com.vipercode.ide.data.model

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.vipercode.ide.util.Language
import com.vipercode.ide.util.LanguageDetector
import java.util.UUID

/**
 * Lightweight, immutable snapshot of a file or directory in the user's
 * workspace.
 *
 * Designed to flow through Compose without coupling to [DocumentFile]
 * (which holds a live reference to the SAF tree and must not be cached
 * across configuration changes).
 */
data class FileNode(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String?,
    val parentUri: Uri?,
    val children: List<FileNode> = emptyList(),
    val isExpandable: Boolean = isDirectory,
) {
    val extension: String
        get() = name.substringAfterLast('.', missingDelimiterValue = "")

    val language: Language
        get() = LanguageDetector.detect(name, mimeType)
}

/** An open document in the editor (one per tab). */
data class EditorTab(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val language: Language,
    val content: String = "",
    val originalContent: String = "",
    val scrollOffset: Int = 0,
    val cursorLine: Int = 0,
    val cursorColumn: Int = 0,
    val encoding: String = "UTF-8",
    val readOnly: Boolean = false,
    /**
     * v0.0.7 — set when the source file exceeds the inline-edit
     * threshold (5 MB). The editor shows a banner indicating the
     * content is truncated.
     */
    val truncated: Boolean = false,
) {
    val isDirty: Boolean get() = content != originalContent
}

/**
 * Builds a [FileNode] from a SAF [DocumentFile]. The children list is
 * left empty — callers must explicitly populate it via the file
 * repository so the snapshot stays cheap to create.
 */
fun DocumentFile.toFileNode(parent: Uri? = null): FileNode = FileNode(
    uri = uri,
    name = name ?: "(unknown)",
    isDirectory = isDirectory,
    size = if (isDirectory) 0L else length(),
    lastModified = lastModified(),
    mimeType = type,
    parentUri = parent,
)
