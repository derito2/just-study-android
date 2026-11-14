package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec

/** Pairs word ↔ meaning: 5 (level 1) or 6; level 3: short sentences ↔ translation. No shared sense, no shared Korean. */
val WordMatchRounds = RoundFactory { ctx ->
    val n = level(ctx, 5, 6, 6)
    val pool = if (ctx.level == 3) ctx.bank.sentences.filter { it.korean.split(" ").size <= 4 } else ctx.bank.words
    val items = distinctSenses(ctx, pool, n) ?: return@RoundFactory null
    RoundSpec.WordMatch(ctx.level, items, items.indices.shuffled(ctx.random), items.indices.shuffled(ctx.random))
}
