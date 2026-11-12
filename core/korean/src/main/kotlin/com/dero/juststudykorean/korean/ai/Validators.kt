package com.dero.juststudykorean.korean.ai

import com.dero.juststudykorean.korean.aeoForms
import com.dero.juststudykorean.korean.hasForm
import com.dero.juststudykorean.korean.koreanCores
import com.dero.juststudykorean.korean.past

// Shared validators for what Gemini Nano writes (phase 6, R-013): the model may only write English, may quote only
// the Korean of its input, and may only cite sources that exist. Pure functions: one prompt and validator per use.

/** The fixed instruction of every prompt: without it Nano answered in Korean (docs/poc/2-nano.md, 3). */
const val SYSTEM_PROMPT = "You are an English-speaking tutor. Write your whole answer in English."

private val HANGUL_WORD = Regex("[가-힣]+")
private val HANGUL_OR_JAMO = Regex("[ㄱ-ㆎ가-힣]")
private val OTHER_SCRIPTS = Regex("[\\u3040-\\u30ff\\u3400-\\u4dbf\\u4e00-\\u9fff]") // kana and Han: never in an English answer
private val LATIN_WORD = Regex("[A-Za-z]{2,}")
private val CITATION = Regex("\\[(\\d{1,3})]")
private val PARTICLES = listOf("에서", "으로", "이랑", "하고", "을", "를", "이", "가", "은", "는", "도", "에", "만", "과", "와", "랑", "의", "로", "요")

private fun stripParticle(w: String) = PARTICLES.firstOrNull { w.length > it.length && w.endsWith(it) }?.let { w.dropLast(it.length) } ?: w

/**
 * True when [word] comes from [input]: its base form (particles and endings removed) is there. 커피를 → 커피,
 * 먹어요/먹었어요 → 먹다, 봤어요 → -아/어 보다, 할게요 → -(으)ㄹ게요. Not a lemmatizer: a new verb with a taught ending
 * (갈게요 when the input has no 가다) counts as invented. Port of the PoC 2 check (docs/poc/2-nano.md, 4).
 */
fun fromInput(word: String, input: String): Boolean {
    val w = stripParticle(word)
    val inputWords = HANGUL_WORD.findAll(input).map { it.value }.toList()
    val cores = input.lines().flatMap { koreanCores(it.substringAfter(":")) }.filter { it.second }.map { it.first }
    return inputWords.any { e ->
        val base = stripParticle(e)
        val stems = if (e.length >= 2 && e.endsWith("다")) {
            val forms = aeoForms(e) + e.dropLast(1)
            forms + forms.mapNotNull { past(it) }
        } else emptySet()
        (base.length >= 2 && base in w) || w == base || stems.any { w.startsWith(it) } || (e.length >= 3 && e.endsWith("다") && hasForm(w, e))
    } || cores.any { core -> hasForm(word, core) && (word.length <= core.length + 1 || inputWords.any { it.length >= 2 && word.startsWith(it.take(2)) }) }
}

/** The Korean words of [output] that are not in [input] (invented Korean). */
fun inventedKorean(output: String, input: String): List<String> =
    HANGUL_WORD.findAll(output).map { it.value }.distinct().filterNot { fromInput(it, input) }.toList()

/** The output's Korean comes only from [input]. */
fun koreanOnlyFrom(output: String, input: String) = inventedKorean(output, input).isEmpty()

/** Every [n] of [output] is one of [sources] (1-based numbers of the sources shown). */
fun citationsValid(output: String, sources: Set<Int>) = CITATION.findAll(output).all { it.groupValues[1].toInt() in sources }

/** The citations of [output], in order of first use. */
fun citations(output: String): List<Int> = CITATION.findAll(output).map { it.groupValues[1].toInt() }.distinct().toList()

/**
 * Written in English: at least [minWords] English words, no kana or Han, and more English words than Korean ones
 * (Korean quoted from the input is allowed; an answer in Korean is not).
 */
fun isEnglish(output: String, minWords: Int = 3): Boolean {
    if (OTHER_SCRIPTS.containsMatchIn(output)) return false
    val english = LATIN_WORD.findAll(output).count()
    return english >= minWords && english > HANGUL_WORD.findAll(output).count()
}

/** Any Hangul at all (the meaning judge only judges English answers). */
fun hasHangul(text: String) = HANGUL_OR_JAMO.containsMatchIn(text)
