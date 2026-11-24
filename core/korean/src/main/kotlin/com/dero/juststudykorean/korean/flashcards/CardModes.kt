package com.dero.juststudykorean.korean.flashcards

import com.dero.juststudykorean.korean.aeoForms
import com.dero.juststudykorean.korean.past
import kotlin.random.Random

// F2 (FASE_11_PLAN PR 4, PLAN_FUNCIONES_ESTUDIO F2): the same card, shown another way. No new card and no SM-2 per mode:
// the rating goes to the same flashcard. Golden rule: if the gap is not certain, the card stays classic.

enum class CardMode { Classic, Cloze, Listen }

/** One of YOUR examples with a gap: [before] + [answer] + [after] is the sentence as written. */
data class Cloze(val before: String, val after: String, val answer: String, val translation: String)

// What may follow the word inside its eojeol: particles (nouns), or endings after the stem / the 아/어 form / the past (verbs).
private val PARTICLES = listOf(
    "", "이", "가", "은", "는", "을", "를", "의", "에", "에서", "에게", "한테", "께", "도", "만", "와", "과", "랑", "이랑", "하고",
    "로", "으로", "까지", "부터", "보다", "처럼", "요", "이에요", "예요", "이야", "야", "이다", "입니다", "이랑은", "에는", "에서는", "에도",
)
private val AFTER_STEM = listOf(
    "고", "지", "는", "네", "게", "기", "면", "으면", "니까", "으니까", "세요", "으세요", "습니다", "ㅂ니다", "겠", "다", "던", "을", "은",
    "지만", "는데", "고요", "죠", "지요",
)
private val AFTER_AEO = listOf("", "요", "서", "서요", "도", "야", "라", "줘", "주세요", "봐", "보세요", "있")
private val AFTER_PAST = listOf("어", "어요", "다", "습니다", "는데", "지만", "던", "을", "는", "고")
private val TRAILING = Regex("[^가-힣ㄱ-ㆎ]+$")

/** The forms of [word] that may stand in a sentence, each with what may follow it in the same eojeol. */
private fun forms(word: String): List<Pair<String, List<String>>> {
    if (!(word.length >= 2 && word.endsWith("다"))) return listOf(word to PARTICLES)
    val aeo = aeoForms(word)
    return aeo.map { it to AFTER_AEO } + aeo.mapNotNull(::past).map { it to AFTER_PAST } + listOf(word.dropLast(1) to AFTER_STEM)
}

/** A gap in one of [examples] (Korean, translation) where [word] stands; null if none uses it for sure. [seed] picks one. */
fun clozeFor(word: String, examples: List<Pair<String, String>>, seed: Long): Cloze? {
    val w = word.trim()
    if (w.isEmpty()) return null
    val options = examples.filter { it.second.isNotBlank() }.mapNotNull { (ko, en) -> gapIn(w, ko)?.let { (at, form) -> Cloze(ko.take(at), ko.drop(at + form.length), form, en.trim()) } }
    return options.takeIf { it.isNotEmpty() }?.let { it[Random(seed).nextInt(it.size)] }
}

/** Where (index, form) the word starts an eojeol of [sentence], followed only by what that form allows. */
private fun gapIn(word: String, sentence: String): Pair<Int, String>? {
    var start = 0
    for (eojeol in sentence.split(" ")) {
        val bare = eojeol.replace(TRAILING, "")
        val hit = forms(word).filter { (f, after) -> bare.startsWith(f) && bare.removePrefix(f) in after }.maxByOrNull { it.first.length }
        if (hit != null) return start to hit.first
        start += eojeol.length + 1
    }
    return null
}

/**
 * Which way this card is shown: with [gap] and [listen] off, as today. Otherwise it turns by card among the modes it
 * admits (Cloze needs a gap, Listen the voice); a card that admits none is classic.
 */
fun modeFor(cardId: Long, gap: Boolean, listen: Boolean, hasCloze: Boolean, voice: Boolean): CardMode {
    val options = listOfNotNull(CardMode.Cloze.takeIf { gap && hasCloze }, CardMode.Listen.takeIf { listen && voice })
    return if (options.isEmpty()) CardMode.Classic else options[(cardId % options.size).toInt()]
}
