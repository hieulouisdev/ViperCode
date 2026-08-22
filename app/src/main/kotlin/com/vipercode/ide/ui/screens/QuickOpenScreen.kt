package com.vipercode.ide.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vipercode.ide.data.model.FileNode
import com.vipercode.ide.data.repo.FileRepository
import com.vipercode.ide.util.FileUtils
import com.vipercode.ide.util.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick-open screen (v0.0.4) — VS Code "Ctrl+P" style file picker.
 *
 * Walks the entire open workspace (recursively) and shows every file
 * whose name matches the user's query (case-insensitive substring).
 * Tap a result to open it in the editor.
 *
 * The walk is cached on first open so subsequent queries filter the
 * cached list in O(n) on the main thread without re-walking the tree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickOpenScreen(
    onBack: () -> Unit,
    onOpenFile: (tabId: String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { FileRepository.get(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val openFolder by repo.openFolder.collectAsState()

    val activeLanguage by Strings.active.collectAsState()
    val s = Strings.get()

    var query by remember { mutableStateOf("") }
    var allFiles by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var results by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    // Walk the tree once on first composition (or when the open
    // folder changes). Subsequent queries filter the cached list.
    LaunchedEffect(openFolder?.uri) {
        if (openFolder == null) return@LaunchedEffect
        loading = true
        val files = withContext(Dispatchers.IO) {
            val out = mutableListOf<FileNode>()
            val queue = ArrayDeque<Uri>()
            queue.addLast(openFolder!!.uri)
            val visited = HashSet<Uri>()
            while (queue.isNotEmpty() && out.size < 1000) {
                val current = queue.removeFirst()
                if (!visited.add(current)) continue
                val children = FileUtils.listChildren(context, current)
                for (child in children) {
                    if (child.isDirectory) queue.addLast(child.uri)
                    else out.add(child)
                }
            }
            out
        }
        allFiles = files
        results = files
        loading = false
    }

    // Filter the cached list as the user types. Case-insensitive
    // substring match. We also rank results so files whose NAME
    // (not path) starts with the query bubble to the top.
    LaunchedEffect(query, allFiles) {
        if (query.isBlank()) {
            results = allFiles.take(200)
            return@LaunchedEffect
        }
        val q = query.lowercase()
        val matched = allFiles.filter { it.name.contains(q, ignoreCase = true) }
        results = matched.sortedWith(
            compareBy(
                { !it.name.lowercase().startsWith(q) },  // starts-with first
                { !it.name.substringBeforeLast('.').lowercase().contains(q) },  // basename contains
                { it.name.lowercase() },  // alphabetical tie-breaker
            )
        ).take(200)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.dialogQuickOpenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.editorBack)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text(s.dialogQuickOpenTitle) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            )
            HorizontalDivider()
            if (loading) {
                // v0.0.7 — proper loading indicator (was just "…").
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = s.commonLoading,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else if (results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = s.commonNoResults,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.uri.toString() }) { node ->
                        Row(
                            modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    // v0.0.7 — openFile now returns RepoResult.
                                    val result = repo.openFile(node.uri)
                                    val tab = (result as? com.vipercode.ide.data.repo.RepoResult.Success)?.value
                                    if (tab != null) onOpenFile(tab.id)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            // v0.0.7 — removed dead `isDirectory`
                            // branch; `allFiles` only contains files.
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // v0.0.7 — show the parent path so users can
                            // disambiguate same-named files.
                            val pathSegment = node.parentUri?.let { p ->
                                val decoded = android.net.Uri.decode(p.toString())
                                decoded.substringAfterLast('/').ifBlank { null }
                            }
                            if (pathSegment != null) {
                                Text(
                                    text = pathSegment,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
