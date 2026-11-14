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

/** The 13 games; the two listening games only come when the phone has an offline Korean voice (the engine checks). */
val PLAYABLE: Set<GameType> = setOf(
    GameType.ListenGuess, GameType.SoundDetective,
    GameType.WordMatch, GameType.MemoryFlip, GameType.WordScramble, GameType.SentenceBuilder, GameType.WordRecall,
    GameType.Hangman, GameType.HangulBuilder, GameType.HangulTyping,
    GameType.WordSort, GameType.WordPath, GameType.GrammarFixer,
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

/** The hint pill: the meaning of your notes, where the game shows one. */
fun hintOf(spec: RoundSpec): String? = when (spec) {
    is RoundSpec.Hangman -> spec.item.meaning
    is RoundSpec.WordScramble -> spec.item.meaning
    is RoundSpec.HangulBuilder -> spec.item.meaning
    is RoundSpec.WordPath -> spec.item.meaning
    else -> null
}

/** The document the round's content comes from (null: Extra, the fixed sound pairs). */
fun originOf(spec: RoundSpec, bank: GameBank = GameBank(emptyList(), emptyList())): String? = when (spec) {
    is RoundSpec.WordMatch -> spec.items.first().documentName
    is RoundSpec.MemoryFlip -> spec.items.first().documentName
    is RoundSpec.ListenGuess -> spec.cards.first().documentName
    is RoundSpec.WordSort -> spec.tiles.first().first.documentName
    is RoundSpec.WordRecall -> spec.sequence.first().documentName
    is RoundSpec.Hangman -> spec.item.documentName
    is RoundSpec.SentenceBuilder -> spec.item.documentName
    is RoundSpec.WordScramble -> spec.item.documentName
    is RoundSpec.HangulBuilder -> spec.item.documentName
    is RoundSpec.GrammarFixer -> spec.item.documentName
    is RoundSpec.HangulTyping -> spec.item.documentName
    is RoundSpec.WordPath -> spec.item.documentName
    is RoundSpec.SoundDetective.SameOrDifferent -> if (spec.first.extra || spec.second.extra) null else spec.first.key?.let { bank.byKey[it]?.documentName }
    is RoundSpec.SoundDetective.CameTwice -> spec.words.first().documentName
    is RoundSpec.SoundDetective.WhatOrder -> spec.order.first().documentName
    else -> null
}

/** What the lost ribbon shows after "Answer:" (null where the round already showed it all). */
fun answerOf(spec: RoundSpec): String? = when (spec) {
    is RoundSpec.Hangman -> "${spec.item.korean} — ${spec.item.meaning}"
    is RoundSpec.WordScramble -> "${spec.item.korean} — ${spec.item.meaning}"
    is RoundSpec.HangulBuilder -> "${spec.item.korean} — ${spec.item.meaning}"
    is RoundSpec.HangulTyping -> "${spec.item.korean} — ${spec.item.meaning}"
    is RoundSpec.WordPath -> "${spec.item.korean} — ${spec.item.meaning}"
    is RoundSpec.SentenceBuilder -> spec.item.korean
    is RoundSpec.GrammarFixer -> spec.item.korean
    is RoundSpec.WordRecall -> spec.sequence.joinToString(" · ") { it.korean }
    else -> null
}

/** The neutral ribbon's text (Sentence Builder: another right order). */
fun neutralText(spec: RoundSpec): String = when (spec) {
    is RoundSpec.SentenceBuilder -> "Your notes say it this way:"
    else -> "Almost."
}
