package com.dero.juststudykorean.korean.games

/**
 * One round of a game, everything decided (and shuffled) by its factory with the session's Random: the screen only
 * shows it. [targets]: the keys of the items the round asks about (they get the result).
 */
sealed class RoundSpec {
    abstract val game: GameType
    abstract val level: Int
    abstract val targets: List<String>

    /** 4.4. [keys]: the keyboard (syllables or jamo); [answer]: the units to find, in order (spaces stay as " "). */
    data class Hangman(override val level: Int, val item: GameItem, val jamoMode: Boolean, val answer: List<String>, val keys: List<String>, val lives: Int = 3) : RoundSpec() {
        override val game get() = GameType.Hangman
        override val targets get() = listOf(item.key)
    }

    /** Pairs word ↔ meaning; [meaningOrder]: the right column, as indexes of [items]. */
    data class WordMatch(override val level: Int, val items: List<GameItem>, val koreanOrder: List<Int>, val meaningOrder: List<Int>) : RoundSpec() {
        override val game get() = GameType.WordMatch
        override val targets get() = items.map { it.key }
    }

    /** 4.6. [tiles]: shuffled; [answer]: the order of your notes; [alsoRight]: other orders of the bank with the same meaning. */
    data class SentenceBuilder(override val level: Int, val item: GameItem, val tiles: List<String>, val answer: List<String>, val sign: String, val alsoRight: List<List<String>>) : RoundSpec() {
        override val game get() = GameType.SentenceBuilder
        override val targets get() = listOf(item.key)
    }

    /** [cards]: (index of [items], true for the meaning side), shuffled; [shownMs]: how long a wrong pair stays visible. */
    data class MemoryFlip(override val level: Int, val items: List<GameItem>, val cards: List<Pair<Int, Boolean>>, val columns: Int, val shownMs: Int) : RoundSpec() {
        override val game get() = GameType.MemoryFlip
        override val targets get() = items.map { it.key }
    }

    /** 4.5. [tiles]: the blocks in an order that is never the right one; [extra]: a block that does not belong (level 3). */
    data class WordScramble(override val level: Int, val item: GameItem, val tiles: List<String>, val extra: String?) : RoundSpec() {
        override val game get() = GameType.WordScramble
        override val targets get() = listOf(item.key)
    }

    /** [cards]: shown with the meaning (level 1, 3) or the Korean (level 2); [played]: the ones that sound, in order. */
    data class ListenGuess(override val level: Int, val cards: List<GameItem>, val played: List<GameItem>, val showKorean: Boolean) : RoundSpec() {
        override val game get() = GameType.ListenGuess
        override val targets get() = played.map { it.key }
    }

    /** [missing]: the index of the block to build; [pieces]: its jamo, in order; [tray]: the pieces and look-alikes, shuffled. */
    data class HangulBuilder(override val level: Int, val item: GameItem, val missing: Int, val pieces: List<Char>, val tray: List<Char>) : RoundSpec() {
        override val game get() = GameType.HangulBuilder
        override val targets get() = listOf(item.key)
    }

    /** 4.8. [bins]: their labels; [tiles]: (item, its bin), shuffled; [showMeaning]: level 1. */
    data class WordSort(override val level: Int, val bins: List<String>, val tiles: List<Pair<GameItem, Int>>, val showMeaning: Boolean) : RoundSpec() {
        override val game get() = GameType.WordSort
        override val targets get() = tiles.map { it.first.key }
    }

    /** 4.9. [words]: the sentence with the error; [wrong]: its index; [correct]: the fix; [options]: the fix and distractors, shuffled. */
    data class GrammarFixer(
        override val level: Int, val item: GameItem, val words: List<String>, val sign: String, val wrong: Int,
        val noun: String, val correct: String, val options: List<String>, val rule: String,
    ) : RoundSpec() {
        override val game get() = GameType.GrammarFixer
        override val targets get() = listOf(item.key)
    }

    /** [sequence]: the order to remember; [tiles]: the same words shuffled. */
    data class WordRecall(override val level: Int, val sequence: List<GameItem>, val tiles: List<GameItem>) : RoundSpec() {
        override val game get() = GameType.WordRecall
        override val targets get() = sequence.map { it.key }
    }

    enum class TypingMode { COPY, FROM_MEANING, FROM_MEMORY }

    data class HangulTyping(override val level: Int, val item: GameItem, val mode: TypingMode) : RoundSpec() {
        override val game get() = GameType.HangulTyping
        override val targets get() = listOf(item.key)
    }

    /** 4.7. [grid]: size × size blocks, row by row; [path]: the cells that spell the word (the only path that does). */
    data class WordPath(override val level: Int, val item: GameItem, val size: Int, val grid: List<String>, val path: List<Int>) : RoundSpec() {
        override val game get() = GameType.WordPath
        override val targets get() = listOf(item.key)
    }

    /** 4.10. A: same or different (a pair from your notes or the fixed list, [extra]); B: which came twice; C: what order. */
    sealed class SoundDetective : RoundSpec() {
        override val game get() = GameType.SoundDetective
        abstract val rate: Float
        abstract val pauseMs: Int

        data class SameOrDifferent(override val level: Int, val first: SoundWord, val second: SoundWord, val explanation: String?, override val rate: Float, override val pauseMs: Int) : SoundDetective() {
            val same get() = first.korean == second.korean
            override val targets get() = listOfNotNull(first.key, second.key).distinct()
        }

        data class CameTwice(override val level: Int, val words: List<GameItem>, val played: List<Int>, val twice: Int, override val rate: Float, override val pauseMs: Int) : SoundDetective() {
            override val targets get() = words.map { it.key }
        }

        data class WhatOrder(override val level: Int, val order: List<GameItem>, val tiles: List<GameItem>, override val rate: Float, override val pauseMs: Int) : SoundDetective() {
            override val targets get() = order.map { it.key }
        }
    }
}

/** A word of Sound Detective: from your notes ([key]) or from the fixed list (no key, [extra]). */
data class SoundWord(val korean: String, val meaning: String, val key: String?, val extra: Boolean)
