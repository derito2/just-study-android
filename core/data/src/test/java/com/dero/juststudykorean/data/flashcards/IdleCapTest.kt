package com.dero.juststudykorean.data.flashcards

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.time.FixedClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/** QA round 1, R1-13: in a cards session, more than 2 min without flipping or rating does not count. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class IdleCapTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FixedClock(Instant.parse("2026-11-10T18:00:00Z"), ZoneId.of("America/Mexico_City"))
    private val db = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
    private val progress = ProgressRepository(db, clock)

    @After
    fun close() = db.close()

    @Test
    fun activeTimeCapsEachWaitAtTwoMinutes() {
        val min = 60_000L
        assertEquals(30_000L, activeCardMs(0, 10_000, 30_000)) // looked 10 s, rated 20 s later
        assertEquals(2 * min + 5_000, activeCardMs(0, 10 * min, 10 * min + 5_000)) // left the card open 10 min
        assertEquals(2 * min + 2 * min, activeCardMs(0, 5 * min, 20 * min)) // and again after flipping
        assertEquals(2 * min, activeCardMs(0, null, 9 * min)) // never flipped (not possible today, but safe)
    }

    @Test
    fun aCardsSessionAddsOnlyTheActiveTime() = runBlocking {
        val first = progress.attachSession(clock.instant.toEpochMilli(), 20_000, null, "flashcards", activeMs = 20_000)
        assertEquals(20, first.durationSeconds)
        clock.instant = clock.instant.plusSeconds(15 * 60) // 15 min later: still the same session, but only 4 min were active
        val second = progress.attachSession(clock.instant.toEpochMilli(), 15 * 60_000, null, "flashcards", activeMs = 4 * 60_000)
        assertEquals(first.id, second.id)
        assertEquals(20L + 240, second.durationSeconds)
    }

    @Test
    fun quizSessionsAreAsBefore() = runBlocking {
        val first = progress.attachSession(clock.instant.toEpochMilli(), 20_000, null, "quiz")
        clock.instant = clock.instant.plusSeconds(15 * 60)
        assertEquals(20L + 900, progress.attachSession(clock.instant.toEpochMilli(), 60_000, null, "quiz").also { assertEquals(first.id, it.id) }.durationSeconds)
    }
}
