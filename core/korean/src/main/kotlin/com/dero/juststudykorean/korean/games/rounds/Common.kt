package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.englishSenses
import com.dero.juststudykorean.korean.games.GameItem
import com.dero.juststudykorean.korean.games.RoundContext
import com.dero.juststudykorean.korean.hangul.decompose
import kotlin.random.Random

// What several factories share (FASE_8_PLAN 4.3).

internal fun level(ctx: RoundContext, one: Int, two: Int, three: Int) = when (ctx.level) { 1 -> one; 2 -> two; else -> three }

/**
 * [n] items with no sense in common and no Korean in common (4.3: never two right answers on screen), in the order the
 * chooser prefers; null if there are not that many. Distractors would be chosen the same way.
 */
internal fun distinctSenses(ctx: RoundContext, candidates: List<GameItem>, n: Int): List<GameItem>? {
    val out = mutableListOf<GameItem>()
    val senses = mutableSetOf<String>()
    val koreans = mutableSetOf<String>()
    for (item in ctx.pick(candidates, candidates.size)) {
        val s = englishSenses(item.meaning)
        val k = com.dero.juststudykorean.korean.games.flatKorean(item.korean)
        if (s.isEmpty() || s.any { it in senses } || k in koreans) continue
        out += item; senses += s; koreans += k
        if (out.size == n) return out
    }
    return null
}

/** [items] shuffled so the result is never [items] itself (null if that is impossible: all equal). */
internal fun <T> shuffledNotSame(items: List<T>, random: Random): List<T>? {
    if (items.distinct().size < 2) return null
    repeat(50) { val s = items.shuffled(random); if (s != items) return s }
    return items.drop(1) + items.first() // a rotation is never the same when two items differ
}

/** Look-alike jamo (games-minigames.md, Hangul Builder). */
internal val LOOK_ALIKES: List<Set<Char>> = listOf(
    setOf('ㅏ', 'ㅓ'), setOf('ㅗ', 'ㅜ'), setOf('ㄱ', 'ㅋ', 'ㄲ'), setOf('ㄷ', 'ㅌ', 'ㄸ'),
    setOf('ㅂ', 'ㅍ', 'ㅃ'), setOf('ㅈ', 'ㅊ', 'ㅉ'), setOf('ㅅ', 'ㅆ'), setOf('ㅐ', 'ㅔ'),
)

/** Two blocks look alike if they share the first consonant or the vowel. */
internal fun similarBlock(a: String, b: String): Boolean {
    val x = a.singleOrNull()?.let(::decompose) ?: return false
    val y = b.singleOrNull()?.let(::decompose) ?: return false
    return x.lead == y.lead || x.vowel == y.vowel
}

internal fun firstLead(item: GameItem): Char? = item.blocks.firstOrNull()?.singleOrNull()?.let(::decompose)?.lead
