package com.dero.juststudykorean.korean.games

import com.dero.juststudykorean.korean.hangul.blocks
import java.text.Normalizer

// FASE_8_PLAN 4.3: Korean is always compared normalized (NFC, without spaces).

internal fun flat(text: String): String = flatKorean(text)

/** NFC, without spaces: how Games compares Korean. */
fun flatKorean(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC).replace(" ", "").trim()

internal val HANGUL_ONLY = Regex("^[가-힣]+$")
private val NOT_A_SENTENCE = Regex("->|→|=|[A-Za-z]|[0-9]")
private val END_SIGN = Regex("[.?!]+$")

/** The syllable blocks of a Korean text, without spaces or signs. */
internal fun syllables(text: String): List<String> = blocks(text).filter { it.single() in '가'..'힣' }

/** A Korean sentence Games can use: no arrows, "=", Latin letters or digits in it. */
internal fun usableSentence(korean: String): Boolean = korean.isNotBlank() && !NOT_A_SENTENCE.containsMatchIn(korean) && syllables(korean).isNotEmpty()

/** The words of a sentence, the final sign apart: "학교에 가요." → ([학교에, 가요], "."). */
internal fun sentenceTiles(korean: String): Pair<List<String>, String> {
    val text = Normalizer.normalize(korean.trim(), Normalizer.Form.NFC)
    val sign = END_SIGN.find(text)?.value.orEmpty()
    return text.removeSuffix(sign).trim().split(Regex("\\s+")).filter { it.isNotEmpty() } to sign
}
