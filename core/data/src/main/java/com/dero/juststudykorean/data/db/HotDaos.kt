package com.dero.juststudykorean.data.db

import androidx.room3.Dao
import androidx.room3.Query

// Hand-written (Daos.kt is generated and only has "SELECT * ... ORDER BY id"): the targeted queries of the paths that
// run on every rated card and every answered question, so their cost does not grow with the study history (R-001).
// Each one returns exactly what the Kotlin filter it replaces returned, in the same order.

data class LastReview(val flashcardId: Long, val reviewedAt: Long)

data class TopicTotals(val attempts: Long, val correct: Long)

@Dao
interface HotDao {
    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun flashcard(id: Long): FlashcardsEntity?

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun question(id: Long): QuestionsEntity?

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun questions(ids: List<Long>): List<QuestionsEntity>

    @Query("SELECT * FROM quiz_runs WHERE id = :id")
    suspend fun quizRun(id: Long): QuizRunsEntity?

    @Query("SELECT DISTINCT flashcard_id FROM flashcard_reviews")
    suspend fun reviewedCardIds(): List<Long>

    @Query("SELECT flashcard_id AS flashcardId, MAX(reviewed_at) AS reviewedAt FROM flashcard_reviews GROUP BY flashcard_id")
    suspend fun lastReviews(): List<LastReview>

    /** Newest first; only reviews whose card still exists. [limit] < 0: all of them. */
    @Query(
        "SELECT r.* FROM flashcard_reviews r JOIN flashcards c ON c.id = r.flashcard_id " +
            "ORDER BY r.reviewed_at DESC, r.id DESC LIMIT :limit",
    )
    suspend fun latestReviews(limit: Int): List<FlashcardReviewsEntity>

    @Query("SELECT * FROM study_sessions WHERE mode = :mode ORDER BY started_at DESC, id DESC LIMIT 1")
    suspend fun lastSession(mode: String): StudySessionsEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM study_session_topics WHERE session_id = :sessionId AND topic_id = :topicId)")
    suspend fun sessionHasTopic(sessionId: Long, topicId: Long): Boolean

    /** How many attempts the questions of a topic have, and how many were right. */
    @Query(
        "SELECT COUNT(*) AS attempts, COALESCE(SUM(a.is_correct), 0) AS correct FROM question_attempts a " +
            "JOIN questions q ON q.id = a.question_id WHERE q.topic_id = :topicId",
    )
    suspend fun topicTotals(topicId: Long): TopicTotals

    /** The last [limit] attempts at the questions of a topic, newest first. */
    @Query(
        "SELECT a.* FROM question_attempts a JOIN questions q ON q.id = a.question_id " +
            "WHERE q.topic_id = :topicId ORDER BY a.created_at DESC, a.id DESC LIMIT :limit",
    )
    suspend fun latestAttemptsOfTopic(topicId: Long, limit: Int): List<QuestionAttemptsEntity>
}
