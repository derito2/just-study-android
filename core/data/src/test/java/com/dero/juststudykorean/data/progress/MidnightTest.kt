package com.dero.juststudykorean.data.progress

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.quiz.QuizRepository
import com.dero.juststudykorean.data.time.FixedClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Risk 1 of the phase plan: the day changes at midnight in the PHONE's zone (not UTC) for the streak and the calendar. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MidnightTest {
    private val zone = ZoneId.of("America/Mexico_City") // UTC−6: 23:59 here is already the next day in UTC
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: JustStudyDatabase
    private val clock = FixedClock(at(7, 12, 0), zone)
    private lateinit var progress: ProgressRepository
    private lateinit var quiz: QuizRepository

    private fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant()

    @Before
    fun open() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        progress = ProgressRepository(db, clock)
        quiz = QuizRepository(db, clock, progress, NotesSource(context))
        db.questionsDao().insert(QuestionsEntity(1, "ko_to_en", "What does \"물\" mean?", null, "Water", null, 1, null, "[]", "rules.v1", "rules", 0))
    }

    @After
    fun close() = db.close()

    private suspend fun answerAt(instant: java.time.Instant) {
        clock.instant = instant
        quiz.answer(null, 1, "Water", 3000)
    }

    @Test
    fun answersAt2359And0001AreTwoDays() = runBlocking {
        answerAt(at(7, 23, 59))
        answerAt(at(8, 0, 1))

        clock.instant = at(8, 12, 0)
        val summary = progress.summary()
        assertEquals(LocalDate.of(2026, 11, 8), summary.today.date)
        assertEquals("only the 00:01 answer is today", 1, summary.today.questions)
        assertEquals("two days in a row", 2, summary.streakDays)

        val days = progress.calendarNow(2026, 11).days.associateBy { it.date.dayOfMonth }
        assertEquals(1, days.getValue(7).questions)
        assertEquals(1, days.getValue(8).questions)
        assertEquals(0, days.getValue(9).questions) // what UTC would say for both

        // Just before midnight the day is still the 8th; just after it, the 9th: nothing yet today, the streak holds.
        clock.instant = at(8, 23, 59)
        assertEquals(LocalDate.of(2026, 11, 8), progress.summary().today.date)
        clock.instant = at(9, 0, 1)
        val next = progress.summary()
        assertEquals(LocalDate.of(2026, 11, 9), next.today.date)
        assertEquals(0, next.today.questions)
        assertEquals(2, next.streakDays)
    }

    @Test
    fun aDayWithOnlyCardsIsStudied() = runBlocking { // R1-06
        db.flashcardReviewsDao().insert(com.dero.juststudykorean.data.db.FlashcardReviewsEntity(1, 1, "good", at(6, 10, 0).toEpochMilli(), 0, 24))
        answerAt(at(7, 10, 0))
        clock.instant = at(7, 12, 0)
        assertEquals(2, progress.summary().streakDays)
        assertEquals(true, progress.calendarNow(2026, 11).days.first { it.date.dayOfMonth == 6 }.studied)
    }
}
