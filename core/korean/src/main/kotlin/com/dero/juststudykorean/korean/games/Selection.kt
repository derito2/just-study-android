package com.dero.juststudykorean.korean.games

import kotlin.random.Random

// FASE_8_PLAN 5.2 (GameSelector), 5.3 (level) and 5.4 (ItemPicker). Pure: the same inputs and Random give the same choice.

/** A round already played in this session: its game, result and targets (for the selector and the cooldown). */
data class PlayedRound(val game: GameType, val outcome: Outcome, val targets: List<String>)

/** Rounds a target rests before it can be a target again (4.3). */
const val COOLDOWN_ROUNDS = 8

fun cooldown(history: List<PlayedRound>): Set<String> = history.takeLast(COOLDOWN_ROUNDS).flatMap { it.targets }.toSet()

private fun lastTwoLost(history: List<PlayedRound>) = history.size >= 2 && history.takeLast(2).all { it.outcome == Outcome.LOST }

/** 5.2: which game comes next. [available]: the games that can be played, with their number of valid items. */
fun selectGame(available: Map<GameType, Int>, history: List<PlayedRound>, levels: Map<GameType, Int>, random: Random): GameType? {
    val previous = history.lastOrNull()?.game
    var candidates = available.keys.filter { it != previous }.sortedBy { it.ordinal }
    if (candidates.isEmpty()) return available.keys.singleOrNull() // only one game: it plays again
    val otherFamilies = candidates.filter { it.gesture != previous?.gesture }
    if (previous != null && otherFamilies.size >= 3) candidates = otherFamilies
    val recentSkills = history.takeLast(3).map { it.game.skill }.toSet()
    val struggling = lastTwoLost(history)
    val weights = candidates.map { game ->
        val last = history.indexOfLast { it.game == game }
        var w = if (last < 0) 1.5 else minOf(1.0, (history.size - last) / 5.0)
        if (game.skill !in recentSkills) w *= 1.4
        w *= minOf(1.0, (available[game] ?: 0) / 20.0)
        val demanding = game.demanding || (game == GameType.Hangman && (levels[game] ?: 1) >= 2) // Hangman in jamo mode
        if (struggling && game.gentle) w *= 2.0
        if (struggling && demanding) w *= 0.5
        w
    }
    val total = weights.sum()
    if (total <= 0) return candidates.first()
    var r = random.nextDouble() * total
    for ((i, w) in weights.withIndex()) { r -= w; if (r < 0) return candidates[i] }
    return candidates.last()
}

/** 5.3: a game's level from its last results (any session), newest first: up after 3 won in a row, down after 2 lost. */
fun levelFor(lastOutcomes: List<Outcome>): Int {
    var level = 1
    var won = 0
    var lost = 0
    for (o in lastOutcomes.take(6).asReversed()) when (o) {
        Outcome.WON -> { won++; lost = 0; if (won == 3) { level = minOf(3, level + 1); won = 0 } }
        Outcome.LOST -> { lost++; won = 0; if (lost == 2) { level = maxOf(1, level - 1); lost = 0 } }
        else -> Unit
    }
    return level
}

/** 5.3: after two lost rounds in the session (any game), the next one is a level easier (not saved). */
fun sessionLevel(level: Int, history: List<PlayedRound>): Int = if (lastTwoLost(history)) maxOf(1, level - 1) else level

/** An item's progress, as game_item_progress keeps it. */
data class ItemStats(val seen: Long, val correct: Long, val wrong: Long, val streak: Long, val lastSeenAt: Long, val lastWrongAt: Long?)

/** The progress of an item after a round where it was a target (the repository and the engine use this same rule). */
fun progressAfter(old: ItemStats?, wrong: Boolean, at: Long): ItemStats {
    val o = old ?: ItemStats(0, 0, 0, 0, at, null)
    return ItemStats(
        seen = o.seen + 1, correct = o.correct + if (wrong) 0 else 1, wrong = o.wrong + if (wrong) 1 else 0,
        streak = if (wrong) 0 else o.streak + 1, lastSeenAt = at, lastWrongAt = if (wrong) at else o.lastWrongAt,
    )
}

private const val DAY = 86_400_000L

/** 5.4: weak × 3, never seen × 1.5, mastered × 0.3. [now] is the session's start, so a session is reproducible. */
fun itemWeight(item: GameItem, progress: Map<String, ItemStats>, now: Long): Double {
    val p = progress[item.key] ?: return 1.5
    var w = 1.0
    if (p.wrong > p.correct || (p.lastWrongAt != null && now - p.lastWrongAt < 7 * DAY)) w *= 3.0
    if (p.streak >= 3 && now - p.lastSeenAt < 2 * DAY) w *= 0.3
    return w
}

/** 5.4: a weighted draw without repeats (the cooldown is the context's [RoundContext.avoid]: weight 0). */
class ItemPicker(private val progress: Map<String, ItemStats>, private val now: Long) : Chooser {
    override fun choose(candidates: List<GameItem>, n: Int, random: Random): List<GameItem> {
        val pool = candidates.distinctBy { it.key }.map { it to itemWeight(it, progress, now) }.toMutableList()
        val out = mutableListOf<GameItem>()
        while (out.size < n && pool.isNotEmpty()) {
            val total = pool.sumOf { it.second }
            var r = random.nextDouble() * total
            var i = pool.indexOfFirst { r -= it.second; r < 0 }
            if (i < 0) i = pool.lastIndex
            out += pool.removeAt(i).first
        }
        return out
    }
}
