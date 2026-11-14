package com.dero.juststudykorean.speechpoc

/** The test protocol of docs/FASE_9_PLAN.md §2 (synthetic phrases only) and its metrics. */
data class Item(val expected: String, val spoken: String, val kind: String, val condition: String, val rep: Int)

val CONDITIONS = listOf(
    "normal" to "Say it normally.",
    "slow" to "Say it slowly.",
    "fast" to "Say it fast.",
    "quiet" to "Say it in a low voice.",
    "noise" to "With moderate noise (music or TV in the background).",
    "arm" to "Phone at arm's length.",
)
private val PHRASES = listOf("안녕하세요", "감사합니다", "학교에 가요", "오늘은 날씨가 좋아요", "저는 한국어를 공부하고 있어요")
private val WORDS = listOf("학교", "친구", "사과", "도서관", "병원", "먹다", "마시다", "크다", "예쁘다", "날씨")
// (expected, what the owner says instead): similar words, to count false accepts.
private val ERRORS = listOf("사과" to "사자", "학교" to "학생", "친구" to "친척", "병원" to "병역", "날씨" to "날개")

fun conditionHelp(name: String) = CONDITIONS.first { it.first == name }.second

/** "main" is the full session (85 utterances); the others (airplane, reboot) repeat 10 normal phrases. */
fun items(session: String): List<Item> =
    if (session == "main") {
        CONDITIONS.flatMap { (c, _) -> PHRASES.flatMap { p -> (1..2).map { Item(p, p, "phrase", c, it) } } } +
            WORDS.flatMap { w -> (1..2).map { Item(w, w, "word", "normal", it) } } +
            ERRORS.map { (w, e) -> Item(w, e, "error", "normal", 1) }
    } else PHRASES.flatMap { p -> (1..2).map { Item(p, p, "phrase", "normal", it) } }

/** Only Hangul syllables count: the recognizer's spacing and punctuation are not reliable (plan §2, CER). */
fun normalize(s: String) = s.filter { it in '가'..'힣' }

/** Syllable-level edit distance divided by the number of expected syllables. */
fun cer(expected: String, heard: String): Double {
    val a = normalize(expected)
    val b = normalize(heard)
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1).also { it[0] = i }
        for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        prev = cur
    }
    return prev[b.length].toDouble() / a.length
}

fun selfCheck() {
    check(items("main").size == 85 && items("airplane").size == 10)
    check(cer("사과", "사자") == 0.5 && cer("학교에 가요", "학교에가요") == 0.0 && cer("사과", "") == 1.0)
}
