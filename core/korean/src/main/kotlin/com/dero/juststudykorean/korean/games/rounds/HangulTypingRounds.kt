package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.games.RoundSpec.TypingMode
import com.dero.juststudykorean.korean.games.syllables

/** Level 1: copy; 2: from the meaning; 3: from memory, or a short sentence (up to 10 blocks) from its meaning. */
val HangulTypingRounds = RoundFactory { ctx ->
    if (ctx.level == 3 && ctx.random.nextBoolean()) {
        ctx.pick(ctx.bank.sentences.filter { syllables(it.korean).size <= 10 }, 1).firstOrNull()
            ?.let { return@RoundFactory RoundSpec.HangulTyping(3, it, TypingMode.FROM_MEANING) }
    }
    val item = ctx.pick(ctx.bank.words, 1).firstOrNull() ?: return@RoundFactory null
    RoundSpec.HangulTyping(ctx.level, item, when (ctx.level) { 1 -> TypingMode.COPY; 2 -> TypingMode.FROM_MEANING; else -> TypingMode.FROM_MEMORY })
}
