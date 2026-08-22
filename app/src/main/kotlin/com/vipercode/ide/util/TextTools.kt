package com.vipercode.ide.util

import java.security.MessageDigest
import java.util.UUID

/**
 * v0.0.9 — SUPER UPDATE: a single home for offline text utility
 * functions used by the Tools screen + the editor's text-transform
 * menu.
 *
 * Every function is pure + side-effect-free (no I/O, no Android
 * framework dependencies) so it can be unit-tested on the JVM.
 */
object TextTools {

    // ── JSON ──────────────────────────────────────────────────────

    /**
     * Pretty-prints a JSON document with 2-space indentation. Returns
     * the original input on parse failure (with a leading `// ` marker
     * so the caller can see the failure visually).
     */
    fun formatJson(input: String, indent: Int = 2): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val reader = java.io.StringReader(trimmed)
            val parser = org.json.JSONTokener(reader)
            val value = parser.nextValue()
            value.toString(indent)
        } catch (e: Throwable) {
            "// Invalid JSON: ${e.message}\n$input"
        }
    }

    /** Minifies a JSON document (strips all whitespace between tokens). */
    fun minifyJson(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val parser = org.json.JSONTokener(java.io.StringReader(trimmed))
            val value = parser.nextValue()
            value.toString()
        } catch (e: Throwable) {
            input
        }
    }

    /** Returns true if [input] is parseable as JSON. */
    fun isValidJson(input: String): Boolean = try {
        org.json.JSONTokener(java.io.StringReader(input.trim())).nextValue()
        true
    } catch (e: Throwable) {
        false
    }

    // ── Base64 ─────────────────────────────────────────────────────

    fun encodeBase64(input: String): String =
        android.util.Base64.encodeToString(input.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

    fun decodeBase64(input: String): String = try {
        String(android.util.Base64.decode(input, android.util.Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Throwable) {
        input
    }

    // ── URL ────────────────────────────────────────────────────────

    fun encodeUrl(input: String): String =
        java.net.URLEncoder.encode(input, "UTF-8")

    fun decodeUrl(input: String): String = try {
        java.net.URLDecoder.decode(input, "UTF-8")
    } catch (e: Throwable) {
        input
    }

    // ── HTML ───────────────────────────────────────────────────────

    fun escapeHtml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    fun unescapeHtml(input: String): String = input
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")

    // ── Hash ───────────────────────────────────────────────────────

    fun md5(input: String): String = hash(input, "MD5")
    fun sha1(input: String): String = hash(input, "SHA-1")
    fun sha256(input: String): String = hash(input, "SHA-256")
    fun sha512(input: String): String = hash(input, "SHA-512")

    private fun hash(input: String, algo: String): String {
        val md = MessageDigest.getInstance(algo)
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ── UUID ──────────────────────────────────────────────────────

    fun uuid(): String = UUID.randomUUID().toString()

    fun uuidShort(): String = UUID.randomUUID().toString().replace("-", "")

    // ── Password generator ────────────────────────────────────────

    fun generatePassword(
        length: Int = 16,
        includeUpper: Boolean = true,
        includeLower: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true,
    ): String {
        val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lower = "abcdefghijklmnopqrstuvwxyz"
        val digits = "0123456789"
        val symbols = "!@#\$%^&*()-_=+[]{}<>?/"
        val pool = StringBuilder()
        if (includeUpper) pool.append(upper)
        if (includeLower) pool.append(lower)
        if (includeDigits) pool.append(digits)
        if (includeSymbols) pool.append(symbols)
        if (pool.isEmpty()) return ""
        val sb = StringBuilder(length)
        val rnd = java.security.SecureRandom()
        repeat(length) {
            sb.append(pool[rnd.nextInt(pool.length)])
        }
        return sb.toString()
    }

    // ── Lorem ipsum ───────────────────────────────────────────────

    private val LOREM_WORDS = listOf(
        "lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing",
        "elit", "sed", "do", "eiusmod", "tempor", "incididunt", "ut", "labore",
        "et", "dolore", "magna", "aliqua", "ut", "enim", "ad", "minim", "veniam",
        "quis", "nostrud", "exercitation", "ullamco", "laboris", "nisi", "ut",
        "aliquip", "ex", "ea", "commodo", "consequat", "duis", "aute", "irure",
        "in", "reprehenderit", "voluptate", "velit", "esse", "cillum", "eu",
        "fugiat", "nulla", "pariatur", "excepteur", "sint", "occaecat", "cupidatat",
        "non", "proident", "sunt", "culpa", "qui", "officia", "deserunt", "mollit",
        "anim", "id", "est", "laborum",
    )

    fun lorem(words: Int = 50): String {
        val rnd = java.util.Random()
        val sb = StringBuilder()
        repeat(words) { i ->
            if (i > 0) sb.append(' ')
            sb.append(LOREM_WORDS[rnd.nextInt(LOREM_WORDS.size)])
        }
        sb.replace(0, 1, sb.substring(0, 1).uppercase())
        sb.append('.')
        return sb.toString()
    }

    fun loremParagraphs(count: Int = 3, wordsPerPara: Int = 50): String =
        (1..count).joinToString("\n\n") { lorem(wordsPerPara) }

    // ── Timestamp converter ──────────────────────────────────────

    fun epochToHuman(epochSeconds: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        fmt.timeZone = java.util.TimeZone.getDefault()
        return fmt.format(java.util.Date(epochSeconds * 1000))
    }

    fun humanToEpoch(human: String): Long = try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        fmt.timeZone = java.util.TimeZone.getDefault()
        fmt.parse(human)?.time?.div(1000) ?: 0L
    } catch (e: Throwable) {
        0L
    }

    fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

    // ── Slugify ───────────────────────────────────────────────────

    fun slugify(input: String): String = input.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    // ── JWT decoder ───────────────────────────────────────────────

    /**
     * Returns a triple of decoded (header, payload, signature) for
     * the given JWT. On failure returns three empty strings.
     */
    fun decodeJwt(token: String): Triple<String, String, String> = try {
        val parts = token.trim().split(".")
        if (parts.size < 2) return Triple("", "", "")
        val header = decodeBase64Url(parts[0])
        val payload = decodeBase64Url(parts[1])
        val sig = if (parts.size > 2) parts[2] else ""
        Triple(header, payload, sig)
    } catch (e: Throwable) {
        Triple("", "", "")
    }

    private fun decodeBase64Url(input: String): String {
        val padded = input.padEnd((input.length + 3) / 4 * 4, '=')
            .replace('-', '+').replace('_', '/')
        return try {
            String(android.util.Base64.decode(padded, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Throwable) {
            ""
        }
    }

    // ── Color converter ───────────────────────────────────────────

    data class RGB(val r: Int, val g: Int, val b: Int)

    fun hexToRgb(hex: String): RGB? {
        val clean = hex.removePrefix("#").removePrefix("0x")
        val full = when (clean.length) {
            3 -> clean.map { "$it$it" }.joinToString("")
            6 -> clean
            8 -> clean.substring(0, 6)
            else -> return null
        }
        val r = full.substring(0, 2).toIntOrNull(16) ?: return null
        val g = full.substring(2, 4).toIntOrNull(16) ?: return null
        val b = full.substring(4, 6).toIntOrNull(16) ?: return null
        return RGB(r, g, b)
    }

    fun rgbToHex(rgb: RGB): String = "#%02X%02X%02X".format(rgb.r, rgb.g, rgb.b)

    fun rgbToHsl(rgb: RGB): Triple<Float, Float, Float> {
        val r = rgb.r / 255f
        val g = rgb.g / 255f
        val b = rgb.b / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        if (max == min) return Triple(0f, 0f, l * 100f)
        val d = max - min
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        val h = when (max) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return Triple(h, s * 100f, l * 100f)
    }

    // ── Text statistics ──────────────────────────────────────────

    data class TextStats(
        val chars: Int,
        val charsNoSpaces: Int,
        val words: Int,
        val lines: Int,
        val sentences: Int,
        val paragraphs: Int,
        val readingTimeMin: Int,
    )

    fun stats(input: String): TextStats {
        val chars = input.length
        val charsNoSpaces = input.count { !it.isWhitespace() }
        val words = if (input.isBlank()) 0 else input.trim().split(Regex("\\s+")).size
        val lines = input.count { it == '\n' } + 1
        val sentences = input.split(Regex("[.!?]+")).count { it.isNotBlank() }
        val paragraphs = input.split(Regex("\n\n+")).count { it.isNotBlank() }
        val readingTime = (words / 200.0).let { if (it < 1) 1 else it.toInt() }
        return TextStats(chars, charsNoSpaces, words, lines, sentences, paragraphs, readingTime)
    }

    // ── Text diff (line-level) ───────────────────────────────────

    data class DiffLine(val text: String, val kind: DiffKind)
    enum class DiffKind { CONTEXT, ADDED, REMOVED }

    /**
     * Computes a simple line-level diff between [a] and [b]. Uses the
     * classic LCS algorithm; O(m*n) space, fine for typical code files.
     */
    fun diff(a: String, b: String): List<DiffLine> {
        val aLines = a.split('\n')
        val bLines = b.split('\n')
        val m = aLines.size
        val n = bLines.size
        // LCS table.
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in m - 1 downTo 0) {
            for (j in n - 1 downTo 0) {
                dp[i][j] = if (aLines[i] == bLines[j]) dp[i + 1][j + 1] + 1
                else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val out = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        while (i < m && j < n) {
            if (aLines[i] == bLines[j]) {
                out.add(DiffLine(aLines[i], DiffKind.CONTEXT))
                i++; j++
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                out.add(DiffLine(aLines[i], DiffKind.REMOVED))
                i++
            } else {
                out.add(DiffLine(bLines[j], DiffKind.ADDED))
                j++
            }
        }
        while (i < m) { out.add(DiffLine(aLines[i], DiffKind.REMOVED)); i++ }
        while (j < n) { out.add(DiffLine(bLines[j], DiffKind.ADDED)); j++ }
        return out
    }

    // ── Number base converter ────────────────────────────────────

    fun toBinary(n: Long): String = java.lang.Long.toBinaryString(n)
    fun toOctal(n: Long): String = java.lang.Long.toOctalString(n)
    fun toHex(n: Long): String = java.lang.Long.toHexString(n).uppercase()
    fun fromBinary(s: String): Long = s.toLongOrNull(2) ?: 0L
    fun fromOctal(s: String): Long = s.toLongOrNull(8) ?: 0L
    fun fromHex(s: String): Long = s.removePrefix("0x").removePrefix("0X").toLongOrNull(16) ?: 0L

    // ── Sort / dedupe lines ───────────────────────────────────────

    fun sortLinesAsc(input: String): String = input.split('\n').sorted().joinToString("\n")
    fun sortLinesDesc(input: String): String = input.split('\n').sortedDescending().joinToString("\n")
    fun dedupeLines(input: String): String = input.split('\n').distinct().joinToString("\n")
    fun reverseLines(input: String): String = input.split('\n').reversed().joinToString("\n")
    fun shuffleLines(input: String): String = input.split('\n').shuffled().joinToString("\n")
    fun removeEmptyLines(input: String): String =
        input.split('\n').filter { it.isNotBlank() }.joinToString("\n")

    // ── Number lines ─────────────────────────────────────────────

    fun numberLines(input: String, padTo: Int = 3): String =
        input.split('\n').mapIndexed { idx, line ->
            "${(idx + 1).toString().padStart(padTo, '0')}: $line"
        }.joinToString("\n")

    // ── Trim whitespace ───────────────────────────────────────────

    fun trimTrailingWs(input: String): String =
        input.split('\n').joinToString("\n") { it.trimEnd() }

    fun trimLeadingWs(input: String): String =
        input.split('\n').joinToString("\n") { it.trimStart() }

    fun trimAllLines(input: String): String =
        input.split('\n').joinToString("\n") { it.trim() }

    // ── Case conversion ──────────────────────────────────────────

    fun toCamelCase(input: String): String {
        val parts = input.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase()
            .split('_').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return input
        return parts[0].lowercase() + parts.drop(1).joinToString("") {
            it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
        }
    }

    fun toPascalCase(input: String): String {
        val camel = toCamelCase(input)
        return if (camel.isEmpty()) camel else
            camel.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun toSnakeCase(input: String): String {
        val sb = StringBuilder()
        for ((idx, c) in input.withIndex()) {
            if (c.isUpperCase() && idx > 0 && sb.lastOrNull() != '_') sb.append('_')
            sb.append(c.lowercaseChar())
        }
        return sb.toString().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    }

    fun toKebabCase(input: String): String = toSnakeCase(input).replace('_', '-')

    fun toConstantCase(input: String): String = toSnakeCase(input).uppercase()

    fun toTitleCase(input: String): String = input.lowercase().split(' ').joinToString(" ") { word ->
        if (word.isEmpty()) word else word.substring(0, 1).uppercase() + word.substring(1)
    }

    // ── EOL conversion ───────────────────────────────────────────

    fun toUnixEol(input: String): String =
        input.replace("\r\n", "\n").replace('\r', '\n')

    fun toWindowsEol(input: String): String =
        toUnixEol(input).replace("\n", "\r\n")

    fun toMacEol(input: String): String =
        toUnixEol(input).replace('\n', '\r')

    // ── Misc utilities ───────────────────────────────────────────

    fun rot13(input: String): String = input.map { c ->
        when {
            c in 'a'..'z' -> (((c - 'a') + 13) % 26 + 'a'.code).toChar()
            c in 'A'..'Z' -> (((c - 'A') + 13) % 26 + 'A'.code).toChar()
            else -> c
        }
    }.joinToString("")

    fun swapCase(input: String): String = input.map { c ->
        when {
            c.isUpperCase() -> c.lowercaseChar()
            c.isLowerCase() -> c.uppercaseChar()
            else -> c
        }
    }.joinToString("")

    fun reverse(input: String): String = input.reversed()
}
