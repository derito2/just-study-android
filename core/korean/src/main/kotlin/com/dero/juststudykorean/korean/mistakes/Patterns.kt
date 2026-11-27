package com.dero.juststudykorean.korean.mistakes

import com.dero.juststudykorean.korean.games.PARTICLE_PAIRS
import com.dero.juststudykorean.korean.hangul.decompose
import com.dero.juststudykorean.korean.hangul.hasBatchim

// F5 (FASE_11_PLAN PR 9, PLAN_FUNCIONES_ESTUDIO F5): your mistakes from every part of the app, grouped by rules. Pure.

enum class PatternKind(val label: String) {
    ParticleBatchim("Particles after the 받침"),
    VowelHarmony("아 or 어 after the stem"),
    Contraction("Contracting 아/어 (가아서 → 가서)"),
    Irregular("Irregular verbs done as regular"),
    Tense("Present and past mixed up"),
    Batchim("Final consonants (받침)"),
    Vowel("Similar vowels"),
    Spacing("Spacing"),
    WrongWord("Words you mix up"),
    Meaning("Meanings in English"),
}

/** [given]: what you answered; null where the app keeps only right or wrong (an Again, a lost game round). */
data class Mistake(val source: String, val expected: String, val given: String?, val wordKey: String?, val pointKey: String?, val at: Long)

data class Pattern(val kind: PatternKind, val label: String, val examples: List<Mistake>, val count: Int, val lastAt: Long)

private val HANGUL = Regex("[가-힣]")

/** 책를 for 책을: the other particle of its pair, with the same noun. */
private fun particleSwap(expected: String, given: String): Pair<String, String>? {
    val e = expected.split(' ')
    val g = given.split(' ')
    if (e.size != g.size) return null
    for ((we, wg) in e.zip(g)) {
        if (we == wg) continue
        for (pair in PARTICLE_PAIRS) {
            val (right, other) = listOf(pair.vowel to pair.consonant, pair.consonant to pair.vowel).sortedByDescending { it.first.length }
                .firstOrNull { we.endsWith(it.first) && we.length > it.first.length } ?: continue
            val noun = we.dropLast(right.length)
            if (wg == noun + other) return noun to "${pair.consonant}/${pair.vowel} after a ${if (hasBatchim(noun.last())) "consonant" else "vowel"}"
        }
    }
    return null
}

private fun pasts(s: String) = s.count { decompose(it)?.tail == "ㅆ" }

/** The one syllable where two strings of the same length differ, or null. */
private fun oneDiff(e: String, g: String): Int? = if (e.length != g.length) null else e.indices.filter { e[it] != g[it] }.singleOrNull()

fun classify(m: Mistake): PatternKind {
    val e = m.expected.trim()
    val g = m.given?.trim() ?: return PatternKind.WrongWord
    if (!HANGUL.containsMatchIn(e)) return PatternKind.Meaning
    if (e.replace(" ", "") == g.replace(" ", "")) return PatternKind.Spacing
    if (particleSwap(e, g) != null) return PatternKind.ParticleBatchim
    val (e0, g0) = decompose(e[0]) to g.firstOrNull()?.let(::decompose)
    if (pasts(e) != pasts(g) && e0 != null && g0 != null && e0.lead == g0.lead && e0.vowel == g0.vowel) return PatternKind.Tense
    if (g.length == e.length + 1 && g.indices.any { g[it] in "아어여" && g.removeRange(it, it + 1) == e }) return PatternKind.Contraction
    if ("르아" in g || "르어" in g) return PatternKind.Irregular
    val i = e.indices.firstOrNull { it >= g.length || e[it] != g[it] }
    if (i != null && e.length == g.length) {
        val (x, y) = decompose(e[i]) to decompose(g[i])
        // The stem kept its ㄷ/ㅂ/ㅅ before an ending that starts with a vowel: 듣어요 for 들어요.
        if (x != null && y != null && x.lead == y.lead && y.tail in setOf("ㄷ", "ㅂ", "ㅅ") && x.tail != y.tail && g.getOrNull(i + 1)?.let(::decompose)?.lead == 'ㅇ') return PatternKind.Irregular
    }
    val one = oneDiff(e, g)
    if (one != null) {
        val (x, y) = decompose(e[one]) to decompose(g[one])
        if (x != null && y != null) {
            if (one > 0 && x.lead == 'ㅇ' && x.lead == y.lead && x.tail == y.tail && setOf(x.vowel, y.vowel) == setOf('ㅏ', 'ㅓ')) return PatternKind.VowelHarmony
            if (x.lead == y.lead && x.vowel == y.vowel) return PatternKind.Batchim
            if (x.lead == y.lead && x.tail == y.tail) return PatternKind.Vowel
        }
    }
    return PatternKind.WrongWord
}

/** Your mistakes since [since], one Pattern per kind, the most frequent (then the most recent) first; 3 examples, newest first. */
fun patterns(mistakes: List<Mistake>, since: Long): List<Pattern> =
    mistakes.filter { it.at >= since }.groupBy(::classify).map { (kind, ms) ->
        val newest = ms.sortedByDescending { it.at }
        val label = if (kind == PatternKind.ParticleBatchim) newest.firstNotNullOfOrNull { m -> m.given?.let { particleSwap(m.expected, it)?.second } } ?: kind.label else kind.label
        Pattern(kind, label, newest.take(3), ms.size, newest.first().at)
    }.sortedWith(compareByDescending<Pattern> { it.count }.thenByDescending { it.lastAt })
