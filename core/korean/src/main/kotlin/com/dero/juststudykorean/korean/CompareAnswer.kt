package com.dero.juststudykorean.korean

// Port of frontend/src/flashcards/compare.ts (Type the answer), checked against golden/compare_answer.json.
// JavaScript details kept: strings split by code point, and JS's trim()/\s, which include NBSP and U+FEFF.

enum class Mark { Ok, Wrong, Extra, Missing }
enum class Verdict { Exact, Meaning, Almost, Wrong, Empty }

data class Letter(val char: String, val mark: Mark)

/** [closest]: the answer from your notes it was compared with; [off]: letters changed, extra or missing. */
data class Comparison(val verdict: Verdict, val closest: String, val letters: List<Letter>, val off: Int)

private const val JS_SPACE = "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"
private val jsSpaces = Regex("[$JS_SPACE]+")
private fun jsTrim(s: String) = s.replace(Regex("^[$JS_SPACE]+|[$JS_SPACE]+$"), "")
private fun codePoints(s: String): List<String> = s.codePoints().toArray().map { String(Character.toChars(it)) }

/** Every valid answer of the card ("Seat / Spot", "Joke, prank" -> several), without notes in parentheses. */
fun alternatives(answer: String, korean: Boolean): List<String> {
    val bare = jsTrim(answer.replace(Regex("[$JS_SPACE]*\\([^)]*\\)"), "")).ifEmpty { jsTrim(answer) }
    val sep = if (korean) Regex("[$JS_SPACE]*/[$JS_SPACE]*") else Regex("[$JS_SPACE]*[/;,][$JS_SPACE]*")
    val parts = bare.split(sep).map(::jsTrim).filter { it.isNotEmpty() }
    return (parts + bare).distinct().filter { it.isNotEmpty() }
}

/** Comparable English: no parentheses, "to"/"to be", articles, "sth" or punctuation. */
fun plainEnglish(text: String): String {
    var t = jsTrim(
        text.lowercase().replace(Regex("\\(.*?\\)"), " ").replace(Regex("[’']"), "")
            .replace(Regex("[^a-z$JS_SPACE]"), " ").replace(jsSpaces, " "),
    )
    t = t.replaceFirst(Regex("^to be "), "").replaceFirst(Regex("^to "), "").replaceFirst(Regex("^(a|an|the) "), "")
    t = t.replaceFirst(Regex(" (doing )?(sth|smth|something|someone|somebody|sb)$"), "")
    return jsTrim(t)
}

private fun plainKorean(text: String) = text.replace(Regex("[$JS_SPACE.,!?~]"), "")

/** Minimal alignment (Levenshtein) of what you typed with the answer. */
private fun align(typed: String, target: String): Pair<List<Letter>, Int> {
    val a = codePoints(typed)
    val b = codePoints(target)
    val d = Array(a.size + 1) { i -> IntArray(b.size + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
    fun same(i: Int, j: Int) = if (a[i - 1].lowercase() == b[j - 1].lowercase()) 0 else 1
    for (i in 1..a.size) for (j in 1..b.size) d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + same(i, j))
    val letters = ArrayDeque<Letter>()
    var i = a.size
    var j = b.size
    while (i > 0 || j > 0) {
        if (i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + same(i, j)) {
            letters.addFirst(Letter(a[i - 1], if (same(i, j) == 1) Mark.Wrong else Mark.Ok))
            i--; j--
        } else if (i > 0 && d[i][j] == d[i - 1][j] + 1) {
            letters.addFirst(Letter(a[i - 1], Mark.Extra)); i--
        } else {
            letters.addFirst(Letter(b[j - 1], Mark.Missing)); j--
        }
    }
    return letters.toList() to d[a.size][b.size]
}

fun compareAnswer(typed: String, answer: String, korean: Boolean): Comparison {
    val given = jsTrim(typed)
    val options = alternatives(answer, korean)
    if (given.isEmpty()) return Comparison(Verdict.Empty, options.firstOrNull() ?: answer, emptyList(), 0)
    val plain: (String) -> String = if (korean) ::plainKorean else ::plainEnglish
    var best: Triple<String, List<Letter>, Int>? = null
    for (option in options) {
        val (letters, off) = align(given, option)
        if (best == null || off < best.third) best = Triple(option, letters, off)
    }
    val (option, letters, off) = best!!
    val meaning = options.any { plain(it).isNotEmpty() && plain(it) == plain(given) }
    val verdict = when {
        off == 0 -> Verdict.Exact
        meaning -> Verdict.Meaning
        off <= maxOf(1, option.length / 5) -> Verdict.Almost
        else -> Verdict.Wrong
    }
    // Nothing like the answer: what you typed, all red (mixing both sentences could not be read).
    val shown = if (verdict == Verdict.Wrong) codePoints(given).map { Letter(it, if (it == " ") Mark.Ok else Mark.Wrong) } else letters
    return Comparison(verdict, option, shown, off)
}
