package com.dero.juststudykorean.data.flashcards

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.FlashcardsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.notes.NotesSource
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

/** QA round 1, R1-28 (design/flashcards.md): a coach deck never repeats the name of another deck. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class CoachDeckNameTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FixedClock(Instant.parse("2026-11-13T18:00:00Z"), ZoneId.of("America/Mexico_City"))
    private val db = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
    private val repo = FlashcardRepository(db, clock, ProgressRepository(db, clock), NotesSource(context))

    @After
    fun close() = db.close()

    @Test
    fun aRepeatedNameTakesTheDateThenANumber() = runBlocking {
        db.flashcardsDao().insert(FlashcardsEntity(1, null, "물", "Water", "물", "Water", null, null, null, 1, 2.5, 0, 0, 0, "9:1:0", null, 0, null))
        val names = List(3) { repo.createCustomDeck("Your mistakes", listOf("물")) }.map { id -> db.customDecksDao().all().first { it.id == -id }.name }
        assertEquals(listOf("Your mistakes", "Your mistakes · 13 Nov", "Your mistakes · 13 Nov (2)"), names)
    }
}
