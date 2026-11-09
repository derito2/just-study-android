package com.dero.juststudykorean.data.golden

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.backup.BackupExporter
import com.dero.juststudykorean.data.backup.SCHEMA
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.quiz.QuizRepository
import com.dero.juststudykorean.data.quiz.QuizRequest
import com.dero.juststudykorean.korean.PyRandom
import com.dero.juststudykorean.korean.Rating
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.util.zip.ZipFile

/**
 * The writes: base.jsbackup.zip (after processing) + every operation of ops.json at its time, through the
 * repositories, gives the same 18 tables as fixture.jsbackup.zip (the web did the same through its services).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ReplayGoldenTest : GoldenDb(zip = "base") {
    @Test
    fun replayGivesTheFixture() = runBlocking {
        val progress = ProgressRepository(db, clock)
        val notes = NotesSource(context)
        val cards = FlashcardRepository(db, clock, progress, notes)
        val quiz = QuizRepository(db, clock, progress, notes)
        val ops = golden("ops").jsonArray
        for (element in ops) {
            val op = element.jsonObject
            fun long(k: String) = op.getValue(k).jsonPrimitive.long
            clock.instant = Instant.parse(op.getValue("at").jsonPrimitive.content + "Z")
            when (op.getValue("op").jsonPrimitive.content) {
                "create_cards" -> cards.createCards(emptyList())
                "generate_quiz" -> quiz.generateQuestions(
                    QuizRequest(count = op.getValue("count").jsonPrimitive.int, types = op.getValue("types").jsonArray.map { it.jsonPrimitive.content }),
                    PyRandom(long("seed")),
                )
                "create_run" -> quiz.createRun(op.getValue("question_ids").jsonArray.map { it.jsonPrimitive.long })
                "answer" -> quiz.answer(op.getValue("run_id").takeUnless { it is JsonNull }?.jsonPrimitive?.long, long("question_id"), op.getValue("given").jsonPrimitive.content, long("ms"))
                "review" -> cards.review(long("card_id"), Rating.valueOf(op.getValue("rating").jsonPrimitive.content.replaceFirstChar(Char::uppercase)), long("ms"))
                "rename_deck" -> cards.rename(long("deck_id"), op.getValue("name").jsonPrimitive.content)
                "reorder_decks" -> cards.reorder(op.getValue("deck_ids").jsonArray.map { it.jsonPrimitive.long })
                "merge_decks" -> cards.merge(op.getValue("deck_ids").jsonArray.map { it.jsonPrimitive.long }, op.getValue("name").jsonPrimitive.content)
                else -> error("unknown op $op")
            }
        }
        clock.instant = NOW
        quiz.historyNow(20) // the web's history() deletes quizzes older than 15 days, as the golden run did
        val out = File(context.cacheDir, "replay.jsbackup.zip")
        BackupExporter(context, db).export(Uri.fromFile(out))
        ZipFile(file("fixture.jsbackup.zip")).use { want ->
            ZipFile(out).use { got ->
                for (table in SCHEMA.keys) {
                    val name = "tables/$table.jsonl"
                    val w = String(want.getInputStream(want.getEntry(name)).readBytes()).lines()
                    val g = String(got.getInputStream(got.getEntry(name)).readBytes()).lines()
                    val first = w.indices.firstOrNull { it >= g.size || w[it] != g[it] }
                    assertEquals("$table: ${w.size} vs ${g.size} lines; first difference at line $first:\n  want ${first?.let { w.getOrNull(it) }}\n  got  ${first?.let { g.getOrNull(it) }}", w, g)
                }
            }
        }
        println("replayed ${ops.size} operations")
    }
}
