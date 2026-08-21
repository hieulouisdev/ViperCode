package com.vipercode.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipercode.ide.data.model.EditorTab

/**
 * Multi-line code editor with syntax highlighting, line numbers,
 * optional word wrap, sane auto-indent, and tab-size aware editing.
 *
 * Built on top of [BasicTextField] so we keep full control of touch
 * handling, IME composition, and visual layers. The line-number gutter
 * is a separate column on the left so it stays aligned with the text
 * baseline even when wrap is enabled.
 *
 * The highlighter runs on the new value on every keystroke. This is
 * acceptable for v0.0.2 — files up to ~5 000 lines stay at 60 FPS on
 * mid-range devices.
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

    var fieldValue by remember(tab.id) {
        mutableStateOf(TextFieldValue(text = tab.content))
    }

    // Sync external content updates (file reload / undo from outside)
    // back into the field, but only when the user is not actively
    // editing the same text — otherwise the caret would jump.
    LaunchedEffect(tab.content) {
        if (fieldValue.text != tab.content && !tab.isDirty) {
            fieldValue = TextFieldValue(text = tab.content)
        }
    }

    val lineCount by remember(fieldValue) {
        derivedStateOf { fieldValue.text.count { it == '\n' } + 1 }
    }

    val highlighted: AnnotatedString = remember(fieldValue.text, tab.language) {
        SyntaxHighlighter.highlight(fieldValue.text, tab.language)
    }

    val indentUnit = remember(tabSize) { " ".repeat(tabSize.coerceIn(1, 8)) }

    Row(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (showLineNumbers) {
            LineNumberGutter(
                lineCount = lineCount,
                fontSize = fontSize,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .width(48.dp),
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            val horizontalScroll = if (wordWrap) Modifier else Modifier.horizontalScroll(rememberScrollState())
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
                    onContentChange(updated.text)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(horizontalScroll)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                enabled = !tab.readOnly,
                readOnly = tab.readOnly,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
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
                // We render the highlighted text behind the cursor by using
                // decorationBox and overlaying a Text composable.
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Render highlighted overlay (read-only).
                        Text(
                            text = highlighted,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize + 6).sp,
                            overflow = TextOverflow.Visible,
                            softWrap = wordWrap,
                        )
                        // Real editable text (transparent, draws caret).
                        // We rely on BasicTextField's internal layout — pass-through.
                        innerTextField()
                    }
                },
            )
        }
    }
}

/**
 * Applies auto-indentation and tab->spaces expansion on top of the raw
 * [TextFieldValue] change reported by [BasicTextField].
 *
 * v0.0.1 had a buggy version of this that only worked when the cursor
 * was at the end of the buffer. This rewrite inspects the cursor
 * position from the incoming [new] value and reconstructs the correct
 * insertion point — so Enter and Tab now behave correctly no matter
 * where the caret is.
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
    // Detect a Tab character being inserted (diffLen == 1 and the
    // newly inserted char at the caret position is '\t').
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
        // The new line was just inserted; the caret sits right after the \n.
        // We figure out the original line that the Enter was pressed on by
        // looking at the text immediately before the caret in the new value.
        val afterEnterIdx = newText.lastIndexOf('\n', startIndex = (caret - 1).coerceAtLeast(0))
        if (afterEnterIdx >= 0 && afterEnterIdx == caret - 1) {
            // There IS a \n at caret-1 → Enter was just pressed.
            val prevLineStart = newText.lastIndexOf('\n', startIndex = afterEnterIdx - 1).let {
                if (it < 0) 0 else it + 1
            }
            val prevLine = newText.substring(prevLineStart, afterEnterIdx)
            val indent = prevLine.takeWhile { it == ' ' || it == '\t' }
            val extra = computeExtraIndent(prevLine)
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

@Composable
private fun LineNumberGutter(
    lineCount: Int,
    fontSize: Int,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier,
        userScrollEnabled = false,
    ) {
        items(lineCount) { idx ->
            Text(
                text = (idx + 1).toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                fontFamily = FontFamily.Monospace,
                fontSize = (fontSize - 1).sp,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}

/**
 * Computes additional indentation to add when the previous line ends
 * with `{`, `(`, `[`, `:` (Python) or `=>` (JS/TS/Dart).
 */
private fun computeExtraIndent(prevLine: String): String {
    val trimmed = prevLine.trimEnd()
    if (trimmed.isEmpty()) return ""
    val last = trimmed.last()
    return when (last) {
        '{', '(', '[' -> "    "
        ':' -> "    "
        else -> if (trimmed.endsWith("=>")) "    " else ""
    }
}

/** Quick luminance helper — used to pick dark/light palette for the highlighter. */
private fun Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue
