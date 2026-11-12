package com.dero.juststudykorean.korean

import java.text.Normalizer

// Port of backend/app/quiz/text.py, checked against golden/text.json (TextGoldenTest).

// Python's \w is Unicode (see unicodePattern).
private val notWord = unicodePattern("""[^\w\s]|_""")
private val spaces = unicodePattern("""\s+""")
private val hangul = Regex("[ㄱ-ㆎ가-힣]")

/** NFC + lower case + no punctuation + collapsed spaces. */
fun norm(text: String): String =
    casefold(Normalizer.normalize(text, Normalizer.Form.NFC)).replace(notWord, " ").replace(spaces, " ").pyStrip()

// Python's str.casefold(): uppercase then lowercase gives the same full folding for ß, ﬁ, ς and the like.
fun casefold(s: String) = s.uppercase().lowercase()

/** The answer appears in the source (whole phrase, or all its words; Hangul words also inside longer ones). */
fun isGrounded(answer: String, source: String): Boolean {
    val a = norm(answer)
    val s = norm(source)
    if (a.isEmpty()) return false
    if (" $s ".contains(" $a ")) return true
    val words = s.split(" ").toSet()
    val tokens = a.split(" ").filter { it.length >= 2 || hangul.containsMatchIn(it) }
    if (tokens.isEmpty()) return false
    return tokens.all { if (hangul.containsMatchIn(it)) s.contains(it) else it in words }
}

/** The question already contains the answer (answers of 1–2 Latin letters are ignored). */
fun leaksAnswer(prompt: String, answer: String): Boolean {
    val a = norm(answer)
    val p = norm(prompt)
    if (a.isEmpty() || !(a.length >= 3 || hangul.containsMatchIn(a))) return false
    return " $p ".contains(" $a ")
}

private val alternatives = py("[;/,]")
private val slashAlternatives = py("[;/]") // "Hello; Hi" -> two valid answers
private val parens = py("\\([^)]*\\)")
private val placeholder = py("\\s+(?:doing\\s+)?(?:sth|smth|something|someone|somebody|sb|sm1)\\.?$", ignoreCase = true)

/** write_ko: "안 / 속" -> two words of your notes with the same meaning; either is right. */
const val KOREAN_ALTERNATIVES = " / "
private val typedEnglishTypes = setOf("short_answer", "ko_to_en")
private val typedKoreanTypes = setOf("write_ko")

private fun dropPrefix(text: String, prefixes: List<String>) =
    prefixes.firstOrNull { text.startsWith(it) && text.length > it.length }?.let { text.substring(it.length) } ?: text

private fun dropArticle(t: String) = dropPrefix(t, listOf("a ", "an ", "the "))

/** Comparable English: no parentheses, article, "to be" / "to" or "sth" ("To walk" = "walk"). */
private fun englishForms(text: String): String {
    var t = dropArticle(norm(text.replace(parens, " ")))
    t = dropArticle(dropPrefix(t, listOf("to be ", "to ", "be ")))
    return t.replace(placeholder, "").pyStrip().ifEmpty { t }
}

/** The valid answers: the one in your notes and, if it has several meanings, each one alone. */
fun englishAnswers(correct: String, split: Boolean = true): List<String> {
    val valid = listOf(correct) + if (split) correct.replace(parens, " ").split(alternatives) else emptyList()
    return valid.map { it.pyStrip() }.distinct().filter { norm(it).isNotEmpty() }
}

private fun sameKorean(given: String, correct: String): Boolean {
    val g = norm(given)
    val c = norm(correct)
    return g.isNotEmpty() && (g == c || g.replace(" ", "") == c.replace(" ", ""))
}

fun isCorrect(type: String, given: String, correct: String): Boolean {
    val g = norm(given)
    val c = norm(correct)
    if (g.isEmpty()) return false
    if (type in typedKoreanTypes) return correct.split(KOREAN_ALTERNATIVES).any { sameKorean(given, it) }
    if (type in typedEnglishTypes && !hangul.containsMatchIn(c)) {
        val split = type == "ko_to_en"
        val valid = englishAnswers(correct, split).map(::englishForms).toMutableSet()
        if (!split) valid += correct.split(slashAlternatives).filter { norm(it).isNotEmpty() }.map(::englishForms)
        if (englishForms(given) in valid) return true
        val parts = given.split(alternatives).filter { norm(it).isNotEmpty() }.map(::englishForms)
        return split && parts.size > 1 && parts.all { it in valid }
    }
    if (g == c) return true
    // Hangul: spaces do not change the answer, and "곁 / 옆" are two words of your notes: either is right.
    if (hangul.containsMatchIn(c)) return (listOf(correct) + correct.split(KOREAN_ALTERNATIVES)).any { sameKorean(given, it) }
    return false
}

private const val CHOSEONG = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"

/** The consonant a Korean word starts with (가 -> ㄱ, 빵 -> ㅃ), or null if it does not start with Hangul. */
fun initialConsonant(word: String): String? {
    val w = Normalizer.normalize(word, Normalizer.Form.NFC).pyStrip()
    if (w.isEmpty() || w[0] !in '가'..'힣') return null
    return CHOSEONG[(w[0].code - 0xAC00) / 588].toString()
}

/** The meanings of an entry, comparable: "To be alone; By oneself" -> {alone, by oneself}. */
fun englishSenses(text: String): Set<String> =
    text.replace(parens, " ").split(alternatives).map(::englishForms).filter { it.isNotEmpty() }.toSet()
