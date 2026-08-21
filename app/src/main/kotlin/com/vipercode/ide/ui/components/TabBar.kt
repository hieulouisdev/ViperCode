package com.vipercode.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vipercode.ide.data.model.EditorTab

/**
 * Horizontally scrollable row of open editor tabs.
 *
 * Behaviour mirrors modern desktop editors (VS Code, IntelliJ):
 *   - click → activate
 *   - click on the × → close (caller is responsible for unsaved-changes
 *     confirmation)
 *   - dirty tabs render with a dot prefix instead of a dot suffix
 */
@Composable
fun TabBar(
    tabs: List<EditorTab>,
    activeTabId: String?,
    onActivate: (String) -> Unit,
    onClose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) {
        items(tabs, key = { it.id }) { tab ->
            TabChip(
                tab = tab,
                active = tab.id == activeTabId,
                onActivate = { onActivate(tab.id) },
                onClose = { onClose(tab.id) },
            )
        }
    }
}

@Composable
private fun TabChip(
    tab: EditorTab,
    active: Boolean,
    onActivate: () -> Unit,
    onClose: () -> Unit,
) {
    val bg = if (active) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.surfaceVariant
    // v0.0.3: cap the chip width so a single long file name can't push
    // every other chip off-screen. The chip still grows for short names
    // but ellipsises after 180 dp.
    Box(
        modifier = Modifier
            .height(36.dp)
            .background(bg)
            .clickable { onActivate() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.widthIn(max = 180.dp, min = 60.dp),
        ) {
            if (tab.isDirty) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = tab.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close tab",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
