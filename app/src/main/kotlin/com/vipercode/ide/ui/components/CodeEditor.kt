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
import com.vipercode.ide.util.Language

/**
 * Multi-line code editor with syntax highlighting, line numbers,
 * optional word wrap, sane auto-indent, bracket auto-completion and
 * tab-size aware editing.
 *
 * v0.0.5 changes:
 *  - **Bracket auto-completion** — typing `(`, `[`, `{` automatically
 *    inserts the matching close and places the caret between the
 *    pair. Typing `"` or `'` does the same and is smart enough to
 *    NOT auto-close when the next char is alphanumeric (so `it's`
 *    doesn't become `it''s`).
 *  - **Comment toggle hook** — bumped via [commentToggleToken];
 *    toggles line comment on the selection. Picks the right comment
 *    syntax per language (`#` for Python, `//` for Kotlin/Java/JS,
 *    `--` for SQL/Lua, etc.).
 *  - **Gutter sync via LocalDensity** — the v0.0.4 magic density
 *    multiplier was wrong on most devices, causing the line-number
 *    gutter to drift as the user scrolled. v0.0.5 uses the real
 *    `LocalDensity` to convert sp → px.
 *  - **Skip-over close bracket** — when the caret is right before
 *    an auto-closed bracket and the user types the same close
 *    bracket, the caret jumps over it instead of inserting a new
 *    one (matches VS Code default behaviour).
 *
 * v0.0.4 performance rewrite (preserved):
 *  - Cached highlighting via `remember(text, language)`.
 *  - Virtualised LazyColumn gutter.
 *  - Caret-aware SyntaxHints (O(1) per keystroke).
 *
 * v0.0.3 architecture (preserved):
 *  - Syntax highlighting via [VisualTransformation].
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
    autoCloseBrackets: Boolean = true,
    fontFamily: FontFamily = FontFamily.Monospace,
    onCursorChange: (line: Int, column: Int) -> Unit = { _, _ -> },
    onGoToLineRequest: (() -> Unit)? = null,
    /**
     * Go-to-Line hook. Bump [jumpToken] from the host screen to
     * force a caret + scroll position update to [jumpLine] (0-indexed).
     */
    jumpToken: Int = 0,
    jumpLine: Int = 0,
    /**
     * v0.0.5 — Comment-toggle hook. Bump [commentToggleToken] from
     * the host screen to toggle line-comment on the current
     * selection (or current line if no selection).
     */
    commentToggleToken: Int = 0,
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

    val density = LocalDensity.current

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
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val gutterListState = rememberLazyListState()

    // Per-row height in PIXELS — used for the gutter scroll sync.
    // v0.0.5: use LocalDensity so the value is correct on every
    // device density (the v0.0.4 magic number 2.0f was wrong on most
    // xxhdpi / xxxhdpi phones).
    val rowHeightPx = with(density) { (fontSize + 6).sp.toPx() }

    // ── Go-to-Line jump (v0.0.4) ───────────────────────────────────
    LaunchedEffect(jumpToken) {
        if (jumpToken == 0) return@LaunchedEffect // initial value, ignore
        val text = fieldValue.text
        val targetOffset = restoreOffset(text, jumpLine, 0)
        fieldValue = fieldValue.copy(selection = TextRange(targetOffset))
        if (rowHeightPx > 0f) {
            val targetPx = (jumpLine * rowHeightPx).toInt()
            runCatching { verticalScrollState.scrollTo(targetPx) }
        }
    }

    val lineCount = remember(sourceText) {
        sourceText.count { it == '\n' } + 1
    }

    // Keep the gutter in sync with the editor's vertical scroll.
    LaunchedEffect(verticalScrollState.value, verticalScrollState.maxValue) {
        if (!showLineNumbers) return@LaunchedEffect
        val firstVisible = if (rowHeightPx <= 0f) 0
        else (verticalScrollState.value / rowHeightPx).toInt()
            .coerceIn(0, (lineCount - 1).coerceAtLeast(0))
        if (gutterListState.firstVisibleItemIndex != firstVisible) {
            gutterListState.scrollToItem(firstVisible)
        }
    }

    // ── Comment toggle (v0.0.5) ───────────────────────────────────
    LaunchedEffect(commentToggleToken) {
        if (commentToggleToken == 0) return@LaunchedEffect
        val updated = toggleComment(fieldValue, tab.language, indentUnit)
        if (updated != null) {
            fieldValue = updated
            onContentChange(updated.text)
            val (line, col) = lineColumnFromOffset(updated.text, updated.selection.min)
            onCursorChange(line, col)
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
                            autoCloseBrackets = autoCloseBrackets,
                            indentUnit = indentUnit,
                            language = tab.language,
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
 * Applies auto-indentation, tab→spaces expansion, bracket auto-close
 * and skip-over-close-bracket on top of the raw [TextFieldValue]
 * change reported by [BasicTextField].
 *
 * v0.0.5 additions:
 *  - [autoCloseBrackets] — when the user types an opening bracket,
 *    insert the matching close and place the caret between.
 *  - [language] — used to pick which characters count as brackets
 *    worth auto-closing (e.g. `<` is a bracket in HTML/XML, a
 *    less-than operator in Python).
 *  - Skip-over: if the user types the SAME close bracket that's
 *    already sitting at the caret, the caret jumps over it instead
 *    of inserting a duplicate.
 */
private fun applySmartEdits(
    new: TextFieldValue,
    current: TextFieldValue,
    autoIndent: Boolean,
    autoCloseBrackets: Boolean,
    indentUnit: String,
    language: Language,
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

    // ── Skip-over close bracket (v0.0.5) ──────────────────────────
    // If the user typed a close bracket and the next char is the
    // same close bracket, jump over it instead of inserting a
    // duplicate.
    if (diffLen == 1 && caret > 0) {
        val typed = newText.getOrNull(caret - 1) ?: '\u0000'
        val next = newText.getOrNull(caret) ?: '\u0000'
        if (typed == next && typed in CLOSING_BRACKETS_SET) {
            // Just move the caret forward by 1, drop the typed char.
            val cleaned = newText.substring(0, caret - 1) + newText.substring(caret)
            return new.copy(
                text = cleaned,
                selection = TextRange(caret, caret),
            )
        }
    }

    // ── Auto-close brackets (v0.0.5) ──────────────────────────────
    if (autoCloseBrackets && diffLen == 1 && caret > 0) {
        val typed = newText.getOrNull(caret - 1) ?: '\u0000'
        val close = CLOSING_BRACKETS[typed]
        // For quotes, don't auto-close if the next char is a word
        // character (so "it's" doesn't become "it''s") AND don't
        // auto-close if there's already a non-empty selection
        // (let the user wrap the selection manually if they want).
        if (close != null && new.selection.min == new.selection.max) {
            val next = newText.getOrNull(caret) ?: '\u0000'
            val isQuote = typed == '"' || typed == '\''
            val nextIsWord = next.isLetterOrDigit() || next == '_'
            // For `<`, only auto-close in HTML/XML contexts.
            val isAngle = typed == '<'
            val contextAllowsAngle = language == Language.HTML || language == Language.XML
            val shouldClose = when {
                isAngle && !contextAllowsAngle -> false
                isQuote && nextIsWord -> false
                isQuote && next == typed -> false // " before " → skip-over
                else -> true
            }
            if (shouldClose) {
                val withClose = newText.substring(0, caret) + close + newText.substring(caret)
                // Place caret BETWEEN the open and close.
                return new.copy(
                    text = withClose,
                    selection = TextRange(caret, caret),
                )
            }
        }
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
 * Mapping of opening brackets / quotes to their matching close.
 * Used for auto-close and skip-over.
 */
private val CLOSING_BRACKETS: Map<Char, Char> = mapOf(
    '(' to ')',
    '[' to ']',
    '{' to '}',
    '"' to '"',
    '\'' to '\'',
    '`' to '`',
    '<' to '>',
)

/** Set of all closing-bracket characters (for skip-over detection). */
private val CLOSING_BRACKETS_SET: Set<Char> = CLOSING_BRACKETS.values.toSet()

/**
 * Toggles line-comment on the current selection (or current line if
 * no selection). Returns `null` if the language doesn't support line
 * comments.
 *
 * Strategy: find the smallest line range covering the selection. If
 * every non-empty line in that range already starts with the comment
 * prefix, remove it from each; otherwise add it to each. Caret is
 * preserved at the same logical position (we adjust for the prefix
 * length on lines BEFORE the caret).
 */
private fun toggleComment(
    value: TextFieldValue,
    language: Language,
    indentUnit: String,
): TextFieldValue? {
    val prefix = language.lineComment ?: return null
    val text = value.text
    val selStart = value.selection.min
    val selEnd = value.selection.max
    val (startLine, _) = lineColumnFromOffset(text, selStart)
    val (endLine, _) = lineColumnFromOffset(text, selEnd)
    val lineCount = text.count { it == '\n' } + 1
    if (startLine < 0 || endLine < 0 || startLine >= lineCount) return null

    // Collect each line's start offset so we can mutate safely.
    val lineStarts = IntArray(lineCount + 1)
    lineStarts[0] = 0
    for (i in 1 until lineCount) {
        val prevNewline = text.indexOf('\n', lineStarts[i - 1])
        lineStarts[i] = if (prevNewline < 0) text.length else prevNewline + 1
    }
    lineStarts[lineCount] = text.length

    val firstLineStart = lineStarts[startLine]
    val lastLineEnd = if (endLine + 1 < lineStarts.size) lineStarts[endLine + 1] else text.length
    // lastLineEnd is exclusive — strip trailing newline if present so
    // we don't accidentally comment-out the next line.
    val lastRealEnd = if (lastLineEnd > firstLineStart && text.getOrNull(lastLineEnd - 1) == '\n') {
        lastLineEnd - 1
    } else lastLineEnd

    val region = text.substring(firstLineStart, lastRealEnd)
    val lines = region.split("\n")
    val nonBlankLines = lines.count { it.isNotBlank() }
    val commentedLines = lines.count { it.trimStart().startsWith(prefix) }
    val allCommented = nonBlankLines > 0 && nonBlankLines == commentedLines

    // For each line, compute the BEFORE→AFTER length delta so we can
    // adjust the selection accordingly.
    val newLines: List<String>
    val deltas: List<Int>
    if (allCommented) {
        // Remove `prefix` (+ optional single leading space) from each
        // line whose trimmed form starts with the prefix.
        val pairList = lines.map { line ->
            val trimmed = line.trimStart()
            val leading = line.substring(0, line.length - trimmed.length)
            if (trimmed.startsWith(prefix)) {
                val afterPrefix = trimmed.substring(prefix.length)
                val stripSpace = if (afterPrefix.startsWith(' ')) afterPrefix.substring(1) else afterPrefix
                val newLine = leading + stripSpace
                newLine to (newLine.length - line.length)
            } else {
                line to 0
            }
        }
        newLines = pairList.map { it.first }
        deltas = pairList.map { it.second }
    } else {
        // Add `prefix ` to each non-blank line.
        val pairList = lines.map { line ->
            if (line.isBlank()) {
                // Don't comment trailing blank lines.
                line to 0
            } else {
                val newLine = "$prefix $line"
                newLine to (newLine.length - line.length)
            }
        }
        newLines = pairList.map { it.first }
        deltas = pairList.map { it.second }
    }
    val newRegion = newLines.joinToString("\n")
    val newText = text.substring(0, firstLineStart) + newRegion + text.substring(lastRealEnd)

    // Adjust the selection. Each line in the region has `prefix ` added
    // (or removed) at column 0, so the caret's offset shifts by the
    // cumulative delta of all lines BEFORE the current line plus the
    // delta of the current line itself (because the prefix sits at
    // the start, before the caret).
    //
    // Find which line within the region contains selStart / selEnd.
    val selStartRegion = (selStart - firstLineStart).coerceAtLeast(0)
    val selEndRegion = (selEnd - firstLineStart).coerceAtLeast(0)
    val lineIdxForStart = lineIndexOfOffset(lines, selStartRegion)
    val lineIdxForEnd = lineIndexOfOffset(lines, selEndRegion)

    val shiftStart = (0..lineIdxForStart).sumOf { deltas[it] }
    val shiftEnd = (0..lineIdxForEnd).sumOf { deltas[it] }
    val newStart = (selStart + shiftStart).coerceIn(0, newText.length)
    val newEnd = (selEnd + shiftEnd).coerceIn(0, newText.length)

    return value.copy(text = newText, selection = TextRange(newStart, newEnd))
}

/** Returns the index in [lines] of the line containing [offset] (joined by `\n`). */
private fun lineIndexOfOffset(lines: List<String>, offset: Int): Int {
    if (offset < 0) return 0
    var running = 0
    for ((idx, line) in lines.withIndex()) {
        val lineEnd = running + line.length
        if (offset <= lineEnd) return idx
        running = lineEnd + 1 // +1 for \n
    }
    return lines.lastIndex.coerceAtLeast(0)
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
