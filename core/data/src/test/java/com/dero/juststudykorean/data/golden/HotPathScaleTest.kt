package com.dero.juststudykorean.data.golden

import androidx.room3.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.quiz.QuizRepository
import com.dero.juststudykorean.korean.Rating
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * R-001: rating a card and answering a question must not get slower as the study history grows. With 50 000 synthetic
 * reviews and 20 000 synthetic attempts (on other topics: an answer recounts its own topic's attempts, by design) on top
 * of the golden base, a review or an answer takes less than twice what it takes without them.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class HotPathScaleTest : GoldenDb() {
    /** The fastest of [times] runs: the cost of the work itself, without the pauses of a busy machine (CI, other tests). */
    private fun bestMs(times: Int = 25, block: suspend () -> Unit): Double = runBlocking {
        repeat(5) { block() } // warm up
        (1..times).minOf { val t = System.nanoTime(); block(); (System.nanoTime() - t) / 1e6 }
    }

    @Test
    fun reviewAndAnswerDoNotGrowWithTheHistory() {
        val progress = ProgressRepository(db, clock)
        val notes = NotesSource(context)
        val cards = FlashcardRepository(db, clock, progress, notes)
        val quiz = QuizRepository(db, clock, progress, notes)
        val card = runBlocking { db.flashcardsDao().all().first() }
        val question = runBlocking { db.questionsDao().all().first { it.topicId != null } }
        val review: suspend () -> Unit = { cards.review(card.id, Rating.Good, 1200) }
        val answer: suspend () -> Unit = { quiz.answer(null, question.id, question.correctAnswer, 1200) }

        val reviewBase = bestMs(block = review)
        val answerBase = bestMs(block = answer)

        runBlocking {
            db.useWriterConnection { c ->
                c.usePrepared(
                    "WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 2000) " +
                        "INSERT INTO flashcard_reviews(id, flashcard_id, rating, reviewed_at, previous_interval, new_interval) " +
                        "SELECT (SELECT MAX(id) FROM flashcard_reviews) + ROW_NUMBER() OVER (), f.id, 'good', " +
                        "1700000000000 + ROW_NUMBER() OVER (), 1, 2 FROM n CROSS JOIN flashcards f LIMIT 50000",
                ) { it.step() }
                c.usePrepared(
                    "WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 2000) " +
                        "INSERT INTO question_attempts(id, question_id, session_id, quiz_run_id, user_answer, is_correct, response_time_ms, created_at) " +
                        "SELECT (SELECT MAX(id) FROM question_attempts) + ROW_NUMBER() OVER (), q.id, NULL, NULL, 'x', i % 2, 900, " +
                        "1700000000000 + ROW_NUMBER() OVER () FROM n CROSS JOIN questions q WHERE q.topic_id IS NOT ${question.topicId} LIMIT 20000",
                ) { it.step() }
            }
            assertTrue(db.flashcardReviewsDao().count() > 50_000)
            assertTrue(db.questionAttemptsDao().count() > 20_000)
        }

        val reviewBig = bestMs(block = review)
        val answerBig = bestMs(block = answer)
        println("R-001 review: %.2f ms -> %.2f ms; answer: %.2f ms -> %.2f ms".format(reviewBase, reviewBig, answerBase, answerBig))
        assertTrue("review: $reviewBase ms -> $reviewBig ms", reviewBig < 2 * reviewBase)
        assertTrue("answer: $answerBase ms -> $answerBig ms", answerBig < 2 * answerBase)
        assertEquals(card.id, runBlocking { db.hotDao().flashcard(card.id) }?.id)
    }
}
