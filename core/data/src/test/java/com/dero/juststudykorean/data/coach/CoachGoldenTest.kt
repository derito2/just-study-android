package com.dero.juststudykorean.data.coach

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.golden.GoldenDb
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.korean.coach.plannedTools
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * golden/coach.json (tools/golden/export_coach_golden.py): the web's coach without a model over the golden/db base at
 * its fixed now. Every phrase that does not need the embeddings must give the same reply, steps, proposals, intent and
 * planned tools. Also risks 1 and 4 of docs/FASE_5_PLAN.md.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class CoachGoldenTest : GoldenDb() {
    private val coach by lazy {
        val progress = ProgressRepository(db, clock)
        val notes = NotesSource(context)
        CoachEngine(db, clock, progress, FlashcardRepository(db, clock, progress, notes), notes)
    }

    private fun plain(v: Any?): Any? = when (v) {
        null, JSONObject.NULL -> null
        is JSONObject -> v.keys().asSequence().associateWith { plain(v.get(it)) }
        is JSONArray -> List(v.length()) { plain(v.get(it)) }
        is Number -> v.toLong()
        is Map<*, *> -> v.entries.associate { it.key.toString() to plain(it.value) }
        is List<*> -> v.map(::plain)
        else -> v
    }

    @Test
    fun everyPhraseWithoutEmbeddingsMatches() = runBlocking {
        val rows = JSONObject(File("../../golden/coach.json").readText()).getJSONArray("phrases").let { a -> List(a.length()) { a.getJSONObject(it) } }
        var compared = 0
        val wrong = mutableListOf<String>()
        for (row in rows) {
            if (row.getBoolean("needsEmbeddings")) continue
            compared++
            val message = row.getString("message")
            val got = coach.ask(message)
            val want = mapOf(
                "reply" to row.getString("reply"),
                "steps" to plain(row.getJSONArray("steps")),
                "quiz" to plain(row.opt("quiz")),
                "deck" to plain(row.opt("deck")),
                "fallback" to row.getBoolean("fallback"),
                "intent" to plain(row.opt("intent"))?.let { (it as Map<*, *>).let { m -> m + ("count" to m["count"]) } },
                "planned" to plain(row.getJSONArray("planned")),
            )
            val asked = coach.intentOf(message)
            val have = mapOf(
                "reply" to got.text,
                "steps" to got.steps.map { plain(mapOf("tool" to it.tool, "arguments" to it.arguments, "ok" to it.ok, "summary" to it.summary)) },
                "quiz" to got.quiz?.let { plain(mapOf("topic" to it.topic, "title" to it.title, "words" to it.words, "count" to it.count, "types" to it.types)) },
                "deck" to got.deck?.let { plain(mapOf("name" to it.name, "words" to it.words)) },
                "fallback" to got.fallback,
                "intent" to asked?.let { plain(mapOf("kind" to it.kind, "source" to it.source, "value" to it.value, "count" to it.count)) },
                "planned" to plannedTools(message).map { (n, a) -> plain(listOf(n, a)) },
            )
            for (key in want.keys) if (want[key] != have[key]) wrong += "$message [$key]\n  want ${want[key]}\n  got  ${have[key]}"
        }
        println("coach: ${rows.size} phrases, $compared without embeddings, ${compared - wrong.map { it.substringBefore(" [") }.distinct().size} same")
        wrong.take(20).forEach(::println)
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    /** Risk 1: a question mixing a word and a quiz takes one clear route (the web's), never two half answers. */
    @Test
    fun mixedQuestionTakesOneRoute() = runBlocking {
        val got = coach.ask("What does 근처 mean? also give me a quiz")
        assertEquals(1, listOfNotNull(got.quiz, got.deck).size)
        assertTrue(got.text, got.text.startsWith("Stop avoiding it: study "))
    }

    /** Risk 4: no quiz is proposed when there are no questions to make it from. */
    @Test
    fun noQuizWithoutQuestions() = runBlocking {
        val got = coach.ask("Quiz me on animals")
        assertEquals(null, got.quiz)
        assertTrue(got.text, got.text.startsWith("Your notes don't have words about animals."))
    }
}
