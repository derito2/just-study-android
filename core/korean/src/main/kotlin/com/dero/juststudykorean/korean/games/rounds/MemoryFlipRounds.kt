package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec

/** Level 1: 6 pairs (3 × 4); 2: 8 pairs (4 × 4); 3: 8 pairs, a wrong pair visible 700 ms instead of 1000. */
val MemoryFlipRounds = RoundFactory { ctx ->
    val n = level(ctx, 6, 8, 8)
    val items = distinctSenses(ctx, ctx.bank.words, n) ?: return@RoundFactory null
    val cards = items.indices.flatMap { listOf(it to false, it to true) }.shuffled(ctx.random)
    RoundSpec.MemoryFlip(ctx.level, items, cards, columns = if (n == 6) 3 else 4, shownMs = if (ctx.level == 3) 700 else 1000)
}
