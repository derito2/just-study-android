package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.games.RoundContext
import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.games.wordleCandidates

/** F7 (FASE_11_PLAN PR 10). Level 1: the first syllable shows; 2: the meaning only; 3: the meaning and 5 attempts. */
val HangulWordleRounds = RoundFactory { ctx: RoundContext ->
    val candidates = wordleCandidates(ctx.bank)
    val item = ctx.pick(candidates.map { it.first }, 1).firstOrNull() ?: return@RoundFactory null
    RoundSpec.HangulWordle(ctx.level, item, candidates.first { it.first == item }.second, if (ctx.level == 3) 5 else 6, item.blocks.first().takeIf { ctx.level == 1 })
}
