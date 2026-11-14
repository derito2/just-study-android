package com.dero.juststudykorean.data.games

import androidx.room3.withWriteTransaction
import com.dero.juststudykorean.data.db.GameItemProgressEntity
import com.dero.juststudykorean.data.db.GameRoundsEntity
import com.dero.juststudykorean.data.db.GameSessionsEntity
import com.dero.juststudykorean.data.db.GameTotals
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.time.AppClock
import com.dero.juststudykorean.korean.games.GameType
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.ItemStats
import com.dero.juststudykorean.korean.games.PlayedRound
import com.dero.juststudykorean.korean.games.RoundResult
import com.dero.juststudykorean.korean.games.progressAfter
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/** A round with more than this without touching anything counts this much (the same cap as R1-13). */
private const val ROUND_CAP_MS = 120_000L
private const val MOST_FAILED = 5

/** Games' figures, computed with SQL over the three tables (FASE_8_PLAN 6). */
data class GameStats(
    val rounds: Int,
    val accuracy: Double?,
    val averageResponseMs: Long,
    val bestStreak: Long,
    val mostFailed: List<String>,
    val mastered: Int,
    val lastPlayedAt: Long?,
    val perGame: List<GameTotals>,
)

private fun GameItemProgressEntity.stats() = ItemStats(seen, correct, wrong, streak, lastSeenAt, lastWrongAt)

/** Saves Games' sessions, rounds and the progress of each item (phone-only tables, D8-3). Never touches the 18 tables. */
@Singleton
class GameRepository @Inject constructor(private val db: JustStudyDatabase, private val clock: AppClock) {
    private fun now() = clock.now().toEpochMilli()

    suspend fun startSession(seed: Long): Long = db.withWriteTransaction {
        val id = db.gameSessionsDao().maxId() + 1
        db.gameSessionsDao().insert(GameSessionsEntity(id, seed, now(), null, 0, 0, 0, 0))
        id
    }

    suspend fun session(id: Long): GameSessionsEntity? = db.gameSessionsDao().get(id)

    /** Saves a played round (a skipped one is not saved), the session's counts and time, and each item's progress. */
    suspend fun saveRound(sessionId: Long, roundIndex: Int, game: GameType, level: Int, result: RoundResult) {
        if (result.outcome == Outcome.SKIPPED) return
        db.withWriteTransaction {
            val at = now()
            db.gameRoundsDao().insert(
                GameRoundsEntity(
                    db.gameRoundsDao().maxId() + 1, sessionId, roundIndex.toLong(), game.name, level.toLong(), result.outcome.name.lowercase(),
                    result.mistakes.toLong(), result.responseMs, JSONArray(result.itemKeys).toString(), at,
                ),
            )
            val session = db.gameSessionsDao().get(sessionId) ?: return@withWriteTransaction
            val streak = db.gameRoundsDao().ofSession(sessionId).map { it.outcome }.fold(0L to 0L) { (cur, best), o ->
                val next = if (o == "won") cur + 1 else if (o == "lost") 0 else cur
                next to maxOf(best, next)
            }.second
            db.gameSessionsDao().update(
                session.copy(
                    rounds = session.rounds + 1,
                    won = session.won + if (result.outcome == Outcome.WON) 1 else 0,
                    bestStreak = streak,
                    durationSeconds = session.durationSeconds + (minOf(result.responseMs, ROUND_CAP_MS) + 500) / 1000,
                ),
            )
            if (result.outcome == Outcome.NEUTRAL) return@withWriteTransaction
            for (key in result.itemKeys.distinct()) {
                val old = db.gameItemProgressDao().get(key)?.stats()
                val p = progressAfter(old, wrong = key in result.failedKeys || result.outcome == Outcome.LOST, at = at)
                db.gameItemProgressDao().upsert(GameItemProgressEntity(key, p.seen, p.correct, p.wrong, p.streak, p.lastSeenAt, p.lastWrongAt))
            }
        }
    }

    suspend fun endSession(id: Long) = db.withWriteTransaction {
        db.gameSessionsDao().get(id)?.let { if (it.endedAt == null) db.gameSessionsDao().update(it.copy(endedAt = now())) }
    }

    /** The last [limit] results of a game, of any session, newest first (5.3, the level). */
    suspend fun lastOutcomes(game: GameType, limit: Int): List<Outcome> =
        db.gameRoundsDao().lastOutcomes(game.name, limit).map { Outcome.valueOf(it.uppercase()) }

    /** Every item's progress, by key (5.4, ItemPicker). */
    suspend fun progress(): Map<String, ItemStats> = db.gameItemProgressDao().all().associate { it.itemKey to it.stats() }

    /** Each game's last [limit] results, newest first (5.3). */
    suspend fun lastOutcomes(limit: Int = 6): Map<GameType, List<Outcome>> = GameType.entries.associateWith { lastOutcomes(it, limit) }

    /** The rounds a session played, in order (to rebuild its history after the process was killed, 5.5). */
    suspend fun history(sessionId: Long): List<PlayedRound> = db.gameRoundsDao().ofSession(sessionId).map { r ->
        val keys = JSONArray(r.itemKeys).let { a -> List(a.length()) { a.getString(it) } }
        PlayedRound(GameType.valueOf(r.game), Outcome.valueOf(r.outcome.uppercase()), keys)
    }

    /** When the session last did something: its last round, or its start. */
    suspend fun lastActivity(sessionId: Long): Long? =
        db.gameRoundsDao().ofSession(sessionId).maxOfOrNull { it.createdAt } ?: session(sessionId)?.startedAt

    suspend fun stats(): GameStats {
        val graded = db.gameRoundsDao().graded()
        return GameStats(
            rounds = db.gameRoundsDao().count(),
            accuracy = if (graded == 0) null else db.gameRoundsDao().won().toDouble() / graded,
            averageResponseMs = db.gameRoundsDao().averageResponseMs(),
            bestStreak = db.gameSessionsDao().bestStreak(),
            mostFailed = db.gameItemProgressDao().mostFailed(MOST_FAILED),
            mastered = db.gameItemProgressDao().mastered(),
            lastPlayedAt = db.gameRoundsDao().lastPlayedAt(),
            perGame = db.gameRoundsDao().perGame(),
        )
    }
}
