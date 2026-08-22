package com.vipercode.ide.util

/**
 * v0.0.9 — SUPER UPDATE: lightweight symbol extractor.
 *
 * Walks a source file and pulls out top-level (and class-level)
 * declarations — functions, classes, interfaces, structs, enums,
 * imports — using simple per-language regex heuristics.
 *
 * Not a real parser. Trades correctness for the ability to handle
 * 50+ languages without bundling a tree-sitter grammar per language.
 *
 * Used by:
 *  - The Symbol Outline panel in the editor (a new v0.0.9 panel that
 *    lets the user jump to a function/class definition).
 *  - The TODO/FIXME panel (separate utility — see [TodoExtractor]).
 */
object SymbolOutline {

    data class Symbol(
        val name: String,
        val kind: Kind,
        val line: Int,           // 0-indexed line number
        val offset: Int,          // character offset of the line start
    )

    enum class Kind {
        FUNCTION,
        CLASS,
        INTERFACE,
        STRUCT,
        ENUM,
        TRAIT,
        IMPORT,
        CONSTANT,
        VARIABLE,
        NAMESPACE,
        UNKNOWN,
    }

    /**
     * Extracts symbols from [source] for the given [language].
     *
     * Returns an empty list for languages we don't have patterns for
     * yet — the caller can show a "no symbols found" message.
     */
    fun extract(source: String, language: Language): List<Symbol> {
        val patterns = patternsFor(language) ?: return emptyList()
        val out = mutableListOf<Symbol>()
        val lines = source.split('\n')
        for ((lineIdx, lineText) in lines.withIndex()) {
            val lineStart = offsetOfLine(source, lineIdx)
            for ((regex, kind) in patterns) {
                val match = regex.find(lineText) ?: continue
                val name = match.groupValues.getOrNull(1)?.trim() ?: continue
                if (name.isEmpty() || name.length > 200) continue
                // Avoid noise from comments (a single-line `//` is
                // detected by the regex itself since we anchor on
                // line-start).
                out.add(Symbol(name = name, kind = kind, line = lineIdx, offset = lineStart))
                break
            }
        }
        return out
    }

    private fun offsetOfLine(source: String, line: Int): Int {
        if (line <= 0) return 0
        var current = 0
        var idx = 0
        val len = source.length
        while (current < line && idx < len) {
            if (source[idx] == '\n') current++
            idx++
        }
        return idx
    }

    /** Returns a list of (regex, kind) pairs for the given language. */
    private fun patternsFor(language: Language): List<Pair<Regex, Kind>>? = when (language) {
        Language.KOTLIN, Language.SCALA, Language.GROOVY, Language.GRADLE -> listOf(
            Regex("^\\s*(?:private\\s+|public\\s+|internal\\s+|protected\\s+)?(?:open\\s+|abstract\\s+|sealed\\s+|data\\s+|inline\\s+)?class\\s+(\\w+)") to Kind.CLASS,
            Regex("^\\s*(?:private\\s+|public\\s+|internal\\s+|protected\\s+)?(?:open\\s+|abstract\\s+)?interface\\s+(\\w+)") to Kind.INTERFACE,
            Regex("^\\s*(?:private\\s+|public\\s+|internal\\s+|protected\\s+)?(?:open\\s+|abstract\\s+|sealed\\s+)?object\\s+(\\w+)") to Kind.CLASS,
            Regex("^\\s*(?:private\\s+|public\\s+|internal\\s+|protected\\s+)?(?:suspend\\s+|inline\\s+)?fun\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*(?:private\\s+|public\\s+|internal\\s+|protected\\s+)?val\\s+(\\w+)") to Kind.CONSTANT,
            Regex("^\\s*(?:private\\s+|public\\s+|internal\\s+|protected\\s+)?var\\s+(\\w+)") to Kind.VARIABLE,
            Regex("^\\s*import\\s+([\\w.]+)") to Kind.IMPORT,
            Regex("^\\s*package\\s+([\\w.]+)") to Kind.NAMESPACE,
            Regex("^\\s*(?:private\\s+|public\\s+|internal\\s+|protected\\s+)?enum\\s+class\\s+(\\w+)") to Kind.ENUM,
            Regex("^\\s*(?:private\\s+|public\\s+|internal\\s+|protected\\s+)?typealias\\s+(\\w+)") to Kind.CLASS,
        )
        Language.JAVA -> listOf(
            Regex("^\\s*(?:public\\s+|private\\s+|protected\\s+|static\\s+|final\\s+|abstract\\s+)*class\\s+(\\w+)") to Kind.CLASS,
            Regex("^\\s*(?:public\\s+|private\\s+|protected\\s+|static\\s+|final\\s+|abstract\\s+)*interface\\s+(\\w+)") to Kind.INTERFACE,
            Regex("^\\s*(?:public\\s+|private\\s+|protected\\s+|static\\s+|final\\s+|abstract\\s+)*enum\\s+(\\w+)") to Kind.ENUM,
            Regex("^\\s*(?:public\\s+|private\\s+|protected\\s+|static\\s+|final\\s+|abstract\\s+|synchronized\\s+|native\\s+)*[\\w<>\\[\\],\\s]+\\s+(\\w+)\\s*\\(") to Kind.FUNCTION,
            Regex("^\\s*import\\s+([\\w.]+)") to Kind.IMPORT,
            Regex("^\\s*package\\s+([\\w.]+)") to Kind.NAMESPACE,
        )
        Language.PYTHON -> listOf(
            Regex("^\\s*(?:async\\s+)?def\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*class\\s+(\\w+)") to Kind.CLASS,
            Regex("^\\s*import\\s+(\\w+)") to Kind.IMPORT,
            Regex("^\\s*from\\s+(\\w[\\w.]*)\\s+import") to Kind.IMPORT,
            Regex("^(\\w+)\\s*=\\s*") to Kind.VARIABLE,
        )
        Language.JAVASCRIPT, Language.TYPESCRIPT, Language.VUE, Language.SVELTE, Language.ASTRO -> listOf(
            Regex("^\\s*(?:export\\s+)?(?:default\\s+)?function\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*(?:export\\s+)?(?:default\\s+)?class\\s+(\\w+)") to Kind.CLASS,
            Regex("^\\s*(?:export\\s+)?interface\\s+(\\w+)") to Kind.INTERFACE,
            Regex("^\\s*(?:export\\s+)?enum\\s+(\\w+)") to Kind.ENUM,
            Regex("^\\s*(?:export\\s+)?const\\s+(\\w+)") to Kind.CONSTANT,
            Regex("^\\s*(?:export\\s+)?let\\s+(\\w+)") to Kind.VARIABLE,
            Regex("^\\s*(?:export\\s+)?var\\s+(\\w+)") to Kind.VARIABLE,
            Regex("^\\s*import\\s+.+from\\s+['\"]([^'\"]+)['\"]") to Kind.IMPORT,
            Regex("^\\s*(?:export\\s+)?type\\s+(\\w+)") to Kind.CLASS,
        )
        Language.GO -> listOf(
            Regex("^\\s*func\\s+(?:\\([^)]*\\)\\s*)?(\\w+)\\s*\\(") to Kind.FUNCTION,
            Regex("^\\s*type\\s+(\\w+)\\s+struct") to Kind.STRUCT,
            Regex("^\\s*type\\s+(\\w+)\\s+interface") to Kind.INTERFACE,
            Regex("^\\s*type\\s+(\\w+)\\s+") to Kind.CLASS,
            Regex("^\\s*import\\s+\"([^\"]+)\"") to Kind.IMPORT,
            Regex("^\\s*package\\s+(\\w+)") to Kind.NAMESPACE,
            Regex("^\\s*const\\s+(\\w+)") to Kind.CONSTANT,
            Regex("^\\s*var\\s+(\\w+)") to Kind.VARIABLE,
        )
        Language.RUST -> listOf(
            Regex("^\\s*fn\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*pub\\s+struct\\s+(\\w+)") to Kind.STRUCT,
            Regex("^\\s*struct\\s+(\\w+)") to Kind.STRUCT,
            Regex("^\\s*pub\\s+enum\\s+(\\w+)") to Kind.ENUM,
            Regex("^\\s*enum\\s+(\\w+)") to Kind.ENUM,
            Regex("^\\s*pub\\s+trait\\s+(\\w+)") to Kind.TRAIT,
            Regex("^\\s*trait\\s+(\\w+)") to Kind.TRAIT,
            Regex("^\\s*pub\\s+mod\\s+(\\w+)") to Kind.NAMESPACE,
            Regex("^\\s*mod\\s+(\\w+)") to Kind.NAMESPACE,
            Regex("^\\s*use\\s+([\\w:]+)") to Kind.IMPORT,
            Regex("^\\s*pub\\s+const\\s+(\\w+)") to Kind.CONSTANT,
            Regex("^\\s*const\\s+(\\w+)") to Kind.CONSTANT,
            Regex("^\\s*pub\\s+static\\s+(\\w+)") to Kind.CONSTANT,
        )
        Language.C, Language.CPP -> listOf(
            Regex("^\\s*(?:[\\w]+\\s+)*(\\w+)\\s*\\(") to Kind.FUNCTION,
            Regex("^\\s*(?:typedef\\s+)?struct\\s+(\\w+)") to Kind.STRUCT,
            Regex("^\\s*(?:typedef\\s+)?enum\\s+(\\w+)") to Kind.ENUM,
            Regex("^\\s*(?:typedef\\s+)?union\\s+(\\w+)") to Kind.STRUCT,
            Regex("^\\s*#include\\s*[<\"]([^>\"]+)[>\"]") to Kind.IMPORT,
            Regex("^\\s*(?:typedef\\s+)?class\\s+(\\w+)") to Kind.CLASS,
        )
        Language.CSHARP -> listOf(
            Regex("^\\s*(?:public\\s+|private\\s+|protected\\s+|internal\\s+|static\\s+|sealed\\s+|abstract\\s+)*class\\s+(\\w+)") to Kind.CLASS,
            Regex("^\\s*(?:public\\s+|private\\s+|protected\\s+|internal\\s+|static\\s+|sealed\\s+|abstract\\s+)*interface\\s+(\\w+)") to Kind.INTERFACE,
            Regex("^\\s*(?:public\\s+|private\\s+|protected\\s+|internal\\s+|static\\s+|sealed\\s+|abstract\\s+)*struct\\s+(\\w+)") to Kind.STRUCT,
            Regex("^\\s*(?:public\\s+|private\\s+|protected\\s+|internal\\s+|static\\s+|sealed\\s+|abstract\\s+)*enum\\s+(\\w+)") to Kind.ENUM,
            Regex("^\\s*(?:public\\s+|private\\s+|protected\\s+|internal\\s+|static\\s+|sealed\\s+|abstract\\s+|virtual\\s+|override\\s+|async\\s+)*[\\w<>\\[\\],\\s]+\\s+(\\w+)\\s*\\(") to Kind.FUNCTION,
            Regex("^\\s*namespace\\s+([\\w.]+)") to Kind.NAMESPACE,
            Regex("^\\s*using\\s+([\\w.]+)") to Kind.IMPORT,
        )
        Language.SWIFT -> listOf(
            Regex("^\\s*(?:public\\s+|private\\s+|fileprivate\\s+|internal\\s+|open\\s+|static\\s+|final\\s+|@\\w+\\s+)*func\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*(?:public\\s+|private\\s+|fileprivate\\s+|internal\\s+|open\\s+|static\\s+|final\\s+|@\\w+\\s+)*class\\s+(\\w+)") to Kind.CLASS,
            Regex("^\\s*(?:public\\s+|private\\s+|fileprivate\\s+|internal\\s+|open\\s+|static\\s+|final\\s+|@\\w+\\s+)*struct\\s+(\\w+)") to Kind.STRUCT,
            Regex("^\\s*(?:public\\s+|private\\s+|fileprivate\\s+|internal\\s+|open\\s+|static\\s+|final\\s+|@\\w+\\s+)*enum\\s+(\\w+)") to Kind.ENUM,
            Regex("^\\s*(?:public\\s+|private\\s+|fileprivate\\s+|internal\\s+|open\\s+|static\\s+|final\\s+|@\\w+\\s+)*protocol\\s+(\\w+)") to Kind.TRAIT,
            Regex("^\\s*import\\s+(\\w+)") to Kind.IMPORT,
        )
        Language.DART -> listOf(
            Regex("^\\s*(?:abstract\\s+|external\\s+|static\\s+|const\\s+|factory\\s+)*class\\s+(\\w+)") to Kind.CLASS,
            Regex("^\\s*(?:abstract\\s+|external\\s+|static\\s+)*interface\\s+class\\s+(\\w+)") to Kind.INTERFACE,
            Regex("^\\s*(?:abstract\\s+|external\\s+|static\\s+)*mixin\\s+(\\w+)") to Kind.TRAIT,
            Regex("^\\s*enum\\s+(\\w+)") to Kind.ENUM,
            Regex("^\\s*(?:void\\s+|static\\s+|async\\s+|external\\s+)*[\\w<>\\[\\],\\s]+\\s+(\\w+)\\s*\\(") to Kind.FUNCTION,
            Regex("^\\s*(?:late\\s+|final\\s+|const\\s+|var\\s+)*(\\w+)\\s*=") to Kind.VARIABLE,
            Regex("^\\s*import\\s+['\"]([^'\"]+)['\"]") to Kind.IMPORT,
            Regex("^\\s*library\\s+(\\w+)") to Kind.NAMESPACE,
        )
        Language.RUBY -> listOf(
            Regex("^\\s*(?:def\\s+)?(?:self\\.)?(\\w+)\\s*[?(]") to Kind.FUNCTION,
            Regex("^\\s*class\\s+([\\w:]+)") to Kind.CLASS,
            Regex("^\\s*module\\s+([\\w:]+)") to Kind.NAMESPACE,
            Regex("^\\s*require[_-]?relative?\\s+['\"]([^'\"]+)['\"]") to Kind.IMPORT,
        )
        Language.PHP -> listOf(
            Regex("^\\s*(?:final\\s+|abstract\\s+|public\\s+|private\\s+|protected\\s+|static\\s+)*function\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*(?:final\\s+|abstract\\s+|public\\s+|private\\s+|protected\\s+|static\\s+)*class\\s+(\\w+)") to Kind.CLASS,
            Regex("^\\s*(?:final\\s+|abstract\\s+|public\\s+|private\\s+|protected\\s+|static\\s+)*interface\\s+(\\w+)") to Kind.INTERFACE,
            Regex("^\\s*namespace\\s+([\\w\\\\]+)") to Kind.NAMESPACE,
            Regex("^\\s*use\\s+([\\w\\\\]+)") to Kind.IMPORT,
        )
        Language.LUA -> listOf(
            Regex("^\\s*function\\s+(\\w[\\w.:]*)") to Kind.FUNCTION,
            Regex("^\\s*local\\s+function\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*local\\s+(\\w+)") to Kind.VARIABLE,
            Regex("^\\s*(\\w+)\\s*=\\s*function") to Kind.FUNCTION,
            Regex("^\\s*require\\s*[\"']([^\"']+)['\"]") to Kind.IMPORT,
        )
        Language.SQL -> listOf(
            Regex("^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?TABLE\\s+(\\w+)", RegexOption.IGNORE_CASE) to Kind.CLASS,
            Regex("^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\s+(\\w+)", RegexOption.IGNORE_CASE) to Kind.CLASS,
            Regex("^\\s*CREATE\\s+INDEX\\s+(\\w+)", RegexOption.IGNORE_CASE) to Kind.CLASS,
            Regex("^\\s*CREATE\\s+TRIGGER\\s+(\\w+)", RegexOption.IGNORE_CASE) to Kind.FUNCTION,
            Regex("^\\s*CREATE\\s+PROCEDURE\\s+(\\w+)", RegexOption.IGNORE_CASE) to Kind.FUNCTION,
            Regex("^\\s*CREATE\\s+FUNCTION\\s+(\\w+)", RegexOption.IGNORE_CASE) to Kind.FUNCTION,
        )
        Language.SHELL, Language.INI_BASHRC -> listOf(
            Regex("^\\s*(\\w+)\\s*\\(\\)\\s*\\{") to Kind.FUNCTION,
            Regex("^\\s*function\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*(\\w+)\\s*=") to Kind.VARIABLE,
            Regex("^\\s*alias\\s+(\\w+)") to Kind.VARIABLE,
            Regex("^\\s*source\\s+(\\S+)") to Kind.IMPORT,
            Regex("^\\s*\\..(\\S+)") to Kind.IMPORT,
        )
        Language.CLOJURE -> listOf(
            Regex("^\\s*\\(defn-?\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*\\(defmacro\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*\\(def\\s+(\\w+)") to Kind.VARIABLE,
            Regex("^\\s*\\(defrecord\\s+(\\w+)") to Kind.CLASS,
            Regex("^\\s*\\(deftype\\s+(\\w+)") to Kind.CLASS,
            Regex("^\\s*\\(ns\\s+([\\w.-]+)") to Kind.NAMESPACE,
            Regex("^\\s*\\(require\\s+['\"]([^'\"]+)['\"]") to Kind.IMPORT,
        )
        Language.HASKELL -> listOf(
            Regex("^\\s*module\\s+([\\w.]+)") to Kind.NAMESPACE,
            Regex("^\\s*import\\s+([\\w.]+)") to Kind.IMPORT,
            Regex("^\\s*(\\w+)\\s*::") to Kind.FUNCTION,
            Regex("^\\s*(\\w+)\\s*=\\s*") to Kind.VARIABLE,
            Regex("^\\s*data\\s+(\\w+)") to Kind.CLASS,
            Regex("^\\s*type\\s+(\\w+)") to Kind.CLASS,
            Regex("^\\s*newtype\\s+(\\w+)") to Kind.CLASS,
            Regex("^\\s*class\\s+(\\w+)") to Kind.TRAIT,
            Regex("^\\s*instance\\s+(\\w+)") to Kind.CLASS,
        )
        Language.ELIXIR -> listOf(
            Regex("^\\s*def\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*defp\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*defmodule\\s+([\\w.]+)") to Kind.NAMESPACE,
            Regex("^\\s*defstruct\\s+([\\w:]+)") to Kind.STRUCT,
            Regex("^\\s*defprotocol\\s+(\\w+)") to Kind.TRAIT,
            Regex("^\\s*defmacro\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*import\\s+(\\w+)") to Kind.IMPORT,
            Regex("^\\s*alias\\s+([\\w.]+)") to Kind.IMPORT,
            Regex("^\\s*require\\s+(\\w+)") to Kind.IMPORT,
            Regex("^\\s*use\\s+(\\w+)") to Kind.IMPORT,
        )
        Language.ERLANG -> listOf(
            Regex("^\\s*-module\\(([\\w]+)\\)") to Kind.NAMESPACE,
            Regex("^\\s*-export\\(([\\w]+)\\)") to Kind.IMPORT,
            Regex("^\\s*-import\\(([\\w]+)\\)") to Kind.IMPORT,
            Regex("^\\s*(\\w+)\\([^)]*\\)\\s*->") to Kind.FUNCTION,
            Regex("^\\s*-record\\(([\\w]+)\\)") to Kind.STRUCT,
            Regex("^\\s*-define\\(([\\w]+)\\)") to Kind.CONSTANT,
        )
        Language.VIM -> listOf(
            Regex("^\\s*function!?\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*command!?\\s+(\\w+)") to Kind.VARIABLE,
            Regex("^\\s*let\\s+(\\w+)") to Kind.VARIABLE,
            Regex("^\\s*map\\s+(\\S+)") to Kind.VARIABLE,
        )
        Language.EMACSLISP -> listOf(
            Regex("^\\s*\\(defun\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*\\(defmacro\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*\\(defvar\\s+(\\w+)") to Kind.VARIABLE,
            Regex("^\\s*\\(defcustom\\s+(\\w+)") to Kind.VARIABLE,
            Regex("^\\s*\\(defconst\\s+(\\w+)") to Kind.CONSTANT,
            Regex("^\\s*\\(require\\s+'(\\w+)") to Kind.IMPORT,
            Regex("^\\s*\\(provide\\s+'(\\w+)") to Kind.NAMESPACE,
        )
        Language.SCHEME, Language.COMMONLISP -> listOf(
            Regex("^\\s*\\(define\\s+\\((\\w+)") to Kind.FUNCTION,
            Regex("^\\s*\\(define\\s+(\\w+)") to Kind.VARIABLE,
            Regex("^\\s*\\(defmacro\\s+(\\w+)") to Kind.FUNCTION,
            Regex("^\\s*\\(load\\s+\"([^\"]+)\"") to Kind.IMPORT,
        )
        else -> null
    }
}

/**
 * v0.0.9 — TODO / FIXME / NOTE extractor.
 *
 * Walks a source file and collects every line that contains a
 * TODO, FIXME, NOTE, HACK, XXX or BUG marker — with line number,
 * kind, and the text after the marker.
 */
object TodoExtractor {

    data class TodoItem(
        val kind: Kind,
        val line: Int,
        val text: String,
    )

    enum class Kind(val marker: String) {
        TODO("TODO"),
        FIXME("FIXME"),
        NOTE("NOTE"),
        HACK("HACK"),
        XXX("XXX"),
        BUG("BUG"),
    }

    private val MARKERS = Kind.values().map { it.marker }
    private val REGEX = Regex("\\b(${MARKERS.joinToString("|")})[\\s:]+(.*)")

    fun extract(source: String): List<TodoItem> {
        val out = mutableListOf<TodoItem>()
        source.split('\n').forEachIndexed { idx, line ->
            val m = REGEX.find(line) ?: return@forEachIndexed
            val marker = m.groupValues[1]
            val text = m.groupValues[2].trim().take(500)
            val kind = Kind.values().firstOrNull { it.marker == marker } ?: return@forEachIndexed
            out.add(TodoItem(kind = kind, line = idx, text = text))
        }
        return out
    }
}
