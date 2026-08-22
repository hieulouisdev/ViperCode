package com.vipercode.ide.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipercode.ide.data.repo.FileRepository
import com.vipercode.ide.util.FileUtils
import com.vipercode.ide.util.Strings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Search-in-files screen (v0.0.4).
 *
 * Walks the entire open workspace looking for files whose name OR
 * contents match the user's query (case-insensitive substring). The
 * walk is delegated to [FileUtils.searchInFiles] (breadth-first,
 * capped at 200 hits) so even a 1 000-file workspace responds within
 * a second on mid-range hardware.
 *
 * Tap a hit to open it in the editor at the matching line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchInFilesScreen(
    onBack: () -> Unit,
    onOpenFile: (tabId: String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { FileRepository.get(context) }
    val scope = rememberCoroutineScope()
    val openFolder by repo.openFolder.collectAsState()

    val activeLanguage by Strings.active.collectAsState()
    val s = Strings.get()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FileUtils.SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    // Debounced search — fire 350 ms after the user stops typing.
    LaunchedEffect(query, openFolder?.uri) {
        // v0.0.8 — capture the folder at the top of the effect
        // so a folder-close between `delay` returning and
        // `openFolder!!.uri` evaluating doesn't NPE.
        val folder = openFolder ?: run {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        if (query.isBlank()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(350)
        val hits = repo.searchInFiles(folder.uri, query)
        results = hits
        searching = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.dialogSearchInFilesTitle) },
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
                placeholder = { Text(s.dialogSearchInFilesHint) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            )
            HorizontalDivider()
            if (searching) {
                // v0.0.7 — replace plain "…" text with a proper
                // Material 3 CircularProgressIndicator + loading label.
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
            } else if (results.isEmpty() && query.isNotBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Search,
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
                    // v0.0.8 — include the result index in the key so
                    // duplicate (uri, line, column) tuples don't crash
                    // the LazyColumn with "Key X was already used".
                    itemsIndexed(results) { idx, hit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        // v0.0.7 — openFile now returns
                                        // RepoResult; unwrap on the caller side.
                                        val result = repo.openFile(hit.uri)
                                        val tab = (result as? com.vipercode.ide.data.repo.RepoResult.Success)?.value
                                        if (tab != null) {
                                            // v0.0.8 — coerce negative values
                                            // so cursor math doesn't crash on
                                            // name-only hits where line/column
                                            // are 0.
                                            repo.updateTabCursor(
                                                tab.id,
                                                (hit.line - 1).coerceAtLeast(0),
                                                (hit.column - 1).coerceAtLeast(0),
                                            )
                                            onOpenFile(tab.id)
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = hit.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!hit.matchedInName) {
                                    Text(
                                        text = "${hit.line}:${hit.column}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    )
                                } else {
                                    // v0.0.7 — distinguish name-match hits
                                    // from content-match hits visually.
                                    Text(
                                        text = s.commonMatchInFile.format(1, hit.fileName),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
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
}
