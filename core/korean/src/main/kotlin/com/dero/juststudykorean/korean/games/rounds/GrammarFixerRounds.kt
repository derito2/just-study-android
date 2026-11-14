package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.games.particleOptions
import com.dero.juststudykorean.korean.games.particleSpots
import com.dero.juststudykorean.korean.games.sentenceTiles

/**
 * 4.9 (D8-7). A sentence of your notes where one known noun + particle gets the wrong particle by the 받침. Level 1:
 * 2–4 words, 2 fixes offered; 2: 4–6 words, 3 fixes; 3: up to 8 words, 3 fixes (other right particles may stay).
 */
val GrammarFixerRounds = RoundFactory { ctx ->
    val sizes = when (ctx.level) { 1 -> 2..4; 2 -> 4..6; else -> 2..8 }
    val candidates = ctx.bank.sentences.filter { sentenceTiles(it.korean).first.size in sizes }
    for (item in ctx.pick(candidates, candidates.size)) {
        val (words, sign) = sentenceTiles(item.korean)
        val spots = particleSpots(words, ctx.bank)
        if (spots.isEmpty()) continue
        val spot = spots.random(ctx.random)
        val broken = words.toMutableList().also { it[spot.index] = spot.noun + spot.wrong }
        val options = particleOptions(spot, if (ctx.level == 1) 1 else 2, ctx.random)
        return@RoundFactory RoundSpec.GrammarFixer(ctx.level, item, broken, sign, spot.index, spot.noun, spot.particle, options, spot.pair.rule)
    }
    null
}
