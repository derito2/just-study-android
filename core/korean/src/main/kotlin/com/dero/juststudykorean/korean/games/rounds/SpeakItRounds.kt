package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.englishSenses
import com.dero.juststudykorean.korean.games.GameBank
import com.dero.juststudykorean.korean.games.GameItem
import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec

/** Words whose English no other word of your notes shares: what you say can only be one word (4.3). */
internal fun speakable(bank: GameBank): List<GameItem> {
    val senses = bank.words.associateWith { englishSenses(it.meaning) }
    val count = senses.values.flatten().groupingBy { it }.eachCount()
    return bank.words.filter { w -> senses.getValue(w).let { s -> s.isNotEmpty() && s.all { count.getValue(it) == 1 } } }
}

/** F7 (FASE_11_PLAN PR 11): the English, said in Korean. Level 1: words of up to 2 syllables; 2: up to 3; 3: up to 5. */
val SpeakItRounds = RoundFactory { ctx ->
    val max = level(ctx, 2, 3, 5)
    ctx.pick(speakable(ctx.bank).filter { it.blocks.size in 1..max }, 1).firstOrNull()?.let { RoundSpec.SpeakIt(ctx.level, it) }
}
