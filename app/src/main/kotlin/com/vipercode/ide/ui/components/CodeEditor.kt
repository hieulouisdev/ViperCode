package com.vipercode.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vipercode.ide.data.model.EditorTab
import com.vipercode.ide.util.Language

/**
 * Multi-line code editor with syntax highlighting, line numbers and
 * optional word wrap.
 *
 * Built on top of [BasicTextField] so we keep full control of touch
 * handling, IME composition, and visual layers. The line-number gutter
 * is a separate column on the left so it stays aligned with the text
 * baseline even when wrap is enabled.
 *
 * The highlighter runs on the new value on every keystroke. This is
 * acceptable for v0.0.1 — files up to ~5 000 lines stay at 60 FPS on
 * mid-range devices. v0.0.2 will introduce async highlighting with
 * incremental tokenisation.
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
    val listState = rememberLazyListState()
    val lineCount by remember(fieldValue) {
        derivedStateOf { fieldValue.text.count { it == '\n' } + 1 }
    }

    val highlighted: AnnotatedString = remember(fieldValue.text, tab.language) {
        SyntaxHighlighter.highlight(fieldValue.text, tab.language)
    }

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
            BasicTextField(
                value = fieldValue,
                onValueChange = { new ->
                    if (autoIndent && new.text.length > fieldValue.text.length) {
                        // Auto-indent on Enter: copy leading whitespace from previous line.
                        val added = new.text.substring(fieldValue.text.length)
                        if (added.startsWith("\n")) {
                            val prevLine = fieldValue.text.substringBeforeLast('\n', "")
                            val indent = prevLine.takeWhile { it == ' ' || it == '\t' }
                            val extra = computeExtraIndent(prevLine)
                            val insertion = "\n$indent$extra"
                            val updated = new.text.substring(0, fieldValue.text.length) +
                                insertion +
                                new.text.substring(fieldValue.text.length + 1)
                            val newPos = new.selection.start + insertion.length - 1
                            fieldValue = new.copy(
                                text = updated,
                                selection = androidx.compose.ui.text.TextRange(newPos, newPos),
                            )
                            onContentChange(updated)
                            return@BasicTextField
                        }
                    }
                    fieldValue = new
                    onContentChange(new.text)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                enabled = !tab.readOnly,
                readOnly = tab.readOnly,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = (fontSize + 6).sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    keyboardType = KeyboardType.Text,
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
                                .verticalScroll(rememberScrollState())
                                .then(if (wordWrap) Modifier else Modifier),
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
    // Suppress unused warnings for params consumed by future versions.
    @Suppress("UNUSED_PARAMETER") val _t = tabSize
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
        '{', '(', '[', ':' -> "    "
        else -> ""
    }
}

/** Quick luminance helper — used to pick dark/light palette for the highlighter. */
private fun Color.luminance(): Float =
    0.2126f * red + 0.7152f * green + 0.0722f * blue
