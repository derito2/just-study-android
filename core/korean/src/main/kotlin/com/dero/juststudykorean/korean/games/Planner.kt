package com.dero.juststudykorean.korean.games

// FASE_8_PLAN 5.1 and 5.5: round n of a session as a pure function of the session's state. The engine uses it for the
// next round (and its two candidates while a round is played) and to regenerate the same round after a kill.

/** What decides a round: nothing else (no clock: the weights' "now" is the session's start). */
data class SessionInputs(
    val bank: GameBank,
    val seed: Long,
    val startedAt: Long,
    /** The games that can be played (4.2 and the voice), with their number of valid items. */
    val available: Map<GameType, Int>,
    val history: List<PlayedRound>,
    val progress: Map<String, ItemStats>,
    /** Each game's last results, of any session, newest first (5.3). */
    val lastOutcomes: Map<GameType, List<Outcome>>,
) {
    /** The state after a round: the same rule the repository saves with (a lost round or a failed key is wrong). */
    fun after(game: GameType, result: RoundResult): SessionInputs {
        if (result.outcome == Outcome.SKIPPED) return this
        val p = progress.toMutableMap()
        if (result.outcome != Outcome.NEUTRAL) for (key in result.itemKeys.distinct()) {
            p[key] = progressAfter(p[key], wrong = key in result.failedKeys || result.outcome == Outcome.LOST, at = maxOf(startedAt, p[key]?.lastSeenAt ?: 0))
        }
        val outcomes = lastOutcomes.toMutableMap().also { it[game] = listOf(result.outcome) + it[game].orEmpty() }
        return copy(history = history + PlayedRound(game, result.outcome, result.itemKeys), progress = p, lastOutcomes = outcomes)
    }
}

data class RoundPlan(val index: Int, val game: GameType, val level: Int, val spec: RoundSpec)

/**
 * The game and round [index] of the session: Random(seed, index) picks the game; its factory builds the round; if it
 * cannot (null), that game is left out and another one is picked. Null only if no game can make a round.
 */
fun planRound(inputs: SessionInputs, index: Int): RoundPlan? {
    val random = roundRandom(inputs.seed, index)
    val levels = inputs.available.keys.associateWith { levelFor(inputs.lastOutcomes[it].orEmpty()) }
    val picker = ItemPicker(inputs.progress, inputs.startedAt)
    // shortcut: the cooldown (4.3) is 8 rounds; only if no game can make a round with it (a single game with little
    // content), it is shortened (4, then none) so the session goes on. The factories' no-ambiguity rule never changes.
    for (rounds in listOf(COOLDOWN_ROUNDS, COOLDOWN_ROUNDS / 2, 0)) {
        val avoid = inputs.history.takeLast(rounds).flatMap { it.targets }.toSet()
        val candidates = inputs.available.toMutableMap()
        while (candidates.isNotEmpty()) {
            val game = selectGame(candidates, inputs.history, levels, random) ?: break
            val level = sessionLevel(levels.getValue(game), inputs.history)
            val spec = FACTORIES.getValue(game).build(RoundContext(inputs.bank, level, random, picker, avoid))
            if (spec != null) return RoundPlan(index, game, level, spec)
            candidates.remove(game)
        }
    }
    return null
}

/** The games that can be played with this bank (and the voice), with their number of valid items. */
fun availableGames(bank: GameBank, voice: Boolean): Map<GameType, Int> =
    GameType.entries.filter { available(it, bank, voice) }.associateWith { validItems(it, bank) }
