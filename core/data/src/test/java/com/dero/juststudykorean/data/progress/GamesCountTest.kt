package com.dero.juststudykorean.data.progress

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.GameSessionsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.time.FixedClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/** D8-4: playing Games counts as studying (streak, calendar, Study time); a session with no round doesn't. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class GamesCountTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
    private val clock = FixedClock(ZonedDateTime.of(2026, 11, 8, 20, 0, 0, 0, zone).toInstant(), zone)
    private val progress = ProgressRepository(db, clock)

    @After
    fun close() = db.close()

    @Test
    fun gamesCountAsStudying() = runBlocking {
        db.gameSessionsDao().insert(GameSessionsEntity(1, 1, at(7, 22), at(7, 22) + 600_000, durationSeconds = 600, rounds = 10, won = 8, bestStreak = 4))
        db.gameSessionsDao().insert(GameSessionsEntity(2, 2, at(8, 9), at(8, 9) + 300_000, durationSeconds = 300, rounds = 5, won = 3, bestStreak = 2))
        db.gameSessionsDao().insert(GameSessionsEntity(3, 3, at(6, 9), null, durationSeconds = 0, rounds = 0, won = 0, bestStreak = 0)) // opened, nothing played

        val summary = progress.summary()
        assertEquals(900L, summary.totalStudySeconds)
        assertEquals(300L, summary.today.studySeconds)
        assertEquals(2, summary.streakDays)

        val days = progress.calendarNow(2026, 11).days.associateBy { it.date.dayOfMonth }
        assertTrue(days.getValue(7).studied)
        assertEquals(10L, days.getValue(7).minutes)
        assertFalse(days.getValue(6).studied)
    }
}
