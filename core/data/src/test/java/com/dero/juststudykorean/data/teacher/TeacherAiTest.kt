package com.dero.juststudykorean.data.teacher

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ai.AppForeground
import com.dero.juststudykorean.ai.NanoGate
import com.dero.juststudykorean.ai.NanoModel
import com.dero.juststudykorean.ai.NanoState
import com.dero.juststudykorean.ai.NanoStatus
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.NotesFtsEntity
import com.dero.juststudykorean.korean.ai.AI_MODEL
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Phase 6, task 3 (use 4) with a fake model on a golden PDF's notes index. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TeacherAiTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()

    init {
        val chunks = JSONArray(File("../../golden/chunks/Korean_Notes_Example.json").readText()).let { a -> List(a.length()) { a.getJSONObject(it) } }
        runBlocking {
            val docId = chunks.first().getLong("document_id")
            db.documentsDao().insert(DocumentsEntity(docId, "notes.pdf", "notes.pdf", "sha", 1, "ready", true, 0, 0, 0, "[]", null, 0, 0))
            db.notesFtsDao().insertAll(chunks.mapIndexed { i, c ->
                NotesFtsEntity(
                    documentId = docId, chromaId = c.getString("chroma_id"), page = c.getLong("page_number"),
                    section = c.optString("section").takeUnless { c.isNull("section") }, topic = c.getString("topic"),
                    contentType = c.getString("content_type"), textHash = "$i", text = c.getString("text"),
                )
            })
        }
    }

    @After
    fun close() = db.close()

    /** Cites [1] and quotes its first Korean word; with [invent], a word that is in no source. */
    private class Fake(var invent: Boolean = false) : NanoModel {
        override suspend fun status() = NanoStatus.Available
        override fun download() = emptyFlow<NanoStatus>()
        override suspend fun generate(system: String, prompt: String, maxTokens: Int, temperature: Float?): String {
            if ("SOURCES:" !in prompt) return if (invent) "In Korean you say 안녕하세요 to greet people politely." else "Greetings depend on how formal the situation is."
            val first = prompt.substringAfter("[1] ").substringAfter("\n").let { Regex("[가-힣]+").find(it)?.value ?: "" }
            return if (invent) "Your notes use 포도 for this [1]." else "Your notes show $first for this, as written there [1]."
        }
    }

    private fun service(model: Fake?): TeacherService {
        if (model == null) return TeacherService(db)
        AppForeground.visible = true
        val state = NanoState(model).also { it.refresh() }
        runBlocking { repeat(50) { if (state.status.value != NanoStatus.Available) delay(20) } }
        return TeacherService(db, NanoGate(model, state) { false })
    }

    private val questions = listOf(
        "What does 근처 mean?", "Why is the sky blue?", "What is the capital of France?", "How do Koreans celebrate birthdays?",
        "When should I use 저 instead of 나?", "Is 학교 a place?", "Explain the history of Hangul.", "How polite is 안녕?",
    )

    @Test
    fun theModelOnlyAnswersWhatTheRulesCouldNot() = runBlocking {
        val rules = questions.associateWith { service(null).ask(it) }
        val withModel = service(Fake())
        var cited = 0; var free = 0; var same = 0
        for ((q, r) in rules) {
            val got = withModel.ask(q)
            when (r.status) {
                "not_found" -> if (r.sources.isNotEmpty()) {
                    assertEquals(q, AI_MODEL, got.model); assertTrue(got.sources.any { it.cited }); cited++
                }
                "no_context" -> { assertEquals(q, AI_MODEL, got.model); assertTrue(got.sources.isEmpty()); free++ }
                else -> { assertEquals(q, r, got); same++ } // answered by the rules: same route, same answer (the selfcheck holds)
            }
        }
        println("use 4: cited=$cited free=$free rules=$same of ${questions.size}")
        assertTrue("rules=$same cited=$cited free=$free (statuses: ${rules.values.map { it.status }})", same > 0 && cited + free > 0)
    }

    @Test
    fun inventedKoreanFallsBackToTheRules() = runBlocking {
        val rules = questions.associateWith { service(null).ask(it) }
        val inventing = service(Fake(invent = true))
        for ((q, r) in rules) assertEquals(q, r, inventing.ask(q)) // nothing invented reaches the screen: the web's message
    }
}
