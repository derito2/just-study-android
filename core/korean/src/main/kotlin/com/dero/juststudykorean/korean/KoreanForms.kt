package com.dero.juststudykorean.korean

// Ports of backend/app/rag/entries.py (korean_cores, has_form) and rag/conjugation.py (aeo_forms, past),
// checked against golden/korean.json (GoldenTest). Brought from the PoC 2 branch without changes in behaviour.

private const val BASE = 0xAC00
private const val A = 0; private const val AE = 1; private const val YA = 2; private const val YAE = 3
private const val EO = 4; private const val E = 5; private const val YEO = 6; private const val V_O = 8
private const val WA = 9; private const val WAE = 10; private const val OE = 11; private const val U = 13
private const val WEO = 14; private const val EU = 18; private const val V_I = 20
private const val NO_TAIL = 0; private const val T_D = 7; private const val T_L = 8; private const val T_B = 17
private const val T_S = 19; private const val T_SS = 20; private const val T_H = 27; private const val L_R = 5
private const val HIEUT = 18

private val hangul = Regex("[가-힣]")
private val hangulWords = Regex("[가-힣]+")

// The syllable parts come from hangul/Hangul.kt (phase 8): same formula, one place.
private fun split(ch: Char): Triple<Int, Int, Int>? = com.dero.juststudykorean.korean.hangul.indexesOf(ch)

private fun join(lead: Int, vowel: Int, tail: Int = 0) = com.dero.juststudykorean.korean.hangul.syllableOf(lead, vowel, tail).toString()

private fun bright(ch: Char) = split(ch)?.second in setOf(A, V_O, YA)

fun aeoForms(verbIn: String): Set<String> {
    val verb = verbIn.trim()
    if (verb.length < 2 || !verb.endsWith("다")) return emptySet()
    val stem = verb.dropLast(1)
    if (stem.any { split(it) == null }) return emptySet()
    val last = stem.last()
    val (lead, vowel, tail) = split(last)!!
    val head = stem.dropLast(1)
    val ending = if (bright(last)) "아" else "어"
    val out = mutableSetOf<String>()
    if (last == '하') return setOf(head + "해")
    if (tail == NO_TAIL) {
        when {
            vowel == EU -> {
                val new = if (head.isNotEmpty() && bright(head.last())) A else EO
                out += head + join(lead, new)
                if (lead == L_R && head.isNotEmpty()) {
                    val (pl, pv, pt) = split(head.last())!!
                    if (pt == NO_TAIL) out += head.dropLast(1) + join(pl, pv, T_L) + join(L_R, new)
                }
            }
            vowel == V_O -> out += listOf(head + join(lead, WA), stem + "아")
            vowel == U -> out += listOf(head + join(lead, WEO), stem + "어")
            vowel == V_I -> out += listOf(head + join(lead, YEO), stem + "어")
            vowel == OE -> out += listOf(head + join(lead, WAE), stem + "어")
            vowel in setOf(A, EO, AE, E, YEO) -> { out += stem; if (vowel == AE || vowel == E) out += stem + "어" }
            else -> out += stem + ending
        }
        return out
    }
    out += stem + ending
    val plain = head + join(lead, vowel)
    when {
        tail == T_B -> out += plain + if (vowel == V_O && head.isEmpty()) "와" else "워"
        tail == T_D -> out += head + join(lead, vowel, T_L) + ending
        tail == T_S -> out += plain + ending
        tail == T_H && vowel in setOf(A, EO, YA) -> out += head + join(lead, if (vowel == YA) YAE else AE)
    }
    return out
}

fun past(form: String): String? {
    val parts = form.lastOrNull()?.let { split(it) } ?: return null
    if (parts.third != NO_TAIL) return null
    return form.dropLast(1) + join(parts.first, parts.second, T_SS)
}

private val coreSplit = Regex("""\s+(?:and|y|,)\s+|,|/""")
private val grammarStart = Regex("""^[\s~-]*(?:\(으\)|\(이\)|[ㄱ-ㅎ]|-)""")
private val coreLead = Regex("""^[\s~-]*(?:\(으\)|\(이\))?\s*[ㄱ-ㅎ]?\s*""")
private val parens = Regex("""\([^)]*\)""")

fun koreanCores(target: String): List<Pair<String, Boolean>> = target.split(coreSplit).mapNotNull { raw ->
    val grammar = grammarStart.containsMatchIn(raw)
    var part = raw.replaceFirst(coreLead, "").trim(' ', '-')
    part = part.replace(parens, "").trim()
    if (part.length > 2) part = part.removeSuffix("다").trim()
    if (hangul.containsMatchIn(part)) part to grammar else null
}

private fun contracts(ch: Char, vowel: String, past: Boolean, rest: String = "서"): Boolean {
    val (lead, v, tail) = split(ch) ?: return false
    if (tail != (if (past) T_SS else 0)) return false
    if (lead == HIEUT && v == AE) return true
    val plain = past || rest.take(1) in setOf("서", "야")
    if (vowel == "아") return v == WA || (v == A && plain)
    return v in setOf(YEO, WEO, WAE) || (v in setOf(EO, AE) && plain)
}

private val conjunctions = "그래서 그래도 그러니까 그래야 그러면 그러나 그런데 그러고 그래".split(" ").toSet()

fun hasForm(text: String, form: String, aeo: Set<String> = emptySet()): Boolean {
    val flat = text.replace(" ", "")
    if (form in flat) return true
    if (form.endsWith("다") && form.length >= 3) {
        val bases = (setOf(form.dropLast(1)) + aeoForms(form)).let { b -> b + b.mapNotNull { past(it) } }
        if (bases.any { it.length >= 2 && it in flat }) return true
    }
    val first = form.take(1); val rest = form.drop(1)
    val tail = mapOf("ㄴ" to 4, "ㄹ" to 8, "ㅁ" to 16, "ㅂ" to 17)[first]
    if (tail != null && rest.isNotEmpty()) {
        for (word in hangulWords.findAll(text).map { it.value }) {
            for (i in 0 until word.length - rest.length) {
                if ((word[i].code - BASE) % 28 == tail && word.substring(i + 1).startsWith(rest)) return true
            }
        }
        return false
    }
    val vowel = mapOf("아" to "아", "았" to "아", "어" to "어", "었" to "어", "여" to "어", "였" to "어")[first]
    if (vowel == null || rest.isEmpty()) return false
    val isPast = first in setOf("았", "었", "였")
    val words = hangulWords.findAll(text).map { it.value }.filterNot { it in conjunctions }.toList()
    for (word in words) {
        for (i in 0 until word.length - rest.length) {
            val after = word.substring(i + 1)
            if ((after == rest || after == rest + "요") && contracts(word[i], vowel, isPast, rest)) return true
        }
    }
    if (aeo.isEmpty()) return false
    val wanted = if (isPast) aeo.mapNotNull { past(it) }.toSet() else aeo
    for (word in words) {
        for (end in listOf(rest, rest + "요")) {
            if (!word.endsWith(end) || word.length == end.length) continue
            if (word.dropLast(end.length) in wanted) return true
        }
    }
    return false
}

// What can follow each form in a conjugated word (conjugation.py), to tell which verb of your notes it comes from.
private val afterAeo = listOf("", "요", "서", "서요", "도", "야", "라", "줘", "주세요", "봐", "보세요", "있")
private val afterPast = listOf("어", "다", "습니다", "는데", "지만", "던", "을", "는")
private val afterStem = listOf(
    "고", "지", "는", "네", "게", "기", "면", "으면", "니까", "으니까", "세요", "으세요", "습니다",
    "겠", "다", "던", "을", "은", "잖아", "거든", "더라", "구나", "군", "죠", "대요", "래요",
)

/** The verbs of your notes a conjugated word can come from: "바빠요" -> [바쁘다] (conjugation.lemmas). */
fun lemmas(wordIn: String, verbs: Set<String>): List<String> {
    val word = wordIn.trim(' ', '.', ',', '!', '?')
    val found = mutableListOf<String>()
    for (verb in verbs.sorted()) {
        val stem = verb.dropLast(1)
        if (stem.isEmpty() || word == verb) continue
        val options = mutableListOf(Triple(stem, afterStem, true))
        val last = split(stem.last())
        if (last != null && last.third == T_L) options += Triple(stem.dropLast(1) + join(last.first, last.second), listOf("는", "니", "세요", "시"), true)
        for (form in aeoForms(verb)) {
            options += Triple(form, afterAeo, false)
            past(form)?.let { options += Triple(it, afterPast, true) }
        }
        for ((base, after, prefix) in options) {
            if (!word.startsWith(base)) continue
            val rest = word.substring(base.length)
            if (if (prefix) after.any { it.isNotEmpty() && rest.startsWith(it) } else rest in after) {
                found += verb
                break
            }
        }
    }
    return found
}
