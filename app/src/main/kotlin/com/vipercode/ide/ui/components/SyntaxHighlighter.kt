package com.vipercode.ide.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.vipercode.ide.util.Language

/**
 * Lightweight regex-free syntax highlighter for v0.0.1.
 *
 * Not a full lexer — trades perfect correctness for low startup cost
 * and zero native dependencies. Highlights:
 *   - comments (line + block)
 *   - string literals (single/double/backtick, with escapes)
 *   - numeric literals (int / float / hex / binary / scientific)
 *   - annotations (@Foo)
 *   - a curated keyword set per language
 *   - function call detection (identifier followed by `(`)
 *
 * Output is a single [AnnotatedString] tagged with [SpanStyle]s, ready
 * to be rendered by a Compose `Text` composable.
 *
 * For v0.0.2+ we plan to swap this for a Tree-sitter-based
 * implementation that ships compiled grammars per language.
 */
object SyntaxHighlighter {

    private val SCALAR_KEYWORDS = setOf(
        "true", "false", "null", "nil", "None", "True", "False",
        "NULL", "undefined", "NaN", "self", "this", "super",
    )

    private val KEYWORDS: Map<Language, Set<String>> = mapOf(
        Language.KOTLIN to setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
            "in", "interface", "is", "null", "object", "package", "return", "super", "this",
            "throw", "true", "try", "typealias", "val", "var", "when", "while", "by", "catch",
            "constructor", "delegate", "dynamic", "field", "file", "finally", "get", "import",
            "infix", "init", "inline", "inner", "internal", "lateinit", "noinline", "open",
            "operator", "out", "override", "private", "protected", "public", "reified",
            "sealed", "suspend", "tailrec", "set", "vararg", "where", "abstract", "actual",
            "annotation", "companion", "const", "crossinline", "data", "enum", "expect",
            "external", "final",
        ),
        Language.JAVA to setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum", "extends",
            "false", "final", "finally", "float", "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native", "new", "null", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "true",
            "try", "void", "volatile", "while", "var", "yield", "record", "sealed", "permits",
        ),
        Language.PYTHON to setOf(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
            "continue", "def", "del", "elif", "else", "except", "finally", "for", "from",
            "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass",
            "raise", "return", "try", "while", "with", "yield", "match", "case",
        ),
        Language.JAVASCRIPT to setOf(
            "await", "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "enum", "export", "extends", "false", "finally",
            "for", "function", "if", "implements", "import", "in", "instanceof", "interface",
            "let", "new", "null", "package", "private", "protected", "public", "return",
            "super", "switch", "static", "this", "throw", "true", "try", "typeof", "undefined",
            "var", "void", "while", "with", "yield", "async", "of",
        ),
        Language.TYPESCRIPT to setOf(
            "abstract", "any", "as", "async", "await", "boolean", "break", "case", "catch",
            "class", "const", "continue", "debugger", "declare", "default", "delete", "do",
            "else", "enum", "export", "extends", "false", "finally", "for", "from", "function",
            "get", "if", "implements", "import", "in", "infer", "instanceof", "interface",
            "is", "keyof", "let", "module", "namespace", "never", "new", "null", "number",
            "object", "of", "package", "private", "protected", "public", "readonly", "return",
            "require", "satisfies", "set", "static", "string", "super", "switch", "symbol",
            "this", "throw", "true", "try", "type", "typeof", "undefined", "unique", "unknown",
            "var", "void", "while", "with", "yield",
        ),
        Language.GO to setOf(
            "break", "case", "chan", "const", "continue", "default", "defer", "else", "fallthrough",
            "for", "func", "go", "goto", "if", "import", "interface", "map", "package", "range",
            "return", "select", "struct", "switch", "type", "var", "nil", "iota",
            "append", "cap", "close", "copy", "len", "make", "new", "panic", "print", "println",
            "recover",
        ),
        Language.RUST to setOf(
            "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum",
            "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod",
            "move", "mut", "pub", "ref", "return", "self", "Self", "static", "struct", "super",
            "trait", "true", "type", "unsafe", "use", "where", "while", "try",
        ),
        Language.C to setOf(
            "auto", "break", "case", "char", "const", "continue", "default", "do", "double",
            "else", "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long",
            "register", "restrict", "return", "short", "signed", "sizeof", "static", "struct",
            "switch", "typedef", "union", "unsigned", "void", "volatile", "while",
        ),
        Language.CPP to setOf(
            "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor", "bool",
            "break", "case", "catch", "char", "char8_t", "char16_t", "char32_t", "class",
            "compl", "concept", "const", "consteval", "constexpr", "constinit", "const_cast",
            "continue", "co_await", "co_return", "co_yield", "decltype", "default", "delete",
            "do", "double", "dynamic_cast", "else", "enum", "explicit", "export", "extern",
            "false", "float", "for", "friend", "goto", "if", "inline", "int", "long", "mutable",
            "namespace", "new", "noexcept", "not", "not_eq", "nullptr", "operator", "or", "or_eq",
            "private", "protected", "public", "register", "reinterpret_cast", "requires",
            "return", "short", "signed", "sizeof", "static", "static_assert", "static_cast",
            "struct", "switch", "template", "this", "thread_local", "throw", "true", "try",
            "typedef", "typeid", "typename", "union", "unsigned", "using", "virtual", "void",
            "volatile", "wchar_t", "while", "xor", "xor_eq",
        ),
        Language.CSHARP to setOf(
            "abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char",
            "checked", "class", "const", "continue", "decimal", "default", "delegate", "do",
            "double", "else", "enum", "event", "explicit", "extern", "false", "finally",
            "fixed", "float", "for", "foreach", "goto", "if", "implicit", "in", "int",
            "interface", "internal", "is", "lock", "long", "namespace", "new", "null",
            "object", "operator", "out", "override", "params", "private", "protected",
            "public", "readonly", "ref", "return", "sbyte", "sealed", "short", "sizeof",
            "stackalloc", "static", "string", "struct", "switch", "this", "throw", "true",
            "try", "typeof", "uint", "ulong", "unchecked", "unsafe", "ushort", "using",
            "virtual", "void", "volatile", "while", "add", "alias", "async", "await",
            "get", "global", "partial", "remove", "set", "value", "var", "when", "where",
            "yield", "record", "init",
        ),
        Language.SWIFT to setOf(
            "associatedtype", "class", "deinit", "enum", "extension", "fileprivate", "func",
            "import", "init", "inout", "internal", "let", "open", "operator", "private",
            "protocol", "public", "static", "struct", "subscript", "typealias", "var", "break",
            "case", "continue", "default", "defer", "do", "else", "fallthrough", "for", "guard",
            "if", "in", "repeat", "return", "switch", "where", "while", "as", "Any", "catch",
            "false", "is", "nil", "rethrows", "super", "self", "Self", "throw", "throws",
            "true", "try", "async", "await", "each",
        ),
        Language.DART to setOf(
            "abstract", "as", "assert", "async", "await", "break", "case", "catch", "class",
            "const", "continue", "covariant", "default", "deferred", "do", "dynamic", "else",
            "enum", "export", "extends", "extension", "external", "factory", "false", "final",
            "finally", "for", "Function", "get", "hide", "if", "implements", "import", "in",
            "interface", "is", "late", "library", "mixin", "new", "null", "on", "operator",
            "part", "rethrow", "return", "set", "show", "static", "super", "switch", "sync",
            "this", "throw", "true", "try", "typedef", "var", "void", "while", "with", "yield",
        ),
        Language.RUBY to setOf(
            "BEGIN", "END", "alias", "and", "begin", "break", "case", "class", "def", "defined?",
            "do", "else", "elsif", "end", "ensure", "false", "for", "if", "in", "module", "next",
            "nil", "not", "or", "redo", "rescue", "retry", "return", "self", "super", "then",
            "true", "undef", "unless", "until", "when", "while", "yield", "__FILE__", "__LINE__",
            "__ENCODING__",
        ),
        Language.LUA to setOf(
            "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "if",
            "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until",
            "while", "goto", "continue",
        ),
        Language.PHP to setOf(
            "abstract", "and", "array", "as", "break", "callable", "case", "catch", "class",
            "clone", "const", "continue", "declare", "default", "do", "echo", "else", "elseif",
            "empty", "enddeclare", "endfor", "endforeach", "endif", "endswitch", "endwhile",
            "enum", "extends", "final", "finally", "fn", "for", "foreach", "function", "global",
            "goto", "if", "implements", "include", "include_once", "instanceof", "insteadof",
            "interface", "isset", "list", "match", "namespace", "new", "or", "print", "private",
            "protected", "public", "readonly", "require", "require_once", "return", "static",
            "switch", "throw", "trait", "try", "unset", "use", "var", "while", "xor", "yield",
            "true", "false", "null",
        ),
        Language.SQL to setOf(
            "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER",
            "TABLE", "VIEW", "INDEX", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "AS",
            "AND", "OR", "NOT", "NULL", "TRUE", "FALSE", "DEFAULT", "PRIMARY", "KEY", "FOREIGN",
            "REFERENCES", "UNIQUE", "CONSTRAINT", "CHECK", "VALUES", "INTO", "SET",
            "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET", "DISTINCT", "UNION", "ALL",
            "CASE", "WHEN", "THEN", "ELSE", "END", "BEGIN", "COMMIT", "ROLLBACK", "TRANSACTION",
            "WITH", "RECURSIVE", "EXPLAIN", "PRAGMA",
        ),
        Language.GROOVY to setOf(
            "as", "assert", "break", "case", "catch", "class", "const", "continue", "def",
            "default", "do", "else", "enum", "extends", "false", "finally", "for", "goto", "if",
            "implements", "import", "in", "instanceof", "interface", "new", "null", "package",
            "return", "super", "switch", "this", "throw", "throws", "trait", "true", "try",
            "while", "void", "it",
        ),
        Language.SCALA to setOf(
            "abstract", "case", "catch", "class", "def", "do", "else", "extends", "false",
            "final", "finally", "for", "forSome", "if", "implicit", "import", "lazy", "match",
            "new", "null", "object", "override", "package", "private", "protected", "return",
            "sealed", "super", "this", "throw", "trait", "try", "true", "type", "val", "var",
            "while", "with", "yield", "given", "using", "enum", "export", "then",
        ),
    )

    /** Tokenize and apply colour spans. */
    fun highlight(source: String, language: Language): AnnotatedString {
        if (language == Language.UNKNOWN || language == Language.TEXT) {
            return AnnotatedString(source)
        }
        val palette = Palette.current()
        return buildAnnotatedString {
            val src = source
            var i = 0
            val n = src.length
            val keywords = KEYWORDS[language] ?: emptySet()
            val lineComment = language.lineComment
            val blockStart = language.blockCommentStart
            val blockEnd = language.blockCommentEnd

            while (i < n) {
                val c = src[i]
                when {
                    // Line comments
                    lineComment != null && src.startsWith(lineComment, i) -> {
                        val end = src.indexOf('\n', i).let { if (it < 0) n else it }
                        append(src.substring(i, end))
                        addStyle(palette.comment, i, end)
                        i = end
                    }
                    // Block comments
                    blockStart != null && blockEnd != null && src.startsWith(blockStart, i) -> {
                        val endIdx = src.indexOf(blockEnd, i + blockStart.length)
                        val end = if (endIdx < 0) n else endIdx + blockEnd.length
                        append(src.substring(i, end))
                        addStyle(palette.comment, i, end)
                        i = end
                    }
                    // String literals (and Python triple-quoted strings).
                    c == '"' || c == '\'' -> {
                        val end = scanString(src, i, c, language)
                        append(src.substring(i, end))
                        addStyle(palette.string, i, end)
                        i = end
                    }
                    // Template literals (JS/TS)
                    c == '`' && (language == Language.JAVASCRIPT || language == Language.TYPESCRIPT) -> {
                        val end = scanBacktick(src, i)
                        append(src.substring(i, end))
                        addStyle(palette.string, i, end)
                        i = end
                    }
                    // Numbers
                    c.isDigit() -> {
                        val end = scanNumber(src, i)
                        append(src.substring(i, end))
                        addStyle(palette.number, i, end)
                        i = end
                    }
                    // Identifiers / keywords
                    c.isLetter() || c == '_' || c == '$' -> {
                        val end = scanIdentifier(src, i)
                        val token = src.substring(i, end)
                        when {
                            keywords.contains(token) || SCALAR_KEYWORDS.contains(token) -> {
                                append(token)
                                addStyle(palette.keyword, i, end)
                            }
                            token.startsWith("@") -> {
                                append(token)
                                addStyle(palette.annotation, i, end)
                            }
                            else -> {
                                val nextNonSpace = src.indexOfFirstNonSpace(end)
                                val isCall = nextNonSpace < n && src.getOrNull(nextNonSpace) == '('
                                append(token)
                                when {
                                    isCall -> addStyle(palette.function, i, end)
                                    token.first().isUpperCase() -> addStyle(palette.type, i, end)
                                    else -> addStyle(palette.identifier, i, end)
                                }
                            }
                        }
                        i = end
                    }
                    // Annotations
                    c == '@' -> {
                        // v0.0.2 had `end > i + 1` which was always true
                        // because `scanIdentifier(...) + 1` is at least
                        // `i + 1 + 0 = i + 1`. We now require `end > i + 1`
                        // so an `@` followed by a non-identifier character
                        // (e.g. `@!foo`) is no longer mis-highlighted as
                        // an annotation.
                        //
                        // v0.1.0 — FIX: drop the stray `+ 1`. scanIdentifier
                        // already returns the index AFTER the last
                        // identifier char, so adding 1 over-consumed one
                        // extra char (e.g. `@Foo)` highlighted `)` as part
                        // of the annotation). The check is now `end > i + 1`
                        // (was `i + 2`) because `end` no longer has the +1
                        // baked in.
                        val end = scanIdentifier(src, i + 1)
                        if (end > i + 1) {
                            append(src.substring(i, end))
                            addStyle(palette.annotation, i, end)
                            i = end
                        } else {
                            append(c); i++
                        }
                    }
                    // Pre-processor (C/C++)
                    c == '#' && (language == Language.C || language == Language.CPP) -> {
                        val end = src.indexOf('\n', i).let { if (it < 0) n else it }
                        append(src.substring(i, end))
                        addStyle(palette.annotation, i, end)
                        i = end
                    }
                    // Markdown headings — v0.0.7: only highlight `#` at
                    // the START of a line (after optional whitespace).
                    // Previously any `#` mid-paragraph was mis-highlighted
                    // as a heading, polluting prose Markdown with blue text.
                    c == '#' && language == Language.MARKDOWN &&
                        (i == 0 || src[i - 1] == '\n') -> {
                        val end = src.indexOf('\n', i).let { if (it < 0) n else it }
                        append(src.substring(i, end))
                        addStyle(palette.keyword, i, end)
                        i = end
                    }
                    // Operators / punctuation
                    else -> {
                        append(c); i++
                    }
                }
            }
        }
    }

    private fun String.indexOfFirstNonSpace(from: Int): Int {
        var k = from
        while (k < length && this[k].isWhitespace()) k++
        return k
    }

    private fun scanString(src: String, start: Int, quote: Char, language: Language = Language.UNKNOWN): Int {
        // v0.0.7 — Python triple-quoted strings (""" or ''') span
        // multiple lines. The previous single-line scan terminated at
        // the first '\n', mis-highlighting the rest of the string body
        // as code.
        //
        // v0.0.8 — Kotlin raw strings ("""...""") also span multiple
        // lines, so they share the same triple-quote scan path.
        val tripleQuoted = language == Language.PYTHON || language == Language.KOTLIN
        if (tripleQuoted && quote == '"') {
            val triple = "\"\"\""
            if (src.startsWith(triple, start)) {
                var i = start + 3
                while (i < src.length) {
                    if (src.startsWith(triple, i)) return i + 3
                    i++
                }
                return src.length
            }
        }
        var i = start + 1
        while (i < src.length) {
            when (src[i]) {
                // v0.0.8 — clamp to src.length on escape-skip; the
                // previous `i += 2` could push past the end and then
                // `src[i]` below would throw
                // StringIndexOutOfBoundsException when the string ended
                // with a lone backslash (e.g. `"foo\`).
                '\\' -> {
                    i += 2
                    if (i > src.length) return src.length
                }
                quote -> return i + 1
                '\n' -> return i
                else -> i++
            }
        }
        return i.coerceAtMost(src.length)
    }

    private fun scanBacktick(src: String, start: Int): Int {
        var i = start + 1
        while (i < src.length) {
            when (src[i]) {
                // v0.0.8 — same clamp as scanString.
                '\\' -> {
                    i += 2
                    if (i > src.length) return src.length
                }
                '`' -> return i + 1
                else -> i++
            }
        }
        return i.coerceAtMost(src.length)
    }

    private fun scanNumber(src: String, start: Int): Int {
        var i = start
        if (src.startsWith("0x", i, ignoreCase = true) || src.startsWith("0b", i, ignoreCase = true)) {
            i += 2
            while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
            return i
        }
        while (i < src.length && (src[i].isDigit() || src[i] == '_')) i++
        if (i < src.length && src[i] == '.') {
            i++
            while (i < src.length && (src[i].isDigit() || src[i] == '_')) i++
        }
        if (i < src.length && (src[i] == 'e' || src[i] == 'E')) {
            i++
            if (i < src.length && (src[i] == '+' || src[i] == '-')) i++
            while (i < src.length && src[i].isDigit()) i++
        }
        // v0.0.7 — multi-char numeric suffixes like `123UL`, `0xFFL`,
        // `1.5e10f` consume ALL trailing `fFlLuUdD` chars (was only one).
        while (i < src.length && src[i] in "fFlLuUdD") i++
        return i
    }

    private fun scanIdentifier(src: String, start: Int): Int {
        var i = start
        while (i < src.length) {
            val c = src[i]
            if (c.isLetterOrDigit() || c == '_' || c == '$') i++ else break
        }
        return i
    }

    /**
     * Per-theme colour palette. Bound at composition time via [Palette.bind]
     * so the highlighter output respects the user's Material 3 colour scheme.
     */
    data class Palette(
        val keyword: SpanStyle,
        val string: SpanStyle,
        val number: SpanStyle,
        val comment: SpanStyle,
        val annotation: SpanStyle,
        val function: SpanStyle,
        val type: SpanStyle,
        val identifier: SpanStyle,
    ) {
        companion object {
            @Volatile private var current: Palette = darkDefault()
            fun current(): Palette = current
            fun bind(palette: Palette) { current = palette }

            fun darkDefault(): Palette = Palette(
                keyword = SpanStyle(color = Color(0xFF82AAFF), fontWeight = FontWeight.Bold),
                string = SpanStyle(color = Color(0xFFC3E88D)),
                number = SpanStyle(color = Color(0xFFF78C6C)),
                comment = SpanStyle(color = Color(0xFF697098), fontStyle = FontStyle.Italic),
                annotation = SpanStyle(color = Color(0xFFFFCB6B)),
                function = SpanStyle(color = Color(0xFF82B1FF)),
                type = SpanStyle(color = Color(0xFFFFB62C)),
                identifier = SpanStyle(color = Color(0xFFEEFFFF)),
            )

            fun lightDefault(): Palette = Palette(
                keyword = SpanStyle(color = Color(0xFF1E88E5), fontWeight = FontWeight.Bold),
                string = SpanStyle(color = Color(0xFF2E7D32)),
                number = SpanStyle(color = Color(0xFFE65100)),
                comment = SpanStyle(color = Color(0xFF757575), fontStyle = FontStyle.Italic),
                annotation = SpanStyle(color = Color(0xFF8E6F00)),
                function = SpanStyle(color = Color(0xFF0D47A1)),
                type = SpanStyle(color = Color(0xFFB85C00)),
                identifier = SpanStyle(color = Color(0xFF1A1A1A)),
            )
        }
    }
}
