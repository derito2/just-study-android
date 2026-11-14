package com.dero.juststudykorean.korean.speech

import com.dero.juststudykorean.korean.KOREAN_ALTERNATIVES
import java.text.Normalizer

// FASE_9_PLAN 4 (D9-6): what the phone heard next to your notes. No verdict: never "wrong", no threshold, no tries.

private val IGNORED = Regex("[\\s.,!?~…\"'“”‘’]")

/** NFC, no spaces and no . , ! ? ~ … " ' (the plan's normalization). */
fun normalizeSpoken(text: String): String = IGNORED.replace(Normalizer.normalize(text, Normalizer.Form.NFC), "")

/**
 * [heard]: the first candidate as given (to show). [notes]: the answer of your notes closest to it (one of its
 * " / " alternatives, as written). [matches]: the normalized first candidate is the answer or one of its alternatives,
 * the only positive signal. [heardMarks] / [notesMarks]: the indices, in [heard] and [notes] as shown, of the
 * syllables that don't line up (a syllable-by-syllable Levenshtein alignment), to underline them.
 */
data class SpokenComparison(val heard: String, val notes: String, val matches: Boolean, val heardMarks: List<Int>, val notesMarks: List<Int>)

/** [candidates] must not be empty: no candidate is "No speech", and this is not called. */
fun compareSpoken(expected: String, candidates: List<String>): SpokenComparison {
    require(candidates.isNotEmpty()) { "no candidates: that is No speech" }
    val heard = Normalizer.normalize(candidates.first(), Normalizer.Form.NFC)
    val alternatives = (expected.split(KOREAN_ALTERNATIVES) + expected).map { Normalizer.normalize(it.trim(), Normalizer.Form.NFC) }.distinct()
    val h = normalizeSpoken(heard)
    val notes = alternatives.minBy { distance(h, normalizeSpoken(it)) }
    val n = normalizeSpoken(notes)
    if (h == n) return SpokenComparison(heard, notes, true, emptyList(), emptyList())
    val (inHeard, inNotes) = misaligned(h, n)
    return SpokenComparison(heard, notes, false, shownIndices(heard, inHeard), shownIndices(notes, inNotes))
}

private fun distance(a: String, b: String): Int = table(a, b)[a.length][b.length]

private fun table(a: String, b: String): Array<IntArray> {
    val d = Array(a.length + 1) { i -> IntArray(b.length + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
    for (i in 1..a.length) for (j in 1..b.length) {
        d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
    }
    return d
}

/** The positions (in the normalized strings) that the alignment doesn't pair with an equal syllable. */
private fun misaligned(a: String, b: String): Pair<Set<Int>, Set<Int>> {
    val d = table(a, b)
    val inA = mutableSetOf<Int>()
    val inB = mutableSetOf<Int>()
    var i = a.length
    var j = b.length
    while (i > 0 || j > 0) {
        when {
            i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1 -> {
                if (a[i - 1] != b[j - 1]) { inA += i - 1; inB += j - 1 }
                i--; j--
            }
            i > 0 && d[i][j] == d[i - 1][j] + 1 -> { inA += i - 1; i-- }
            else -> { inB += j - 1; j-- }
        }
    }
    return inA to inB
}

/** Normalized positions → positions in the text as shown (spaces and signs are skipped, never marked). */
private fun shownIndices(shown: String, normalized: Set<Int>): List<Int> {
    val out = mutableListOf<Int>()
    var k = 0
    shown.forEachIndexed { index, c ->
        if (IGNORED.matches(c.toString())) return@forEachIndexed
        if (k in normalized) out += index
        k++
    }
    return out
}
