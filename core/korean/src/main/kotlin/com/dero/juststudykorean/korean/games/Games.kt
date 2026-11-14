package com.dero.juststudykorean.korean.games

import com.dero.juststudykorean.korean.englishSenses
import com.dero.juststudykorean.korean.games.rounds.GrammarFixerRounds
import com.dero.juststudykorean.korean.games.rounds.HangmanRounds
import com.dero.juststudykorean.korean.games.rounds.HangulBuilderRounds
import com.dero.juststudykorean.korean.games.rounds.HangulTypingRounds
import com.dero.juststudykorean.korean.games.rounds.ListenGuessRounds
import com.dero.juststudykorean.korean.games.rounds.MemoryFlipRounds
import com.dero.juststudykorean.korean.games.rounds.SentenceBuilderRounds
import com.dero.juststudykorean.korean.games.rounds.SoundDetectiveRounds
import com.dero.juststudykorean.korean.games.rounds.WordMatchRounds
import com.dero.juststudykorean.korean.games.rounds.WordPathRounds
import com.dero.juststudykorean.korean.games.rounds.WordRecallRounds
import com.dero.juststudykorean.korean.games.rounds.WordScrambleRounds
import com.dero.juststudykorean.korean.games.rounds.WordSortRounds
import com.dero.juststudykorean.korean.games.rounds.scramblable
import com.dero.juststudykorean.korean.games.sentenceTiles
import kotlin.random.Random

/** One factory per game. */
val FACTORIES: Map<GameType, RoundFactory> = mapOf(
    GameType.Hangman to HangmanRounds, GameType.WordMatch to WordMatchRounds, GameType.SentenceBuilder to SentenceBuilderRounds,
    GameType.MemoryFlip to MemoryFlipRounds, GameType.WordScramble to WordScrambleRounds, GameType.ListenGuess to ListenGuessRounds,
    GameType.HangulBuilder to HangulBuilderRounds, GameType.WordSort to WordSortRounds, GameType.GrammarFixer to GrammarFixerRounds,
    GameType.WordRecall to WordRecallRounds, GameType.HangulTyping to HangulTypingRounds, GameType.WordPath to WordPathRounds,
    GameType.SoundDetective to SoundDetectiveRounds,
)

/** Under this many words: "not enough content" (games.md, 11). */
const val MIN_BANK_WORDS = 8

private fun distinctSenseCount(items: List<GameItem>): Int {
    val senses = mutableSetOf<String>()
    return items.count { item -> englishSenses(item.meaning).let { s -> s.isNotEmpty() && s.none { it in senses }.also { if (it) senses += s } } }
}

/** How many items of the bank the game can use (its minimum is in [available]; the selector also weighs by it, 5.2). */
fun validItems(game: GameType, bank: GameBank): Int = when (game) {
    GameType.Hangman, GameType.WordScramble -> bank.words.count { it.blocks.size in 2..5 && (game != GameType.WordScramble || scramblable(it, bank)) }
    GameType.WordMatch, GameType.MemoryFlip, GameType.ListenGuess -> distinctSenseCount(bank.words)
    GameType.SentenceBuilder -> bank.sentences.count { sentenceTiles(it.korean).first.size in 3..7 }
    GameType.HangulBuilder -> bank.words.count { it.blocks.size in 2..4 }
    GameType.WordSort -> bank.words.size
    GameType.GrammarFixer -> bank.sentences.count { particleSpots(sentenceTiles(it.korean).first, bank).isNotEmpty() }
    GameType.WordRecall -> bank.words.count { it.blocks.size in 1..3 }
    GameType.HangulTyping -> bank.words.size
    GameType.WordPath -> bank.words.count { it.blocks.size in 3..5 }
    GameType.SoundDetective -> bank.words.size
}

/** 4.2: the game has its minimum content (and the Korean voice, for the two listening games). */
fun available(game: GameType, bank: GameBank, voice: Boolean): Boolean {
    if (bank.words.size < MIN_BANK_WORDS || (game.needsVoice && !voice)) return false
    val n = validItems(game, bank)
    return when (game) {
        GameType.Hangman, GameType.WordScramble, GameType.HangulBuilder, GameType.HangulTyping -> n >= 10
        GameType.WordMatch, GameType.WordRecall -> n >= 8
        GameType.MemoryFlip, GameType.ListenGuess -> n >= 10
        GameType.SentenceBuilder, GameType.WordPath -> n >= 5 && (game != GameType.WordPath || bank.allBlocks.size >= 30)
        GameType.GrammarFixer -> n >= 3
        GameType.WordSort -> (1..3).any { WordSortRounds.build(RoundContext(bank, it, Random(0))) != null }
        GameType.SoundDetective -> n >= 6
    }
}

/** Round n of a session: Random(seed xor (n × 0x9E3779B97F4A7C15)) (5.5). */
fun roundRandom(seed: Long, round: Int): Random = Random(seed xor (round.toLong() * -7046029254386353131L))
