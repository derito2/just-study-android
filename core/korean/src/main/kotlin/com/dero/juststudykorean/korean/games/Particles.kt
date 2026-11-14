package com.dero.juststudykorean.korean.games

import com.dero.juststudykorean.korean.hangul.endsInRieul
import com.dero.juststudykorean.korean.hangul.hasBatchim

// FASE_8_PLAN 4.9 (D8-7): only a particle wrong by the 받침, the one error a rule can call wrong with a single fix.

/** A pair: [vowel] after a vowel (and 로 also after ㄹ), [consonant] after a consonant. */
data class ParticlePair(val vowel: String, val consonant: String) {
    private val rieulTakesVowel get() = vowel == "로"
    fun correctAfter(noun: String): String {
        val last = noun.last()
        return if (!hasBatchim(last) || (rieulTakesVowel && endsInRieul(last))) vowel else consonant
    }
    fun wrongAfter(noun: String) = if (correctAfter(noun) == vowel) consonant else vowel
    val rule: String
        get() = if (rieulTakesVowel) "After a vowel or ㄹ: 로. After another consonant: 으로." else "After a vowel: $vowel. After a consonant: $consonant."
}

val PARTICLE_PAIRS = listOf(
    ParticlePair("는", "은"), ParticlePair("가", "이"), ParticlePair("를", "을"),
    ParticlePair("와", "과"), ParticlePair("랑", "이랑"), ParticlePair("로", "으로"),
)

/** Nouns that are not in your notes but are always nouns. */
val PRONOUNS = setOf("저", "나", "너", "우리", "저희", "이것", "그것", "저것", "여기", "거기", "저기", "누구", "뭐")

/** A word of a sentence that is a known noun + the right particle: [index] in the sentence. */
data class ParticleSpot(val index: Int, val noun: String, val pair: ParticlePair, val particle: String) {
    val wrong get() = pair.wrongAfter(noun)
}

private val FORMS: List<Pair<String, ParticlePair>> =
    PARTICLE_PAIRS.flatMap { p -> listOf(p.vowel to p, p.consonant to p) }.sortedByDescending { it.first.length }

/** The words of [words] where a particle can be swapped for its wrong pair (the four conditions of 4.9). */
fun particleSpots(words: List<String>, bank: GameBank): List<ParticleSpot> = words.mapIndexedNotNull { i, word ->
    if (bank.isWord(word)) return@mapIndexedNotNull null // 3: the whole word is a word of your notes
    FORMS.firstNotNullOfOrNull { (form, pair) ->
        if (!word.endsWith(form) || word.length == form.length) return@firstNotNullOfOrNull null
        val noun = word.dropLast(form.length)
        val known = (bank.isWord(noun) && !noun.endsWith("다")) || noun in PRONOUNS // 1
        if (!known || noun.last() !in '가'..'힣') return@firstNotNullOfOrNull null
        if (pair.correctAfter(noun) != form) return@firstNotNullOfOrNull null // 2: your notes already have a typo
        if (bank.isWord(noun + pair.wrongAfter(noun))) return@firstNotNullOfOrNull null // 4
        ParticleSpot(i, noun, pair, form)
    }
}

/** The fixes offered: the right particle and [distractors] wrong forms of OTHER pairs for the same noun (never the one in the sentence). */
fun particleOptions(spot: ParticleSpot, distractors: Int, random: kotlin.random.Random): List<String> {
    val wrongs = PARTICLE_PAIRS.filter { it != spot.pair }.map { it.wrongAfter(spot.noun) }.filter { it != spot.wrong && it != spot.particle }.distinct()
    return (listOf(spot.particle) + wrongs.shuffled(random).take(distractors)).shuffled(random)
}
