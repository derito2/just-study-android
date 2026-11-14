package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.games.sentenceTiles
import com.dero.juststudykorean.korean.norm

/** 4.6. A sentence with its translation; level 1: 3–4 words, 2: 4–5, 3: 5–7. The tiles never start in the right order. */
val SentenceBuilderRounds = RoundFactory { ctx ->
    val sizes = when (ctx.level) { 1 -> 3..4; 2 -> 4..5; else -> 5..7 }
    val candidates = ctx.bank.sentences.filter { sentenceTiles(it.korean).first.size in sizes }
    for (item in ctx.pick(candidates, candidates.size)) {
        val (answer, sign) = sentenceTiles(item.korean)
        val tiles = shuffledNotSame(answer, ctx.random) ?: continue
        // Another sentence of your notes with the same tiles and the same meaning is also right.
        val also = ctx.bank.sentences.filter { it.key != item.key && norm(it.meaning) == norm(item.meaning) }
            .map { sentenceTiles(it.korean).first }.filter { it.sorted() == answer.sorted() && it != answer }
        return@RoundFactory RoundSpec.SentenceBuilder(ctx.level, item, tiles, answer, sign, also)
    }
    null
}
