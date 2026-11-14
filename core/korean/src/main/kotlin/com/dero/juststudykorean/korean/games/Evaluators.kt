package com.dero.juststudykorean.korean.games

import com.dero.juststudykorean.korean.hangul.compose
import com.dero.juststudykorean.korean.hangul.joinVowels
import com.dero.juststudykorean.korean.norm

// The pure checks of every game (FASE_8_PLAN 2): the screen asks, these answer. No Compose, no android.*.

object Evaluators {
    /** Hangman: the positions of [answer] a key reveals (a jamo key reveals every place with that exact jamo). */
    fun hangmanReveals(spec: RoundSpec.Hangman, key: String): List<Int> = spec.answer.indices.filter { spec.answer[it] == key }

    /** Hangman: won once every unit (not the spaces) is revealed. */
    fun hangmanSolved(spec: RoundSpec.Hangman, revealed: Set<Int>): Boolean = spec.answer.indices.all { it in revealed || spec.answer[it] == " " }

    /** Word Match and Memory Flip: the Korean of [a] goes with the meaning of [b]. */
    fun samePair(a: Int, b: Int): Boolean = a == b

    fun memoryPair(spec: RoundSpec.MemoryFlip, first: Int, second: Int): Boolean {
        val (x, xMeaning) = spec.cards[first]
        val (y, yMeaning) = spec.cards[second]
        return first != second && x == y && xMeaning != yMeaning
    }

    /** 4.6: won in the order of your notes (or of another sentence with the same tiles and meaning); neutral with the
     *  same tiles, another order and the same last tile; else lost. */
    fun sentence(spec: RoundSpec.SentenceBuilder, order: List<String>): Outcome = when {
        order == spec.answer || order in spec.alsoRight -> Outcome.WON
        order.sorted() == spec.answer.sorted() && order.lastOrNull() == spec.answer.last() -> Outcome.NEUTRAL
        else -> Outcome.LOST
    }

    /** Word Scramble: the blocks placed (the extra block left out) spell the word. */
    fun scramble(spec: RoundSpec.WordScramble, placed: List<String>): Boolean = placed == spec.item.blocks

    fun listen(spec: RoundSpec.ListenGuess, target: Int, tapped: GameItem): Boolean = spec.played[target].key == tapped.key

    /** Hangul Builder: the pieces placed build the missing block (two vowels placed apart join, ㅗ + ㅏ = ㅘ). */
    fun builder(spec: RoundSpec.HangulBuilder, placed: List<Char>): Boolean {
        if (placed.size != spec.pieces.size || placed.size < 2) return false
        val block = spec.item.blocks[spec.missing].single()
        val whole = compose(placed[0], placed[1], placed.drop(2).joinToString(""))
        val joined = placed.getOrNull(2)?.let { joinVowels(placed[1], it) }?.let { compose(placed[0], it, placed.drop(3).joinToString("")) }
        return whole == block || joined == block
    }

    fun sort(spec: RoundSpec.WordSort, tile: Int, bin: Int): Boolean = spec.tiles[tile].second == bin

    /** Grammar Fixer: first the word with the error, then the fix. */
    fun fixerWord(spec: RoundSpec.GrammarFixer, index: Int): Boolean = index == spec.wrong

    fun fixerFix(spec: RoundSpec.GrammarFixer, option: String): Boolean = option == spec.correct

    /** Word Recall: the [position]-th tap is right. */
    fun recall(spec: RoundSpec.WordRecall, position: Int, tapped: GameItem): Boolean = spec.sequence.getOrNull(position)?.key == tapped.key

    /** Hangul Typing: compared as Korean is compared (nfc, no spaces); any form of "가장 / 제일" counts; the final sign does not. */
    fun typing(spec: RoundSpec.HangulTyping, typed: String): Boolean {
        val given = flatKorean(typed).trimEnd('.', '?', '!')
        return given.isNotEmpty() && (spec.item.variants + spec.item.korean).any { flatKorean(it).trimEnd('.', '?', '!') == given }
    }

    sealed interface PathCheck {
        data object Target : PathCheck
        data class OtherWord(val item: GameItem) : PathCheck
        data object Wrong : PathCheck
    }

    /** Word Path: the word, another word of your notes (no attempt spent), or wrong. */
    fun path(spec: RoundSpec.WordPath, cells: List<Int>, bank: GameBank): PathCheck {
        val spelt = cells.joinToString("") { spec.grid[it] }
        if (cells == spec.path || spelt == spec.item.blocks.joinToString("")) return PathCheck.Target
        return bank.words.firstOrNull { it.key != spec.item.key && it.variants.any { v -> flatKorean(v) == spelt } }?.let { PathCheck.OtherWord(it) } ?: PathCheck.Wrong
    }

    fun sameOrDifferent(spec: RoundSpec.SoundDetective.SameOrDifferent, saidSame: Boolean): Boolean = saidSame == spec.same

    fun cameTwice(spec: RoundSpec.SoundDetective.CameTwice, word: Int): Boolean = word == spec.twice

    fun order(spec: RoundSpec.SoundDetective.WhatOrder, order: List<GameItem>): Boolean = order.map { it.key } == spec.order.map { it.key }

    /** Two meanings say the same (for tests and the summary). */
    fun sameMeaning(a: String, b: String) = norm(a) == norm(b)
}
