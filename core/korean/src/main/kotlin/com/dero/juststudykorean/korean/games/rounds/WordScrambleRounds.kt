package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.games.GameBank
import com.dero.juststudykorean.korean.games.GameItem
import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec

/** 4.5: no word whose blocks are all equal, or whose blocks in another order are another word of the bank. */
internal fun scramblable(item: GameItem, bank: GameBank): Boolean {
    if (item.blocks.distinct().size < 2) return false
    val sorted = item.blocks.sorted()
    return bank.words.none { it.key != item.key && it.blocks != item.blocks && it.blocks.sorted() == sorted }
}

/** Level 1: 2–3 blocks; 2: 3–4; 3: 4–5 and one extra block that does not belong. The first order is never the right one. */
val WordScrambleRounds = RoundFactory { ctx ->
    val sizes = when (ctx.level) { 1 -> 2..3; 2 -> 3..4; else -> 4..5 }
    val item = ctx.pick(ctx.bank.words.filter { it.blocks.size in sizes && scramblable(it, ctx.bank) }, 1).firstOrNull() ?: return@RoundFactory null
    val extra = if (ctx.level == 3) ctx.bank.allBlocks.filter { it !in item.blocks }.sorted().randomOrNull(ctx.random) ?: return@RoundFactory null else null
    // The blocks of the word are never in the right order, with or without the extra block among them.
    val order = shuffledNotSame(item.blocks, ctx.random) ?: return@RoundFactory null
    val tiles = if (extra == null) order else order.toMutableList().apply { add(ctx.random.nextInt(size + 1), extra) }
    RoundSpec.WordScramble(ctx.level, item, tiles, extra)
}
