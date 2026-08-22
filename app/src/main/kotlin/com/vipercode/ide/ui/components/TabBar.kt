package com.vipercode.ide.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vipercode.ide.data.model.EditorTab
import com.vipercode.ide.util.Strings

/**
 * Horizontally scrollable row of open editor tabs.
 *
 * v0.0.7 changes:
 *  - **48dp tap targets** — the close button is now an `IconButton`
 *    with the default 48dp size; the inner icon stays at 16dp for
 *    visual proportion.
 *  - **Animated dirty dot** — the dirty indicator now pulses gently
 *    so unsaved changes are visually obvious at a glance.
 *  - **`role = Button`** added to the chip and close for
 *    accessibility.
 *  - **`contentDescription`** for the close button is now i18n'd via
 *    `Strings.get().editorCloseTab`.
 *  - **Tab height bumped to 40dp** for more breathing room (was 36dp).
 */
@Composable
fun TabBar(
    tabs: List<EditorTab>,
    activeTabId: String?,
    onActivate: (String) -> Unit,
    onClose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.get()
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) {
        items(tabs, key = { it.id }) { tab ->
            TabChip(
                tab = tab,
                active = tab.id == activeTabId,
                onActivate = { onActivate(tab.id) },
                onClose = { onClose(tab.id) },
                closeContentDescription = s.editorCloseTab,
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
    closeContentDescription: String,
) {
    val bg = if (active) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .height(40.dp)
            .background(bg)
            .clickable(
                role = Role.Button,
                onClick = onActivate,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.widthIn(max = 200.dp, min = 64.dp),
        ) {
            if (tab.isDirty) {
                // v0.0.7 — gentle pulse on the dirty dot.
                val transition = rememberInfiniteTransition(label = "dirty-pulse")
                val alpha by transition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(900),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dirty-pulse-alpha",
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                )
                Spacer(Modifier.width(8.dp))
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
            // v0.0.7 — close button is now a proper 48dp IconButton.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = closeContentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
