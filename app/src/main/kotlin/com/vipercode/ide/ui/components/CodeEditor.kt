package com.vipercode.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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
 * v0.0.3 architecture changes (fixes the v0.0.2 visual show-stopper):
 *  - Syntax highlighting is now applied via [VisualTransformation]
 *    instead of an overlay [Text] composable. The overlay approach
 *    had its own scroll state that was never synced with the
 *    BasicTextField's internal scroll, so the colour spans drifted
 *    out of alignment within seconds of typing. A VisualTransformation
 *    is applied to the BasicTextField's own text rendering, so the
 *    colours stay pixel-perfectly aligned with the caret at all times.
 *  - The line-number gutter shares the SAME [rememberScrollState] as
 *    the editor by wrapping both in a single `verticalScroll` parent
 *    Row. v0.0.2 used a separate `LazyColumn` with its own list
 *    state for the gutter, which never received scroll updates from
 *    the editor — so line numbers stayed at the top while text
 *    scrolled past, becoming misaligned.
 *  - Caret position (line + column) is captured on every edit and
 *    forwarded to the caller via [onCursorChange], so it can be
 *    persisted on the [EditorTab] and restored when the user
 *    switches back to this tab. v0.0.2 reset the caret to offset 0
 *    on every tab switch.
 *  - `computeExtraIndent` now respects the user's `tabSize` setting
 *    (was hardcoded to 4 spaces).
 *  - `fontFamily` is wired through from user settings.
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
    // `remember(tab.id)` keeps the field state alive across recompositions
    // for the SAME tab; the field state survives content updates.
    // Caret position is restored from the EditorTab's stored cursor line
    // + column on first creation per tab.
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

    // ── VisualTransformation: highlighting + syntax hints ────────────
    // Wrapped in a VisualTransformation so the colours are applied to the
    // BasicTextField's own text rendering (single layout pass = always
    // aligned with the caret). The transformation does NOT modify the
    // underlying text — the user's edits go through unchanged.
    //
    // v0.0.3: the transformation is also keyed on the caret offset so
    // that bracket matching + unbalanced-bracket hints refresh as the
    // caret moves.
    val caretOffset = fieldValue.selection.min
    val transformation = remember(tab.language, caretOffset) {
        VisualTransformation { text ->
            val highlighted = SyntaxHighlighter.highlight(text.text, tab.language)
            val augmented = SyntaxHints.augment(text.text, highlighted, caretOffset)
            androidx.compose.ui.text.input.TransformedText(
                augmented,
                androidx.compose.ui.text.input.OffsetMapping.Identity,
            )
        }
    }

    val indentUnit = remember(tabSize) { " ".repeat(tabSize.coerceIn(1, 8)) }

    // ── Shared vertical scroll state ──────────────────────────────────
    // Both the line-number gutter and the editor share the same
    // ScrollState — when one scrolls, the other follows. v0.0.2 had a
    // separate scroll state per widget, so the gutter drifted.
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (showLineNumbers) {
                Column(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .verticalScroll(verticalScrollState)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                ) {
                    // The line number's lineHeight MUST match the editor's
                    // textStyle.lineHeight exactly so the gutter stays
                    // pixel-aligned with the editor when both share the
                    // same vertical scroll state. The gutter uses the
                    // same fontSize+lineHeight as the editor (no per-row
                    // vertical padding, otherwise 100 rows drift by
                    // 100 * 2 dp).
                    val lineCount = fieldValue.text.count { it == '\n' } + 1
                    repeat(lineCount) { idx ->
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
                        // Capture caret position back to the tab so it
                        // survives tab switches and app kills.
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
                    // decorationBox just delegates to innerTextField() — the
                    // overlay Text from v0.0.2 is gone (highlighting now
                    // lives in the visualTransformation).
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
 *
 * v0.0.3: `computeExtraIndent` is now parameterised by `indentUnit`
 * (was hardcoded to 4 spaces), so the user's `tabSize` setting is
 * respected for both Tab key and auto-indent after `{`/`(`/`[`.
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
 *
 * v0.0.3: now takes [indentUnit] so the added indent matches the
 * user's `tabSize` setting instead of always being 4 spaces.
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
