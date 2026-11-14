package com.dero.juststudykorean.korean.games.rounds

import com.dero.juststudykorean.korean.games.RoundFactory
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.korean.hangul.LEADS
import com.dero.juststudykorean.korean.hangul.VOWELS
import com.dero.juststudykorean.korean.hangul.compose
import com.dero.juststudykorean.korean.hangul.decompose
import com.dero.juststudykorean.korean.hangul.jamoOf
import com.dero.juststudykorean.korean.hangul.vowelParts
import com.dero.juststudykorean.korean.norm

private const val DOUBLES = "ㄲㄸㅃㅆㅉ"

/**
 * A word of 2–4 blocks with one block to build. Level 1: a block without 받침; 2: with a simple 받침 or a double
 * consonant; 3: a vowel made of two vowels (ㅗ + ㅏ) or a compound 받침. Tray: 6 pieces (level 3: 8), the needed ones
 * and look-alikes; a look-alike that would build another word of your notes with the same meaning is left out.
 */
val HangulBuilderRounds = RoundFactory { ctx ->
    fun fits(block: Char): Boolean {
        val b = decompose(block) ?: return false
        return when (ctx.level) {
            1 -> b.tail.isEmpty() && vowelParts(b.vowel) == null && b.lead !in DOUBLES
            2 -> (b.tail.length == 1 || b.lead in DOUBLES) && vowelParts(b.vowel) == null
            else -> vowelParts(b.vowel) != null || b.tail.length == 2
        }
    }
    val candidates = ctx.bank.words.filter { w -> w.blocks.size in 2..4 && w.blocks.any { fits(it.single()) } }
    val item = ctx.pick(candidates, 1).firstOrNull() ?: return@RoundFactory null
    val missing = item.blocks.indices.filter { fits(item.blocks[it].single()) }.random(ctx.random)
    val block = item.blocks[missing].single()
    val pieces = jamoOf(block, splitVowel = ctx.level == 3)
    val size = if (ctx.level == 3) 8 else 6
    fun buildsAnotherAnswer(piece: Int, other: Char): Boolean {
        val changed = pieces.toMutableList().also { it[piece] = other }
        val b = decompose(block)!!
        val vowelCount = if (ctx.level == 3 && vowelParts(b.vowel) != null) 2 else 1
        val vowel = if (vowelCount == 2) com.dero.juststudykorean.korean.hangul.joinVowels(changed[1], changed[2]) else changed[1]
        val built = vowel?.let { compose(changed[0], it, changed.drop(1 + vowelCount).joinToString("")) } ?: return false
        val word = item.blocks.toMutableList().also { it[missing] = built.toString() }.joinToString("")
        return ctx.bank.words.any { it.key != item.key && it.korean == word && norm(it.meaning) == norm(item.meaning) }
    }
    fun safe(i: Int, c: Char) = c !in pieces && !buildsAnotherAnswer(i, c)
    val alike = pieces.flatMapIndexed { i, p -> LOOK_ALIKES.filter { p in it }.flatMap { it - p }.filter { safe(i, it) } }.distinct().shuffled(ctx.random)
    // shortcut: the plan asks for look-alikes but a block has at most ~3; the rest of the tray are other jamo of the same
    // kind (consonant or vowel) as a piece, each checked the same way. Ask the owner if the tray should rather shrink.
    val sameKind = pieces.flatMapIndexed { i, p ->
        (if (p in VOWELS) VOWELS.filter { vowelParts(it) == null } else LEADS).filter { safe(i, it) }.toList()
    }.distinct().filter { it !in alike }.sorted().shuffled(ctx.random)
    val distractors = (alike + sameKind).take(size - pieces.size)
    if (pieces.size + distractors.size < size) return@RoundFactory null
    RoundSpec.HangulBuilder(ctx.level, item, missing, pieces, (pieces + distractors).shuffled(ctx.random))
}
