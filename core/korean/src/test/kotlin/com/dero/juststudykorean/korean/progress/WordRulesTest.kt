package com.dero.juststudykorean.korean.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** D11-1 as a table of cases: what makes a word Learned (mastered) or Hard, and when it was first learned. */
class WordRulesTest {
    private val hour = 3_600_000L
    private val dayMs = 24 * hour
    private val day: (Long) -> LocalDate = { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

    private fun reviews(vararg ratings: String, lastInterval: Long = 504) =
        ratings.mapIndexed { i, r -> Review(r, if (i == ratings.lastIndex) lastInterval else 24L * (i + 1), i * dayMs) }

    private fun card(vararg ratings: String, interval: Long = 504) = CardEvidence(interval, reviews(*ratings, lastInterval = interval))

    private fun status(vararg cards: CardEvidence, game: GameEvidence? = null, quiz: Boolean? = null) =
        wordStatus(WordEvidence(cards.toList(), game, quiz), day)

    @Test
    fun exactly504HoursWithoutAgainIsLearned() {
        val s = status(card("good", "good", "good", "good", "good", interval = 504))
        assertTrue(s.mastered)
        assertEquals(4 * dayMs, s.masteredAt)
    }

    @Test
    fun oneHourShortIsNotLearned() {
        val s = status(card("good", "good", "good", "good", "good", interval = 503))
        assertFalse(s.mastered)
        assertNull(s.masteredAt)
    }

    @Test
    fun anAgainInTheLastFiveIsNotLearned() {
        assertFalse(status(card("good", "again", "good", "good", "good", "good")).mastered) // again is the 5th from the end
    }

    @Test
    fun anAgainSixReviewsAgoNoLongerCounts() {
        assertTrue(status(card("again", "good", "good", "good", "good", "good")).mastered)
    }

    @Test
    fun aCardNeverReviewedIsNotLearned() {
        assertFalse(status(CardEvidence(600, emptyList())).mastered)
    }

    @Test
    fun learnedAtIsTheFirstReviewThatMetTheRule() {
        // 504 h first reached at review 2 (index 2), then lost with an again, then reached again: the first moment counts.
        val r = listOf(
            Review("good", 100, 0), Review("good", 300, dayMs), Review("good", 600, 2 * dayMs),
            Review("again", 10, 3 * dayMs), Review("good", 100, 4 * dayMs), Review("good", 300, 5 * dayMs),
            Review("good", 600, 6 * dayMs), Review("good", 900, 7 * dayMs), Review("good", 1200, 8 * dayMs), Review("good", 1500, 9 * dayMs),
        )
        val s = status(CardEvidence(1500, r))
        assertTrue(s.mastered)
        assertEquals(2 * dayMs, s.masteredAt)
    }

    @Test
    fun aStreakOfThreeOnOneDayIsNotLearned() {
        val s = status(game = GameEvidence(3, 0, listOf(hour, 2 * hour, 3 * hour)))
        assertFalse(s.mastered)
    }

    @Test
    fun aStreakOfThreeOverTwoDaysIsLearned() {
        val s = status(game = GameEvidence(3, 0, listOf(hour, 2 * hour, dayMs + hour)))
        assertTrue(s.mastered)
        assertEquals(dayMs + hour, s.masteredAt)
    }

    @Test
    fun aStreakOfTwoOverTwoDaysIsNotLearned() {
        assertFalse(status(game = GameEvidence(2, 0, listOf(hour, dayMs + hour))).mastered)
    }

    @Test
    fun learnedAtIsTheEarlierOfCardsAndGames() {
        val s = status(card("good", "good", "good", "good", "good"), game = GameEvidence(3, 0, listOf(0, hour, dayMs)))
        assertEquals(dayMs, s.masteredAt)
    }

    @Test
    fun threeAgainsInTheLastTenIsHard() {
        assertTrue(status(card("again", "good", "again", "good", "again", "good", interval = 24)).hard)
        assertFalse(status(card("again", "good", "again", "good", "good", "good", interval = 24)).hard)
        // the oldest of the three is the 11th from the end: it no longer counts
        assertFalse(status(card("again", "again", "again", *Array(8) { "good" }, interval = 24)).hard)
    }

    @Test
    fun againsOfTwoCardsOfTheSameWordAddUp() {
        val a = CardEvidence(24, listOf(Review("again", 10, 0), Review("again", 10, 2 * hour)))
        val b = CardEvidence(24, listOf(Review("again", 10, hour)))
        assertTrue(status(a, b).hard)
    }

    @Test
    fun moreWrongThanRightInGamesIsHard() {
        assertTrue(status(game = GameEvidence(1, 2, emptyList())).hard)
        assertFalse(status(game = GameEvidence(2, 2, emptyList())).hard)
    }

    @Test
    fun aFailedLastQuizAttemptIsHard() {
        assertTrue(status(quiz = false).hard)
        assertFalse(status(quiz = true).hard)
        assertFalse(status().hard)
    }

    @Test
    fun aWordCanBeLearnedAndHardAtOnce() { // mastered in Games but failed its last quiz: both are shown as they are
        val s = status(game = GameEvidence(3, 0, listOf(0, hour, dayMs)), quiz = false)
        assertTrue(s.mastered)
        assertTrue(s.hard)
    }
}
