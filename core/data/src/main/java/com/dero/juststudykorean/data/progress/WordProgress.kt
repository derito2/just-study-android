package com.dero.juststudykorean.data.progress

import android.util.Log
import androidx.room3.withWriteTransaction
import com.dero.juststudykorean.data.db.FlashcardReviewsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.QuestionWordsEntity
import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.data.db.WordStateEntity
import com.dero.juststudykorean.data.prefs.UserPrefs
import com.dero.juststudykorean.data.time.AppClock
import com.dero.juststudykorean.korean.norm
import com.dero.juststudykorean.korean.progress.CardEvidence
import com.dero.juststudykorean.korean.progress.GameEvidence
import com.dero.juststudykorean.korean.progress.Review
import com.dero.juststudykorean.korean.progress.WordEvidence
import com.dero.juststudykorean.korean.progress.WordStatus
import com.dero.juststudykorean.korean.progress.wordStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val GAME_WORD = "w:"
private val WEEK = Duration.ofDays(7).toMillis()

/** [WordState.sourceMask] bits: the word has a card, a Games row, a quiz question about it. */
const val FROM_CARDS = 1
const val FROM_GAMES = 2
const val FROM_QUIZ = 4

/**
 * A word (key: norm of its Korean) as Flashcards, Games and Quiz know it. [correct]/[wrong]: Games and Quiz together;
 * [firstSeenAt]/[lastEventAt]: its first and last review, round or answer (null if never studied).
 */
data class WordState(
    val key: String,
    val status: WordStatus,
    val again: Int,
    val correct: Long,
    val wrong: Long,
    val sourceMask: Int,
    val firstSeenAt: Long?,
    val lastEventAt: Long?,
)

private fun WordState.row() = WordStateEntity(key, firstSeenAt, lastEventAt, again.toLong(), wrong, correct, sourceMask.toLong(), status.masteredAt, status.hard)

private fun WordStateEntity.state() = WordState(
    wordKey, WordStatus(masteredAt != null, hard, masteredAt), againCount.toInt(), correctCount, wrongCount, sourceMask.toInt(), firstSeenAt, lastEventAt,
)

/**
 * H-03 (docs/FASE_11_PLAN.md PR 1 and PR 2): every word's progress, judged by D11-1 (korean/progress/WordRules.kt).
 * The truth is in the sources (flashcards and their reviews, Games, Quiz); word_state and question_words (phone-only,
 * not in the backup) are a cache of what [computed] gives, written by [rebuildWordState] and kept by [refresh].
 * Until the first rebuild on v6 (wordStateBuiltV6), it computes on the fly: the numbers are never different nor empty.
 */
@Singleton
class WordProgress @Inject constructor(private val db: JustStudyDatabase, private val clock: AppClock, private val prefs: UserPrefs) {
    private val lock = Mutex()

    private class Computed(val states: Map<String, WordState>, val links: Map<Long, String>)

    /**
     * From the sources: every word, or only [only] (its cards, rounds and the questions already linked to it).
     * A question is about a word when its answer, or else its prompt, is that word; otherwise it counts for none.
     */
    private suspend fun compute(only: Set<String>? = null): Computed = withContext(Dispatchers.IO) {
        // shortcut: reads every card to group them by norm(korean_word), which SQL can't do; fine for hundreds of cards.
        val cards = db.flashcardsDao().all().groupBy { norm(it.koreanWord) }.filterKeys { it.isNotEmpty() && (only == null || it in only) }
        val reviewRows: List<FlashcardReviewsEntity> =
            if (only == null) db.flashcardReviewsDao().all() else cards.values.flatten().map { it.id }.chunked(500).flatMap { db.wordStateDao().reviewsOf(it) }
        val reviews = reviewRows.sortedWith(compareBy({ it.reviewedAt }, { it.id })).groupBy { it.flashcardId }
        val games = (if (only == null) db.gameItemProgressDao().all() else only.mapNotNull { db.gameItemProgressDao().get(GAME_WORD + it) })
            .filter { it.itemKey.startsWith(GAME_WORD) }.associateBy { it.itemKey.removePrefix(GAME_WORD) }
        val rounds = HashMap<String, MutableList<Long>>() // word -> times of its graded rounds, oldest first
        for (r in db.gameRoundsDao().gradedKeys()) {
            val keys = JSONArray(r.itemKeys).let { a -> List(a.length()) { a.getString(it) } }
            for (k in keys.toSet()) if (k.startsWith(GAME_WORD) && (only == null || k.removePrefix(GAME_WORD) in only)) rounds.getOrPut(k.removePrefix(GAME_WORD)) { mutableListOf() } += r.createdAt
        }
        val keys = only ?: (cards.keys + games.keys)
        val links: Map<Long, String> = if (only == null) {
            db.questionsDao().all().mapNotNull { q: QuestionsEntity -> (norm(q.correctAnswer).takeIf { it in keys } ?: norm(q.prompt).takeIf { it in keys })?.let { q.id to it } }.toMap()
        } else {
            db.questionWordsDao().ofWords(only.toList()).associate { it.questionId to it.wordKey }
        }
        val attempts = db.questionAttemptsDao().all().filter { it.questionId in links }
            .sortedWith(compareBy({ it.createdAt }, { it.id })).groupBy { links.getValue(it.questionId) }
        val quizWords = links.values.toSet()

        val zone = clock.zone
        val states = keys.filter { it in cards || it in games }.associateWith { key ->
            val cardEvidence = cards[key].orEmpty().map { c -> CardEvidence(c.intervalHours, reviews[c.id].orEmpty().map { Review(it.rating, it.newInterval, it.reviewedAt) }) }
            val played = rounds[key].orEmpty()
            val game = games[key]?.let { g -> GameEvidence(g.correct, g.wrong, if (g.streak == 0L) emptyList() else played.takeLast(g.streak.toInt())) }
            val quiz = attempts[key].orEmpty()
            val status = wordStatus(WordEvidence(cardEvidence, game, quiz.lastOrNull()?.isCorrect)) { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            val times = cardEvidence.flatMap { c -> c.reviews.map { it.at } } + played + quiz.map { it.createdAt }
            WordState(
                key, status, cardEvidence.sumOf { c -> c.reviews.count { it.rating == "again" } },
                (game?.correct ?: 0) + quiz.count { it.isCorrect }, (game?.wrong ?: 0) + quiz.count { !it.isCorrect },
                (if (key in cards) FROM_CARDS else 0) or (if (key in games) FROM_GAMES else 0) or (if (key in quizWords) FROM_QUIZ else 0),
                times.minOrNull(), times.maxOrNull(),
            )
        }
        Computed(states, links)
    }

    /** PR 1's way: straight from the sources (also what the equivalence tests compare the cache with). */
    internal suspend fun computed(): Map<String, WordState> = compute().states

    suspend fun all(): Map<String, WordState> =
        if (prefs.wordStateBuiltV6.first()) withContext(Dispatchers.IO) { db.wordStateDao().all().associate { it.wordKey to it.state() } } else computed()

    /** Writes question_words and word_state again from the sources (first open on v6, import, deleting cards, reprocessing). */
    suspend fun rebuildWordState() = lock.withLock {
        val start = System.nanoTime()
        prefs.setWordStateBuiltV6(false) // a rebuild cut halfway leaves the on-the-fly numbers, never a half-written cache
        val words = db.withWriteTransaction {
            val c = compute()
            db.questionWordsDao().deleteAll()
            db.wordStateDao().deleteAll()
            db.questionWordsDao().insert(c.links.map { (q, w) -> QuestionWordsEntity(q, w) })
            db.wordStateDao().upsert(c.states.values.map { it.row() })
            c.states.size
        }
        prefs.setWordStateBuiltV6(true)
        Log.i("JustStudy", "word_state_rebuilt words=$words ms=${(System.nanoTime() - start) / 1_000_000}") // counts only
    }

    /**
     * After a review, a round or an answer: writes the words it touched again from the sources. A word the cache does not
     * know yet can change which word an old question belongs to, so then everything is rebuilt. Nothing before the first build.
     */
    suspend fun refresh(keys: Collection<String>) {
        val wanted = keys.filter { it.isNotEmpty() }.toSet()
        if (wanted.isEmpty()) return
        val unknown = lock.withLock {
            if (!prefs.wordStateBuiltV6.first()) return
            if (db.wordStateDao().existing(wanted.toList()).size < wanted.size) return@withLock true
            db.withWriteTransaction { db.wordStateDao().upsert(compute(wanted).states.values.map { it.row() }) }
            false
        }
        if (unknown) rebuildWordState()
    }

    /** New quiz questions: each linked to its word by the same rule as the rebuild, then those words written again. */
    suspend fun link(questions: List<QuestionsEntity>) {
        if (questions.isEmpty() || !prefs.wordStateBuiltV6.first()) return
        val known = db.wordStateDao().existing(questions.flatMap { listOf(norm(it.correctAnswer), norm(it.prompt)) }.distinct()).toSet()
        val links = questions.mapNotNull { q -> (norm(q.correctAnswer).takeIf { it in known } ?: norm(q.prompt).takeIf { it in known })?.let { QuestionWordsEntity(q.id, it) } }
        db.questionWordsDao().insert(links)
        refresh(links.map { it.wordKey })
    }

    /** The words of a quiz question (none if it is about no word). */
    suspend fun refreshQuestion(questionId: Long) = refresh(db.questionWordsDao().wordsOf(questionId))

    /** First open on v6: builds the cache once (off the main thread; the screens compute on the fly meanwhile). */
    suspend fun ensureBuilt() { if (!prefs.wordStateBuiltV6.first()) rebuildWordState() }

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
