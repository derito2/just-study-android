package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.games.FIXED_MINIMAL_GROUPS
import com.dero.juststudykorean.korean.games.GameItem
import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.games.SoundWord
import com.dero.juststudykorean.korean.games.minimalDifference
import com.dero.juststudykorean.korean.games.minimalPairsFromNotes

/**
 * 4.10. Three modes, chosen at random among those with content: A same or different (pairs of your notes, else the
 * fixed list: extra); B which came twice (3 words, 4 sounds); C what order (3 words, level 3: 4). Level 1: rate 0.85
 * and 700 ms between sounds; 2: 1.0 and 500 ms; 3: 1.0, 350 ms and look-alike words.
 */
val SoundDetectiveRounds = RoundFactory { ctx ->
    val rate = if (ctx.level == 1) 0.85f else 1f
    val pause = level(ctx, 700, 500, 350)
    fun words(n: Int): List<GameItem>? {
        if (ctx.level < 3) return distinctSenses(ctx, ctx.bank.words, n)
        val first = ctx.pick(ctx.bank.words, 1).firstOrNull() ?: return null
        val alike = ctx.bank.words.filter { it.key != first.key && (firstLead(it) == firstLead(first) || it.blocks.size == first.blocks.size) }
        return distinctSenses(ctx.copy(avoid = ctx.avoid + first.key), alike, n - 1)?.let { listOf(first) + it }
    }
    val modes = listOf("A", "B", "C").shuffled(ctx.random)
    for (mode in modes) {
        when (mode) {
            "A" -> {
                val fromNotes = minimalPairsFromNotes(ctx.bank).filter { (a, b) -> a.key !in ctx.avoid && b.key !in ctx.avoid }
                val (a, b) = if (fromNotes.isNotEmpty()) {
                    fromNotes.random(ctx.random).let { (x, y) -> SoundWord(x.korean, x.meaning, x.key, false) to SoundWord(y.korean, y.meaning, y.key, false) }
                } else {
                    val group = FIXED_MINIMAL_GROUPS.random(ctx.random).shuffled(ctx.random)
                    SoundWord(group[0].first, group[0].second, null, true) to SoundWord(group[1].first, group[1].second, null, true)
                }
                val same = ctx.random.nextBoolean()
                val second = if (same) a else b
                return@RoundFactory RoundSpec.SoundDetective.SameOrDifferent(ctx.level, a, second, if (same) null else minimalDifference(a.korean, b.korean), rate, pause)
            }
            "B" -> {
                val three = words(3) ?: continue
                val twice = ctx.random.nextInt(3)
                val played = (listOf(0, 1, 2) + twice).shuffled(ctx.random)
                return@RoundFactory RoundSpec.SoundDetective.CameTwice(ctx.level, three, played, twice, rate, pause)
            }
            else -> {
                val order = words(if (ctx.level == 3) 4 else 3) ?: continue
                return@RoundFactory RoundSpec.SoundDetective.WhatOrder(ctx.level, order, shuffledNotSame(order, ctx.random)!!, rate, pause)
            }
        }
    }
    null
}
