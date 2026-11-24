package com.dero.juststudykorean.korean.progress

import java.time.LocalDate

// D11-1: what "Learned" (mastered) and "Hard" mean everywhere in the app, from the evidence of one word. Pure.

const val MASTERED_INTERVAL_HOURS = 504L // 21 days
private const val NO_AGAIN_IN = 5
private const val HARD_WINDOW = 10
private const val HARD_AGAINS = 3
private const val GAME_STREAK = 3
private const val GAME_DAYS = 2
private const val AGAIN = "again"

/** A flashcard review: its rating ("again", "hard", "good", "easy"), the interval it left and when. */
data class Review(val rating: String, val newIntervalHours: Long, val at: Long)

/** One card of the word: its interval now and its reviews, oldest first. */
data class CardEvidence(val intervalHours: Long, val reviews: List<Review>)

/** Games: right and wrong totals, and when each round of the current streak was played, oldest first. */
data class GameEvidence(val correct: Long, val wrong: Long, val streak: List<Long>)

/** Everything known about a word; [lastQuizCorrect] is null when no quiz question is about it. */
data class WordEvidence(val cards: List<CardEvidence> = emptyList(), val game: GameEvidence? = null, val lastQuizCorrect: Boolean? = null)

/** [masteredAt]: the first moment the word met the rule; null when it is not learned now. */
data class WordStatus(val mastered: Boolean, val hard: Boolean, val masteredAt: Long?)

/** [day]: the local date of a moment (Games' streak needs two different days). */
fun wordStatus(e: WordEvidence, day: (Long) -> LocalDate): WordStatus {
    val masteredAt = listOfNotNull(e.cards.mapNotNull(::cardMasteredAt).minOrNull(), e.game?.let { gameMasteredAt(it, day) }).minOrNull()
    val lastTen = e.cards.flatMap { it.reviews }.sortedBy { it.at }.takeLast(HARD_WINDOW)
    val hard = lastTen.count { it.rating == AGAIN } >= HARD_AGAINS ||
        (e.game != null && e.game.wrong > e.game.correct) ||
        e.lastQuizCorrect == false
    return WordStatus(masteredAt != null, hard, masteredAt)
}

private fun noAgainEnding(reviews: List<Review>, i: Int) = reviews.subList(maxOf(0, i - NO_AGAIN_IN + 1), i + 1).none { it.rating == AGAIN }

/** Null unless the card is learned now; then the first review that met the rule (or its last one, for imported intervals). */
private fun cardMasteredAt(card: CardEvidence): Long? {
    val r = card.reviews
    if (r.isEmpty() || card.intervalHours < MASTERED_INTERVAL_HOURS || !noAgainEnding(r, r.lastIndex)) return null
    return r.indices.firstOrNull { r[it].newIntervalHours >= MASTERED_INTERVAL_HOURS && noAgainEnding(r, it) }?.let { r[it].at } ?: r.last().at
}

/** Null unless the current streak has ≥ 3 rounds over ≥ 2 days; then the round that first made it so. */
private fun gameMasteredAt(game: GameEvidence, day: (Long) -> LocalDate): Long? {
    val s = game.streak
    return (GAME_STREAK - 1 until s.size).firstOrNull { j -> s.subList(0, j + 1).map(day).distinct().size >= GAME_DAYS }?.let { s[it] }
}
