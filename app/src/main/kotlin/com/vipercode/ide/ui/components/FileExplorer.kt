package com.vipercode.ide.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipercode.ide.data.model.FileNode
import com.vipercode.ide.util.FileUtils

/**
 * A scrollable tree view of the open workspace.
 *
 * Each directory entry keeps an "expanded" flag in the parent
 * composition so the tree can be reused across screens.
 *
 * Long-press a file/folder to surface the per-row context menu
 * (rename, delete, new file, new folder).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
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
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            FolderHeader(name = root.name)
        }
        val nodes = children[root.uri].orEmpty()
        items(nodes, key = { it.uri }) { node ->
            FileRow(
                node = node,
                depth = 1,
                children = children,
                expanded = expanded,
                onToggleFolder = onToggleFolder,
                onOpenFile = onOpenFile,
                onLongPress = onLongPress,
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    node: FileNode,
    depth: Int,
    children: Map<android.net.Uri, List<FileNode>>,
    expanded: Set<android.net.Uri>,
    onToggleFolder: (android.net.Uri) -> Unit,
    onOpenFile: (FileNode) -> Unit,
    onLongPress: (FileNode) -> Unit,
) {
    val isExpanded = node.uri in expanded
    val rotate by animateFloatAsState(if (isExpanded) 90f else 0f, label = "chevron")
    Column {
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
                .padding(start = (depth * 12 + 8).dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
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
        AnimatedVisibility(visible = isExpanded && node.isDirectory) {
            val sub = children[node.uri].orEmpty()
            Column {
                sub.forEach { child ->
                    FileRow(
                        node = child,
                        depth = depth + 1,
                        children = children,
                        expanded = expanded,
                        onToggleFolder = onToggleFolder,
                        onOpenFile = onOpenFile,
                        onLongPress = onLongPress,
                    )
                }
            }
        }
    }
}

private fun iconFor(node: FileNode) = when (node.language) {
    com.vipercode.ide.util.Language.MARKDOWN, com.vipercode.ide.util.Language.TEXT -> Icons.Outlined.InsertDriveFile
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
