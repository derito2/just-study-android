package com.dero.juststudykorean.korean.hangul

import java.text.Normalizer

// FASE_8_PLAN 3.1: syllables and compatibility jamo (U+3131–U+318E), the Unicode formula
// syllable = 0xAC00 + (lead × 21 + vowel) × 28 + tail. Pure Kotlin.

private const val BASE = 0xAC00
private const val COUNT = 11172

const val LEADS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
const val VOWELS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
internal const val TAILS = "\u0000ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ" // index 0: no final

private val COMPOUND_TAILS = mapOf(
    'ㄳ' to "ㄱㅅ", 'ㄵ' to "ㄴㅈ", 'ㄶ' to "ㄴㅎ", 'ㄺ' to "ㄹㄱ", 'ㄻ' to "ㄹㅁ", 'ㄼ' to "ㄹㅂ",
    'ㄽ' to "ㄹㅅ", 'ㄾ' to "ㄹㅌ", 'ㄿ' to "ㄹㅍ", 'ㅀ' to "ㄹㅎ", 'ㅄ' to "ㅂㅅ",
)
private val COMPOUND_VOWELS = mapOf('ㅘ' to "ㅗㅏ", 'ㅙ' to "ㅗㅐ", 'ㅚ' to "ㅗㅣ", 'ㅝ' to "ㅜㅓ", 'ㅞ' to "ㅜㅔ", 'ㅟ' to "ㅜㅣ", 'ㅢ' to "ㅡㅣ")
private val TAIL_OF_PARTS = COMPOUND_TAILS.entries.associate { (k, v) -> v to k }
private val VOWEL_OF_PARTS = COMPOUND_VOWELS.entries.associate { (k, v) -> v to k }

/** A syllable's parts; [tail] is "", one jamo, or a compound final split in two ("ㄹㄱ" for ㄺ). */
data class Block(val lead: Char, val vowel: Char, val tail: String)

/** (lead, vowel, tail) indexes of a syllable 가..힣, or null. */
internal fun indexesOf(syllable: Char): Triple<Int, Int, Int>? {
    val code = syllable.code - BASE
    if (code !in 0 until COUNT) return null
    return Triple(code / 588, (code % 588) / 28, code % 28)
}

/** The syllable of those indexes (no checks: the callers pass valid ones). */
internal fun syllableOf(lead: Int, vowel: Int, tail: Int = 0): Char = (BASE + (lead * 21 + vowel) * 28 + tail).toChar()

fun decompose(syllable: Char): Block? {
    val (l, v, t) = indexesOf(syllable) ?: return null
    val tail = TAILS[t].takeIf { t != 0 }?.let { COMPOUND_TAILS[it] ?: it.toString() } ?: ""
    return Block(LEADS[l], VOWELS[v], tail)
}

/** Null if the combination does not exist (ㄸ, ㅃ, ㅉ are never finals). */
fun compose(lead: Char, vowel: Char, tail: String = ""): Char? {
    val l = LEADS.indexOf(lead)
    val v = VOWELS.indexOf(vowel)
    if (l < 0 || v < 0) return null
    val t = when (tail.length) {
        0 -> 0
        1 -> TAILS.indexOf(tail[0]).takeIf { it > 0 } ?: return null
        2 -> TAIL_OF_PARTS[tail]?.let { TAILS.indexOf(it) } ?: return null
        else -> return null
    }
    return syllableOf(l, v, t)
}

/** "말하다" → [말, 하, 다]; spaces, signs and Latin letters stay, one element each. Input is normalized to NFC. */
fun blocks(text: String): List<String> = Normalizer.normalize(text, Normalizer.Form.NFC).map { it.toString() }

/** 닭 → [ㄷ, ㅏ, ㄹ, ㄱ]; 와 → [ㅇ, ㅘ] or, with [splitVowel], [ㅇ, ㅗ, ㅏ]. Empty for a non-syllable. */
fun jamoOf(syllable: Char, splitVowel: Boolean = false): List<Char> {
    val b = decompose(syllable) ?: return emptyList()
    val vowel = if (splitVowel) COMPOUND_VOWELS[b.vowel]?.toList() ?: listOf(b.vowel) else listOf(b.vowel)
    return listOf(b.lead) + vowel + b.tail.toList()
}

fun joinVowels(a: Char, b: Char): Char? = VOWEL_OF_PARTS["$a$b"]

fun joinTails(a: Char, b: Char): Char? = TAIL_OF_PARTS["$a$b"]

/** The two parts of a compound vowel (ㅘ → "ㅗㅏ"), or null. */
fun vowelParts(vowel: Char): String? = COMPOUND_VOWELS[vowel]

/** A consonant that can close a syllable on its own (not ㄸ, ㅃ, ㅉ). */
fun canBeTail(consonant: Char): Boolean = TAILS.indexOf(consonant) > 0

enum class VowelShape { Vertical, Horizontal, Mixed }

fun shapeOf(vowel: Char): VowelShape = when (vowel) {
    in "ㅗㅛㅜㅠㅡ" -> VowelShape.Horizontal
    in COMPOUND_VOWELS.keys -> VowelShape.Mixed
    else -> VowelShape.Vertical
}

fun hasBatchim(syllable: Char): Boolean = (indexesOf(syllable)?.third ?: 0) != 0

fun endsInRieul(syllable: Char): Boolean = indexesOf(syllable)?.third == TAILS.indexOf('ㄹ')

/** Edit distance over the jamo of both texts (compound finals count as two jamo). */
fun jamoDistance(a: String, b: String): Int {
    fun seq(s: String) = blocks(s).flatMap { block -> block.single().let { c -> jamoOf(c).ifEmpty { listOf(c) } } }
    val x = seq(a)
    val y = seq(b)
    var prev = IntArray(y.size + 1) { it }
    for (i in 1..x.size) {
        val cur = IntArray(y.size + 1)
        cur[0] = i
        for (j in 1..y.size) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (x[i - 1] == y[j - 1]) 0 else 1)
        prev = cur
    }
    return prev[y.size]
}
