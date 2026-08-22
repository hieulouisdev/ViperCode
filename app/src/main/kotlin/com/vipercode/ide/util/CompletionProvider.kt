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
        // v0.0.9 — new languages.
        Language.DOCKERFILE -> DOCKERFILE_KEYWORDS
        Language.MAKEFILE -> MAKEFILE_KEYWORDS
        Language.CMAKE -> CMAKE_KEYWORDS
        Language.R -> R_KEYWORDS
        Language.HASKELL -> HASKELL_KEYWORDS
        Language.ELIXIR -> ELIXIR_KEYWORDS
        Language.ERLANG -> ERLANG_KEYWORDS
        Language.CLOJURE -> CLOJURE_KEYWORDS
        Language.VUE, Language.SVELTE, Language.ASTRO -> JS_KEYWORDS
        Language.SOLIDITY -> SOLIDITY_KEYWORDS
        Language.GRAPHQL -> GRAPHQL_KEYWORDS
        Language.PROTOBUF -> PROTOBUF_KEYWORDS
        Language.PASCAL -> PASCAL_KEYWORDS
        Language.FORTRAN -> FORTRAN_KEYWORDS
        Language.COBOL -> COBOL_KEYWORDS
        Language.BASIC -> BASIC_KEYWORDS
        Language.FSHARP -> FSHARP_KEYWORDS
        Language.OCAML -> OCAML_KEYWORDS
        Language.CRYSTAL -> CRYSTAL_KEYWORDS
        Language.NIM -> NIM_KEYWORDS
        Language.ZIG -> ZIG_KEYWORDS
        Language.VLANG -> VLANG_KEYWORDS
        Language.JULIA -> JULIA_KEYWORDS
        Language.PERL -> PERL_KEYWORDS
        Language.VBNET -> VBNET_KEYWORDS
        Language.POWERSHELL -> POWERSHELL_KEYWORDS
        Language.BATCH -> BATCH_KEYWORDS
        Language.VIM -> VIM_KEYWORDS
        Language.EMACSLISP -> EMACSLISP_KEYWORDS
        Language.SCHEME -> SCHEME_KEYWORDS
        Language.COMMONLISP -> COMMONLISP_KEYWORDS
        Language.TERRAFORM -> TERRAFORM_KEYWORDS
        Language.YAML, Language.TOML, Language.INI, Language.INI_BASHRC, Language.PROPERTIES, Language.ENVFILE, Language.GIT -> emptyList()
        Language.HTML, Language.CSS, Language.XML, Language.JSON, Language.MARKDOWN, Language.CSV, Language.JUPYTER -> emptyList()
        Language.LATEX, Language.BIBTEX, Language.POSTSCRIPT -> emptyList()
        Language.ASSEMBLY, Language.VERILOG, Language.VHDL, Language.SYSTEMVERILOG -> emptyList()
        Language.ADA, Language.HAML, Language.SLIM, Language.PUG, Language.STYLUS -> emptyList()
        Language.DJANGO, Language.ANSIBLE -> emptyList()
        Language.TEXT, Language.UNKNOWN -> emptyList()
    }

    // ── Per-language snippet tables ───────────────────────────────
    private data class Snippet(val trigger: String, val body: String, val description: String)

    private fun snippetsFor(language: Language): List<Snippet> = when (language) {
        Language.KOTLIN, Language.SCALA, Language.GROOVY, Language.GRADLE -> JVM_SNIPPETS
        Language.JAVA -> JAVA_SNIPPETS
        Language.PYTHON -> PYTHON_SNIPPETS
        Language.JAVASCRIPT, Language.TYPESCRIPT, Language.VUE, Language.SVELTE, Language.ASTRO -> JS_SNIPPETS
        Language.C, Language.CPP -> C_SNIPPETS
        Language.CSHARP -> CSHARP_SNIPPETS
        Language.GO -> GO_SNIPPETS
        Language.RUST -> RUST_SNIPPETS
        Language.DART -> DART_SNIPPETS
        Language.SWIFT -> SWIFT_SNIPPETS
        Language.PHP -> PHP_SNIPPETS
        Language.RUBY -> RUBY_SNIPPETS
        Language.LUA -> LUA_SNIPPETS
        Language.SHELL, Language.INI_BASHRC -> SHELL_SNIPPETS
        Language.DOCKERFILE -> DOCKERFILE_SNIPPETS
        Language.MAKEFILE, Language.CMAKE -> MAKEFILE_SNIPPETS
        Language.R -> R_SNIPPETS
        Language.HASKELL -> HASKELL_SNIPPETS
        Language.ELIXIR -> ELIXIR_SNIPPETS
        Language.ERLANG -> ERLANG_SNIPPETS
        Language.CLOJURE -> CLOJURE_SNIPPETS
        Language.SOLIDITY -> SOLIDITY_SNIPPETS
        Language.GRAPHQL -> GRAPHQL_SNIPPETS
        Language.PROTOBUF -> PROTOBUF_SNIPPETS
        Language.PASCAL -> PASCAL_SNIPPETS
        Language.FORTRAN -> FORTRAN_SNIPPETS
        Language.COBOL -> COBOL_SNIPPETS
        Language.BASIC, Language.VBNET -> BASIC_SNIPPETS
        Language.FSHARP -> FSHARP_SNIPPETS
        Language.OCAML -> OCAML_SNIPPETS
        Language.CRYSTAL -> CRYSTAL_SNIPPETS
        Language.NIM -> NIM_SNIPPETS
        Language.ZIG -> ZIG_SNIPPETS
        Language.VLANG -> VLANG_SNIPPETS
        Language.JULIA -> JULIA_SNIPPETS
        Language.PERL -> PERL_SNIPPETS
        Language.POWERSHELL, Language.BATCH -> POWERSHELL_SNIPPETS
        Language.VIM -> VIM_SNIPPETS
        Language.EMACSLISP -> EMACSLISP_SNIPPETS
        Language.SCHEME, Language.COMMONLISP -> LISP_SNIPPETS
        Language.TERRAFORM -> TERRAFORM_SNIPPETS
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
        // v0.0.8 — escape '$' as ${'$'} so Kotlin string interpolation
        // doesn't treat PHP variable syntax as a Kotlin variable ref.
        Snippet("for", "for (${'$'}i = 0; ${'$'}i < ; ${'$'}i++) {\n    \n}", "for loop"),
        Snippet("foreach", "foreach (${'$'}items as ${'$'}item) {\n    \n}", "foreach loop"),
        Snippet("while", "while (condition) {\n    \n}", "while loop"),
        Snippet("try", "try {\n    \n} catch (Exception ${'$'}e) {\n    \n}", "try/catch"),
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


    // ── v0.0.9 — new language keyword tables ──────────────────────
    private val DOCKERFILE_KEYWORDS = listOf(
        "FROM", "RUN", "CMD", "LABEL", "MAINTAINER", "EXPOSE", "ENV", "ADD",
        "COPY", "ENTRYPOINT", "VOLUME", "USER", "WORKDIR", "ARG", "ONBUILD",
        "STOPSIGNAL", "HEALTHCHECK", "SHELL", "AS",
    )
    private val MAKEFILE_KEYWORDS = listOf(
        "ifeq", "ifneq", "ifdef", "ifndef", "else", "endif", "include", "define",
        "endef", "export", "unexport", "override", "vpath", "subst", "patsubst",
        "wildcard", "shell", "notdir", "basename", "dir", "suffix", "addprefix",
        "addsuffix", "firstword", "filter", "filter-out", "sort", "word", "words",
        ".PHONY", ".SILENT", ".DEFAULT", ".PRECIOUS", ".INTERMEDIATE",
    )
    private val CMAKE_KEYWORDS = listOf(
        "cmake_minimum_required", "project", "set", "message", "include",
        "find_package", "add_executable", "add_library", "add_subdirectory",
        "target_link_libraries", "target_include_directories", "target_compile_options",
        "if", "elseif", "else", "endif", "foreach", "endforeach", "while", "endwhile",
        "function", "endfunction", "macro", "endmacro", "option", "install",
        "configure_file", "file", "list", "string", "math", "option",
    )
    private val R_KEYWORDS = listOf(
        "if", "else", "for", "while", "function", "return", "break", "next",
        "TRUE", "FALSE", "NULL", "NA", "Inf", "NaN", "in", "repeat", "library",
        "require", "source", "data", "c", "list", "vector", "matrix", "data.frame",
        "factor", "apply", "lapply", "sapply", "vapply", "mapply", "tapply",
        "aggregate", "merge", "subset", "transform", "with", "within", "by",
        "lm", "glm", "summary", "plot", "ggplot", "aes", "geom_point", "geom_line",
    )
    private val HASKELL_KEYWORDS = listOf(
        "case", "class", "data", "default", "deriving", "do", "else", "foreign",
        "if", "import", "in", "infix", "infixl", "infixr", "instance", "let",
        "module", "newtype", "of", "then", "type", "where", "_",
    )
    private val ELIXIR_KEYWORDS = listOf(
        "after", "and", "catch", "case", "cond", "def", "defp", "defmodule",
        "defprotocol", "defimpl", "defmacro", "defmacrop", "defstruct", "defguard",
        "defguardp", "do", "else", "end", "fn", "for", "if", "in", "import",
        "not", "or", "quote", "raise", "receive", "require", "rescue", "return",
        "throw", "try", "unless", "unquote", "use", "when", "while", "with",
    )
    private val ERLANG_KEYWORDS = listOf(
        "after", "and", "andalso", "band", "begin", "bnot", "bor", "bsl",
        "bsr", "bxor", "case", "catch", "cond", "div", "end", "fun", "if",
        "let", "not", "of", "or", "orelse", "query", "receive", "rem", "try",
        "xor", "when", "module", "export", "import", "record", "behaviour",
    )
    private val CLOJURE_KEYWORDS = listOf(
        "def", "defn", "defn-", "defmacro", "defmulti", "defmethod", "defprotocol",
        "defrecord", "deftype", "fn", "let", "letfn", "loop", "recur", "if",
        "when", "when-not", "when-let", "case", "cond", "condp", "cond->",
        "cond->>", "do", "while", "doseq", "doall", "dorun", "->", "->>",
        "and", "or", "not", "import", "require", "use", "ns", "in-ns",
    )
    private val SOLIDITY_KEYWORDS = listOf(
        "pragma", "contract", "interface", "library", "function", "modifier",
        "constructor", "fallback", "receive", "event", "struct", "enum", "mapping",
        "public", "private", "internal", "external", "pure", "view", "payable",
        "virtual", "override", "returns", "return", "memory", "storage", "calldata",
        "if", "else", "for", "while", "do", "break", "continue", "throw", "try",
        "catch", "import", "using", "is", "new", "delete", "emit", "unchecked",
        "address", "bool", "string", "bytes", "uint", "int", "true", "false",
    )
    private val GRAPHQL_KEYWORDS = listOf(
        "type", "input", "interface", "union", "enum", "scalar", "schema",
        "directive", "extend", "implements", "query", "mutation", "subscription",
        "fragment", "on", "null", "true", "false",
    )
    private val PROTOBUF_KEYWORDS = listOf(
        "syntax", "package", "import", "option", "message", "enum", "service",
        "rpc", "stream", "returns", "reserved", "extensions", "extend", "oneof",
        "map", "repeated", "optional", "required", "packed", "deprecated",
        "int32", "int64", "uint32", "uint64", "sint32", "sint64", "fixed32",
        "fixed64", "sfixed32", "sfixed64", "float", "double", "bool", "string", "bytes",
    )
    private val PASCAL_KEYWORDS = listOf(
        "program", "unit", "interface", "implementation", "initialization",
        "finalization", "uses", "begin", "end", "var", "const", "type",
        "procedure", "function", "class", "record", "object", "array", "set",
        "of", "string", "integer", "boolean", "char", "real", "if", "then",
        "else", "case", "while", "repeat", "until", "for", "to", "downto", "do",
        "with", "try", "except", "finally", "raise", "inherited", "self",
    )
    private val FORTRAN_KEYWORDS = listOf(
        "PROGRAM", "SUBROUTINE", "FUNCTION", "MODULE", "END", "IF", "THEN",
        "ELSE", "ELSEIF", "ENDIF", "DO", "ENDDO", "WHILE", "SELECT", "CASE",
        "DEFAULT", "WHERE", "FORALL", "TYPE", "CLASS", "INTERFACE", "IMPLEMENTATION",
        "INTENT", "PARAMETER", "ALLOCATABLE", "DIMENSION", "OPTIONAL", "POINTER",
        "TARGET", "SAVE", "PUBLIC", "PRIVATE", "INTEGER", "REAL", "COMPLEX",
        "CHARACTER", "LOGICAL", "DOUBLE", "PRECISION", "ALLOCATE", "DEALLOCATE",
        "CALL", "RETURN", "CONTAINS", "USE", "IMPLICIT", "EXPLICIT", "NONE",
    )
    private val COBOL_KEYWORDS = listOf(
        "IDENTIFICATION", "DIVISION", "PROGRAM-ID", "ENVIRONMENT", "CONFIGURATION",
        "INPUT-OUTPUT", "FILE-CONTROL", "DATA", "WORKING-STORAGE", "LINKAGE",
        "PROCEDURE", "DISPLAY", "ACCEPT", "STOP", "RUN", "MOVE", "TO", "ADD",
        "SUBTRACT", "MULTIPLY", "DIVIDE", "COMPUTE", "IF", "ELSE", "END-IF",
        "PERFORM", "UNTIL", "VARYING", "EXIT", "GOBACK", "INITIALIZE", "SET",
        "CALL", "USING", "COPY", "REPLACE", "PICTURE", "PIC", "VALUE", "VALUES",
        "OCCURS", "DEPENDING", "ON", "SIZE", "ERROR", "END-DISPLAY", "END-ACCEPT",
    )
    private val BASIC_KEYWORDS = listOf(
        "Dim", "As", "If", "Then", "Else", "ElseIf", "End", "If", "For", "Each",
        "Next", "To", "Step", "While", "Wend", "Do", "Loop", "Until", "Select",
        "Case", "Sub", "Function", "End", "Sub", "Function", "Class", "Module",
        "Imports", "Option", "Explicit", "Strict", "On", "Off", "Try", "Catch",
        "Finally", "Throw", "New", "Me", "MyBase", "MyClass", "True", "False",
        "Nothing", "And", "Or", "Not", "Xor", "Mod", "Integer", "String", "Boolean",
        "Double", "Object", "Date", "Long", "Short", "Byte", "Char", "Decimal",
    )
    private val FSHARP_KEYWORDS = listOf(
        "abstract", "and", "as", "assert", "base", "begin", "class", "default",
        "delegate", "do", "done", "downcast", "downto", "elif", "else", "end",
        "exception", "extern", "false", "finally", "fixed", "for", "fun", "function",
        "global", "if", "in", "inherit", "inline", "interface", "internal", "lazy",
        "let", "let!", "match", "match!", "member", "module", "mutable", "namespace",
        "new", "not", "null", "of", "open", "or", "override", "private", "public",
        "rec", "return", "return!", "sig", "static", "struct", "then", "to",
        "true", "try", "type", "upcast", "use", "use!", "val", "void", "when",
        "while", "with", "yield", "yield!",
    )
    private val OCAML_KEYWORDS = listOf(
        "and", "as", "assert", "asr", "begin", "class", "constraint", "do",
        "done", "downto", "else", "end", "exception", "external", "false",
        "for", "fun", "function", "functor", "if", "in", "include", "inherit",
        "initializer", "land", "lazy", "let", "lor", "lsl", "lsr", "lxor",
        "match", "method", "mod", "module", "mutable", "new", "object", "of",
        "open", "or", "private", "rec", "sig", "struct", "then", "to", "true",
        "try", "type", "val", "virtual", "when", "while", "with",
    )
    private val CRYSTAL_KEYWORDS = listOf(
        "abstract", "alias", "as", "asm", "begin", "break", "case", "class",
        "def", "do", "else", "elsif", "end", "ensure", "enum", "extend", "false",
        "for", "fun", "if", "in", "include", "instance_sizeof", "is_a?", "lib",
        "macro", "module", "next", "nil", "of", "out", "pointerof", "private",
        "protected", "public", "raise", "require", "rescue", "responds_to?",
        "return", "select", "self", "sizeof", "struct", "super", "then", "true",
        "type", "typeof", "uninitialized", "union", "unless", "until", "verbatim",
        "when", "while", "with", "yield",
    )
    private val NIM_KEYWORDS = listOf(
        "addr", "and", "as", "asm", "bind", "block", "break", "case", "cast",
        "concept", "const", "continue", "converter", "defer", "discard", "distinct",
        "div", "do", "elif", "else", "end", "enum", "except", "export", "finally",
        "for", "from", "func", "if", "import", "in", "include", "interface",
        "is", "isnot", "iterator", "let", "macro", "method", "mixin", "mod",
        "nil", "not", "notin", "object", "of", "out", "proc", "ptr", "raise",
        "ref", "return", "shl", "shr", "static", "template", "try", "tuple",
        "type", "using", "var", "when", "while", "xor", "yield",
    )
    private val ZIG_KEYWORDS = listOf(
        "addrspace", "align", "allowzero", "and", "asm", "async", "await",
        "break", "callconv", "catch", "comptime", "const", "continue", "defer",
        "else", "enum", "errdefer", "error", "export", "extern", "fn", "for",
        "if", "inline", "noalias", "nosuspend", "opaque", "or", "orelse",
        "packed", "pub", "resume", "return", "struct", "suspend", "switch",
        "test", "threadlocal", "try", "type", "union", "unreachable", "var",
        "volatile", "while", "undefined", "null", "true", "false",
    )
    private val VLANG_KEYWORDS = listOf(
        "as", "asm", "assert", "atomic", "break", "const", "continue", "defer",
        "else", "enum", "fn", "for", "go", "goto", "if", "import", "in", "interface",
        "is", "lock", "match", "module", "mut", "or", "pub", "return", "rlock",
        "select", "shared", "static", "struct", "type", "typeof", "union", "unsafe",
        "true", "false", "nil",
    )
    private val JULIA_KEYWORDS = listOf(
        "baremodule", "begin", "break", "catch", "const", "continue", "do",
        "else", "elseif", "end", "export", "false", "finally", "for", "function",
        "global", "if", "import", "in", "isa", "let", "local", "macro", "module",
        "mutable", "primitive", "quote", "return", "struct", "true", "try",
        "type", "using", "where", "while", "abstract", "type", "mutable struct",
    )
    private val PERL_KEYWORDS = listOf(
        "if", "elsif", "else", "unless", "while", "until", "for", "foreach",
        "do", "last", "next", "redo", "return", "goto", "sub", "my", "our",
        "local", "use", "no", "require", "package", "BEGIN", "END", "defined",
        "undef", "exists", "delete", "scalar", "wantarray", "ref", "bless",
        "tie", "untie", "tied", "print", "printf", "say", "open", "close",
        "read", "write", "seek", "tell", "chomp", "chop", "split", "join",
        "shift", "unshift", "push", "pop", "sort", "reverse", "map", "grep",
    )
    private val VBNET_KEYWORDS = BASIC_KEYWORDS
    private val POWERSHELL_KEYWORDS = listOf(
        "Begin", "Break", "Catch", "Class", "Continue", "Data", "Define", "Do",
        "DynamicParam", "Else", "Elseif", "End", "Exit", "Filter", "Finally",
        "For", "ForEach", "From", "Function", "If", "In", "Param", "Process",
        "Return", "Switch", "Throw", "Trap", "Try", "Until", "Using", "Var",
        "While", "With", "Yield", "Write-Output", "Write-Host", "Write-Error",
        "Get-Content", "Set-Content", "Test-Path", "New-Item", "Remove-Item",
    )
    private val BATCH_KEYWORDS = listOf(
        "REM", "ECHO", "SET", "IF", "ELSE", "FOR", "DO", "GOTO", "LABEL",
        "CALL", "EXIT", "START", "PAUSE", "PUSHD", "POPD", "SHIFT", "TITLE",
        "COLOR", "PROMPT", "VER", "VOL", "DIR", "CD", "MD", "RD", "DEL", "COPY",
        "MOVE", "REN", "TYPE", "FIND", "FINDSTR", "SORT", "MORE", "TREE",
    )
    private val VIM_KEYWORDS = listOf(
        "let", "set", "setlocal", "setglobal", "unlet", "if", "elseif", "else",
        "endif", "while", "endwhile", "for", "endfor", "function", "endfunction",
        "return", "try", "catch", "finally", "endtry", "throw", "autocmd",
        "augroup", "command", "map", "nmap", "imap", "vmap", "noremap", "nnoremap",
        "inoremap", "vnoremap", "nmap", "echo", "echomsg", "execute", "normal",
        "call", "syntax", "highlight", "filetype", "plugin", "indent",
    )
    private val EMACSLISP_KEYWORDS = listOf(
        "defun", "defmacro", "defvar", "defcustom", "defconst", "defsubst",
        "defadvice", "lambda", "let", "let*", "letrec", "if", "when", "unless",
        "cond", "case", "while", "dolist", "dotimes", " progn", "save-excursion",
        "save-window-excursion", "save-restriction", "with-current-buffer",
        "with-temp-buffer", "setq", "setq-default", "set", "put", "get",
        "interactive", "require", "provide", "autoload", "load", "load-file",
        "message", "format", "concat", "substring", "length", "list", "cons",
        "car", "cdr", "nth", "assoc", "rassoc", "mapcar", "mapc", "mapconcat",
        "add-to-list", "add-hook", "remove-hook", "run-hooks", "define-key",
        "global-set-key", "local-set-key", "kbd",
    )
    private val SCHEME_KEYWORDS = listOf(
        "define", "lambda", "let", "let*", "letrec", "if", "cond", "case",
        "when", "unless", "do", "quote", "quasiquote", "unquote", "set!",
        "begin", "and", "or", "not", "define-syntax", "let-syntax", "syntax-rules",
        "car", "cdr", "cons", "list", "null?", "pair?", "eq?", "eqv?", "equal?",
        "map", "for-each", "filter", "reduce", "apply", "eval", "load",
    )
    private val COMMONLISP_KEYWORDS = SCHEME_KEYWORDS + listOf(
        "defmacro", "defun", "defvar", "defparameter", "defconstant", "defstruct",
        "defclass", "defmethod", "defgeneric", "make-instance", "print-object",
        "loop", "with", "for", "in", "across", "collect", "sum", "count",
        "minimize", "maximize", "finally", "do", "while", "until", "when",
        "unless", "if",
    )
    private val TERRAFORM_KEYWORDS = listOf(
        "resource", "data", "variable", "output", "locals", "module", "provider",
        "terraform", "required_version", "required_providers", "backend", "cloud",
        "depends_on", "count", "for_each", "lifecycle", "create_before_destroy",
        "prevent_destroy", "ignore_changes", "source", "version", "alias", "default",
        "type", "description", "sensitive", "validation", "condition", "error_message",
    )

    // ── v0.0.9 — new language snippet tables ──────────────────────
    private val DOCKERFILE_SNIPPETS = listOf(
        Snippet("FROM", "FROM ${'$'}{1:alpine}", "base image"),
        Snippet("RUN", "RUN ${'$'}{1:cmd}", "run command"),
        Snippet("COPY", "COPY ${'$'}{1:src} ${'$'}{2:dest}", "copy file"),
        Snippet("WORKDIR", "WORKDIR ${'$'}{1:/app}", "set workdir"),
        Snippet("EXPOSE", "EXPOSE ${'$'}{1:8080}", "expose port"),
        Snippet("CMD", "CMD [\"${'$'}{1:cmd}\"]", "default command"),
        Snippet("ENTRYPOINT", "ENTRYPOINT [\"${'$'}{1:cmd}\"]", "entrypoint"),
        Snippet("ENV", "ENV ${'$'}{1:KEY}=${'$'}{2:value}", "env var"),
        Snippet("ARG", "ARG ${'$'}{1:NAME}=${'$'}{2:default}", "build arg"),
    )
    private val MAKEFILE_SNIPPETS = listOf(
        Snippet("target", "${'$'}{1:target}:\n        ${'$'}{2:cmd}", "make target"),
        Snippet("var", "${'$'}{1:VAR}=${'$'}{2:value}", "variable"),
        Snippet("ifeq", "ifeq (${'$'}{1:X}, ${'$'}{2:Y})\n        \nelse\n        \nendif", "if equal"),
        Snippet("foreach", "$(foreach ${'$'}{1:var},${'$'}{2:list},${'$'}{3:expr})", "foreach"),
    )
    private val R_SNIPPETS = listOf(
        Snippet("function", "${'$'}{1:name} <- function(${'$'}{2:args}) {\n    ${'$'}{3:body}\n}", "function"),
        Snippet("if", "if (${'$'}{1:cond}) {\n    ${'$'}{2:body}\n}", "if statement"),
        Snippet("for", "for (${'$'}{1:var} in ${'$'}{2:iter}) {\n    ${'$'}{3:body}\n}", "for loop"),
        Snippet("library", "library(${'$'}{1:pkg})", "load library"),
    )
    private val HASKELL_SNIPPETS = listOf(
        Snippet("module", "module ${'$'}{1:Name} where\n", "module"),
        Snippet("func", "${'$'}{1:name} :: ${'$'}{2:Type}\n${'$'}{1:name} ${'$'}{3:args} = ${'$'}{4:body}", "function with type"),
        Snippet("data", "data ${'$'}{1:Name} = ${'$'}{2:Constructor}", "data type"),
        Snippet("class", "class ${'$'}{1:Name} ${'$'}{2:a} where\n    ${'$'}{3:method}", "type class"),
        Snippet("do", "do\n    ${'$'}{1:body}", "do block"),
    )
    private val ELIXIR_SNIPPETS = listOf(
        Snippet("def", "def ${'$'}{1:name}(${'$'}{2:args}) do\n    ${'$'}{3:body}\nend", "public function"),
        Snippet("defp", "defp ${'$'}{1:name}(${'$'}{2:args}) do\n    ${'$'}{3:body}\nend", "private function"),
        Snippet("defmodule", "defmodule ${'$'}{1:Name} do\n    ${'$'}{2:body}\nend", "module"),
        Snippet("defstruct", "defstruct ${'$'}{1:fields}", "struct"),
        Snippet("case", "case ${'$'}{1:value} do\n    ${'$'}{2:pattern} -> ${'$'}{3:result}\nend", "case"),
    )
    private val ERLANG_SNIPPETS = listOf(
        Snippet("module", "-module(${'$'}{1:name}).", "module declaration"),
        Snippet("export", "-export([${'$'}{1:funcs}]).", "export"),
        Snippet("function", "${'$'}{1:name}(${'$'}{2:args}) ->\n    ${'$'}{3:body}.", "function"),
        Snippet("case", "case ${'$'}{1:expr} of\n    ${'$'}{2:pattern} -> ${'$'}{3:body}\nend.", "case"),
    )
    private val CLOJURE_SNIPPETS = listOf(
        Snippet("defn", "(defn ${'$'}{1:name} [${'$'}{2:args}]\n  ${'$'}{3:body})", "function"),
        Snippet("def", "(def ${'$'}{1:name} ${'$'}{2:value})", "definition"),
        Snippet("let", "(let [${'$'}{1:binding} ${'$'}{2:value}]\n  ${'$'}{3:body})", "let binding"),
        Snippet("if", "(if ${'$'}{1:cond}\n  ${'$'}{2:then}\n  ${'$'}{3:else})", "if"),
        Snippet("fn", "(fn [${'$'}{1:args}] ${'$'}{2:body})", "anonymous fn"),
    )
    private val SOLIDITY_SNIPPETS = listOf(
        Snippet("contract", "contract ${'$'}{1:Name} {\n    ${'$'}{2:body}\n}", "contract"),
        Snippet("function", "function ${'$'}{1:name}(${'$'}{2:args}) public ${'$'}{3:returns} {\n    ${'$'}{4:body}\n}", "function"),
        Snippet("event", "event ${'$'}{1:Name}(${'$'}{2:args});", "event"),
        Snippet("modifier", "modifier ${'$'}{1:name}(${'$'}{2:args}) {\n    _;\n}", "modifier"),
        Snippet("mapping", "mapping(address => uint256) public ${'$'}{1:name};", "mapping"),
    )
    private val GRAPHQL_SNIPPETS = listOf(
        Snippet("type", "type ${'$'}{1:Name} {\n    ${'$'}{2:field}\n}", "type"),
        Snippet("input", "input ${'$'}{1:Name} {\n    ${'$'}{2:field}\n}", "input type"),
        Snippet("query", "query ${'$'}{1:name} {\n    ${'$'}{2:field}\n}", "query"),
        Snippet("mutation", "mutation ${'$'}{1:name} {\n    ${'$'}{2:field}\n}", "mutation"),
        Snippet("interface", "interface ${'$'}{1:Name} {\n    ${'$'}{2:field}\n}", "interface"),
    )
    private val PROTOBUF_SNIPPETS = listOf(
        Snippet("message", "message ${'$'}{1:Name} {\n    ${'$'}{2:field}\n}", "message"),
        Snippet("service", "service ${'$'}{1:Name} {\n    rpc ${'$'}{2:method}(${'$'}{3:req}) returns (${'$'}{4:res});\n}", "service"),
        Snippet("enum", "enum ${'$'}{1:Name} {\n    ${'$'}{2:VALUE} = 0;\n}", "enum"),
    )
    private val PASCAL_SNIPPETS = listOf(
        Snippet("program", "program ${'$'}{1:Name};\nbegin\n    \nend.", "program"),
        Snippet("procedure", "procedure ${'$'}{1:name}(${'$'}{2:args});\nbegin\n    \nend;", "procedure"),
        Snippet("function", "function ${'$'}{1:name}(${'$'}{2:args}): ${'$'}{3:Type};\nbegin\n    \nend;", "function"),
        Snippet("if", "if ${'$'}{1:cond} then\nbegin\n    \nend;", "if"),
        Snippet("for", "for ${'$'}{1:i} := 0 to ${'$'}{2:n} do\nbegin\n    \nend;", "for loop"),
    )
    private val FORTRAN_SNIPPETS = listOf(
        Snippet("program", "PROGRAM ${'$'}{1:NAME}\n    \nEND PROGRAM ${'$'}{1:NAME}", "program"),
        Snippet("subroutine", "SUBROUTINE ${'$'}{1:name}(${'$'}{2:args})\n    \nEND SUBROUTINE ${'$'}{1:name}", "subroutine"),
        Snippet("function", "FUNCTION ${'$'}{1:name}(${'$'}{2:args}) RESULT(${'$'}{3:r})\n    \nEND FUNCTION ${'$'}{1:name}", "function"),
        Snippet("if", "IF (${'$'}{1:cond}) THEN\n    \nEND IF", "if-then"),
        Snippet("do", "DO ${'$'}{1:i}=1,${'$'}{2:n}\n    \nEND DO", "do loop"),
    )
    private val COBOL_SNIPPETS = listOf(
        Snippet("program", "IDENTIFICATION DIVISION.\nPROGRAM-ID. ${'$'}{1:NAME}.\n\nPROCEDURE DIVISION.\n    \nSTOP RUN.", "program"),
        Snippet("display", "DISPLAY ${'$'}{1:message}.", "display"),
        Snippet("if", "IF ${'$'}{1:cond}\n    \nELSE\n    \nEND-IF.", "if"),
    )
    private val BASIC_SNIPPETS = listOf(
        Snippet("if", "If ${'$'}{1:cond} Then\n    \nEnd If", "if"),
        Snippet("for", "For ${'$'}{1:i} = 0 To ${'$'}{2:n}\n    \nNext ${'$'}{1:i}", "for loop"),
        Snippet("sub", "Sub ${'$'}{1:name}()\n    \nEnd Sub", "sub"),
        Snippet("function", "Function ${'$'}{1:name}() As ${'$'}{2:Type}\n    \nEnd Function", "function"),
    )
    private val FSHARP_SNIPPETS = listOf(
        Snippet("let", "let ${'$'}{1:name} ${'$'}{2:args} = ${'$'}{3:body}", "let binding"),
        Snippet("letf", "let ${'$'}{1:name} (${'$'}{2:args}) =\n    ${'$'}{3:body}", "function"),
        Snippet("type", "type ${'$'}{1:Name} =\n    ", "type"),
        Snippet("match", "match ${'$'}{1:expr} with\n| ${'$'}{2:pattern} -> ${'$'}{3:result}", "match"),
    )
    private val OCAML_SNIPPETS = listOf(
        Snippet("let", "let ${'$'}{1:name} = ${'$'}{2:body}", "let binding"),
        Snippet("letf", "let ${'$'}{1:name} ${'$'}{2:args} = ${'$'}{3:body}", "function"),
        Snippet("match", "match ${'$'}{1:expr} with\n| ${'$'}{2:pattern} -> ${'$'}{3:body}", "match"),
        Snippet("type", "type ${'$'}{1:name} = ${'$'}{2:def}", "type"),
    )
    private val CRYSTAL_SNIPPETS = listOf(
        Snippet("def", "def ${'$'}{1:name}(${'$'}{2:args})\n    ${'$'}{3:body}\nend", "method"),
        Snippet("class", "class ${'$'}{1:Name}\n    \nend", "class"),
        Snippet("if", "if ${'$'}{1:cond}\n    ${'$'}{2:body}\nend", "if"),
        Snippet("do", "do |${'$'}{1:arg}|\n    ${'$'}{2:body}\nend", "block"),
    )
    private val NIM_SNIPPETS = listOf(
        Snippet("proc", "proc ${'$'}{1:name}(${'$'}{2:args}) =\n    ${'$'}{3:body}", "procedure"),
        Snippet("type", "type ${'$'}{1:Name} = object\n    ", "type"),
        Snippet("if", "if ${'$'}{1:cond}:\n    ${'$'}{2:body}", "if"),
        Snippet("for", "for ${'$'}{1:i} in ${'$'}{2:iter}:\n    ${'$'}{3:body}", "for loop"),
    )
    private val ZIG_SNIPPETS = listOf(
        Snippet("fn", "fn ${'$'}{1:name}(${'$'}{2:args}) ${'$'}{3:ReturnType} {\n    ${'$'}{4:body}\n}", "function"),
        Snippet("const", "const ${'$'}{1:name} = ${'$'}{2:value};", "constant"),
        Snippet("if", "if (${'$'}{1:cond}) {\n    ${'$'}{2:body}\n}", "if"),
        Snippet("while", "while (${'$'}{1:cond}) {\n    ${'$'}{2:body}\n}", "while loop"),
    )
    private val VLANG_SNIPPETS = listOf(
        Snippet("fn", "fn ${'$'}{1:name}(${'$'}{2:args}) {\n    ${'$'}{3:body}\n}", "function"),
        Snippet("struct", "struct ${'$'}{1:Name} {\n    \n}", "struct"),
        Snippet("if", "if ${'$'}{1:cond} {\n    ${'$'}{2:body}\n}", "if"),
        Snippet("for", "for ${'$'}{1:i} in ${'$'}{2:iter} {\n    ${'$'}{3:body}\n}", "for loop"),
    )
    private val JULIA_SNIPPETS = listOf(
        Snippet("function", "function ${'$'}{1:name}(${'$'}{2:args})\n    ${'$'}{3:body}\nend", "function"),
        Snippet("struct", "struct ${'$'}{1:Name}\n    \nend", "struct"),
        Snippet("if", "if ${'$'}{1:cond}\n    ${'$'}{2:body}\nend", "if"),
        Snippet("for", "for ${'$'}{1:i} in ${'$'}{2:iter}\n    ${'$'}{3:body}\nend", "for loop"),
    )
    private val PERL_SNIPPETS = listOf(
        Snippet("sub", "sub ${'$'}{1:name} {\n    my (${'$'}{2:args}) = @_;\n    ${'$'}{3:body}\n}", "subroutine"),
        Snippet("if", "if (${'$'}{1:cond}) {\n    ${'$'}{2:body}\n}", "if"),
        Snippet("for", "for my ${'$'}{1:i} (@${'$'}{2:list}) {\n    ${'$'}{3:body}\n}", "for loop"),
        Snippet("while", "while (${'$'}{1:cond}) {\n    ${'$'}{2:body}\n}", "while loop"),
    )
    private val POWERSHELL_SNIPPETS = listOf(
        Snippet("function", "function ${'$'}{1:Name} {\n    ${'$'}{2:body}\n}", "function"),
        Snippet("if", "if (${'$'}{1:cond}) {\n    ${'$'}{2:body}\n}", "if"),
        Snippet("foreach", "foreach (${'$'}{1:item} in ${'$'}{2:coll}) {\n    ${'$'}{3:body}\n}", "foreach loop"),
        Snippet("try", "try {\n    ${'$'}{1:body}\n} catch {\n    ${'$'}{2:err}\n}", "try/catch"),
    )
    private val VIM_SNIPPETS = listOf(
        Snippet("function", "function! ${'$'}{1:Name}()\n    \nendfunction", "function"),
        Snippet("if", "if ${'$'}{1:cond}\n    \nendif", "if"),
        Snippet("map", "nnoremap ${'$'}{1:key} ${'$'}{2:action}", "mapping"),
        Snippet("autocmd", "autocmd ${'$'}{1:event} ${'$'}{2:pattern} ${'$'}{3:cmd}", "autocmd"),
    )
    private val EMACSLISP_SNIPPETS = listOf(
        Snippet("defun", "(defun ${'$'}{1:name} ()\n  ${'$'}{2:body})", "function"),
        Snippet("defvar", "(defvar ${'$'}{1:name} ${'$'}{2:value})", "variable"),
        Snippet("let", "(let (${'$'}{1:bindings})\n  ${'$'}{2:body})", "let binding"),
        Snippet("if", "(if ${'$'}{1:cond}\n    ${'$'}{2:then}\n  ${'$'}{3:else})", "if"),
    )
    private val LISP_SNIPPETS = listOf(
        Snippet("define", "(define (${'$'}{1:name} ${'$'}{2:args})\n  ${'$'}{3:body})", "function"),
        Snippet("let", "(let ((${'$'}{1:var} ${'$'}{2:val}))\n  ${'$'}{3:body})", "let binding"),
        Snippet("if", "(if ${'$'}{1:cond}\n    ${'$'}{2:then}\n    ${'$'}{3:else})", "if"),
        Snippet("lambda", "(lambda (${'$'}{1:args}) ${'$'}{2:body})", "lambda"),
    )
    private val TERRAFORM_SNIPPETS = listOf(
        Snippet("resource", "resource \"${'$'}{1:type}\" \"${'$'}{2:name}\" {\n    ${'$'}{3:body}\n}\n", "resource"),
        Snippet("data", "data \"${'$'}{1:type}\" \"${'$'}{2:name}\" {\n    ${'$'}{3:body}\n}\n", "data source"),
        Snippet("variable", "variable \"${'$'}{1:name}\" {\n    type = ${'$'}{2:string}\n    default = ${'$'}{3:value}\n}\n", "variable"),
        Snippet("output", "output \"${'$'}{1:name}\" {\n    value = ${'$'}{2:expr}\n}\n", "output"),
        Snippet("provider", "provider \"${'$'}{1:name}\" {\n    ${'$'}{2:body}\n}\n", "provider"),
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
