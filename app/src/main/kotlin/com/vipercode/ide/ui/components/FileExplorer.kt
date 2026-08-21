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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    // Flatten the visible portion of the tree into a list. Only
    // expanded directories contribute their children; collapsed
    // directories contribute only themselves.
    val flatRows = flattenTree(root, children, expanded)

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "header:${root.uri}") {
            FolderHeader(name = root.name)
        }
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
            IconButton(
                onClick = { onToggleFolder(node.uri) },
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotate),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.width(20.dp))
        }
        Spacer(Modifier.width(8.dp))
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
            if (!node.isDirectory && node.size > 0) {
                Text(
                    text = FileUtils.humanSize(node.size),
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

@Composable
private fun EmptyWorkspace(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "No folder opened",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Pick a folder to start coding",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
