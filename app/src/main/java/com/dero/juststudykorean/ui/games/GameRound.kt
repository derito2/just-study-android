package com.dero.juststudykorean.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.dero.juststudykorean.korean.games.GameBank
import com.dero.juststudykorean.korean.games.GameType
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.RoundSpec
import com.dero.juststudykorean.ui.games.minigames.GrammarFixerGame
import com.dero.juststudykorean.ui.games.minigames.HangmanGame
import com.dero.juststudykorean.ui.games.minigames.ListenGuessGame
import com.dero.juststudykorean.ui.games.minigames.SoundDetectiveGame
import com.dero.juststudykorean.ui.games.minigames.HangulBuilderGame
import com.dero.juststudykorean.ui.games.minigames.HangulTypingGame
import com.dero.juststudykorean.ui.games.minigames.MemoryFlipGame
import com.dero.juststudykorean.ui.games.minigames.SentenceBuilderGame
import com.dero.juststudykorean.ui.games.minigames.WordMatchGame
import com.dero.juststudykorean.ui.games.minigames.WordPathGame
import com.dero.juststudykorean.ui.games.minigames.WordRecallGame
import com.dero.juststudykorean.ui.games.minigames.WordScrambleGame
import com.dero.juststudykorean.ui.games.minigames.WordSortGame
import com.dero.juststudykorean.ui.games.stage.StageScope

/** The 17 games; the two listening games only come with an offline Korean voice, Speak It with the speech recognizer and the microphone (the engine checks). */
val PLAYABLE: Set<GameType> = setOf(
    GameType.ListenGuess, GameType.SoundDetective,
    GameType.WordMatch, GameType.MemoryFlip, GameType.WordScramble, GameType.SentenceBuilder, GameType.WordRecall,
    GameType.Hangman, GameType.HangulBuilder, GameType.HangulTyping,
    GameType.WordSort, GameType.WordPath, GameType.GrammarFixer,
    GameType.HangulWordle, GameType.Crossword, GameType.ConjugationRunner, GameType.SpeakIt,
)

/** The bank of your notes, for Word Path's "another word of your notes" (empty in tests and before it loads). */
val LocalGameBank = staticCompositionLocalOf { GameBank(emptyList(), emptyList()) }

/**
 * One round of a minigame (FASE_8_PLAN 2): XGame(spec, paused, onResult, stage). A game doesn't know which comes next
 * nor touches Room. [timer]: the typing games' clock is on (and TalkBack off).
 */
@Composable
fun GameRound(spec: RoundSpec, paused: Boolean, stage: StageScope, timer: Boolean, onResult: (RoundResult) -> Unit) {
    when (spec) {
        is RoundSpec.WordMatch -> WordMatchGame(spec, paused, onResult, stage)
        is RoundSpec.MemoryFlip -> MemoryFlipGame(spec, paused, onResult, stage)
        is RoundSpec.WordScramble -> WordScrambleGame(spec, paused, onResult, stage)
        is RoundSpec.SentenceBuilder -> SentenceBuilderGame(spec, paused, onResult, stage)
        is RoundSpec.WordRecall -> WordRecallGame(spec, paused, onResult, stage)
        is RoundSpec.Hangman -> HangmanGame(spec, paused, onResult, stage)
        is RoundSpec.HangulBuilder -> HangulBuilderGame(spec, paused, onResult, stage)
        is RoundSpec.HangulTyping -> HangulTypingGame(spec, paused, timer, onResult, stage)
        is RoundSpec.WordSort -> WordSortGame(spec, paused, onResult, stage)
        is RoundSpec.WordPath -> WordPathGame(spec, paused, onResult, stage)
        is RoundSpec.GrammarFixer -> GrammarFixerGame(spec, paused, onResult, stage)
        is RoundSpec.ListenGuess -> ListenGuessGame(spec, paused, onResult, stage)
        is RoundSpec.SoundDetective -> SoundDetectiveGame(spec, paused, onResult, stage)
        is RoundSpec.HangulWordle -> com.dero.juststudykorean.ui.games.minigames.HangulWordleGame(spec, paused, onResult, stage)
        is RoundSpec.Crossword -> com.dero.juststudykorean.ui.games.minigames.CrosswordGame(spec, paused, onResult, stage)
        is RoundSpec.ConjugationRunner -> com.dero.juststudykorean.ui.games.minigames.ConjugationRunnerGame(spec, paused, onResult, stage)
        is RoundSpec.SpeakIt -> com.dero.juststudykorean.ui.games.minigames.SpeakItGame(spec, paused, onResult, stage)
    }
}

/** The instruction of the round (some games change it by level or phase). */
fun instructionOf(spec: RoundSpec, text: GameText): Pair<String, String> = when {
    spec is RoundSpec.HangulTyping && spec.mode == RoundSpec.TypingMode.FROM_MEANING -> "Type it in Korean." to "한국어로 쓰세요."
    spec is RoundSpec.HangulTyping && spec.mode == RoundSpec.TypingMode.FROM_MEMORY -> "Type what you saw." to "본 것을 쓰세요."
    spec is RoundSpec.SoundDetective.CameTwice -> "Which word did you hear twice?" to "두 번 들린 단어는 뭐예요?"
    spec is RoundSpec.SoundDetective.WhatOrder -> "Put them in the order you heard." to "들은 순서대로 놓으세요."
    else -> text.instruction to text.instructionKo
}

/**
 * The round's tip (owner, PR 10): hidden behind the lightbulb until asked; null where the level gives none (then no
 * button). Hangman, Word Scramble, Hangul Builder and Word Path: the meaning; Hangul Wordle: its first syllable (level 1).
 */
fun tipOf(spec: RoundSpec): String? = when (spec) {
    is RoundSpec.Hangman -> spec.item.meaning
    is RoundSpec.WordScramble -> spec.item.meaning
    is RoundSpec.HangulBuilder -> spec.item.meaning
    is RoundSpec.WordPath -> spec.item.meaning
    is RoundSpec.HangulWordle -> spec.firstSyllable?.let { "Starts with $it" }
    else -> null
}

/** What the lost ribbon shows after "Answer:" (null where the round already showed it all). */
fun answerOf(spec: RoundSpec): String? = when (spec) {
    is RoundSpec.Hangman -> "${spec.item.korean} — ${spec.item.meaning}"
    is RoundSpec.WordScramble -> "${spec.item.korean} — ${spec.item.meaning}"
    is RoundSpec.HangulBuilder -> "${spec.item.korean} — ${spec.item.meaning}"
    is RoundSpec.HangulTyping -> "${spec.item.korean} — ${spec.item.meaning}"
    is RoundSpec.WordPath -> "${spec.item.korean} — ${spec.item.meaning}"
    is RoundSpec.HangulWordle -> "${spec.item.korean} — ${spec.item.meaning}"
    is RoundSpec.SentenceBuilder -> spec.item.korean
    is RoundSpec.GrammarFixer -> spec.item.korean
    is RoundSpec.SpeakIt -> "${spec.item.korean} — ${spec.item.meaning}"
    is RoundSpec.ConjugationRunner -> null // each form was shown as it went
    is RoundSpec.WordRecall -> spec.sequence.joinToString(" · ") { it.korean }
    else -> null
}

/** The neutral ribbon's text (Sentence Builder: another right order; Speak It: said, or skipped; never "Not quite", D9-6). */
fun neutralText(spec: RoundSpec, result: RoundResult? = null): String = when (spec) {
    is RoundSpec.SentenceBuilder -> "Your notes say it this way:"
    is RoundSpec.SpeakIt -> if (result?.failedKeys.isNullOrEmpty()) "You said it!" else "Here it is:"
    else -> "Almost."
}
