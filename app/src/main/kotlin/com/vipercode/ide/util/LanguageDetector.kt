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
    PYTHON("Python", setOf("py", "pyw", "rpy", "pyi"), setOf("text/x-python", "application/x-python"), "#"),
    JAVASCRIPT("JavaScript", setOf("js", "mjs", "jsx"), setOf("text/javascript", "application/javascript"), "//", "/*", "*/"),
    TYPESCRIPT("TypeScript", setOf("ts", "tsx", "mts", "cts"), setOf("application/typescript", "text/typescript"), "//", "/*", "*/"),
    HTML("HTML", setOf("html", "htm", "xhtml", "jhtml"), setOf("text/html")),
    CSS("CSS", setOf("css", "scss", "sass", "less", "styl"), setOf("text/css", "text/x-scss", "text/x-less")),
    XML("XML", setOf("xml", "xsd", "xsl", "svg", "plist", "resx", "rss", "atom", "csproj", "vbproj", "fsproj"), setOf("application/xml", "text/xml")),
    JSON("JSON", setOf("json", "geojson", "json5", "jsonc", "topojson"), setOf("application/json")),
    YAML("YAML", setOf("yml", "yaml"), setOf("application/yaml", "text/yaml"), "#"),
    MARKDOWN("Markdown", setOf("md", "markdown", "mdx"), setOf("text/markdown")),
    SHELL("Shell", setOf("sh", "bash", "zsh", "fish", "ksh"), setOf("application/x-sh", "text/x-shellscript"), "#"),
    C("C", setOf("c", "h"), setOf("text/x-csrc", "text/x-chdr"), "//", "/*", "*/"),
    CPP("C++", setOf("cpp", "cc", "cxx", "hpp", "hxx", "hh", "inl"), setOf("text/x-c++src", "text/x-c++hdr"), "//", "/*", "*/"),
    CSHARP("C#", setOf("cs", "csx"), setOf("text/x-csharp"), "//", "/*", "*/"),
    GO("Go", setOf("go"), setOf("text/x-go"), "//", "/*", "*/"),
    RUST("Rust", setOf("rs"), setOf("text/rust", "text/x-rust"), "//", "/*", "*/"),
    PHP("PHP", setOf("php", "phtml", "php3", "php4", "php5", "phar"), setOf("application/x-php", "text/x-php"), "//", "/*", "*/"),
    SQL("SQL", setOf("sql", "psql", "mysql", "sqlite"), setOf("application/sql", "text/x-sql"), "--", "/*", "*/"),
    DART("Dart", setOf("dart"), setOf("application/dart", "text/x-dart"), "//", "/*", "*/"),
    SWIFT("Swift", setOf("swift"), setOf("text/x-swift"), "//", "/*", "*/"),
    RUBY("Ruby", setOf("rb", "rbw", "rake", "gemspec", "ru"), setOf("application/x-ruby", "text/x-ruby"), "#"),
    LUA("Lua", setOf("lua", "wlua"), setOf("text/x-lua", "application/x-lua"), "--", "--[[", "]]"),
    TOML("TOML", setOf("toml"), setOf("application/toml"), "#"),
    INI("INI", setOf("ini", "cfg", "conf", "desktop", "directory"), setOf("text/plain", "text/x-ini"), "#", ";"),
    GIT("Git Ignore", setOf("gitignore", "gitattributes", "gitmodules"), emptySet(), "#"),
    GRADLE("Gradle", setOf("gradle"), setOf("text/x-groovy", "text/x-gradle"), "//", "/*", "*/"),
    PROPERTIES("Properties", setOf("properties"), setOf("text/x-java-properties", "text/plain"), "#", "!"),
    TEXT("Plain Text", setOf("txt", "log", "text", "plain"), setOf("text/plain")),
    // v0.0.9 — SUPER UPDATE: 30+ new languages.
    DOCKERFILE("Dockerfile", setOf("dockerfile", "containerfile"), setOf("text/x-dockerfile"), "#"),
    MAKEFILE("Makefile", setOf("mk", "makefile", "gnumakefile"), setOf("text/x-makefile"), "#"),
    CMAKE("CMake", setOf("cmake"), setOf("text/x-cmake"), "#"),
    R("R", setOf("r", "rmd", "rscript"), setOf("text/x-r"), "#"),
    HASKELL("Haskell", setOf("hs", "lhs", "hsc"), setOf("text/x-haskell"), "--", "{-", "-}"),
    ELIXIR("Elixir", setOf("ex", "exs", "eex", "heex", "leex"), setOf("text/x-elixir"), "#"),
    ERLANG("Erlang", setOf("erl", "hrl"), setOf("text/x-erlang"), "%"),
    CLOJURE("Clojure", setOf("clj", "cljs", "cljc", "edn", "cljd"), setOf("text/x-clojure"), ";"),
    VUE("Vue", setOf("vue"), setOf("text/x-vue"), "//", "/*", "*/"),
    SVELTE("Svelte", setOf("svelte"), setOf("text/x-svelte"), "//", "/*", "*/"),
    SOLIDITY("Solidity", setOf("sol"), setOf("text/x-solidity"), "//", "/*", "*/"),
    GRAPHQL("GraphQL", setOf("graphql", "gql"), setOf("application/graphql"), "#"),
    PROTOBUF("Protobuf", setOf("proto", "protodevel"), setOf("text/x-protobuf"), "//", "/*", "*/"),
    CSV("CSV", setOf("csv", "tsv"), setOf("text/csv", "text/tab-separated-values")),
    LATEX("LaTeX", setOf("tex", "ltx", "sty", "cls"), setOf("application/x-tex", "text/x-tex"), "%"),
    BIBTEX("BibTeX", setOf("bib", "bibtex"), setOf("text/x-bibtex"), "%"),
    ASSEMBLY("Assembly", setOf("asm", "s", "S"), setOf("text/x-asm"), ";", "/*", "*/"),
    VERILOG("Verilog", setOf("v", "vh"), setOf("text/x-verilog"), "//", "/*", "*/"),
    VHDL("VHDL", setOf("vhd", "vhdl"), setOf("text/x-vhdl"), "--"),
    SYSTEMVERILOG("SystemVerilog", setOf("sv", "svh"), setOf("text/x-systemverilog"), "//", "/*", "*/"),
    ADA("Ada", setOf("adb", "ads", "ada"), setOf("text/x-ada"), "--"),
    FORTRAN("Fortran", setOf("f", "f90", "f95", "f03", "f08", "for", "ftn"), setOf("text/x-fortran"), "!"),
    COBOL("COBOL", setOf("cbl", "cob", "cpy"), setOf("text/x-cobol"), "*"),
    PASCAL("Pascal", setOf("pas", "pp", "dpr"), setOf("text/x-pascal"), "//", "(*", "*)"),
    BASIC("BASIC", setOf("bas", "vb", "vbs"), setOf("text/x-basic"), "'"),
    FSHARP("F#", setOf("fs", "fsx", "fsi"), setOf("text/x-fsharp"), "//", "(*", "*)"),
    OCAML("OCaml", setOf("ml", "mli"), setOf("text/x-ocaml"), "(*", "(*", "*)"),
    CRYSTAL("Crystal", setOf("cr"), setOf("text/x-crystal"), "#"),
    NIM("Nim", setOf("nim", "nims"), setOf("text/x-nim"), "#"),
    ZIG("Zig", setOf("zig"), setOf("text/x-zig"), "//"),
    VLANG("V (Vlang)", setOf("v", "vsh"), setOf("text/x-vlang"), "//"),
    JULIA("Julia", setOf("jl"), setOf("text/x-julia"), "#", "#=", "=#"),
    PERL("Perl", setOf("pl", "pm", "pod"), setOf("text/x-perl"), "#", "=", "=cut"),
    VBNET("VB.NET", setOf("vb", "vbnet"), setOf("text/x-vbnet"), "'"),
    POWERSHELL("PowerShell", setOf("ps1", "psm1", "psd1"), setOf("application/x-powershell"), "#"),
    BATCH("Batch", setOf("bat", "cmd"), setOf("application/x-bat"), "REM"),
    VIM("Vim script", setOf("vim", "vimrc", "gvimrc"), setOf("text/x-vim"), "\""),
    EMACSLISP("Emacs Lisp", setOf("el", "emacs"), setOf("text/x-elisp"), ";"),
    SCHEME("Scheme", setOf("scm", "ss"), setOf("text/x-scheme"), ";", "#|", "|#"),
    COMMONLISP("Common Lisp", setOf("lisp", "lsp", "cl"), setOf("text/x-common-lisp"), ";", "#|", "|#"),
    ASTRO("Astro", setOf("astro"), setOf("text/x-astro"), "//", "/*", "*/"),
    DJANGO("Django Template", setOf("jinja", "j2", "jinja2", "djhtml"), setOf("text/x-django"), "{#", "#}"),
    HAML("HAML", setOf("haml"), setOf("text/x-haml"), "-#"),
    SLIM("Slim", setOf("slim"), setOf("text/x-slim"), "/"),
    PUG("Pug", setOf("pug", "jade"), setOf("text/x-pug"), "//", "//-", ""),
    STYLUS("Stylus", setOf("styl"), setOf("text/x-stylus"), "//"),
    INI_BASHRC("Bashrc", setOf("bashrc", "bash_profile", "profile", "zshrc"), setOf("text/x-shellsrc"), "#"),
    ENVFILE("Env", setOf("env", "dotenv"), setOf("text/plain"), "#"),
    TERRAFORM("Terraform", setOf("tf", "tfvars", "tfstate"), setOf("text/x-terraform"), "#", "/*", "*/"),
    ANSIBLE("Ansible", setOf("yml.ansible"), setOf("text/yaml"), "#"),
    JUPYTER("Jupyter Notebook", setOf("ipynb"), setOf("application/x-ipynb+json")),
    POSTSCRIPT("PostScript", setOf("ps", "eps"), setOf("application/postscript"), "%"),
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
