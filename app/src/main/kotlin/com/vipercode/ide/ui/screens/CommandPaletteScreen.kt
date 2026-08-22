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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vipercode.ide.util.Strings

/**
 * Command palette (v0.0.8 — new feature).
 *
 * VS Code "Ctrl+Shift+P" style launcher. Lets the user discover
 * and execute any ViperCode action by typing a substring of its
 * name. Selected commands can take parameters (e.g. Go-to-Line
 * accepts a line number).
 *
 * The actual command list is provided by the caller via [commands]
 * — this keeps the palette pure and lets each host screen expose
 * only its own actions (editor commands differ from home commands).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPaletteScreen(
    onBack: () -> Unit,
    commands: List<Command>,
    onExecute: (Command) -> Unit,
) {
    val s = Strings.get()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(commands) }
    var selectedIdx by remember { mutableStateOf(0) }

    LaunchedEffect(query, commands) {
        if (query.isBlank()) {
            results = commands
            selectedIdx = 0
            return@LaunchedEffect
        }
        val q = query.lowercase()
        results = commands.filter { cmd ->
            cmd.title.contains(q, ignoreCase = true) ||
                cmd.description.contains(q, ignoreCase = true) ||
                cmd.category.contains(q, ignoreCase = true)
        }.sortedWith(
            compareBy(
                { !it.title.startsWith(query, ignoreCase = true) },
                { !it.title.contains(" $q", ignoreCase = true) },
                { it.title.lowercase() },
            )
        )
        if (selectedIdx >= results.size) selectedIdx = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.dialogCommandPaletteTitle) },
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
                placeholder = { Text(s.dialogCommandPaletteHint) },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (selectedIdx in results.indices) {
                        onExecute(results[selectedIdx])
                    }
                }),
            )
            HorizontalDivider()
            if (results.isEmpty()) {
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
                    itemsIndexed(results) { idx, cmd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onExecute(cmd)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cmd.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (cmd.description.isNotEmpty()) {
                                    Text(
                                        text = cmd.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                text = cmd.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * A single command in the palette.
 *
 * @property id          Stable identifier; the host uses this to
 *                       dispatch the action.
 * @property title       Display name (e.g. "Go to Line").
 * @property description One-line description.
 * @property category    Short tag (e.g. "Editor", "File", "View").
 */
data class Command(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String = "",
)
