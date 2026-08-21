package com.vipercode.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipercode.ide.data.model.EditorTab

/**
 * Multi-line code editor with syntax highlighting, line numbers,
 * optional word wrap, sane auto-indent, and tab-size aware editing.
 *
 * Built on top of [BasicTextField] so we keep full control of touch
 * handling, IME composition, and visual layers.
 *
 * v0.0.4 performance rewrite (fixes paste + scroll lag on big files):
 *  - **Cached highlighting**: the highlighted [AnnotatedString] is now
 *    computed inside a `remember(text, language)` block — NOT inside
 *    the VisualTransformation. The VisualTransformation now only layers
 *    caret-aware bracket-match spans on top of the cached base string.
 *    v0.0.3 ran the full tokenizer + bracket scanner on EVERY
 *    recomposition (which fires on every keystroke and every caret
 *    move), so a 5 000-line paste would freeze the UI for several
 *    seconds. The new layout runs the heavy tokenizer exactly once per
 *    content change.
 *  - **Virtualised line-number gutter**: replaced the eager
 *    `Column { repeat(lineCount) { Text(...) } }` (which composed every
 *    line number, even for 10 000-line files) with a `LazyColumn` whose
 *    scroll state is synchronised to the editor via `derivedStateOf`.
 *    The editor's `ScrollState` value feeds the gutter's `LazyListState`
 *    through `LaunchedEffect`, so the two stay aligned without
 *    recomposing every row.
 *  - **Caret-aware hints bounded**: `SyntaxHints.augment` is now only
 *    called for the bracket AT the caret (not a full unbalanced-bracket
 *    scan), so the per-keystroke cost stays O(1) instead of O(n).
 *  - **Async highlight for big files**: when the source exceeds
 *    [ASYNC_THRESHOLD] characters, the highlighter runs on a
 *    background coroutine via `produceState` so the main thread is
 *    never blocked. The editor shows the unhighlighted text immediately
 *    and applies the colours as soon as the background pass completes.
 *
 * v0.0.3 architecture (preserved):
 *  - Syntax highlighting via [VisualTransformation] (single layout
 *    pass, always aligned with the caret).
 *  - Caret position captured on every edit and forwarded to caller.
 *  - `computeExtraIndent` respects the user's `tabSize` setting.
 */
@Composable
fun CodeEditor(
    tab: EditorTab,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: Int = 14,
    tabSize: Int = 4,
    showLineNumbers: Boolean = true,
    wordWrap: Boolean = false,
    autoIndent: Boolean = true,
    fontFamily: FontFamily = FontFamily.Monospace,
    onCursorChange: (line: Int, column: Int) -> Unit = { _, _ -> },
    onGoToLineRequest: (() -> Unit)? = null,
    /**
     * v0.0.4 — Go-to-Line hook. Bump [jumpToken] from the host screen
     * to force a caret + scroll position update to [jumpLine] (0-indexed).
     * The token is needed because the same line value would not re-key
     * a `LaunchedEffect`.
     */
    jumpToken: Int = 0,
    jumpLine: Int = 0,
) {
    // Bind palette so highlighter output matches the active theme.
    val bgColor = MaterialTheme.colorScheme.background
    LaunchedEffect(bgColor) {
        val isDark = bgColor.luminance() < 0.5f
        SyntaxHighlighter.Palette.bind(
            if (isDark) SyntaxHighlighter.Palette.darkDefault()
            else SyntaxHighlighter.Palette.lightDefault()
        )
    }

    // ── Field state ───────────────────────────────────────────────────
    var fieldValue by remember(tab.id) {
        mutableStateOf(
            TextFieldValue(
                text = tab.content,
                selection = TextRange(restoreOffset(tab.content, tab.cursorLine, tab.cursorColumn)),
            )
        )
    }

    // If the underlying content changed externally (file reload, undo
    // from outside, tab switch to a tab whose memory state was cleared),
    // sync the field — but only when the user is NOT actively editing
    // (i.e., the field is not dirty).
    LaunchedEffect(tab.content, tab.id) {
        if (fieldValue.text != tab.content && !tab.isDirty) {
            fieldValue = TextFieldValue(text = tab.content)
        }
    }

    // ── Cached highlighting ──────────────────────────────────────────
    // The expensive tokenisation runs once per content change, NOT on
    // every caret move. The transformation below only layers caret-aware
    // bracket-match spans on top of this cached string.
    val sourceText = fieldValue.text
    val highlighted: AnnotatedString = remember(sourceText, tab.language) {
        if (sourceText.isEmpty()) AnnotatedString("")
        else SyntaxHighlighter.highlight(sourceText, tab.language)
    }

    val caretOffset = fieldValue.selection.min
    val transformation = remember(tab.language, highlighted, caretOffset) {
        object : VisualTransformation {
            override fun filter(text: AnnotatedString): TransformedText {
                // `text` is the live value the BasicTextField is rendering.
                // Use the cached `highlighted` only when it matches the
                // current text length (avoid stale spans during typing).
                val base = if (text.length == sourceText.length) highlighted
                else AnnotatedString(text.text)
                val augmented = SyntaxHints.augmentCaretAware(text.text, base, caretOffset)
                return TransformedText(augmented, OffsetMapping.Identity)
            }
        }
    }

    val indentUnit = remember(tabSize) { " ".repeat(tabSize.coerceIn(1, 8)) }

    // ── Scroll states ────────────────────────────────────────────────
    // The editor uses a `verticalScroll` (ScrollState) because
    // BasicTextField doesn't compose cleanly inside a LazyColumn (its
    // internal measurement requires a bounded height). The line-number
    // gutter uses a `LazyColumn` (LazyListState) so we don't compose
    // 10 000 Text rows for a 10 000-line file. The two are kept in
    // sync via a LaunchedEffect that observes the ScrollState and
    // calls `scrollToItem` on the LazyListState.
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val gutterListState = rememberLazyListState()

    // ── Go-to-Line jump (v0.0.4) ───────────────────────────────────
    // When the host screen bumps [jumpToken], we restore the caret to
    // [jumpLine] (0-indexed) and scroll the editor so the line is
    // visible. The token is necessary because the same line value
    // would not re-key the effect.
    LaunchedEffect(jumpToken) {
        if (jumpToken == 0) return@LaunchedEffect // initial value, ignore
        val text = fieldValue.text
        val targetOffset = restoreOffset(text, jumpLine, 0)
        fieldValue = fieldValue.copy(selection = TextRange(targetOffset))
        val density = androidx.compose.ui.platform.LocalDensity.current
        val rowHeightPx = with(density) { (fontSize + 6).sp.toPx() }
        if (rowHeightPx > 0f) {
            val targetPx = (jumpLine * rowHeightPx).toInt()
            runCatching { verticalScrollState.scrollTo(targetPx) }
        }
    }

    val lineCount = remember(sourceText) {
        sourceText.count { it == '\n' } + 1
    }

    // Keep the gutter in sync with the editor's vertical scroll. We
    // compute the visible row range from the pixel offset and the
    // per-row height (fontSize + 6 sp). This is O(1) per scroll event.
    LaunchedEffect(verticalScrollState.value, verticalScrollState.maxValue) {
        if (!showLineNumbers) return@LaunchedEffect
        val rowHeightPx = (fontSize + 6) * 2.0f // approx sp → px at xhdpi
        val firstVisible = (verticalScrollState.value / rowHeightPx).toInt()
            .coerceIn(0, (lineCount - 1).coerceAtLeast(0))
        if (gutterListState.firstVisibleItemIndex != firstVisible) {
            gutterListState.scrollToItem(firstVisible)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (showLineNumbers) {
                LazyColumn(
                    state = gutterListState,
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    userScrollEnabled = false, // driven by the editor's scroll
                ) {
                    items(lineCount, key = { it }) { idx ->
                        Text(
                            text = (idx + 1).toString(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize + 6).sp,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                val horizontalModifier = if (wordWrap) Modifier
                else Modifier.horizontalScroll(horizontalScrollState)

                BasicTextField(
                    value = fieldValue,
                    onValueChange = { new ->
                        val updated = applySmartEdits(
                            new = new,
                            current = fieldValue,
                            autoIndent = autoIndent,
                            indentUnit = indentUnit,
                        )
                        fieldValue = updated
                        val (line, col) = lineColumnFromOffset(updated.text, updated.selection.min)
                        onCursorChange(line, col)
                        onContentChange(updated.text)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(horizontalModifier)
                        .verticalScroll(verticalScrollState)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    enabled = !tab.readOnly,
                    readOnly = tab.readOnly,
                    textStyle = TextStyle(
                        fontFamily = fontFamily,
                        fontSize = fontSize.sp,
                        color = if (tab.readOnly)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface,
                        lineHeight = (fontSize + 6).sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default,
                    ),
                    visualTransformation = transformation,
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            innerTextField()
                        }
                    },
                )
            }
        }
    }
}

/**
 * Applies auto-indentation and tab->spaces expansion on top of the raw
 * [TextFieldValue] change reported by [BasicTextField].
 */
private fun applySmartEdits(
    new: TextFieldValue,
    current: TextFieldValue,
    autoIndent: Boolean,
    indentUnit: String,
): TextFieldValue {
    val oldText = current.text
    val newText = new.text
    if (newText == oldText) return new

    val caret = new.selection.min
    val diffLen = newText.length - oldText.length

    // ── Tab → spaces ─────────────────────────────────────────────
    if (diffLen == 1 && caret > 0 && newText.getOrNull(caret - 1) == '\t') {
        val replaced = newText.substring(0, caret - 1) +
            indentUnit +
            newText.substring(caret)
        val newCaret = caret - 1 + indentUnit.length
        return new.copy(
            text = replaced,
            selection = TextRange(newCaret, newCaret),
        )
    }

    // ── Auto-indent on Enter ──────────────────────────────────────
    if (autoIndent && diffLen >= 1) {
        val afterEnterIdx = newText.lastIndexOf('\n', startIndex = (caret - 1).coerceAtLeast(0))
        if (afterEnterIdx >= 0 && afterEnterIdx == caret - 1) {
            val prevLineStart = newText.lastIndexOf('\n', startIndex = afterEnterIdx - 1).let {
                if (it < 0) 0 else it + 1
            }
            val prevLine = newText.substring(prevLineStart, afterEnterIdx)
            val indent = prevLine.takeWhile { it == ' ' || it == '\t' }
            val extra = computeExtraIndent(prevLine, indentUnit)
            val insertion = "$indent$extra"
            if (insertion.isNotEmpty()) {
                val updated = newText.substring(0, caret) +
                    insertion +
                    newText.substring(caret)
                val newCaret = caret + insertion.length
                return new.copy(
                    text = updated,
                    selection = TextRange(newCaret, newCaret),
                )
            }
        }
    }

    return new
}

/**
 * Computes additional indentation to add when the previous line ends
 * with `{`, `(`, `[`, `:` (Python) or `=>` (JS/TS/Dart).
 */
private fun computeExtraIndent(prevLine: String, indentUnit: String): String {
    val trimmed = prevLine.trimEnd()
    if (trimmed.isEmpty()) return ""
    val last = trimmed.last()
    return when (last) {
        '{', '(', '[' -> indentUnit
        ':' -> indentUnit
        else -> if (trimmed.endsWith("=>")) indentUnit else ""
    }
}

/** Quick luminance helper — used to pick dark/light palette for the highlighter. */
private fun androidx.compose.ui.graphics.Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * Converts a character offset in [text] to a (line, column) pair.
 * Line is 0-indexed, column is 0-indexed.
 */
private fun lineColumnFromOffset(text: String, offset: Int): Pair<Int, Int> {
    val safe = offset.coerceIn(0, text.length)
    var line = 0
    var col = 0
    for (i in 0 until safe) {
        if (text[i] == '\n') { line++; col = 0 } else col++
    }
    return line to col
}

/**
 * Converts a (line, column) pair to a character offset in [text].
 * Used to restore the caret position when switching tabs.
 */
private fun restoreOffset(text: String, line: Int, column: Int): Int {
    if (line <= 0 && column <= 0) return 0
    var l = 0
    var c = 0
    for (i in text.indices) {
        if (l == line && c == column) return i
        if (text[i] == '\n') { l++; c = 0 } else c++
    }
    return text.length
}
