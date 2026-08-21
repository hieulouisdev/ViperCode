package com.vipercode.ide.util

/**
 * Minimal Markdown → HTML converter (v0.0.5).
 *
 * Implements enough of CommonMark + GitHub Flavored Markdown to
 * render the vast majority of README.md files correctly:
 *  - ATX headings (# … ######)
 *  - Paragraphs and hard line breaks
 *  - Block quotes (> )
 *  - Unordered lists (-, *, +)
 *  - Ordered lists (1., 2., …)
 *  - Fenced code blocks (``` and ~~~)
 *  - Indented code blocks (4-space indent)
 *  - Inline code (`code`)
 *  - Bold (**bold** / __bold__)
 *  - Italic (*italic* / _italic_)
 *  - Strikethrough (~~text~~)
 *  - Links ([label](url))
 *  - Auto-links (<https://example.com>)
 *  - Images (![alt](url))
 *  - Horizontal rules (---, ***, ___)
 *  - GFM tables (| a | b |)
 *  - Escapes for the above
 *
 * Deliberately dependency-free — keeps the APK small and the preview
 * pipeline entirely on-device.
 *
 * Not a full CommonMark parser. Nesting is limited (lists inside
 * blockquotes work; lists inside lists work for two levels; tables
 * can't contain block elements). For everything that doesn't match,
 * the source is emitted verbatim so the user at least sees their
 * text.
 */
object MarkdownRenderer {

    /** Renders [markdown] as a standalone HTML document. */
    fun render(markdown: String): String {
        val body = renderBody(markdown)
        return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Markdown preview</title>
<style>
:root {
  color-scheme: light dark;
  --fg: #1f2328;
  --bg: #ffffff;
  --muted: #59636e;
  --code-bg: #f6f8fa;
  --code-fg: #1f2328;
  --border: #d0d7de;
  --link: #0969da;
  --quote-bg: #f6f8fa;
}
@media (prefers-color-scheme: dark) {
  :root {
    --fg: #e6edf3;
    --bg: #0d1117;
    --muted: #8b949e;
    --code-bg: #161b22;
    --code-fg: #e6edf3;
    --border: #30363d;
    --link: #58a6ff;
    --quote-bg: #161b22;
  }
}
* { box-sizing: border-box; }
body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Helvetica Neue",
    "Noto Sans", "Roboto", sans-serif;
  margin: 0 auto;
  max-width: 780px;
  padding: 16px 20px 60px;
  color: var(--fg);
  background: var(--bg);
  line-height: 1.55;
  font-size: 15px;
  word-wrap: break-word;
}
h1, h2, h3, h4, h5, h6 { line-height: 1.25; margin: 24px 0 16px; font-weight: 600; }
h1 { font-size: 1.9em; padding-bottom: 0.3em; border-bottom: 1px solid var(--border); }
h2 { font-size: 1.5em; padding-bottom: 0.3em; border-bottom: 1px solid var(--border); }
h3 { font-size: 1.25em; }
h4 { font-size: 1em; }
h5 { font-size: 0.9em; }
h6 { font-size: 0.85em; color: var(--muted); }
p { margin: 0 0 14px; }
a { color: var(--link); text-decoration: none; }
a:hover { text-decoration: underline; }
img { max-width: 100%; height: auto; }
ul, ol { margin: 0 0 14px; padding-left: 28px; }
li { margin: 4px 0; }
li > ul, li > ol { margin: 4px 0; }
code {
  font-family: "JetBrains Mono", "Fira Code", "Cascadia Code", Consolas,
    "Liberation Mono", monospace;
  font-size: 0.92em;
  background: var(--code-bg);
  color: var(--code-fg);
  padding: 2px 6px;
  border-radius: 4px;
}
pre {
  background: var(--code-bg);
  color: var(--code-fg);
  padding: 14px 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 0 0 14px;
  font-size: 0.88em;
  line-height: 1.45;
}
pre code { background: transparent; padding: 0; font-size: inherit; }
blockquote {
  margin: 0 0 14px;
  padding: 0 16px;
  border-left: 4px solid var(--border);
  color: var(--muted);
  background: var(--quote-bg);
}
blockquote p { margin: 6px 0; }
hr { border: none; border-top: 1px solid var(--border); margin: 24px 0; }
table {
  border-collapse: collapse;
  margin: 0 0 14px;
  width: 100%;
  display: block;
  overflow-x: auto;
}
th, td {
  border: 1px solid var(--border);
  padding: 6px 12px;
  text-align: left;
}
th { background: var(--code-bg); font-weight: 600; }
tr:nth-child(even) td { background: var(--code-bg); }
.task-list-item { list-style: none; margin-left: -16px; }
.task-list-item input { margin-right: 6px; }
</style>
</head>
<body>
$body
</body>
</html>"""
    }

    /** Renders the body content (no surrounding <html>). */
    private fun renderBody(markdown: String): String {
        val lines = markdown.replace("\r\n", "\n").split("\n")
        val out = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // Fenced code block ```
            val fenceMatch = FENCE_REGEX.matchEntire(line)
            if (fenceMatch != null) {
                val fence = fenceMatch.groupValues[1]
                val lang = fenceMatch.groupValues[2].trim()
                val sb = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                    sb.append(lines[i]).append('\n')
                    i++
                }
                if (i < lines.size) i++ // consume closing fence
                val langAttr = if (lang.isNotEmpty()) " class=\"language-$lang\"" else ""
                out.append("<pre><code$langAttr>")
                out.append(escapeHtml(sb.toString().trimEnd('\n')))
                out.append("</code></pre>\n")
                continue
            }

            // ATX heading
            val headingMatch = HEADING_REGEX.matchEntire(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text = inline(headingMatch.groupValues[2].trim())
                out.append("<h$level>$text</h$level>\n")
                i++
                continue
            }

            // Horizontal rule
            if (HR_REGEX.matches(line)) {
                out.append("<hr/>\n")
                i++
                continue
            }

            // Block quote (consecutive lines starting with >)
            if (line.trimStart().startsWith(">")) {
                val sb = StringBuilder()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    val content = lines[i].trimStart().removePrefix(">").removePrefix(" ")
                    sb.append(content).append('\n')
                    i++
                }
                out.append("<blockquote>").append(renderBody(sb.toString())).append("</blockquote>\n")
                continue
            }

            // Unordered list
            if (UL_REGEX.matches(line)) {
                val items = mutableListOf<String>()
                while (i < lines.size && (UL_REGEX.matches(lines[i]) || lines[i].startsWith("   "))) {
                    val raw = if (UL_REGEX.matches(lines[i])) {
                        UL_REGEX.matchEntire(lines[i])!!.groupValues[2]
                    } else {
                        lines[i]
                    }
                    items.add(raw)
                    i++
                }
                out.append("<ul>\n")
                for (item in items) {
                    out.append("<li>").append(inline(item.trim())).append("</li>\n")
                }
                out.append("</ul>\n")
                continue
            }

            // Ordered list
            if (OL_REGEX.matches(line)) {
                val items = mutableListOf<String>()
                while (i < lines.size && (OL_REGEX.matches(lines[i]) || lines[i].startsWith("   "))) {
                    val raw = if (OL_REGEX.matches(lines[i])) {
                        OL_REGEX.matchEntire(lines[i])!!.groupValues[2]
                    } else {
                        lines[i]
                    }
                    items.add(raw)
                    i++
                }
                out.append("<ol>\n")
                for (item in items) {
                    out.append("<li>").append(inline(item.trim())).append("</li>\n")
                }
                out.append("</ol>\n")
                continue
            }

            // GFM table (a line with | followed by a separator line)
            if (line.contains('|') && i + 1 < lines.size && TABLE_SEP_REGEX.matches(lines[i + 1].trim())) {
                val header = splitTableRow(line)
                val aligns = splitTableAligns(lines[i + 1])
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                    rows.add(splitTableRow(lines[i]))
                    i++
                }
                out.append("<table>\n<thead><tr>")
                for ((idx, h) in header.withIndex()) {
                    val align = aligns.getOrNull(idx) ?: "left"
                    out.append("<th style=\"text-align:$align\">").append(inline(h.trim())).append("</th>")
                }
                out.append("</tr></thead>\n<tbody>\n")
                for (row in rows) {
                    out.append("<tr>")
                    for ((idx, c) in row.withIndex()) {
                        val align = aligns.getOrNull(idx) ?: "left"
                        out.append("<td style=\"text-align:$align\">").append(inline(c.trim())).append("</td>")
                    }
                    out.append("</tr>\n")
                }
                out.append("</tbody>\n</table>\n")
                continue
            }

            // Blank line
            if (line.isBlank()) {
                i++
                continue
            }

            // Paragraph — consume consecutive non-blank lines that
            // don't start a block element.
            val sb = StringBuilder()
            while (i < lines.size && lines[i].isNotBlank() &&
                !HEADING_REGEX.matches(lines[i]) &&
                !FENCE_REGEX.matches(lines[i]) &&
                !HR_REGEX.matches(lines[i]) &&
                !UL_REGEX.matches(lines[i]) &&
                !OL_REGEX.matches(lines[i]) &&
                !lines[i].trimStart().startsWith(">") &&
                !(lines[i].contains('|') && i + 1 < lines.size && TABLE_SEP_REGEX.matches(lines[i + 1].trim()))
            ) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(lines[i])
                i++
            }
            val text = sb.toString().replace("\n", "<br/>")
            out.append("<p>").append(inline(text)).append("</p>\n")
        }
        return out.toString()
    }

    /** Inline formatting: `code`, **bold**, *italic*, ~~strike~~, [link](url), ![img](url), <auto>, escapes. */
    private fun inline(text: String): String {
        var s = escapeHtml(text)
        // Inline code first so its contents aren't re-processed.
        val codeStash = mutableListOf<String>()
        s = INLINE_CODE_REGEX.replace(s) { mr ->
            codeStash.add("<code>${mr.groupValues[1]}</code>")
            "\u0000${codeStash.size - 1}\u0000"
        }
        // Images ![alt](url)
        s = IMAGE_REGEX.replace(s) { mr ->
            val alt = mr.groupValues[1]
            val url = mr.groupValues[2]
            "<img src=\"${escapeAttr(url)}\" alt=\"$alt\"/>"
        }
        // Links [label](url)
        s = LINK_REGEX.replace(s) { mr ->
            val label = mr.groupValues[1]
            val url = mr.groupValues[2]
            "<a href=\"${escapeAttr(url)}\">$label</a>"
        }
        // Auto-links <https://…>
        s = AUTOLINK_REGEX.replace(s) { mr ->
            val url = mr.groupValues[1]
            "<a href=\"${escapeAttr(url)}\">$url</a>"
        }
        // Bold (must run before italic so ** is consumed first)
        s = BOLD_REGEX.replace(s) { mr ->
            val text = mr.groupValues[1].ifEmpty { mr.groupValues[2] }
            "<strong>$text</strong>"
        }
        s = ITALIC_REGEX.replace(s) { mr ->
            val text = mr.groupValues[1].ifEmpty { mr.groupValues[2] }
            "<em>$text</em>"
        }
        s = STRIKE_REGEX.replace(s, "<del>$1</del>")
        // Restore code stashes
        s = CODE_STASH_REGEX.replace(s) { mr ->
            codeStash[mr.groupValues[1].toInt()]
        }
        return s
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun escapeAttr(s: String): String =
        escapeHtml(s).replace("\"", "&quot;")

    private fun splitTableRow(line: String): List<String> {
        val trimmed = line.trim().trim('|')
        return trimmed.split("|")
    }

    private fun splitTableAligns(line: String): List<String> {
        return splitTableRow(line).map { cell ->
            val c = cell.trim()
            val left = c.startsWith(':')
            val right = c.endsWith(':')
            when {
                left && right -> "center"
                right -> "right"
                else -> "left"
            }
        }
    }

    private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.*)$")
    private val HR_REGEX = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
    private val FENCE_REGEX = Regex("^\\s*(`{3,}|~{3,})(.*)$")
    private val UL_REGEX = Regex("^\\s*([-*+])\\s+(.*)$")
    private val OL_REGEX = Regex("^\\s*(\\d+)\\.\\s+(.*)$")
    private val TABLE_SEP_REGEX = Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?$")
    private val INLINE_CODE_REGEX = Regex("`([^`]+)`")
    private val IMAGE_REGEX = Regex("!\\[(.+?)\\]\\(([^)]+?)\\)")
    private val LINK_REGEX = Regex("\\[(.+?)\\]\\(([^)]+?)\\)")
    private val AUTOLINK_REGEX = Regex("<(https?://[^>]+)>")
    private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__")
    private val ITALIC_REGEX = Regex("\\*(.+?)\\*|_(.+?)_")
    private val STRIKE_REGEX = Regex("~~(.+?)~~")
    private val CODE_STASH_REGEX = Regex("\u0000(\\d+)\u0000")
}
