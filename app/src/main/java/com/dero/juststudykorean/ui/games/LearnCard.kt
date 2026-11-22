package com.dero.juststudykorean.ui.games

import androidx.compose.runtime.staticCompositionLocalOf
import com.dero.juststudykorean.korean.games.GameItem
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec

// design/games-characters.md 6: the learning moment of each round. Pure functions (tested on the JVM); the card itself is in GamesScreen.

/** What the card teaches: a word and its meaning, or a sentence ([sentence]: shown in 20 sp) and its translation. */
data class Learn(val korean: String, val meaning: String, val sentence: Boolean = false)

private fun GameItem.word() = Learn(korean, meaning)

/**
 * The word of the round. Several words (Word Match, Memory Flip, Word Sort, Listen & Guess, Word Recall): in a lost round the first you
 * missed; in a won one, the spec asks for the word with the fewest hits so far (`game_item_progress`). That table is not read here yet,
 * so a won round teaches the first word. Sentence games teach the sentence. null: nothing to teach.
 */
fun learnOf(round: RoundSpec, result: RoundResult): Learn? {
    fun many(items: List<GameItem>): Learn? {
        if (items.isEmpty()) return null
        val lost = result.outcome == Outcome.LOST
        val first = if (lost) result.failedKeys.firstNotNullOfOrNull { k -> items.firstOrNull { it.key == k } } else null
        return (first ?: items.first()).word()
    }
    return when (round) {
        is RoundSpec.Hangman -> round.item.word()
        is RoundSpec.WordScramble -> round.item.word()
        is RoundSpec.HangulBuilder -> round.item.word()
        is RoundSpec.HangulTyping -> round.item.word()
        is RoundSpec.WordPath -> round.item.word()
        is RoundSpec.SentenceBuilder -> Learn(round.item.korean, round.item.meaning, sentence = true)
        is RoundSpec.GrammarFixer -> Learn(round.item.korean, round.item.meaning, sentence = true)
        is RoundSpec.WordMatch -> many(round.items)
        is RoundSpec.MemoryFlip -> many(round.items)
        is RoundSpec.WordSort -> many(round.tiles.map { it.first })
        is RoundSpec.ListenGuess -> many(round.played.ifEmpty { round.cards })
        is RoundSpec.WordRecall -> many(round.sequence)
        is RoundSpec.SoundDetective.CameTwice -> many(round.words)
        is RoundSpec.SoundDetective.WhatOrder -> many(round.order)
        is RoundSpec.SoundDetective.SameOrDifferent -> Learn(round.first.korean, round.first.meaning)
    }
}

/** The listening games (6 and 13): when you win you already heard the word, so it is not said again; when you lose, it is. */
fun isListening(round: RoundSpec) = round is RoundSpec.ListenGuess || round is RoundSpec.SoundDetective

/**
 * How long the card stays. Won: 900 ms, or 300 ms after the voice ends (at most 2500). Lost or time's up: 2200 ms, or 600 ms after the voice
 * (at most 4000). [voiceEndMs]: when the voice finished, from the moment the card appeared; null when it did not sound.
 */
fun cardMs(won: Boolean, voiceEndMs: Long?): Long {
    val base = if (won) 900L else 2200L
    val gap = if (won) 300L else 600L
    val cap = if (won) 2500L else 4000L
    return if (voiceEndMs == null) base else maxOf(base, voiceEndMs + gap).coerceAtMost(cap)
}

/** "Say the word after each round" (D10-3): the setting and its writer, provided by the screen; the default is on and does nothing. */
class SpeakWordSetting(val on: Boolean, val set: (Boolean) -> Unit)

val LocalSpeakWord = staticCompositionLocalOf { SpeakWordSetting(true) { } }
