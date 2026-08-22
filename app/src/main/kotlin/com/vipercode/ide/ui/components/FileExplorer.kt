package com.vipercode.ide.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipercode.ide.data.model.FileNode
import com.vipercode.ide.util.FileUtils
import com.vipercode.ide.util.Language
import com.vipercode.ide.util.Strings

/**
 * A scrollable tree view of the open workspace.
 *
 * v0.0.3 architecture change: the tree is now flattened into a
 * single [List] of [FlatRow]s and rendered via a [LazyColumn].
 * v0.0.2 used eager recursion inside `AnimatedVisibility { Column { sub.forEach { FileRow(...) } } }`
 * — that composed the ENTIRE subtree eagerly, creating thousands of
 * composables for large workspaces. The flattened approach gives us
 * full virtualization: only visible rows are composed.
 *
 * Long-press a file/folder to surface the per-row context menu
 * (rename, delete).
 *
 * v0.0.6 — the empty-state strings are routed through [Strings.get]
 * so the explorer honours the user's interface language, and an
 * "empty folder" hint is shown when the open folder is non-null but
 * has no children (matches the v0.0.6 fix for "click 'use local
 * workspace' and nothing shows" — the local workspace was empty and
 * the old UI rendered a blank tree with no explanation).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExplorer(
    root: FileNode?,
    children: Map<android.net.Uri, List<FileNode>>,
    expanded: Set<android.net.Uri>,
    onToggleFolder: (android.net.Uri) -> Unit,
    onOpenFile: (FileNode) -> Unit,
    onLongPress: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (root == null) {
        EmptyWorkspace(modifier = modifier)
        return
    }
    // v0.0.7 — flattenTree is now remembered so it isn't recomputed
    // on every recomposition. Previously every keystroke in the
    // editor rebuilt the list.
    val flatRows = remember(root, children, expanded) {
        flattenTree(root, children, expanded)
    }
    val s = Strings.get()

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "header:${root.uri}") {
            FolderHeader(name = root.name)
        }
        if (flatRows.isEmpty()) {
            item(key = "empty-folder-hint") {
                EmptyFolderHint(s.homeEmptyFolder, s.homeEmptyFolderHint)
            }
        } else {
            items(flatRows, key = { it.node.uri.toString() + ":" + it.depth }) { row ->
                FlatFileRow(
                    row = row,
                    isExpanded = row.node.uri in expanded,
                    onToggleFolder = onToggleFolder,
                    onOpenFile = onOpenFile,
                    onLongPress = onLongPress,
                )
            }
        }
    }
}

/**
 * Walks the tree depth-first, emitting a [FlatRow] for every node
 * that should be visible given the current [expanded] set.
 *
 * Collapsed directories contribute only themselves; expanded directories
 * also contribute their children (recursively).
 */
private fun flattenTree(
    root: FileNode,
    children: Map<android.net.Uri, List<FileNode>>,
    expanded: Set<android.net.Uri>,
): List<FlatRow> {
    val out = mutableListOf<FlatRow>()
    val stack = ArrayDeque<FlatRow>()
    // Push root's children in REVERSE so we pop them in forward order.
    children[root.uri].orEmpty().asReversed().forEach { child ->
        stack.addLast(FlatRow(child, depth = 1))
    }
    while (stack.isNotEmpty()) {
        val row = stack.removeLast()
        out.add(row)
        if (row.node.isDirectory && row.node.uri in expanded) {
            children[row.node.uri].orEmpty().asReversed().forEach { child ->
                stack.addLast(FlatRow(child, depth = row.depth + 1))
            }
        }
    }
    return out
}

private data class FlatRow(val node: FileNode, val depth: Int)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlatFileRow(
    row: FlatRow,
    isExpanded: Boolean,
    onToggleFolder: (android.net.Uri) -> Unit,
    onOpenFile: (FileNode) -> Unit,
    onLongPress: (FileNode) -> Unit,
) {
    val node = row.node
    val rotate by animateFloatAsState(if (isExpanded) 90f else 0f, label = "chevron")
    // v0.0.7 — cache humanSize per node so it isn't recomputed
    // on every recomposition.
    val sizeText = remember(node.size) {
        if (!node.isDirectory && node.size > 0) FileUtils.humanSize(node.size) else null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (node.isDirectory) onToggleFolder(node.uri)
                    else onOpenFile(node)
                },
                onLongClick = { onLongPress(node) },
            )
            .padding(start = (row.depth * 12 + 8).dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.isDirectory) {
            // v0.0.7 — IconButton uses default 48dp tap target
            // (was size(20.dp), below the Material 3 minimum).
            IconButton(
                onClick = { onToggleFolder(node.uri) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotate),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.width(28.dp))
        }
        Icon(
            imageVector = if (node.isDirectory) Icons.Filled.Folder else iconFor(node),
            contentDescription = null,
            tint = if (node.isDirectory) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sizeText != null) {
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun FolderHeader(name: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun iconFor(node: FileNode) = when (node.language) {
    Language.MARKDOWN, Language.TEXT -> Icons.Outlined.InsertDriveFile
    else -> Icons.Filled.Description
}

/**
 * v0.0.6 — shown when the open folder exists but contains no files.
 *
 * Previously the explorer just rendered an empty `LazyColumn`, so when
 * the user tapped "Use local workspace" and the local workspace had
 * just been created (and was therefore empty), the screen looked
 * blank. The hint tells the user to use the + FAB to create a new
 * file or folder so they understand they CAN actually do something.
 */
@Composable
private fun EmptyFolderHint(title: String, body: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyWorkspace(modifier: Modifier = Modifier) {
    val s = Strings.get()
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = s.homeNoFolder,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = s.homePickFolder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
