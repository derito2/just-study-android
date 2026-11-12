package com.dero.juststudykorean.data.teacher

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dero.juststudykorean.data.coach.CoachEngine
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.time.SystemClock
import com.dero.juststudykorean.korean.teacher.SelfCheck
import com.dero.juststudykorean.korean.teacher.Teacher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase 5, task 4: the web's self-check on YOUR notes, on the phone, with the app's search (notes_fts). Skipped unless a
 * copy of the app's database was put in this test package's `files/selfcheck/` (juststudy.db + -wal, via `run-as`; for
 * the coach also `files/backup-files/extracted/<id>.json`). Writes only counts and times to `selfcheck/result.txt`:
 * never a question, an answer or a line of your notes. Delete `selfcheck/` and `backup-files/` when done.
 */
@RunWith(AndroidJUnit4::class)
class SelfCheckTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.filesDir, "selfcheck")

    private fun pct(times: List<Long>, p: Int) = times.sorted()[((times.size - 1) * p) / 100] / 1_000_000.0

    @Test
    fun selfCheckOnYourNotes() {
        assumeTrue(File(dir, "juststudy.db").isFile)
        val db = Room.databaseBuilder(context, JustStudyDatabase::class.java, File(dir, "juststudy.db").path).setDriver(BundledSQLiteDriver()).build()
        try {
            val times = mutableListOf<Long>()
            val teacher = Teacher(NotesSearch(db))
            val indexMs = System.nanoTime().let { t -> teacher.index(); (System.nanoTime() - t) / 1_000_000 }
            val report = SelfCheck(teacher, timings = times).run()
            val clock = SystemClock()
            val progress = ProgressRepository(db, clock)
            val notes = NotesSource(context)
            val coach = CoachEngine(db, clock, progress, FlashcardRepository(db, clock, progress, notes), notes)
            val coachTimes = listOf(
                "What should I study?", "How am I doing?", "I have 20 minutes", "How many flashcards are due?", "Quiz on my mistakes",
                "Deck from my mistakes", "a quiz with adjectives", "words that start with ㄱ", "a quiz with my latest words", "food words",
            ).map { q -> System.nanoTime().let { t -> runBlocking { coach.ask(q) }; System.nanoTime() - t } }
            val lines = listOf(
                "asked ${report.asked} ok ${report.ok}",
                "by check ${report.byCheck}",
                "failures ${report.counts()}",
                "notes: ${teacher.index().hits.size} fragments, index built in $indexMs ms",
                "teacher p50 %.1f ms p90 %.1f ms max %.1f ms".format(pct(times, 50), pct(times, 90), times.max() / 1_000_000.0),
                "coach p50 %.1f ms p90 %.1f ms max %.1f ms".format(pct(coachTimes, 50), pct(coachTimes, 90), coachTimes.max() / 1_000_000.0),
            )
            File(dir, "result.txt").writeText(lines.joinToString("\n") + "\n")
            assertTrue(report.counts().toString(), report.ok)
        } finally {
            db.close()
        }
    }
}
