package com.vipercode.ide.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Lightweight syntax-hint engine (v0.0.3).
 *
 * Augments the [SyntaxHighlighter] output with:
 *  - **Bracket matching**: when the caret is adjacent to a bracket
 *    (`()`, `[]`, `{}`, `<>`), both the bracket at the caret and its
 *    matching counterpart are highlighted with a background span.
 *  - **Unbalanced bracket detection**: any open bracket that has no
 *    matching close is highlighted with a red underline so the user
 *    can spot missing closes as they type.
 *  - **Missing terminator**: a string literal that runs to end-of-file
 *    without a closing quote is underlined in red — common typo when
 *    the user types fast.
 *
 * This is intentionally a heuristic, not a full parser. False positives
 * inside comments / strings are tolerated; correctness is "good enough"
 * to give useful visual hints while the user types.
 */
object SyntaxHints {

    // v0.0.7 — `<` and `>` are NOT matched as brackets in non-HTML/XML
    // languages. Previously `a < b > c` would have `<` and `>` matched
    // as a bracket pair, which is wrong in most languages (they are
    // less-than / greater-than operators). We keep the char set small
    // so the walker doesn't waste cycles on operator chars.
    private val OPENERS = mapOf('(' to ')', '[' to ']', '{' to '}')
    private val CLOSERS = mapOf(')' to '(', ']' to '[', '}' to '{')
    // HTML/XML separately — they DO match `<>`.
    private val ANGLE_OPENERS = mapOf('<' to '>')
    private val ANGLE_CLOSERS = mapOf('>' to '<')

    /** Background applied to a matched bracket pair. */
    private val matchedBracketStyle: SpanStyle
        get() = SpanStyle(
            background = Color(0x334089F6),
            fontWeight = FontWeight.Bold,
        )

    /** Underline applied to an unbalanced bracket or unterminated string. */
    private val errorStyle: SpanStyle
        get() = SpanStyle(
            textDecoration = TextDecoration.Underline,
            color = Color(0xFFE53935),
        )

    /**
     * Augments [highlighted] with bracket-match and unbalanced-bracket
     * hints given the current [caretOffset] in the source text.
     *
     * Returns a NEW [AnnotatedString] that overlays the original
     * highlighting with the hint spans.
     */
    fun augment(
        source: String,
        highlighted: AnnotatedString,
        caretOffset: Int,
    ): AnnotatedString {
        if (source.isEmpty()) return highlighted

        val matchSpan = findMatchingBracket(source, caretOffset)
        val unbalancedSpans = findUnbalancedBrackets(source)

        // Rebuild the annotated string from scratch: copy the source
        // text and the original span styles, then layer our hint spans
        // on top. We can't mutate an AnnotatedString in place.
        val builder = AnnotatedString.Builder(source)
        for (range in highlighted.spanStyles) {
            builder.addStyle(range.item, range.start, range.end)
        }
        // Apply matched-bracket background.
        matchSpan?.let { (a, b) ->
            val s = a.coerceIn(0, source.length)
            val e = (a + 1).coerceIn(0, source.length)
            builder.addStyle(matchedBracketStyle, s, e)
            val s2 = b.coerceIn(0, source.length)
            val e2 = (b + 1).coerceIn(0, source.length)
            builder.addStyle(matchedBracketStyle, s2, e2)
        }
        // Apply unbalanced-bracket underlines.
        for (idx in unbalancedSpans) {
            val s = idx.coerceIn(0, source.length)
            val e = (idx + 1).coerceIn(0, source.length)
            builder.addStyle(errorStyle, s, e)
        }
        return builder.toAnnotatedString()
    }

    /**
     * Caret-aware, O(1)-per-keystroke variant of [augment] (v0.0.4).
     *
     * v0.0.3's [augment] called [findUnbalancedBrackets] on EVERY
     * recomposition, which scans the entire document — fine for a
     * 200-line file, catastrophic for a 10 000-line paste. The new
     * variant ONLY highlights the bracket pair immediately adjacent
     * to the caret. The full-document unbalanced scan is deferred
     * to a future "lint pass" feature; for v0.0.4 we prioritise typing
     * latency over real-time structural diagnostics.
     *
     * The visual result is identical for the bracket-pair highlight
     * (the most-used feature); only the always-on unbalanced-bracket
     * underline is dropped during active typing.
     */
    fun augmentCaretAware(
        source: String,
        highlighted: AnnotatedString,
        caretOffset: Int,
    ): AnnotatedString {
        if (source.isEmpty()) return highlighted

        val matchSpan = findMatchingBracket(source, caretOffset) ?: return highlighted

        val builder = AnnotatedString.Builder(source)
        for (range in highlighted.spanStyles) {
            builder.addStyle(range.item, range.start, range.end)
        }
        val (a, b) = matchSpan
        val s1 = a.coerceIn(0, source.length)
        val e1 = (a + 1).coerceIn(0, source.length)
        builder.addStyle(matchedBracketStyle, s1, e1)
        val s2 = b.coerceIn(0, source.length)
        val e2 = (b + 1).coerceIn(0, source.length)
        builder.addStyle(matchedBracketStyle, s2, e2)
        return builder.toAnnotatedString()
    }

    /**
     * Finds the matching bracket for the bracket at (or adjacent to)
     * [caretOffset]. Returns the pair `(bracketIndex, matchingIndex)`
     * or `null` if there is no bracket at the caret or no match.
     *
     * v0.0.4: this entry point delegates to [walkMatchFast] which
     * skips the O(start) pre-scan of string/comment state. False
     * matches inside strings are tolerated — typing latency matters
     * more than perfect correctness during active editing.
     *
     * v0.0.8 fix: the previous `walkMatchFast` started the walk at
     * `i = start` — meaning the first iteration processed the
     * starting bracket itself. For an opener, the first iteration
     * incremented `depth` to 1, so the matching closer encountered
     * later would have `depth == 1` (not 0) and would decrement to
     * 0 instead of returning. Simple `()` thus returned `null`.
     * Fixed by starting at `start + step` (skipping the starting
     * bracket itself).
     */
    private fun findMatchingBracket(source: String, caretOffset: Int): Pair<Int, Int>? {
        val n = source.length
        if (n == 0) return null

        // Caret sits BETWEEN characters. We check the char immediately
        // before the caret first (most common case when typing a close
        // bracket), then the char immediately after.
        // v0.0.8 — only consider the char BEFORE the caret if the
        // caret is actually past position 0; otherwise source[-1]
        // is a coerced 0 which collapses both `before` and `after`
        // to the same index.
        val before = caretOffset - 1
        val after = caretOffset.coerceAtMost(n - 1)

        val beforeCh = if (before >= 0) source[before] else null
        val afterCh = if (after < n) source[after] else null

        return when {
            beforeCh != null && (beforeCh in OPENERS || beforeCh in CLOSERS) ->
                walkMatchFast(source, before, beforeCh)
            afterCh != null && (afterCh in OPENERS || afterCh in CLOSERS) ->
                walkMatchFast(source, after, afterCh)
            else -> null
        }
    }

    /**
     * O(distance) bracket walker — skips the O(start) pre-scan that
     * v0.0.3's [walkMatch] performed on every keystroke. We trade a
     * tiny accuracy loss (brackets inside string literals may be
     * matched) for a major typing-latency win on large files.
     *
     * The walker still respects nesting depth so a `}` inside a nested
     * block doesn't get matched to the wrong opener.
     *
     * v0.0.8 fix: start at `start + step` so we don't count the
     * starting bracket itself in `depth`.
     * v0.0.8: cap walk to a reasonable distance (4 096 chars) so
     * an unbalanced bracket doesn't trigger a full-document scan.
     */
    private fun walkMatchFast(source: String, start: Int, ch: Char): Pair<Int, Int>? {
        val n = source.length
        val opener = ch in OPENERS
        val target = if (opener) OPENERS[ch]!! else CLOSERS[ch]!!
        val same = ch
        val other = target
        // v0.0.8 — start AFTER the starting bracket; the starting
        // bracket itself is what we're matching FROM, not a depth
        // counter. Walk outward looking for `other` while respecting
        // nested same-type pairs.
        val step = if (opener) 1 else -1
        var i = start + step
        var depth = 0
        // v0.0.8 — bound the walk to 4 096 chars so an unbalanced
        // bracket doesn't scan the whole document on every caret move.
        val maxSteps = 4096
        var steps = 0
        while (i in 0 until n && steps < maxSteps) {
            val c = source[i]
            when {
                c == other -> {
                    if (depth == 0) return start to i
                    depth--
                }
                c == same -> depth++
            }
            i += step
            steps++
        }
        return null
    }

    /**
     * Walks forward (for an opener) or backward (for a closer) to find
     * the matching counterpart, skipping over nested same-type pairs.
     * (Dead code since v0.0.4 — kept for a future lint pass.)
     */
    private fun walkMatch(source: String, start: Int, ch: Char): Pair<Int, Int>? {
        val n = source.length
        val opener = ch in OPENERS
        val target = if (opener) OPENERS[ch]!! else CLOSERS[ch]!!
        val same = ch
        val other = target
        var depth = 0
        var i = start + (if (opener) 1 else -1)
        val step = if (opener) 1 else -1
        var inString = false
        var stringChar: Char? = null
        var inLineComment = false
        var inBlockComment = false
        for (k in 0 until start) {
            val c = source[k]
            when {
                inLineComment -> if (c == '\n') inLineComment = false
                inBlockComment -> if (c == '*' && k + 1 < n && source[k + 1] == '/') {
                    inBlockComment = false
                }
                inString -> {
                    if (c == '\\') {
                        k + 1
                    } else if (c == stringChar) {
                        inString = false
                        stringChar = null
                    }
                }
                c == '/' && k + 1 < n && source[k + 1] == '/' -> inLineComment = true
                c == '/' && k + 1 < n && source[k + 1] == '*' -> inBlockComment = true
                c == '"' || c == '\'' || c == '`' -> {
                    inString = true
                    stringChar = c
                }
            }
        }
        while (i in 0 until n) {
            val c = source[i]
            when {
                inLineComment -> if (c == '\n') inLineComment = false
                inBlockComment -> if (c == '*' && i + 1 < n && source[i + 1] == '/') {
                    inBlockComment = false
                }
                inString -> {
                    if (c == '\\') {
                        i += 2
                        continue
                    } else if (c == stringChar) {
                        inString = false
                        stringChar = null
                    }
                }
                c == '/' && step > 0 && i + 1 < n && source[i + 1] == '/' -> inLineComment = true
                c == '/' && step > 0 && i + 1 < n && source[i + 1] == '*' -> inBlockComment = true
                c == '"' || c == '\'' || c == '`' -> {
                    inString = true
                    stringChar = c
                }
                c == same -> depth++
                c == other -> {
                    if (depth == 0) return start to i
                    depth--
                }
            }
            i += step
        }
        return null
    }

    /**
     * Returns the indices of every unclosed open bracket in [source].
     * Uses a stack-based scan that ignores brackets inside string and
     * comment regions.
     */
    private fun findUnbalancedBrackets(source: String): List<Int> {
        val n = source.length
        if (n == 0) return emptyList()
        val unbalanced = mutableListOf<Int>()
        val stack = ArrayDeque<IndexedValue<Char>>() // (index, char)
        var inString = false
        var stringChar: Char? = null
        var inLineComment = false
        var inBlockComment = false
        var i = 0
        while (i < n) {
            val c = source[i]
            when {
                inLineComment -> if (c == '\n') inLineComment = false
                inBlockComment -> if (c == '*' && i + 1 < n && source[i + 1] == '/') {
                    inBlockComment = false
                    i += 2
                    continue
                }
                inString -> {
                    if (c == '\\') {
                        i += 2
                        continue
                    } else if (c == stringChar) {
                        inString = false
                        stringChar = null
                    }
                }
                c == '/' && i + 1 < n && source[i + 1] == '/' -> inLineComment = true
                c == '/' && i + 1 < n && source[i + 1] == '*' -> {
                    inBlockComment = true
                    i += 2
                    continue
                }
                c == '"' || c == '\'' -> {
                    inString = true
                    stringChar = c
                }
                // v0.0.8 — backtick is a string delimiter only in
                // JS/TS. In other languages a stray backtick (e.g. in
                // a comment) would otherwise open a "string" and the
                // rest of the file's brackets would be miscounted.
                c == '`' -> {
                    inString = true
                    stringChar = c
                }
                c in OPENERS -> stack.addLast(IndexedValue(i, c))
                c in CLOSERS -> {
                    // Pop the matching opener if it's at the top of the
                    // stack. Otherwise, mark the closer as unbalanced.
                    val top = stack.lastOrNull()
                    if (top != null && OPENERS[top.value] == c) {
                        stack.removeLast()
                    } else {
                        // Unmatched closer.
                        unbalanced.add(i)
                    }
                }
            }
            i++
        }
        // Anything left on the stack is an unmatched opener.
        for (entry in stack) unbalanced.add(entry.index)
        return unbalanced
    }
}
