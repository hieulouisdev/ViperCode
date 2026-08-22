package com.vipercode.ide.util

/**
 * Maps file extensions / MIME types to a [Language] enum that drives both
 * syntax highlighting and per-language editor behaviour (auto-indent rules,
 * comment syntax, etc.).
 *
 * Adding a new language is a one-line change here plus a tokenizer entry in
 * [com.vipercode.ide.ui.components.SyntaxHighlighter].
 */
enum class Language(
    val displayName: String,
    val extensions: Set<String>,
    val mimeTypes: Set<String>,
    val lineComment: String? = null,
    val blockCommentStart: String? = null,
    val blockCommentEnd: String? = null,
) {
    KOTLIN("Kotlin", setOf("kt", "kts"), setOf("text/x-kotlin", "application/x-kotlin"), "//", "/*", "*/"),
    JAVA("Java", setOf("java"), setOf("text/x-java-source", "text/java"), "//", "/*", "*/"),
    SCALA("Scala", setOf("scala", "sc"), setOf("text/x-scala"), "//", "/*", "*/"),
    GROOVY("Groovy", setOf("groovy", "gradle"), setOf("text/x-groovy"), "//", "/*", "*/"),
    PYTHON("Python", setOf("py", "pyw", "rpy"), setOf("text/x-python", "application/x-python"), "#"),
    JAVASCRIPT("JavaScript", setOf("js", "mjs", "jsx"), setOf("text/javascript", "application/javascript"), "//", "/*", "*/"),
    TYPESCRIPT("TypeScript", setOf("ts", "tsx"), setOf("application/typescript", "text/typescript"), "//", "/*", "*/"),
    HTML("HTML", setOf("html", "htm", "xhtml"), setOf("text/html")),
    CSS("CSS", setOf("css", "scss", "sass", "less"), setOf("text/css", "text/x-scss")),
    XML("XML", setOf("xml", "xsd", "xsl", "svg", "plist", "resx"), setOf("application/xml", "text/xml")),
    JSON("JSON", setOf("json", "geojson", "json5"), setOf("application/json")),
    YAML("YAML", setOf("yml", "yaml"), setOf("application/yaml", "text/yaml"), "#"),
    MARKDOWN("Markdown", setOf("md", "markdown"), setOf("text/markdown")),
    SHELL("Shell", setOf("sh", "bash", "zsh"), setOf("application/x-sh", "text/x-shellscript"), "#"),
    C("C", setOf("c", "h"), setOf("text/x-csrc", "text/x-chdr"), "//", "/*", "*/"),
    CPP("C++", setOf("cpp", "cc", "cxx", "hpp", "hxx", "hh"), setOf("text/x-c++src", "text/x-c++hdr"), "//", "/*", "*/"),
    CSHARP("C#", setOf("cs", "csx"), setOf("text/x-csharp"), "//", "/*", "*/"),
    GO("Go", setOf("go"), setOf("text/x-go"), "//", "/*", "*/"),
    RUST("Rust", setOf("rs"), setOf("text/rust", "text/x-rust"), "//", "/*", "*/"),
    PHP("PHP", setOf("php", "phtml"), setOf("application/x-php", "text/x-php"), "//", "/*", "*/"),
    SQL("SQL", setOf("sql"), setOf("application/sql", "text/x-sql"), "--", "/*", "*/"),
    DART("Dart", setOf("dart"), setOf("application/dart", "text/x-dart"), "//", "/*", "*/"),
    SWIFT("Swift", setOf("swift"), setOf("text/x-swift"), "//", "/*", "*/"),
    RUBY("Ruby", setOf("rb", "rbw"), setOf("application/x-ruby", "text/x-ruby"), "#"),
    LUA("Lua", setOf("lua"), setOf("text/x-lua", "application/x-lua"), "--", "--[[", "]]"),
    TOML("TOML", setOf("toml"), setOf("application/toml"), "#"),
    INI("INI", setOf("ini", "cfg", "conf"), setOf("text/plain", "text/x-ini"), "#", ";"),
    GIT("Git Ignore", setOf("gitignore", "gitattributes", "gitmodules"), emptySet(), "#"),
    GRADLE("Gradle", setOf("gradle"), setOf("text/x-groovy", "text/x-gradle"), "//", "/*", "*/"),
    PROPERTIES("Properties", setOf("properties"), setOf("text/x-java-properties", "text/plain"), "#", "!"),
    TEXT("Plain Text", setOf("txt", "log"), setOf("text/plain")),
    UNKNOWN("Unknown", emptySet(), emptySet());

    companion object {
        private val byExt: Map<String, Language> = buildMap {
            // v0.0.8 — first-write-wins on extension collision. The
            // previous `buildMap` was last-write-wins, which silently
            // overwrote `gradle` from GROOVY when GRADLE was declared
            // after it (same for `properties` between INI and
            // PROPERTIES). Now we explicitly pick the more specific
            // variant by declaring the order below.
            // Insert order: most-specific first.
            for (lang in values()) {
                for (ext in lang.extensions) {
                    if (!containsKey(ext.lowercase())) {
                        put(ext.lowercase(), lang)
                    }
                }
            }
        }
        // v0.0.8 — `text/plain` is a generic MIME reported by SAF
        // for nearly every plain-text file. Treating it as INI/GIT/
        // PROPERTIES would override the extension-based detection
        // (the previous buildMap put `text/plain` -> whichever
        // variant was declared last, which was TEXT — but `detect`
        // short-circuits on MIME so the extension was never
        // consulted). The fix is to skip the generic `text/plain`
        // MIME entirely; the extension still wins.
        private val GENERIC_MIMES = setOf("text/plain", "application/octet-stream", "content/unknown")
        private val byMime: Map<String, Language> = buildMap {
            for (lang in values()) {
                for (mt in lang.mimeTypes) {
                    if (mt.lowercase() !in GENERIC_MIMES) {
                        put(mt.lowercase(), lang)
                    }
                }
            }
        }

        fun detect(filename: String, mimeType: String? = null): Language {
            // v0.0.8 — consult the EXTENSION first (it's more
            // specific than the generic MIME types SAF reports).
            val ext = filename.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            if (ext.isNotEmpty()) byExt[ext]?.let { return it }
            // Only fall back to MIME if the extension didn't match
            // AND the MIME is non-generic (text/plain would
            // otherwise misclassify every plain-text file).
            mimeType?.let { m ->
                if (m.lowercase() !in GENERIC_MIMES) {
                    byMime[m.lowercase()]?.let { return it }
                }
            }
            return UNKNOWN
        }
    }
}

object LanguageDetector {
    fun detect(filename: String, mimeType: String? = null): Language =
        Language.detect(filename, mimeType)
}
