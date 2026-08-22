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
 * v0.0.8 changes:
 *  - **48dp tap targets** — the close button is now an `IconButton`
 *    with the default 48dp size; the inner icon stays at 16dp for
 *    visual proportion.
 *  - **Animated dirty dot** — the dirty indicator now pulses gently
 *    so unsaved changes are visually obvious at a glance.
 *  - **`role = Button`** added to the chip and close for
 *    accessibility.
 *  - **`contentDescription`** for the close button is now i18n'd via
 *    `Strings.get().editorCloseTab`.
 *  - **Tab height bumped to 48dp** for more breathing room (was 40dp
 *    in v0.0.7, 36dp before) — meets Material 3 minimum tap target.
 *  - **Auto-scroll to active tab** — the `LazyRow` now keeps the
 *    active tab scrolled into view.
 *  - **Pulse animation** moved out of the conditional branch so
 *    `rememberInfiniteTransition` is called unconditionally (was
 *    crashing with `IllegalStateException` when `isDirty` flipped).
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
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // v0.0.8 — auto-scroll the active tab into view when the active
    // tab changes OR when the tab list size changes (e.g. a new tab
    // is opened off-screen).
    androidx.compose.runtime.LaunchedEffect(activeTabId, tabs.size) {
        if (activeTabId == null) return@LaunchedEffect
        val idx = tabs.indexOfFirst { it.id == activeTabId }
        if (idx < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val firstVisible = visible.firstOrNull()?.index ?: 0
        val lastVisible = visible.lastOrNull()?.index ?: 0
        // Only scroll if the active tab is outside the visible window.
        if (idx < firstVisible || idx > lastVisible) {
            listState.animateScrollToItem(idx)
        }
    }
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
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
    // v0.0.8 — `rememberInfiniteTransition` MUST be called
    // unconditionally. Hoisting it here so that when `tab.isDirty`
    // flips, Compose doesn't throw `IllegalStateException`
    // ("Composable was called in an order that violates the rules").
    val pulseTransition = rememberInfiniteTransition(label = "dirty-pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dirty-pulse-alpha",
    )

    val bg = if (active) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .height(48.dp)
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
                // v0.0.7 — gentle pulse on the dirty dot. The
                // animation state is hoisted above this branch so the
                // remember call is unconditional.
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
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
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
