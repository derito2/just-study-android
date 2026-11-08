package com.dero.juststudykorean.korean

import java.text.Normalizer
import java.util.regex.Pattern

// Ports of backend/app/quiz/text.py (norm, is_grounded, leaks_answer), checked against golden/text.json (GoldenTest).

// Python's \w is Unicode; on the JVM it is ASCII unless UNICODE_CHARACTER_CLASS (Android's ICU already is).
private val notWord = Pattern.compile("""[^\w\s]|_""", Pattern.UNICODE_CHARACTER_CLASS).toRegex()
private val spaces = Pattern.compile("""\s+""", Pattern.UNICODE_CHARACTER_CLASS).toRegex()
private val hangul = Regex("[ㄱ-ㆎ가-힣]")

/** NFC + lower case + no punctuation + collapsed spaces. */
fun norm(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFC).lowercase().replace(notWord, " ").replace(spaces, " ").trim()

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
