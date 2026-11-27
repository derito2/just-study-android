package com.dero.juststudykorean.data.db

import androidx.room3.Dao
import androidx.room3.Query

// Hand-written (Daos.kt is generated and only has "SELECT * ... ORDER BY id"): the targeted queries of the paths that
// run on every rated card and every answered question, so their cost does not grow with the study history (R-001).
// Each one returns exactly what the Kotlin filter it replaces returned, in the same order.

data class LastReview(val flashcardId: Long, val reviewedAt: Long)

data class TopicTotals(val attempts: Long, val correct: Long)

/** F5 (My Mistakes): one wrong answer as each source keeps it; [given] null where only right or wrong is kept. */
data class MistakeRow(val expected: String, val given: String?, val ref: String?, val at: Long)

@Dao
interface HotDao {
    // F5: each source's mistakes since a moment, by its created_at index (R-001: never .all()).
    @Query(
        "SELECT q.correct_answer AS expected, a.user_answer AS given, " +
            "COALESCE((SELECT w.word_key FROM question_words w WHERE w.question_id = q.id LIMIT 1), q.prompt) AS ref, a.created_at AS at " +
            "FROM question_attempts a JOIN questions q ON q.id = a.question_id WHERE a.is_correct = 0 AND a.created_at >= :since",
    )
    suspend fun quizMistakes(since: Long): List<MistakeRow>

    @Query(
        "SELECT c.korean_word AS expected, CAST(NULL AS TEXT) AS given, c.korean_word AS ref, r.reviewed_at AS at " +
            "FROM flashcard_reviews r JOIN flashcards c ON c.id = r.flashcard_id WHERE r.rating = 'again' AND r.reviewed_at >= :since",
    )
    suspend fun againMistakes(since: Long): List<MistakeRow>

    /** [expected]: the round's item keys (a JSON array). */
    @Query("SELECT item_keys AS expected, CAST(NULL AS TEXT) AS given, game AS ref, created_at AS at FROM game_rounds WHERE outcome = 'lost' AND created_at >= :since")
    suspend fun gameMistakes(since: Long): List<MistakeRow>

    /** [expected]: "verb|ending label"; [ref]: the grammar point. */
    @Query("SELECT verb || '|' || ending AS expected, given, point_key AS ref, created_at AS at FROM grammar_drill_attempts WHERE correct = 0 AND created_at >= :since")
    suspend fun drillMistakes(since: Long): List<MistakeRow>

    @Query("SELECT item_key AS expected, CAST(NULL AS TEXT) AS given, item_key AS ref, created_at AS at FROM listening_attempts WHERE mode = 'dictation' AND correct = 0 AND created_at >= :since")
    suspend fun dictationMistakes(since: Long): List<MistakeRow>

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
