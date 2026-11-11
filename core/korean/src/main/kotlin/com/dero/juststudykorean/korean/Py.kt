package com.dero.juststudykorean.korean

import java.util.regex.Pattern

// Python string and regex semantics the ports need, in one place (the goldens come from Python).

/** A Python `re` pattern: \w, \s, \b, \d and IGNORECASE are Unicode-aware, as in Python 3. */
internal fun py(pattern: String, ignoreCase: Boolean = false, multiline: Boolean = false): Regex {
    var flags = 0
    if (ignoreCase) flags = flags or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    if (multiline) flags = flags or Pattern.MULTILINE
    return unicodePattern(pattern, flags)
}

/**
 * The JVM needs UNICODE_CHARACTER_CLASS for Unicode \w \s \d \b; Android's regex (ICU) already works that way
 * and rejects the flag ("not supported"), so there it is left out. Checked on the phone by the db goldens (androidTest).
 */
internal fun unicodePattern(pattern: String, flags: Int = 0): Regex =
    try {
        Pattern.compile(pattern, flags or Pattern.UNICODE_CHARACTER_CLASS).toRegex()
    } catch (e: IllegalArgumentException) {
        Pattern.compile(pattern, flags).toRegex()
    }

/** `re.match`: only at the start of the string. */
internal fun Regex.pyMatch(s: String): MatchResult? = matchAt(s, 0)

/** Python's str.isspace() for one character. */
fun Char.pyIsSpace() = this in "\t\n\u000B\u000C\r\u001C\u001D\u001E\u001F \u0085      　" || this in ' '..' '

fun String.pyStrip() = trim { it.pyIsSpace() }
internal fun String.pyRstrip() = trimEnd { it.pyIsSpace() }
internal fun String.pyLstrip() = trimStart { it.pyIsSpace() }

/** str.split() with no argument: runs of whitespace, no empty strings. */
fun String.pySplit(): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    for (c in this) if (c.pyIsSpace()) { if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() } } else cur.append(c)
    if (cur.isNotEmpty()) out += cur.toString()
    return out
}

private val lineBreaks = Regex("\r\n|[\n\r\u000B\u000C\u001C\u001D\u001E\u0085  ]")

/** str.splitlines(): every Python line boundary, no trailing empty line. */
internal fun String.pySplitlines(): List<String> {
    if (isEmpty()) return emptyList()
    val parts = split(lineBreaks)
    return if (last() in "\n\r\u000B\u000C\u001C\u001D\u001E\u0085\u2028\u2029") parts.dropLast(1) else parts
}

/** len(): code points, not UTF-16 units. */
val String.pyLen get() = codePointCount(0, length)

/** s[:n] by code points. */
internal fun String.pyTake(n: Int): String = if (pyLen <= n) this else substring(0, offsetByCodePoints(0, n))
