package com.dero.juststudykorean.korean.games

import kotlin.random.Random

// FASE_8_PLAN 2 and 4.1: what Games plays with. Pure Kotlin: no Compose, no android.*.

/** The gesture family of a game (design/games-minigames.md, first table). */
enum class Gesture { Keyboard, Match, Order, Listen, Build, Drag, Correct, Trace }

enum class Skill { Writing, Vocabulary, Structure, Memory, Listening, Grammar }

/** The 13 games. [gentle] / [demanding]: how the selector weighs them after two lost rounds (5.2). */
enum class GameType(val gesture: Gesture, val skill: Skill, val gentle: Boolean = false, val demanding: Boolean = false, val needsVoice: Boolean = false) {
    Hangman(Gesture.Keyboard, Skill.Writing),
    WordMatch(Gesture.Match, Skill.Vocabulary, gentle = true),
    SentenceBuilder(Gesture.Order, Skill.Structure),
    MemoryFlip(Gesture.Match, Skill.Memory, gentle = true),
    WordScramble(Gesture.Order, Skill.Vocabulary, gentle = true),
    ListenGuess(Gesture.Listen, Skill.Listening, gentle = true, needsVoice = true),
    HangulBuilder(Gesture.Build, Skill.Writing),
    WordSort(Gesture.Drag, Skill.Vocabulary),
    GrammarFixer(Gesture.Correct, Skill.Grammar, demanding = true),
    WordRecall(Gesture.Order, Skill.Memory),
    HangulTyping(Gesture.Keyboard, Skill.Writing, demanding = true),
    WordPath(Gesture.Trace, Skill.Vocabulary, demanding = true),
    SoundDetective(Gesture.Listen, Skill.Listening, needsVoice = true),
}

enum class ItemKind { WORD, SENTENCE }

/**
 * A word or a sentence of your notes. [key]: "w:" or "s:" + norm(korean), stable across reprocessing and imports.
 * Phase 8, 0b: a word carries ALL its examples and notes (of every chunk and document); a sentence, its translation.
 */
data class GameItem(
    val key: String,
    val kind: ItemKind,
    val korean: String,
    val variants: List<String>,
    val meaning: String,
    val blocks: List<String>,
    val documentId: Long,
    val documentName: String,
    val page: Int,
    val examples: List<Pair<String, String>> = emptyList(),
    val notes: List<String> = emptyList(),
)

/** A grammar point of your notes, whole (0b): meaning, structure, explanation and every example. Key "g:" + norm(pattern). */
data class GamePoint(
    val key: String,
    val pattern: String,
    val meaning: String,
    val structure: List<String>,
    val explanation: String?,
    val examples: List<Pair<String, String>>,
    val documentId: Long,
    val documentName: String,
    val page: Int,
)

/** Everything Games can use, built once (GameBankBuilder). */
class GameBank(val words: List<GameItem>, val sentences: List<GameItem>, val points: List<GamePoint> = emptyList()) {
    val items: List<GameItem> = words + sentences
    val byKey: Map<String, GameItem> = items.associateBy { it.key }
    /** Every Korean form of the bank, compared as Games compares (nfc, no spaces). */
    val koreanForms: Set<String> = items.flatMap { listOf(it.korean) + it.variants }.map(::flat).toSet()
    val wordForms: Set<String> = words.flatMap { listOf(it.korean) + it.variants }.map(::flat).toSet()
    val allBlocks: Set<String> = words.flatMap { it.blocks }.toSet()

    fun isWord(korean: String) = flat(korean) in wordForms
}

enum class Outcome { WON, LOST, NEUTRAL, SKIPPED }

/** What a played round gave: [itemKeys] the targets; [failedKeys] the ones you got wrong. */
data class RoundResult(
    val outcome: Outcome,
    val mistakes: Int,
    val responseMs: Long,
    val itemKeys: List<String>,
    val failedKeys: List<String> = emptyList(),
    /** Lost because the moon's clock ran out (the ribbon says "Time's up", games.md 7.2). */
    val timedOut: Boolean = false,
)

/** Chooses [n] of [candidates] (ItemPicker weighs them by your results; the default: a uniform draw without repeats). */
fun interface Chooser {
    fun choose(candidates: List<GameItem>, n: Int, random: Random): List<GameItem>
}

val UniformChooser = Chooser { candidates, n, random -> candidates.shuffled(random).take(n) }

/** What a round factory gets: the bank, the level (1..3), the session's Random, how to choose, and what it may not use as a target (cooldown, 4.3). */
data class RoundContext(
    val bank: GameBank,
    val level: Int,
    val random: Random,
    val chooser: Chooser = UniformChooser,
    val avoid: Set<String> = emptySet(),
) {
    fun pick(candidates: List<GameItem>, n: Int): List<GameItem> = chooser.choose(candidates.filter { it.key !in avoid }, n, random)
}

/** A round factory: a round without ambiguity, or null (the engine picks another game). */
fun interface RoundFactory {
    fun build(ctx: RoundContext): RoundSpec?
}
