package com.dero.juststudykorean.data.games

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.time.FixedClock
import com.dero.juststudykorean.korean.games.GameType
import com.dero.juststudykorean.korean.games.Outcome
import com.dero.juststudykorean.korean.games.RoundResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/** FASE_8_PLAN 6: sessions, rounds and the progress of each item, by text key. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class GameRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
    private val clock = FixedClock(Instant.parse("2026-11-14T18:00:00Z"), ZoneId.of("America/Mexico_City"))
    private val repo = GameRepository(db, clock)

    @After
    fun close() = db.close()

    private fun result(outcome: Outcome, ms: Long, vararg keys: String, failed: List<String> = emptyList()) = RoundResult(outcome, failed.size, ms, keys.toList(), failed)

    @Test
    fun aSessionKeepsItsRoundsTimeAndBestStreak() = runBlocking {
        val id = repo.startSession(seed = 7)
        repo.saveRound(id, 0, GameType.WordMatch, 1, result(Outcome.WON, 20_000, "w:사과", "w:우유"))
        repo.saveRound(id, 1, GameType.Hangman, 1, result(Outcome.WON, 10 * 60_000, "w:책")) // 10 min untouched counts 2 min
        repo.saveRound(id, 2, GameType.Hangman, 2, result(Outcome.LOST, 30_000, "w:물", failed = listOf("w:물")))
        repo.saveRound(id, 3, GameType.WordMatch, 1, result(Outcome.SKIPPED, 5_000, "w:집")) // a skipped round is not saved
        val s = repo.session(id)!!
        assertEquals(3L, s.rounds)
        assertEquals(2L, s.won)
        assertEquals(2L, s.bestStreak)
        assertEquals(20L + 120 + 30, s.durationSeconds)
        assertNull(s.endedAt)
        repo.endSession(id)
        assertEquals(clock.now().toEpochMilli(), repo.session(id)!!.endedAt)
    }

    @Test
    fun itemProgressByTextKey() = runBlocking {
        val id = repo.startSession(seed = 1)
        repo.saveRound(id, 0, GameType.WordMatch, 1, result(Outcome.WON, 1000, "w:사과"))
        repo.saveRound(id, 1, GameType.WordMatch, 1, result(Outcome.LOST, 1000, "w:사과", failed = listOf("w:사과")))
        repo.saveRound(id, 2, GameType.WordMatch, 1, result(Outcome.WON, 1000, "w:사과"))
        val p = repo.progress().getValue("w:사과")
        assertEquals(3L, p.seen)
        assertEquals(2L, p.correct)
        assertEquals(1L, p.wrong)
        assertEquals(1L, p.streak)
        assertEquals(clock.now().toEpochMilli(), p.lastWrongAt)
    }

    @Test
    fun theLastResultsOfAGameAcrossSessionsAndTheFigures() = runBlocking {
        val a = repo.startSession(seed = 1)
        repeat(4) { repo.saveRound(a, it, GameType.Hangman, 1, result(if (it == 0) Outcome.LOST else Outcome.WON, 2000, "w:책", failed = if (it == 0) listOf("w:책") else emptyList())) }
        val b = repo.startSession(seed = 2)
        repeat(4) { repo.saveRound(b, it, GameType.Hangman, 2, result(Outcome.WON, 4000, "w:물")) }
        assertEquals(6, repo.lastOutcomes(GameType.Hangman, 6).size)
        assertEquals(Outcome.WON, repo.lastOutcomes(GameType.Hangman, 6).first())
        val stats = repo.stats()
        assertEquals(8, stats.rounds)
        assertEquals(7.0 / 8, stats.accuracy!!, 1e-9)
        assertEquals(3000L, stats.averageResponseMs)
        assertEquals(4L, stats.bestStreak) // the best of the sessions
        assertEquals(listOf("w:책"), stats.mostFailed)
    }
}
