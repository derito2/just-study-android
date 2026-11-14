package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.games.ItemKind
import com.dero.juststudykorean.korean.games.RoundContext
import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.hangul.LEADS
import com.dero.juststudykorean.korean.hangul.VOWELS
import com.dero.juststudykorean.korean.hangul.jamoOf
import com.dero.juststudykorean.korean.games.syllables

private const val SYLLABLE_KEYS = 10
private const val MIN_DISTRACTORS = 3

/** The jamo keys of the jamo mode: every consonant and vowel; a compound vowel (ㅘ) is a key of its own (4.4, splitVowel = false). */
val JAMO_KEYS: List<String> = (LEADS + VOWELS).map { it.toString() }

/** 4.4. Level 1: syllables; 2: jamo; 3: jamo with a short phrase (up to 8 blocks, 2 spaces). */
val HangmanRounds = RoundFactory { ctx: RoundContext ->
    if (ctx.level == 3) {
        val phrases = ctx.bank.sentences.filter { syllables(it.korean).size in 2..8 && it.korean.count { c -> c == ' ' } in 1..2 && it.kind == ItemKind.SENTENCE }
        val item = ctx.pick(phrases, 1).firstOrNull() ?: return@RoundFactory null
        val answer = item.korean.trim().trimEnd('.', '?', '!').flatMap { c -> if (c == ' ') listOf(" ") else jamoOf(c).map { it.toString() } }
        return@RoundFactory RoundSpec.Hangman(3, item, jamoMode = true, answer = answer, keys = JAMO_KEYS)
    }
    val item = ctx.pick(ctx.bank.words.filter { it.blocks.size in 2..5 }, 1).firstOrNull() ?: return@RoundFactory null
    if (ctx.level == 2) {
        return@RoundFactory RoundSpec.Hangman(2, item, jamoMode = true, answer = item.blocks.flatMap { b -> jamoOf(b.single()).map { it.toString() } }, keys = JAMO_KEYS)
    }
    val mine = item.blocks.distinct()
    val distractors = ctx.bank.allBlocks.filter { b -> b !in mine && mine.any { similarBlock(it, b) } }.sorted().shuffled(ctx.random)
        .take(SYLLABLE_KEYS - mine.size)
    if (distractors.size < MIN_DISTRACTORS) return@RoundFactory null
    RoundSpec.Hangman(1, item, jamoMode = false, answer = item.blocks, keys = (mine + distractors).shuffled(ctx.random))
}
