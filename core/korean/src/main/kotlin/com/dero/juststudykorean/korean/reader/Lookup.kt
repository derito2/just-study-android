package com.dero.juststudykorean.korean.reader

import com.dero.juststudykorean.korean.hasForm
import com.dero.juststudykorean.korean.lemmas
import com.dero.juststudykorean.korean.teacher.MAX_EXAMPLES
import com.dero.juststudykorean.korean.teacher.patternForms

// F8 (FASE_11_PLAN PR 8, PLAN_FUNCIONES_ESTUDIO F8): what a tapped word of the Reader is, by your notes only. Pure.

/** A tappable word of a line: [start] until [end] (exclusive). */
data class Token(val text: String, val start: Int, val end: Int)

private val HANGUL_WORDS = Regex("[가-힣]+")

/** The Hangul words of [line] (as Teacher splits them). */
fun tokens(line: String): List<Token> = HANGUL_WORDS.findAll(line).map { Token(it.value, it.range.first, it.range.last + 1) }.toList()

sealed interface Gloss {
    data class Entry(val korean: String, val english: String, val source: String, val examples: List<Pair<String, String>>) : Gloss
    /** 학교에서 → 학교 + 에서; 먹었어요 → 먹다. [entry]: the base form's entry, if your notes have one. */
    data class Inflected(val surface: String, val lemma: String, val entry: Entry?, val particle: String?) : Gloss
    data class Grammar(val pattern: String, val meaning: String) : Gloss
    /** Not in your notes: the sentences of your notes where it appears (as Teacher, R1-01). */
    data class NotFound(val surface: String, val sentences: List<String>) : Gloss
}

/** What Lookup reads: your words by their Korean, your verbs, your grammar points (pattern, meaning) and sentences. */
class ReaderNotes(
    val words: Map<String, Gloss.Entry>,
    verbs: Set<String>,
    val grammar: List<Pair<String, String>>,
    val sentences: List<String>,
) {
    val verbs: Set<String> = verbs + words.keys.filter { it.length >= 2 && it.endsWith("다") && ' ' !in it }
    /**
     * Each point's written forms: whole, without spaces ("-ㄹ 지" → ㄹ지) and per spaced piece, as a tap is one word
     * (올까 of "-(으)ㄹ까 봐", 동안 of "-는 동안").
     */
    internal val forms: List<Pair<Pair<String, String>, Set<String>>> by lazy {
        grammar.map { g -> g to (listOf(g.first, g.first.replace(" ", "")) + g.first.split(' ')).flatMap(::patternForms).filter { it.length >= 2 }.toSet() }
    }
}

private val PARTICLES = listOf("에서", "에게", "한테", "까지", "부터", "처럼", "으로", "이랑", "은", "는", "이", "가", "을", "를", "에", "의", "도", "만", "와", "과", "로", "랑")

/** In order: the word; without its particle; a conjugated verb of your notes; a grammar point that ends it; else not found. */
fun lookup(surface: String, notes: ReaderNotes): Gloss {
    notes.words[surface]?.let { return it }
    for (p in PARTICLES) {
        if (surface.length <= p.length || !surface.endsWith(p)) continue
        val stem = surface.dropLast(p.length)
        notes.words[stem]?.let { return Gloss.Inflected(surface, stem, it, p) }
    }
    val verbs = lemmas(surface, notes.verbs)
    (verbs.firstOrNull { it in notes.words } ?: verbs.firstOrNull())?.let { return Gloss.Inflected(surface, it, notes.words[it], null) }
    // The form must close the word: 할게요 ends in -(으)ㄹ게요 (hasForm reads the ㄹ inside 할); 보세요 is 보다 of -아/어 보다 conjugated.
    for ((point, forms) in notes.forms) {
        val ends = forms.any { f ->
            surface.endsWith(f) || (hasForm(surface, f) && surface.endsWith(f.drop(1))) ||
                (f.endsWith("다") && lemmas(surface, setOf(f)).isNotEmpty())
        }
        if (ends) return Gloss.Grammar(point.first, point.second)
    }
    return Gloss.NotFound(surface, notes.sentences.filter { surface in it }.take(MAX_EXAMPLES))
}
