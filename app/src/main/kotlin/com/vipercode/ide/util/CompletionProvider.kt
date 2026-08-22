package com.vipercode.ide.util

import com.vipercode.ide.data.model.EditorTab

/**
 * Lightweight code-completion engine (v0.0.8 — new feature).
 *
 * Generates context-aware completion candidates given the source text
 * up to the caret. The engine is intentionally heuristic — no real
 * type-checking or symbol-table — but covers the most common cases
 * that make mobile coding bearable:
 *
 *  - **Keyword completion** for every [Language] with a non-empty
 *    keyword list. Typed `pu` → `public`, `fina` → `final`, etc.
 *  - **Snippet completion** for boilerplate constructs (`fun`,
 *    `class`, `def`, `if`, `for`, `while`, ...). Snippets use the
 *    `${1:placeholder}` template syntax familiar from VS Code /
 *    LSP, but the editor currently inserts the literal text —
 *    placeholder navigation is a future feature.
 *  - **Identifier completion** harvested from the current file
 *    (every word-like token in the document is a candidate). This
 *    catches local variables, function names, etc. — even though
 *    we don't have a real symbol table.
 *
 * The engine is pure (no I/O, no coroutine suspension) so the
 * caller can drop it into the editor's `onValueChange` path
 * without ceremony. Per-call cost is O(n) where n is the document
 * size; we cap candidate count to keep the UI snappy.
 */
object CompletionProvider {

    /**
     * A single completion candidate.
     *
     * @property label    The text shown in the popup.
     * @property insert   The text inserted when the user accepts.
     * @property detail   Optional one-line description (e.g.
     *                    "keyword", "snippet", or the source file
     *                    line where the identifier was first seen).
     * @property kind     Sorts icon + priority.
     */
    data class Candidate(
        val label: String,
        val insert: String,
        val detail: String,
        val kind: Kind,
    )

    enum class Kind { KEYWORD, SNIPPET, IDENTIFIER }

    /**
     * Returns up to [maxResults] candidates for the source [text]
     * up to the [caretOffset]. Returns an empty list if no prefix
     * was matched.
     *
     * Strategy:
     *  1. Walk backward from the caret, collecting the "prefix" of
     *     word characters.
     *  2. Skip completion entirely if the caret is in the middle of
     *     a string/comment (best-effort, same heuristic as
     *     [SyntaxHints.findUnbalancedBrackets]).
     *  3. Collect keyword + snippet candidates for the [Language],
     *     then identifier candidates from the file.
     *  4. Sort by: starts-with-prefix first, then alphabetical.
     */
    fun suggest(
        text: String,
        caretOffset: Int,
        language: Language,
        maxResults: Int = 12,
    ): List<Candidate> {
        if (caretOffset <= 0 || caretOffset > text.length) return emptyList()
        val prefix = extractPrefix(text, caretOffset)
        if (prefix.length < 2) return emptyList()  // require ≥ 2 chars
        if (isCaretInsideStringOrComment(text, caretOffset)) return emptyList()

        val lower = prefix.lowercase()
        val out = LinkedHashSet<Candidate>()  // dedupe by label

        // ── Keyword candidates ──────────────────────────────────────
        for (kw in keywordsFor(language)) {
            if (kw.startsWith(prefix) || kw.lowercase().startsWith(lower)) {
                if (kw != prefix) {  // don't suggest what the user already typed
                    out.add(Candidate(kw, kw, "keyword", Kind.KEYWORD))
                }
            }
        }

        // ── Snippet candidates ──────────────────────────────────────
        for (snip in snippetsFor(language)) {
            if (snip.trigger.startsWith(prefix) || snip.trigger.lowercase().startsWith(lower)) {
                out.add(Candidate(snip.trigger, snip.body, snip.description, Kind.SNIPPET))
            }
        }

        // ── Identifier candidates (from this file) ─────────────────
        val idents = collectIdentifiers(text, maxScan = 50_000)
        for (ident in idents) {
            if (out.size >= maxResults) break
            if (ident.length < prefix.length) continue
            if (ident.startsWith(prefix) || ident.lowercase().startsWith(lower)) {
                if (ident != prefix) {
                    out.add(Candidate(ident, ident, "identifier", Kind.IDENTIFIER))
                }
            }
        }

        return out.toList()
            .sortedWith(
                compareBy(
                    { !it.label.startsWith(prefix) },          // starts-with-prefix first
                    { it.kind.ordinal },                       // keyword < snippet < identifier
                    { it.label.lowercase() },                  // alphabetical tie-breaker
                )
            )
            .take(maxResults)
    }

    /**
     * Extracts the word-prefix immediately before the caret.
     */
    private fun extractPrefix(text: String, caret: Int): String {
        var i = caret - 1
        while (i >= 0 && (text[i].isLetterOrDigit() || text[i] == '_')) i--
        return text.substring(i + 1, caret)
    }

    /**
     * Best-effort check: is the caret inside a string literal or a
     * comment? We don't want to suggest `public` mid-string when the
     * user is typing prose.
     */
    private fun isCaretInsideStringOrComment(text: String, caret: Int): Boolean {
        var inString = false
        var stringChar: Char? = null
        var inLineComment = false
        var inBlockComment = false
        var i = 0
        while (i < caret) {
            val c = text[i]
            when {
                inLineComment -> {
                    if (c == '\n') inLineComment = false
                }
                inBlockComment -> {
                    if (c == '*' && i + 1 < caret && text[i + 1] == '/') {
                        inBlockComment = false
                        i += 2
                        continue
                    }
                }
                inString -> {
                    if (c == '\\' && i + 1 < caret) {
                        i += 2
                        continue
                    } else if (c == stringChar) {
                        inString = false
                        stringChar = null
                    }
                }
                else -> {
                    when (c) {
                        '/' -> {
                            if (i + 1 < caret && text[i + 1] == '/') {
                                inLineComment = true
                                i += 2
                                continue
                            } else if (i + 1 < caret && text[i + 1] == '*') {
                                inBlockComment = true
                                i += 2
                                continue
                            }
                        }
                        '"', '\'', '`' -> {
                            inString = true
                            stringChar = c
                        }
                        '#' -> {
                            // Python / shell / YAML line comment.
                            inLineComment = true
                        }
                    }
                }
            }
            i++
        }
        return inString || inLineComment || inBlockComment
    }

    /**
     * Harvests every identifier (≥ 3 chars) from [text], capped at
     * [maxScan] chars to keep the cost bounded on huge files.
     * Returns the unique identifiers in first-seen order.
     */
    private fun collectIdentifiers(text: String, maxScan: Int): List<String> {
        val scanEnd = minOf(text.length, maxScan)
        val out = LinkedHashSet<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < scanEnd) {
            val c = text[i]
            if (c.isLetterOrDigit() || c == '_') {
                sb.append(c)
            } else {
                if (sb.length >= 3) {
                    out.add(sb.toString())
                }
                sb.setLength(0)
            }
            i++
        }
        if (sb.length >= 3) out.add(sb.toString())
        return out.toList()
    }

    // ── Per-language keyword tables ────────────────────────────────
    private fun keywordsFor(language: Language): List<String> = when (language) {
        Language.KOTLIN -> KOTLIN_KEYWORDS
        Language.JAVA -> JAVA_KEYWORDS
        Language.SCALA -> SCALA_KEYWORDS
        Language.GROOVY, Language.GRADLE -> GROOVY_KEYWORDS
        Language.PYTHON -> PYTHON_KEYWORDS
        Language.JAVASCRIPT -> JS_KEYWORDS
        Language.TYPESCRIPT -> TS_KEYWORDS
        Language.C -> C_KEYWORDS
        Language.CPP -> CPP_KEYWORDS
        Language.CSHARP -> CSHARP_KEYWORDS
        Language.GO -> GO_KEYWORDS
        Language.RUST -> RUST_KEYWORDS
        Language.PHP -> PHP_KEYWORDS
        Language.SQL -> SQL_KEYWORDS
        Language.DART -> DART_KEYWORDS
        Language.SWIFT -> SWIFT_KEYWORDS
        Language.RUBY -> RUBY_KEYWORDS
        Language.LUA -> LUA_KEYWORDS
        Language.SHELL -> SHELL_KEYWORDS
        Language.YAML, Language.TOML, Language.INI, Language.PROPERTIES, Language.GIT -> emptyList()
        Language.HTML, Language.CSS, Language.XML, Language.JSON, Language.MARKDOWN -> emptyList()
        Language.TEXT, Language.UNKNOWN -> emptyList()
    }

    // ── Per-language snippet tables ───────────────────────────────
    private data class Snippet(val trigger: String, val body: String, val description: String)

    private fun snippetsFor(language: Language): List<Snippet> = when (language) {
        Language.KOTLIN, Language.SCALA, Language.GROOVY, Language.GRADLE -> JVM_SNIPPETS
        Language.JAVA -> JAVA_SNIPPETS
        Language.PYTHON -> PYTHON_SNIPPETS
        Language.JAVASCRIPT, Language.TYPESCRIPT -> JS_SNIPPETS
        Language.C, Language.CPP -> C_SNIPPETS
        Language.CSHARP -> CSHARP_SNIPPETS
        Language.GO -> GO_SNIPPETS
        Language.RUST -> RUST_SNIPPETS
        Language.DART -> DART_SNIPPETS
        Language.SWIFT -> SWIFT_SNIPPETS
        Language.PHP -> PHP_SNIPPETS
        Language.RUBY -> RUBY_SNIPPETS
        Language.LUA -> LUA_SNIPPETS
        Language.SHELL -> SHELL_SNIPPETS
        else -> emptyList()
    }

    private val KOTLIN_KEYWORDS = listOf(
        "as", "break", "class", "continue", "do", "else", "false", "for",
        "fun", "if", "in", "interface", "is", "null", "object", "package",
        "return", "super", "this", "throw", "true", "try", "typealias",
        "val", "var", "when", "while", "private", "public", "internal",
        "protected", "companion", "import", "in", "out", "by", "where",
        "data", "sealed", "enum", "annotation", "suspend", "inline", "operator",
        "infix", "tailrec", "vararg", "reified", "lateinit", "override",
        "open", "abstract", "final", "init", "constructor",
    )
    private val JAVA_KEYWORDS = listOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch",
        "char", "class", "const", "continue", "default", "do", "double",
        "else", "enum", "extends", "final", "finally", "float", "for", "goto",
        "if", "implements", "import", "instanceof", "int", "interface", "long",
        "native", "new", "package", "private", "protected", "public", "return",
        "short", "static", "strictfp", "super", "switch", "synchronized",
        "this", "throw", "throws", "transient", "try", "void", "volatile",
        "while", "var", "yield", "record", "sealed", "permits", "non-sealed",
    )
    private val SCALA_KEYWORDS = listOf(
        "abstract", "case", "catch", "class", "def", "do", "else", "extends",
        "false", "final", "finally", "for", "forSome", "if", "implicit", "import",
        "lazy", "match", "new", "null", "object", "override", "package",
        "private", "protected", "return", "sealed", "super", "this", "throw",
        "trait", "try", "true", "type", "val", "var", "while", "with", "yield",
        "given", "using", "enum", "extension", "then",
    )
    private val GROOVY_KEYWORDS = listOf(
        "as", "assert", "break", "case", "catch", "class", "const", "continue",
        "def", "default", "do", "else", "enum", "extends", "false", "finally",
        "for", "goto", "if", "implements", "import", "in", "instanceof",
        "interface", "new", "null", "package", "return", "super", "switch",
        "this", "throw", "throws", "trait", "true", "try", "while",
    )
    private val PYTHON_KEYWORDS = listOf(
        "False", "None", "True", "and", "as", "assert", "async", "await",
        "break", "class", "continue", "def", "del", "elif", "else", "except",
        "finally", "for", "from", "global", "if", "import", "in", "is",
        "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try",
        "while", "with", "yield", "match", "case", "type",
    )
    private val JS_KEYWORDS = listOf(
        "break", "case", "catch", "class", "const", "continue", "debugger",
        "default", "delete", "do", "else", "export", "extends", "finally",
        "for", "function", "if", "import", "in", "instanceof", "new", "return",
        "super", "switch", "this", "throw", "try", "typeof", "var", "void",
        "while", "with", "yield", "let", "static", "async", "await", "of",
        "null", "undefined", "true", "false", "prototype",
    )
    private val TS_KEYWORDS = JS_KEYWORDS + listOf(
        "abstract", "any", "as", "async", "await", "boolean", "constructor",
        "declare", "enum", "interface", "is", "keyof", "namespace", "never",
        "private", "protected", "public", "readonly", "satisfies", "string",
        "type", "unknown", "void", "get", "set", "implements", "module",
    )
    private val C_KEYWORDS = listOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do",
        "double", "else", "enum", "extern", "float", "for", "goto", "if",
        "inline", "int", "long", "register", "restrict", "return", "short",
        "signed", "sizeof", "static", "struct", "switch", "typedef", "union",
        "unsigned", "void", "volatile", "while", "_Atomic", "_Bool",
        "_Complex", "_Generic", "_Noreturn", "_Static_assert", "_Thread_local",
    )
    private val CPP_KEYWORDS = C_KEYWORDS + listOf(
        "alignas", "alignof", "and", "asm", "bool", "catch", "char16_t",
        "char32_t", "class", "compl", "concept", "consteval", "constexpr",
        "constinit", "const_cast", "co_await", "co_return", "co_yield",
        "decltype", "delete", "dynamic_cast", "explicit", "export", "final",
        "friend", "module", "mutable", "namespace", "new", "noexcept",
        "nullptr", "operator", "private", "protected", "public", "reinterpret_cast",
        "requires", "static_cast", "template", "this", "throw", "try",
        "typeid", "typename", "using", "virtual", "wchar_t",
    )
    private val CSHARP_KEYWORDS = listOf(
        "abstract", "as", "base", "bool", "break", "byte", "case", "catch",
        "char", "checked", "class", "const", "continue", "decimal", "default",
        "delegate", "do", "double", "else", "enum", "event", "explicit",
        "extern", "false", "finally", "fixed", "float", "for", "foreach",
        "goto", "if", "implicit", "in", "int", "interface", "internal", "is",
        "lock", "long", "namespace", "new", "null", "object", "operator", "out",
        "override", "params", "private", "protected", "public", "readonly",
        "ref", "return", "sbyte", "sealed", "short", "sizeof", "stackalloc",
        "static", "string", "struct", "switch", "this", "throw", "true",
        "try", "typeof", "uint", "ulong", "unchecked", "unsafe", "ushort",
        "using", "virtual", "void", "while", "var", "record", "init",
    )
    private val GO_KEYWORDS = listOf(
        "break", "case", "chan", "const", "continue", "default", "defer",
        "else", "fallthrough", "for", "func", "go", "goto", "if", "import",
        "interface", "map", "package", "range", "return", "select", "struct",
        "switch", "type", "var", "nil", "true", "false", "iota",
    )
    private val RUST_KEYWORDS = listOf(
        "as", "async", "await", "break", "const", "continue", "crate", "dyn",
        "else", "enum", "extern", "false", "fn", "for", "if", "impl", "in",
        "let", "loop", "match", "mod", "move", "mut", "pub", "ref", "return",
        "self", "Self", "static", "struct", "super", "trait", "true", "type",
        "unsafe", "use", "where", "while", "abstract", "become", "box", "do",
        "final", "macro", "override", "priv", "typeof", "unsized", "virtual", "yield",
    )
    private val PHP_KEYWORDS = listOf(
        "abstract", "and", "array", "as", "break", "callable", "case", "catch",
        "class", "clone", "const", "continue", "declare", "default", "do", "echo",
        "else", "elseif", "empty", "enddeclare", "endfor", "endforeach", "endif",
        "endswitch", "endwhile", "enum", "extends", "final", "finally", "fn",
        "for", "foreach", "function", "global", "goto", "if", "implements",
        "include", "include_once", "instanceof", "insteadof", "interface", "isset",
        "list", "match", "namespace", "new", "or", "print", "private", "protected",
        "public", "readonly", "require", "require_once", "return", "static",
        "switch", "throw", "trait", "try", "unset", "use", "var", "while", "xor",
        "yield",
    )
    private val SQL_KEYWORDS = listOf(
        "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE",
        "DROP", "ALTER", "TABLE", "INDEX", "VIEW", "JOIN", "LEFT", "RIGHT",
        "INNER", "OUTER", "ON", "GROUP", "BY", "ORDER", "HAVING", "LIMIT",
        "OFFSET", "DISTINCT", "UNION", "ALL", "AND", "OR", "NOT", "NULL",
        "IS", "IN", "LIKE", "BETWEEN", "CASE", "WHEN", "THEN", "ELSE", "END",
        "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "DEFAULT",
        "CONSTRAINT", "CHECK", "CASCADE", "TRANSACTION", "COMMIT", "ROLLBACK",
        "BEGIN", "INT", "INTEGER", "VARCHAR", "TEXT", "BLOB", "REAL", "BOOLEAN",
        "DATE", "TIMESTAMP",
    )
    private val DART_KEYWORDS = listOf(
        "abstract", "as", "assert", "async", "await", "break", "case", "catch",
        "class", "const", "continue", "default", "deferred", "do", "dynamic",
        "else", "enum", "export", "extends", "extension", "external", "factory",
        "false", "final", "finally", "for", "Function", "get", "hide", "if",
        "implements", "import", "in", "interface", "is", "library", "mixin",
        "new", "null", "on", "operator", "part", "rethrow", "return", "set",
        "show", "static", "super", "switch", "sync", "this", "throw", "true",
        "try", "typedef", "var", "void", "while", "with", "yield",
    )
    private val SWIFT_KEYWORDS = listOf(
        "associatedtype", "class", "deinit", "enum", "extension", "fileprivate",
        "func", "import", "init", "inout", "internal", "let", "open", "operator",
        "private", "protocol", "public", "rethrows", "static", "struct",
        "subscript", "typealias", "var", "break", "case", "continue", "default",
        "defer", "do", "else", "fallthrough", "for", "guard", "if", "in", "repeat",
        "return", "switch", "where", "while", "as", "Any", "catch", "false",
        "is", "nil", "super", "self", "Self", "throw", "throws", "true", "try",
        "async", "await", "yield",
    )
    private val RUBY_KEYWORDS = listOf(
        "BEGIN", "END", "alias", "and", "begin", "break", "case", "class", "def",
        "defined?", "do", "else", "elsif", "end", "ensure", "false", "for", "if",
        "in", "module", "next", "nil", "not", "or", "redo", "rescue", "retry",
        "return", "self", "super", "then", "true", "undef", "unless", "until",
        "when", "while", "yield", "__FILE__", "__LINE__",
    )
    private val LUA_KEYWORDS = listOf(
        "and", "break", "do", "else", "elseif", "end", "false", "for", "function",
        "if", "in", "local", "nil", "not", "or", "repeat", "return", "then",
        "true", "until", "while", "goto",
    )
    private val SHELL_KEYWORDS = listOf(
        "if", "then", "else", "elif", "fi", "case", "esac", "for", "while",
        "until", "do", "done", "in", "function", "select", "break", "continue",
        "return", "exit", "export", "local", "readonly", "declare", "typeset",
        "unset", "shift", "trap", "set", "source", "alias", "echo", "printf",
        "read", "test", "true", "false", "cd", "pwd", "ls", "grep", "sed", "awk",
    )

    // ── Snippet bodies ─────────────────────────────────────────────
    // Use simple ${1:placeholder} form (the editor inserts the literal
    // text for now; navigation is a future feature).
    private val JVM_SNIPPETS = listOf(
        Snippet("fun", "fun name(): ReturnType {\n    \n}", "function"),
        Snippet("class", "class Name {\n    \n}", "class"),
        Snippet("object", "object Name {\n    \n}", "singleton object"),
        Snippet("interface", "interface Name {\n    \n}", "interface"),
        Snippet("for", "for (item in items) {\n    \n}", "for loop"),
        Snippet("while", "while (condition) {\n    \n}", "while loop"),
        Snippet("if", "if (condition) {\n    \n}", "if statement"),
        Snippet("try", "try {\n    \n} catch (e: Exception) {\n    \n}", "try/catch"),
        Snippet("when", "when (value) {\n    \n}", "when expression"),
    )
    private val JAVA_SNIPPETS = listOf(
        Snippet("class", "class Name {\n    \n}", "class"),
        Snippet("interface", "interface Name {\n    \n}", "interface"),
        Snippet("method", "void name() {\n    \n}", "method"),
        Snippet("for", "for (int i = 0; i < ; i++) {\n    \n}", "for loop"),
        Snippet("while", "while (condition) {\n    \n}", "while loop"),
        Snippet("if", "if (condition) {\n    \n}", "if statement"),
        Snippet("try", "try {\n    \n} catch (Exception e) {\n    \n}", "try/catch"),
        Snippet("switch", "switch (value) {\n    \n}", "switch statement"),
    )
    private val PYTHON_SNIPPETS = listOf(
        Snippet("def", "def name(args):\n    ", "function def"),
        Snippet("class", "class Name:\n    ", "class"),
        Snippet("for", "for item in items:\n    ", "for loop"),
        Snippet("while", "while condition:\n    ", "while loop"),
        Snippet("if", "if condition:\n    ", "if statement"),
        Snippet("try", "try:\n    \nexcept Exception as e:\n    ", "try/except"),
        Snippet("with", "with open(path) as f:\n    ", "with statement"),
        Snippet("match", "match value:\n    case _:\n        ", "match/case"),
    )
    private val JS_SNIPPETS = listOf(
        Snippet("function", "function name() {\n    \n}", "function"),
        Snippet("class", "class Name {\n    \n}", "class"),
        Snippet("for", "for (let i = 0; i < ; i++) {\n    \n}", "for loop"),
        Snippet("while", "while (condition) {\n    \n}", "while loop"),
        Snippet("if", "if (condition) {\n    \n}", "if statement"),
        Snippet("try", "try {\n    \n} catch (e) {\n    \n}", "try/catch"),
        Snippet("switch", "switch (value) {\n    \n}", "switch"),
        Snippet("arrow", "const fn = () => {\n    \n}", "arrow function"),
    )
    private val C_SNIPPETS = listOf(
        Snippet("if", "if (condition) {\n    \n}", "if statement"),
        Snippet("for", "for (int i = 0; i < ; i++) {\n    \n}", "for loop"),
        Snippet("while", "while (condition) {\n    \n}", "while loop"),
        Snippet("switch", "switch (value) {\n    \n}", "switch"),
        Snippet("struct", "struct Name {\n    \n};", "struct"),
        Snippet("enum", "enum Name {\n    \n};", "enum"),
    )
    private val CSHARP_SNIPPETS = listOf(
        Snippet("class", "class Name {\n    \n}", "class"),
        Snippet("interface", "interface Name {\n    \n}", "interface"),
        Snippet("method", "void Name() {\n    \n}", "method"),
        Snippet("property", "public Type Name { get; set; }", "property"),
        Snippet("for", "for (int i = 0; i < ; i++) {\n    \n}", "for loop"),
        Snippet("foreach", "foreach (var item in collection) {\n    \n}", "foreach loop"),
        Snippet("if", "if (condition) {\n    \n}", "if statement"),
        Snippet("try", "try {\n    \n} catch (Exception e) {\n    \n}", "try/catch"),
    )
    private val GO_SNIPPETS = listOf(
        Snippet("func", "func name() {\n    \n}", "function"),
        Snippet("struct", "type Name struct {\n    \n}", "struct"),
        Snippet("interface", "type Name interface {\n    \n}", "interface"),
        Snippet("for", "for i := 0; i < ; i++ {\n    \n}", "for loop"),
        Snippet("if", "if condition {\n    \n}", "if statement"),
        Snippet("switch", "switch value {\n    \n}", "switch"),
        Snippet("defer", "defer func() {\n    \n}()", "defer"),
    )
    private val RUST_SNIPPETS = listOf(
        Snippet("fn", "fn name() {\n    \n}", "function"),
        Snippet("struct", "struct Name {\n    \n}", "struct"),
        Snippet("enum", "enum Name {\n    \n}", "enum"),
        Snippet("impl", "impl Type {\n    \n}", "impl block"),
        Snippet("trait", "trait Name {\n    \n}", "trait"),
        Snippet("for", "for item in items {\n    \n}", "for loop"),
        Snippet("while", "while condition {\n    \n}", "while loop"),
        Snippet("match", "match value {\n    \n}", "match"),
        Snippet("if", "if condition {\n    \n}", "if statement"),
    )
    private val DART_SNIPPETS = listOf(
        Snippet("class", "class Name {\n    \n}", "class"),
        Snippet("widget", "class Name extends StatelessWidget {\n  @override\n  Widget build(BuildContext context) {\n    return Container();\n  }\n}", "stateless widget"),
        Snippet("stful", "class Name extends StatefulWidget {\n  @override\n  State<Name> createState() => _NameState();\n}\n\nclass _NameState extends State<Name> {\n  @override\n  Widget build(BuildContext context) {\n    return Container();\n  }\n}", "stateful widget"),
        Snippet("for", "for (var i = 0; i < ; i++) {\n    \n}", "for loop"),
        Snippet("if", "if (condition) {\n    \n}", "if statement"),
    )
    private val SWIFT_SNIPPETS = listOf(
        Snippet("func", "func name() {\n    \n}", "function"),
        Snippet("class", "class Name {\n    \n}", "class"),
        Snippet("struct", "struct Name {\n    \n}", "struct"),
        Snippet("enum", "enum Name {\n    \n}", "enum"),
        Snippet("protocol", "protocol Name {\n    \n}", "protocol"),
        Snippet("for", "for item in items {\n    \n}", "for loop"),
        Snippet("if", "if condition {\n    \n}", "if statement"),
        Snippet("guard", "guard condition else {\n    \n}", "guard"),
    )
    private val PHP_SNIPPETS = listOf(
        Snippet("function", "function name() {\n    \n}", "function"),
        Snippet("class", "class Name {\n    \n}", "class"),
        Snippet("if", "if (condition) {\n    \n}", "if statement"),
        Snippet("for", "for ($i = 0; $i < ; $i++) {\n    \n}", "for loop"),
        Snippet("foreach", "foreach ($items as $item) {\n    \n}", "foreach loop"),
        Snippet("while", "while (condition) {\n    \n}", "while loop"),
        Snippet("try", "try {\n    \n} catch (Exception $e) {\n    \n}", "try/catch"),
    )
    private val RUBY_SNIPPETS = listOf(
        Snippet("def", "def name\n    \nend", "method"),
        Snippet("class", "class Name\n    \nend", "class"),
        Snippet("module", "module Name\n    \nend", "module"),
        Snippet("if", "if condition\n    \nend", "if statement"),
        Snippet("while", "while condition\n    \nend", "while loop"),
        Snippet("do", "do |item|\n    \nend", "block"),
        Snippet("case", "case value\nwhen \nend", "case statement"),
    )
    private val LUA_SNIPPETS = listOf(
        Snippet("function", "function name()\n    \nend", "function"),
        Snippet("for", "for i = 1, 10 do\n    \nend", "for loop"),
        Snippet("while", "while condition do\n    \nend", "while loop"),
        Snippet("if", "if condition then\n    \nend", "if statement"),
        Snippet("repeat", "repeat\n    \nuntil condition", "repeat/until"),
    )
    private val SHELL_SNIPPETS = listOf(
        Snippet("if", "if [ condition ]; then\n    \nfi", "if statement"),
        Snippet("for", "for i in ; do\n    \ndone", "for loop"),
        Snippet("while", "while condition; do\n    \ndone", "while loop"),
        Snippet("case", "case value in\n    pattern)\n        ;;\nesac", "case"),
        Snippet("func", "name() {\n    \n}", "function"),
    )

    /**
     * Computes the start offset of the prefix the completion should
     * replace when inserted at [caretOffset]. Used by the editor's
     * insert path: the [Candidate.insert] text replaces the range
     * [prefixStart, caretOffset).
     */
    fun prefixStart(text: String, caretOffset: Int): Int {
        var i = caretOffset - 1
        while (i >= 0 && (text[i].isLetterOrDigit() || text[i] == '_')) i--
        return i + 1
    }

    /**
     * Mirrors the [EditorTab] type alias used by the data model so
     * callers from outside `data` don't have to import the model.
     * (EditorTab has a `language: Language` field that drives the
     * per-language keyword/snippet tables above.)
     */
    @JvmName("suggest_for_tab")
    fun suggestForTab(
        text: String,
        caretOffset: Int,
        tab: EditorTab,
        maxResults: Int = 12,
    ): List<Candidate> = suggest(text, caretOffset, tab.language, maxResults)
}
