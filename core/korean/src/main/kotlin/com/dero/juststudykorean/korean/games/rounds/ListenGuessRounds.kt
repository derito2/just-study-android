package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.games.GameItem
import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec

/**
 * Level 1: 6 words, cards with the meaning, 3 sound. 2: 6 look-alike words (same first consonant or same number of
 * blocks), cards in Korean, 3 sound. 3: 4 short sentences, cards with the translation, 2 sound. No shared sense or Korean.
 */
val ListenGuessRounds = RoundFactory { ctx ->
    val cards: List<GameItem> = when (ctx.level) {
        1 -> distinctSenses(ctx, ctx.bank.words, 6)
        2 -> {
            val first = ctx.pick(ctx.bank.words, 1).firstOrNull() ?: return@RoundFactory null
            val alike = ctx.bank.words.filter { it.key != first.key && (firstLead(it) == firstLead(first) || it.blocks.size == first.blocks.size) }
            distinctSenses(ctx.copy(avoid = ctx.avoid + first.key), alike, 5)?.let { listOf(first) + it }
        }
        else -> distinctSenses(ctx, ctx.bank.sentences.filter { it.korean.split(" ").size <= 5 }, 4)
    } ?: return@RoundFactory null
    val played = cards.shuffled(ctx.random).take(if (ctx.level == 3) 2 else 3)
    RoundSpec.ListenGuess(ctx.level, cards.shuffled(ctx.random), played, showKorean = ctx.level == 2)
}
