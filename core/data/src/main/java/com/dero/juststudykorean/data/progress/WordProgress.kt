package com.dero.juststudykorean.data.progress

import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.time.AppClock
import com.dero.juststudykorean.korean.norm
import com.dero.juststudykorean.korean.progress.CardEvidence
import com.dero.juststudykorean.korean.progress.GameEvidence
import com.dero.juststudykorean.korean.progress.Review
import com.dero.juststudykorean.korean.progress.WordEvidence
import com.dero.juststudykorean.korean.progress.WordStatus
import com.dero.juststudykorean.korean.progress.wordStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val GAME_WORD = "w:"
private val WEEK = Duration.ofDays(7).toMillis()

/** A word (key: norm of its Korean) as Flashcards, Games and Quiz know it. */
data class WordState(
    val key: String,
    val status: WordStatus,
    val again: Int,
    val gameCorrect: Long,
    val gameWrong: Long,
    val quizCorrect: Int,
    val quizWrong: Int,
)

/**
 * H-03 phase A (docs/FASE_11_PLAN.md PR 1): every word's progress, computed on the fly from the three sources and
 * judged by D11-1 (korean/progress/WordRules.kt). Read only: nothing is stored, the backup does not change.
 */
@Singleton
class WordProgress @Inject constructor(private val db: JustStudyDatabase, private val clock: AppClock) {
    // shortcut: reads whole tables, as ProgressRepository.weekly() does; PR 2 (word_state) replaces it.
    suspend fun all(): Map<String, WordState> = withContext(Dispatchers.IO) {
        val reviews = db.flashcardReviewsDao().all().sortedWith(compareBy({ it.reviewedAt }, { it.id })).groupBy { it.flashcardId }
        val cards = db.flashcardsDao().all().groupBy { norm(it.koreanWord) }.filterKeys { it.isNotEmpty() }
        val games = db.gameItemProgressDao().all().filter { it.itemKey.startsWith(GAME_WORD) }.associateBy { it.itemKey.removePrefix(GAME_WORD) }
        val rounds = db.gameRoundsDao().gradedKeys().map { r -> r.createdAt to JSONArray(r.itemKeys).let { a -> List(a.length()) { a.getString(it) }.toSet() } }
        val keys = cards.keys + games.keys

        // A question is about a word when its answer, or else its prompt, is that word; otherwise it counts for none.
        val questionWord = db.questionsDao().all().mapNotNull { q ->
            (norm(q.correctAnswer).takeIf { it in keys } ?: norm(q.prompt).takeIf { it in keys })?.let { q.id to it }
        }.toMap()
        val attempts = db.questionAttemptsDao().all().filter { it.questionId in questionWord }
            .sortedWith(compareBy({ it.createdAt }, { it.id })).groupBy { questionWord.getValue(it.questionId) }

        val zone = clock.zone
        keys.associateWith { key ->
            val cardEvidence = cards[key].orEmpty().map { c ->
                CardEvidence(c.intervalHours, reviews[c.id].orEmpty().map { Review(it.rating, it.newInterval, it.reviewedAt) })
            }
            val game = games[key]?.let { g ->
                val streak = if (g.streak == 0L) emptyList() else rounds.filter { g.itemKey in it.second }.takeLast(g.streak.toInt()).map { it.first }
                GameEvidence(g.correct, g.wrong, streak)
            }
            val quiz = attempts[key].orEmpty()
            val status = wordStatus(WordEvidence(cardEvidence, game, quiz.lastOrNull()?.isCorrect)) { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            WordState(
                key, status, cardEvidence.sumOf { c -> c.reviews.count { it.rating == "again" } },
                game?.correct ?: 0, game?.wrong ?: 0, quiz.count { it.isCorrect }, quiz.count { !it.isCorrect },
            )
        }
    }

    /** Flashcards' "Learned": words learned now. */
    suspend fun learned(): Int = all().values.count { it.status.mastered }

    /** Dashboard's "Words learned this week": first learned in the last 7 days, and in the 7 before (null if none was learned before). */
    suspend fun learnedWeekly(): Pair<Int, Int?> {
        val now = clock.now().toEpochMilli()
        val thisStart = now - WEEK
        val at = all().values.mapNotNull { it.status.masteredAt }
        val thisWeek = at.count { it in thisStart..now }
        return thisWeek to if (at.any { it < thisStart }) at.count { it in (thisStart - WEEK) until thisStart } else null
    }
}
