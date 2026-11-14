package com.dero.juststudykorean.data.quiz

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.QuestionsEntity
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.time.FixedClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/** QA round 1, R1-08 (design/quiz.md A.4): a quiz you leave after answering appears in History, marked Left early. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class QuizRoundOneTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FixedClock(Instant.parse("2026-11-10T18:00:00Z"), ZoneId.of("America/Mexico_City"))
    private val db = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
    private val quiz = QuizRepository(db, clock, ProgressRepository(db, clock), NotesSource(context))

    @After
    fun close() = db.close()

    private fun question(id: Long, word: String, meaning: String) =
        QuestionsEntity(id, "ko_to_en", "What does \"$word\" mean?", null, meaning, null, 1, null, "[]", "rules.v1", "rules", 0)

    @Test
    fun aSavedQuestionsQuizLeftAfterOneAnswerIsInHistory() = runBlocking {
        db.questionsDao().insert(question(1, "물", "Water"))
        db.questionsDao().insert(question(2, "불", "Fire"))
        quiz.answer(null, 1, "Water", 2000)
        quiz.answer(null, 2, "Fire", 2000)
        clock.instant = clock.instant.plusSeconds(600)

        val run = quiz.practiceSaved()!!
        quiz.answer(run.id, run.questions[0].id, run.questions[0].correctAnswer, 2000) // then Leave quiz

        val card = quiz.historyNow(20).first { it.key == "run-${run.id}" }
        assertEquals(2, card.items.size)
        assertTrue(card.leftEarly)
    }

    @Test
    fun aFinishedQuizIsNotLeftEarly() = runBlocking {
        db.questionsDao().insert(question(1, "물", "Water"))
        val run = quiz.createRun(listOf(1))
        quiz.answer(run.id, 1, "Water", 2000)
        assertFalse(quiz.historyNow(20).first { it.key == "run-${run.id}" }.leftEarly)
    }
}
