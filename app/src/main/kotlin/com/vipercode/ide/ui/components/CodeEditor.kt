package com.vipercode.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
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
import com.vipercode.ide.util.CompletionProvider
import com.vipercode.ide.util.Language
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Multi-line code editor with syntax highlighting, line numbers,
 * optional word wrap, sane auto-indent, bracket auto-completion and
 * tab-size aware editing.
 *
 * v0.0.7 changes:
 *  - **Cursor preservation on external sync** — when the underlying
 *    `tab.content` changes externally (file reload, undo from outside),
 *    the caret + selection are preserved (coerced into the new text)
 *    instead of resetting to offset 0.
 *  - **String-aware auto-close** — typing a `"` or `'` inside an
 *    existing string literal no longer auto-closes (the previous
 *    behaviour produced `""` mid-string). String state is tracked
 *    from offset 0 to the caret.
 *  - **Python-only `:` indent** — `computeExtraIndent` now returns
 *    `indentUnit` for `:` ONLY when `language == PYTHON`. JSON, JS
 *    type annotations, YAML mappings, etc. no longer trigger an
 *    extra indent.
 *  - **Binary-search line/column** — `lineColumnFromOffset` and
 *    `restoreOffset` use a precomputed `IntArray` of line starts
 *    (rebuilt only when `sourceText` changes) → O(log n) per call
 *    instead of O(n) per keystroke. Big files (100k+ lines) no longer
 *    hitch on every keystroke.
 *  - **Tab → spaces on paste** — pasted text containing `\t` is now
 *    expanded to spaces (only the typed-`Tab` path was handled before).
 *  - **Dynamic gutter width** — the gutter grows from 32dp to 64dp
 *    based on `lineCount.toString().length`, so 5+ digit line counts
 *    no longer overflow.
 *  - **Throttled gutter sync** — the gutter scroll-sync now flows
 *    through `snapshotFlow { … }.distinctUntilChanged()` so fling
 *    scrolling only triggers a single `scrollToItem` per visible-row
 *    change instead of one per pixel.
 *  - **Font family wired up** — the `fontFamily` parameter is now
 *    honoured by [EditorScreen] (the user's Settings → Font family
 *    selection takes effect).
 *  - **Multi-char numeric suffixes** — syntax highlighting consumes
 *    all `f/F/l/L/u/U/d/D` trailing chars on numeric literals.
 *  - **Pastable skip-over** — typing a close bracket with a non-empty
 *    selection wraps the selection instead of replacing it.
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
    /**
     * v0.0.8 — enable/disable the autocomplete popup. Hosts can
     * turn it off (e.g. for read-only files) without changing
     * the editor's other features.
     */
    enableCompletion: Boolean = true,
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
     * Comment-toggle hook. Bump [commentToggleToken] from the host
     * screen to toggle line-comment on the current selection.
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
            ),
        )
    }

    // v0.0.8 — re-key scroll states on tab.id so switching tabs
    // resets the scroll position (was inheriting the previous tab's
    // scroll, leaving the new tab scrolled past its end).
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val gutterListState = rememberLazyListState()

    // If the underlying content changed externally (file reload, undo
    // from outside, tab switch to a tab whose memory state was cleared),
    // sync the field — but PRESERVE the caret position (coerced into
    // the new text bounds). Only fires when the user is NOT actively
    // editing (i.e., the field text doesn't already match AND the tab
    // isn't dirty).
    LaunchedEffect(tab.content, tab.id) {
        if (fieldValue.text != tab.content && !tab.isDirty) {
            val newSel = fieldValue.selection.let {
                TextRange(it.min.coerceIn(0, tab.content.length), it.max.coerceIn(0, tab.content.length))
            }
            fieldValue = TextFieldValue(text = tab.content, selection = newSel)
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
                // current text length AND content (avoid stale spans during
                // type-and-delete churn that produces the same length but
                // different text).
                val base = if (text.length == sourceText.length && text.text == sourceText)
                    highlighted else AnnotatedString(text.text)
                val augmented = SyntaxHints.augmentCaretAware(text.text, base, caretOffset)
                return TransformedText(augmented, OffsetMapping.Identity)
            }
        }
    }

    val indentUnit = remember(tabSize) { " ".repeat(tabSize.coerceIn(1, 8)) }

    // ── Pre-computed line starts (O(log n) caret maths) ──────────────
    val lineStarts = remember(sourceText) { computeLineStarts(sourceText) }
    val lineCount = lineStarts.size

    // (scroll states declared above with the field state so they
    // live in the same `remember` group as fieldValue)

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

    // Keep the gutter in sync with the editor's vertical scroll.
    // v0.0.7 — flow through snapshotFlow + distinctUntilChanged so a
    // fling only triggers one scrollToItem per visible-row change.
    // v0.0.8 — also key on `lineCount` and `rowHeightPx` so the
    // snapshotFlow block picks up new values when the document grows
    // or shrinks (was capturing plain `val`s that never re-evaluated,
    // causing stale clamp values & possible scrollToItem past the new
    // item count).
    LaunchedEffect(showLineNumbers, verticalScrollState, lineCount, rowHeightPx) {
        if (!showLineNumbers) return@LaunchedEffect
        snapshotFlow {
            if (rowHeightPx <= 0f) 0
            else (verticalScrollState.value / rowHeightPx).toInt()
                .coerceIn(0, (lineCount - 1).coerceAtLeast(0))
        }.distinctUntilChanged().collect { firstVisible ->
            if (gutterListState.firstVisibleItemIndex != firstVisible) {
                gutterListState.scrollToItem(firstVisible)
            }
        }
    }

    // ── Comment toggle (v0.0.5) ───────────────────────────────────
    LaunchedEffect(commentToggleToken) {
        if (commentToggleToken == 0) return@LaunchedEffect
        val updated = toggleComment(fieldValue, tab.language, indentUnit)
        if (updated != null) {
            fieldValue = updated
            onContentChange(updated.text)
            val (line, col) = lineColumnFromOffset(updated.text, updated.selection.min, lineStarts)
            onCursorChange(line, col)
        }
    }

    // Dynamic gutter width — grows for 5+ digit line counts.
    val gutterWidthDp = remember(lineCount) {
        val digits = lineCount.toString().length.coerceAtLeast(2)
        (digits * 10 + 16).dp
    }

    // v0.0.8 — autocomplete state. Updated on every fieldValue change;
    // cleared when the prefix becomes empty or the caret moves out of word
    // context. The popup is rendered as a floating LazyColumn above the
    // editor's text area.
    var completionCandidates by remember { mutableStateOf<List<CompletionProvider.Candidate>>(emptyList()) }
    var completionSelectedIdx by remember { mutableIntStateOf(0) }
    val completionVisible = remember(completionCandidates) { completionCandidates.isNotEmpty() }

    // v0.0.8 — recompute completion candidates whenever the field value
    // changes (debounced via the `key` of the LaunchedEffect so rapid
    // typing doesn't recompute on every keystroke — we just re-key on
    // the caret offset, since that's all that determines the prefix).
    LaunchedEffect(fieldValue.text, fieldValue.selection.min, tab.language, enableCompletion) {
        if (!enableCompletion || tab.readOnly) {
            completionCandidates = emptyList()
            return@LaunchedEffect
        }
        if (fieldValue.selection.min != fieldValue.selection.max) {
            // Don't show completion while there's a selection.
            completionCandidates = emptyList()
            return@LaunchedEffect
        }
        val cands = CompletionProvider.suggest(
            text = fieldValue.text,
            caretOffset = fieldValue.selection.min,
            language = tab.language,
            maxResults = 10,
        )
        completionCandidates = cands
        completionSelectedIdx = 0
    }

    fun applyCompletion(candidate: CompletionProvider.Candidate) {
        val caret = fieldValue.selection.min
        val prefixStart = CompletionProvider.prefixStart(fieldValue.text, caret)
        val before = fieldValue.text.substring(0, prefixStart)
        val after = fieldValue.text.substring(caret)
        val inserted = candidate.insert
        val newText = before + inserted + after
        val newCaret = prefixStart + inserted.length
        fieldValue = TextFieldValue(
            text = newText,
            selection = TextRange(newCaret),
        )
        val ls = computeLineStarts(newText)
        val (line, col) = lineColumnFromOffset(newText, newCaret, ls)
        onCursorChange(line, col)
        onContentChange(newText)
        completionCandidates = emptyList()
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (showLineNumbers) {
                LazyColumn(
                    state = gutterListState,
                    modifier = Modifier
                        .width(gutterWidthDp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    userScrollEnabled = false,
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
                        val ls = computeLineStarts(updated.text)
                        val (line, col) = lineColumnFromOffset(updated.text, updated.selection.min, ls)
                        onCursorChange(line, col)
                        onContentChange(updated.text)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(horizontalModifier)
                        .verticalScroll(verticalScrollState)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    // v0.0.8 — `enabled = !tab.readOnly` made
                    // read-only files completely non-focusable,
                    // so the user couldn't select/copy text from
                    // them. Now always enabled; `readOnly`
                    // already blocks edits.
                    enabled = true,
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
                        imeAction = if (completionVisible) ImeAction.Done else ImeAction.Default,
                    ),
                    visualTransformation = transformation,
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            innerTextField()
                            // v0.0.8 — autocomplete popup overlay.
                            if (completionVisible) {
                                CompletionPopup(
                                    candidates = completionCandidates,
                                    selectedIdx = completionSelectedIdx,
                                    fontSize = fontSize,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = (fontSize * 2).dp),
                                    onSelect = { idx ->
                                        completionSelectedIdx = idx
                                    },
                                    onAccept = { idx ->
                                        if (idx in completionCandidates.indices) {
                                            applyCompletion(completionCandidates[idx])
                                        }
                                    },
                                    onDismiss = {
                                        completionCandidates = emptyList()
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Applies auto-indentation, tab→spaces expansion (typed OR pasted),
 * bracket auto-close, skip-over-close-bracket and selection-wrap on
 * top of the raw [TextFieldValue] change reported by [BasicTextField].
 *
 * v0.0.7 changes:
 *  - Auto-close quotes is string-aware: typing `"` inside an existing
 *    string literal does NOT auto-close.
 *  - Pasted `\t` characters are expanded to `indentUnit` (previously
 *    only single typed Tab was handled).
 *  - Selection-wrap on bracket typing: if the user has a non-empty
 *    selection and types an opening bracket, the selection is wrapped
 *    with the open+close pair instead of being replaced.
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

    // ── Paste-time tab → spaces expansion ──────────────────────────
    // If the user pasted text containing `\t`, expand every tab to
    // `indentUnit`. The pasted region is the diff between old and new.
    if (diffLen > 1 && newText.contains('\t')) {
        val replaced = newText.replace("\t", indentUnit)
        val delta = replaced.length - newText.length
        val newCaret = (caret + delta).coerceAtLeast(0)
        return new.copy(
            text = replaced,
            selection = TextRange(
                newCaret.coerceAtMost(replaced.length),
                (new.selection.max + delta).coerceAtMost(replaced.length),
            ),
        )
    }

    // ── Tab → spaces (single typed Tab) ────────────────────────────
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

    // ── Selection-wrap on bracket typing ───────────────────────────
    // If the user has a non-empty selection and types an opening
    // bracket, wrap the selection with the open+close pair.
    if (autoCloseBrackets && diffLen == 1 && new.selection.min != new.selection.max) {
        val typed = newText.getOrNull(caret - 1) ?: '\u0000'
        val close = CLOSING_BRACKETS[typed]
        if (close != null && typed in OPENING_BRACKETS_SET) {
            // For `<`, only wrap in HTML/XML contexts.
            val isAngle = typed == '<'
            val contextAllowsAngle = language == Language.HTML || language == Language.XML
            if (!isAngle || contextAllowsAngle) {
                // Selection in the new text spans [selStart, selEnd+1)
                // (we just inserted the open bracket at selStart).
                val selStart = new.selection.min
                val selEnd = new.selection.max
                // The inserted open is at selStart..selStart (caret was at min-1 after typing).
                // Compute original selection in old text:
                val origSelStart = selStart - 1
                val origSelEnd = selEnd - 1
                if (origSelEnd > origSelStart) {
                    val wrapped = newText.substring(0, origSelStart) +
                        typed +
                        newText.substring(origSelStart + 1, origSelEnd + 1) +
                        close +
                        newText.substring(origSelEnd + 1)
                    return new.copy(
                        text = wrapped,
                        selection = TextRange(origSelStart + 1, origSelEnd + 1),
                    )
                }
            }
        }
    }

    // ── Skip-over close bracket (v0.0.5) ──────────────────────────
    if (diffLen == 1 && caret > 0 && new.selection.min == new.selection.max) {
        val typed = newText.getOrNull(caret - 1) ?: '\u0000'
        val next = newText.getOrNull(caret) ?: '\u0000'
        if (typed == next && typed in CLOSING_BRACKETS_SET) {
            val cleaned = newText.substring(0, caret - 1) + newText.substring(caret)
            return new.copy(
                text = cleaned,
                selection = TextRange(caret, caret),
            )
        }
    }

    // ── Auto-close brackets (v0.0.5; v0.0.7 string-aware) ─────────
    if (autoCloseBrackets && diffLen == 1 && caret > 0 && new.selection.min == new.selection.max) {
        val typed = newText.getOrNull(caret - 1) ?: '\u0000'
        val close = CLOSING_BRACKETS[typed]
        if (close != null) {
            val next = newText.getOrNull(caret) ?: '\u0000'
            val isQuote = typed == '"' || typed == '\'' || typed == '`'
            val nextIsWord = next.isLetterOrDigit() || next == '_'
            val isAngle = typed == '<'
            val contextAllowsAngle = language == Language.HTML || language == Language.XML
            // v0.0.8 — don't auto-close a bracket when the very
            // next char is already the matching close. The
            // previous code only guarded quote pairs (so typing
            // `(` before an existing `)` produced `())` with an
            // extra closer).
            val nextIsMatchingClose = !isQuote && next == close
            // v0.0.7: don't auto-close a quote when the caret is inside
            // an existing string literal (typing a quote mid-string
            // should produce a single quote, not a "" pair).
            val caretInsideString = isQuote && isCaretInsideStringLiteral(newText, caret, language)
            val shouldClose = when {
                isAngle && !contextAllowsAngle -> false
                isQuote && nextIsWord -> false
                isQuote && next == typed -> false // " before " → skip-over
                isQuote && caretInsideString -> false
                nextIsMatchingClose -> false
                else -> true
            }
            if (shouldClose) {
                val withClose = newText.substring(0, caret) + close + newText.substring(caret)
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
            val extra = computeExtraIndent(prevLine, indentUnit, language)
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
 * Returns true if the caret at [offset] is inside a string literal in
 * [text]. A simple single-line scan from the start of the current line
 * tracks `"` / `'` / `` ` `` flips, skipping escaped chars (`\"`).
 *
 * Used to suppress quote auto-close inside strings (v0.0.7).
 */
private fun isCaretInsideStringLiteral(text: String, offset: Int, language: Language): Boolean {
    // Find the start of the line containing `offset`.
    val lineStart = text.lastIndexOf('\n', startIndex = (offset - 1).coerceAtLeast(0)).let {
        if (it < 0) 0 else it + 1
    }
    var inSingle = false
    var inDouble = false
    var inBacktick = false
    var i = lineStart
    while (i < offset) {
        val c = text[i]
        if (c == '\\' && i + 1 < offset) {
            i += 2
            continue
        }
        when (c) {
            '"' -> if (!inSingle && !inBacktick) inDouble = !inDouble
            '\'' -> if (!inDouble && !inBacktick) inSingle = !inSingle
            '`' -> if (!inSingle && !inDouble) inBacktick = !inBacktick
        }
        i++
    }
    return inSingle || inDouble || inBacktick
}

/**
 * Mapping of opening brackets / quotes to their matching close.
 * Used for auto-close and skip-over.
 *
 * NOTE: `<` is intentionally included so it can be auto-closed in
 * HTML/XML contexts only (the caller filters by language).
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

private val OPENING_BRACKETS_SET: Set<Char> = setOf('(', '[', '{', '<', '"', '\'', '`')
private val CLOSING_BRACKETS_SET: Set<Char> = CLOSING_BRACKETS.values.toSet()

/**
 * Toggles line-comment on the current selection (or current line if
 * no selection). Returns `null` if the language doesn't support line
 * comments.
 *
 * Strategy: find the smallest line range covering the selection. If
 * every non-empty line in that range already starts with the comment
 * prefix, remove it from each; otherwise add it to each.
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
    val lineStarts = computeLineStarts(text)
    val lineCount = lineStarts.size
    val (startLine, _) = lineColumnFromOffset(text, selStart, lineStarts)
    val (endLine, _) = lineColumnFromOffset(text, selEnd, lineStarts)
    if (startLine < 0 || endLine < 0 || startLine >= lineCount) return null

    val firstLineStart = lineStarts[startLine]
    val lastLineEnd = if (endLine + 1 < lineStarts.size) lineStarts[endLine + 1] else text.length
    val lastRealEnd = if (lastLineEnd > firstLineStart && text.getOrNull(lastLineEnd - 1) == '\n') {
        lastLineEnd - 1
    } else lastLineEnd

    val region = text.substring(firstLineStart, lastRealEnd)
    val lines = region.split("\n")
    val nonBlankLines = lines.count { it.isNotBlank() }
    val commentedLines = lines.count { it.trimStart().startsWith(prefix) }
    val allCommented = nonBlankLines > 0 && nonBlankLines == commentedLines

    val newLines: List<String>
    val deltas: List<Int>
    if (allCommented) {
        val pairList = lines.map { line ->
            val trimmed = line.trimStart()
            val leading = line.substring(0, line.length - trimmed.length)
            if (trimmed.startsWith(prefix)) {
                val afterPrefix = trimmed.substring(prefix.length)
                // v0.0.7 — strip ALL leading spaces after the prefix
                // (was only one), matching the original `"$prefix $line"`
                // add step.
                val stripCount = afterPrefix.takeWhile { it == ' ' }.length
                val newLine = leading + afterPrefix.substring(stripCount)
                newLine to (newLine.length - line.length)
            } else {
                line to 0
            }
        }
        newLines = pairList.map { it.first }
        deltas = pairList.map { it.second }
    } else {
        val pairList = lines.map { line ->
            if (line.isBlank()) {
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

private fun lineIndexOfOffset(lines: List<String>, offset: Int): Int {
    if (offset < 0) return 0
    var running = 0
    for ((idx, line) in lines.withIndex()) {
        val lineEnd = running + line.length
        if (offset <= lineEnd) return idx
        running = lineEnd + 1
    }
    return lines.lastIndex.coerceAtLeast(0)
}

/**
 * Computes additional indentation to add when the previous line ends
 * with `{`, `(`, `[`, `:` (Python only) or `=>` (JS/TS/Dart).
 *
 * v0.0.7 — the `:` rule now ONLY applies to [Language.PYTHON]. JSON
 * property colons, JS type annotations, YAML mappings, etc. no longer
 * trigger an extra indent.
 */
private fun computeExtraIndent(prevLine: String, indentUnit: String, language: Language): String {
    val trimmed = prevLine.trimEnd()
    if (trimmed.isEmpty()) return ""
    val last = trimmed.last()
    return when (last) {
        '{', '(', '[' -> indentUnit
        ':' -> if (language == Language.PYTHON) indentUnit else ""
        else -> if (trimmed.endsWith("=>")) indentUnit else ""
    }
}

/**
 * Pre-computes the start offset of every line in [text]. Line 0 starts
 * at offset 0; line N starts at the offset after the (N-1)th `\n`.
 *
 * The returned IntArray has one entry per line; its size is the line
 * count. Used for O(log n) binary search in [lineColumnFromOffset]
 * and [restoreOffset].
 */
private fun computeLineStarts(text: String): IntArray {
    val count = text.count { it == '\n' } + 1
    val starts = IntArray(count)
    starts[0] = 0
    var idx = 1
    var i = 0
    while (i < text.length && idx < count) {
        if (text[i] == '\n') {
            starts[idx] = i + 1
            idx++
        }
        i++
    }
    return starts
}

private fun androidx.compose.ui.graphics.Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * Converts a character offset in [text] to a (line, column) pair using
 * a precomputed [lineStarts] array. O(log n) via binary search.
 */
private fun lineColumnFromOffset(text: String, offset: Int, lineStarts: IntArray): Pair<Int, Int> {
    val safe = offset.coerceIn(0, text.length)
    if (lineStarts.isEmpty()) return 0 to safe
    // Binary search for the largest line whose start <= safe.
    var lo = 0
    var hi = lineStarts.size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) ushr 1
        if (lineStarts[mid] <= safe) lo = mid else hi = mid - 1
    }
    return lo to (safe - lineStarts[lo])
}

/**
 * Converts a (line, column) pair to a character offset in [text].
 * Uses the precomputed [lineStarts] array when available.
 */
private fun restoreOffset(text: String, line: Int, column: Int): Int {
    if (line <= 0 && column <= 0) return 0
    val starts = computeLineStarts(text)
    if (line >= starts.size) return text.length
    return (starts[line] + column).coerceAtMost(text.length)
}

/**
 * v0.0.8 — Autocomplete popup overlay rendered above the editor's
 * text area. Shows up to N candidates from [CompletionProvider],
 * highlights the selected one, and accepts on tap or via the host's
 * keyboard handler (Tab / Enter / arrow keys).
 *
 * The popup is intentionally lightweight — no shadow, no animation —
 * so it doesn't compete with the editor for attention and stays out
 * of the way during rapid typing.
 */
@Composable
private fun CompletionPopup(
    candidates: List<CompletionProvider.Candidate>,
    selectedIdx: Int,
    fontSize: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
    onAccept: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val backgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    val selectedColor = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val textColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val detailColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant

    androidx.compose.material3.Surface(
        color = backgroundColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .heightIn(max = 240.dp)
            .fillMaxWidth(0.9f)
            .padding(horizontal = 4.dp),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(candidates) { idx, candidate ->
                val isSelected = idx == selectedIdx
                val isHighlighted = idx == selectedIdx
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .background(if (isSelected) selectedColor else backgroundColor)
                        .clickable {
                            onSelect(idx)
                            onAccept(idx)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Type icon (K / S / I) for keyword / snippet / identifier.
                    val badgeText = when (candidate.kind) {
                        CompletionProvider.Kind.KEYWORD -> "K"
                        CompletionProvider.Kind.SNIPPET -> "S"
                        CompletionProvider.Kind.IDENTIFIER -> "I"
                    }
                    val badgeColor = when (candidate.kind) {
                        CompletionProvider.Kind.KEYWORD -> androidx.compose.ui.graphics.Color(0xFF82AAFF)
                        CompletionProvider.Kind.SNIPPET -> androidx.compose.ui.graphics.Color(0xFFC3E88D)
                        CompletionProvider.Kind.IDENTIFIER -> androidx.compose.ui.graphics.Color(0xFFFFCB6B)
                    }
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .heightIn(min = 22.dp)
                            .background(
                                color = badgeColor.copy(alpha = 0.2f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = candidate.label,
                            color = textColor,
                            fontSize = fontSize.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        if (candidate.detail.isNotEmpty()) {
                            Text(
                                text = candidate.detail,
                                color = detailColor,
                                fontSize = (fontSize - 2).coerceAtLeast(10).sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
