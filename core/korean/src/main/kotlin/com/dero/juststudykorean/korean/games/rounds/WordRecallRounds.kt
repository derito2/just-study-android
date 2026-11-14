package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.games.GameItem
import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec

/** Level 1: 3 words; 2: 4; 3: 5. Words of 1–3 blocks, each with a different first block. */
val WordRecallRounds = RoundFactory { ctx ->
    val n = level(ctx, 3, 4, 5)
    val chosen = mutableListOf<GameItem>()
    for (item in ctx.pick(ctx.bank.words.filter { it.blocks.size in 1..3 }, Int.MAX_VALUE)) {
        if (chosen.none { it.blocks.first() == item.blocks.first() }) chosen += item
        if (chosen.size == n) break
    }
    if (chosen.size < n) return@RoundFactory null
    RoundSpec.WordRecall(ctx.level, chosen, shuffledNotSame(chosen, ctx.random)!!)
}
