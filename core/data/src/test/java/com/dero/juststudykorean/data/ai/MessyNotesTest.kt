package com.dero.juststudykorean.data.ai

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ai.AppForeground
import com.dero.juststudykorean.ai.NanoGate
import com.dero.juststudykorean.ai.NanoModel
import com.dero.juststudykorean.ai.NanoState
import com.dero.juststudykorean.ai.NanoStatus
import com.dero.juststudykorean.data.backup.BackupFormat
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.korean.ai.needsLlmPairs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Phase 6, task 5 (use 6): a synthetic document whose vocabulary the rules don't understand. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MessyNotesTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
    private val page = "Meaning: the words below\n사과\napple\n우유\nmilk\n바나나\nbanana"

    init {
        File(context.filesDir, "${BackupFormat.FILES_DIR}/${BackupFormat.extractedEntry("1")}").apply { parentFile!!.mkdirs() }
            .writeText("""{"pages": [{"page_number": 1, "section": "Vocabulary", "text": ${org.json.JSONObject.quote(page)}, "tables": []}]}""")
        runBlocking { db.documentsDao().insert(DocumentsEntity(1, "messy.pdf", "messy.pdf", "sha", 1, "ready", true, 0, 0, 0, "[]", null, 0, 0)) }
    }

    @After
    fun close() { db.close(); AppForeground.visible = false }

    private class Fake : NanoModel {
        var calls = 0
        override suspend fun status() = NanoStatus.Available
        override fun download() = emptyFlow<NanoStatus>()
        override suspend fun generate(system: String, prompt: String, maxTokens: Int, temperature: Float?): String {
            calls++
            return "사과 = apple\n우유 = milk\n포도 = grape"
        }
    }

    private val files = com.dero.juststudykorean.data.documents.DocumentFiles(context)
    private val indexer = com.dero.juststudykorean.data.time.SystemClock().let { clock ->
        com.dero.juststudykorean.data.documents.DocumentIndexer(db, clock, com.dero.juststudykorean.data.progress.ProgressRepository(db, clock), NotesSource(context), files)
    }

    private fun messy(model: Fake): MessyNotes {
        val state = NanoState(model).also { it.refresh() }
        runBlocking { repeat(50) { if (state.status.value != NanoStatus.Available) delay(20) } }
        return MessyNotes(NanoGate(model, state) { false }, db, NotesSource(context), files, indexer)
    }

    @Test
    fun waitsInTheBackgroundAndReadsInFront() = runBlocking {
        val notes = NotesSource(context)
        val doc = db.documentsDao().all().single()
        assertTrue("the synthetic page is messy notes", notes.chunks(doc).any(::needsLlmPairs))
        val model = Fake()
        val m = messy(model)
        AppForeground.visible = false
        m.readReady()
        assertTrue(m.state.value.getValue(1).waiting)
        assertEquals(0, model.calls)
        AppForeground.visible = true
        m.readReady()
        val s = m.state.value.getValue(1)
        assertFalse(s.waiting)
        assertEquals(listOf("사과", "우유"), s.pairs.map { it.korean }) // 포도 is not in the notes
        assertTrue(db.flashcardsDao().all().isEmpty()) // never cards on their own

        // Add (design/library.md E): a normal word of the document, in its extracted pages; Undo takes it out again.
        val added = m.accept(1, s.pairs.take(1))
        assertEquals(listOf("사과"), added.map { it.korean })
        assertTrue(notes.chunks(doc).flatMap { com.dero.juststudykorean.korean.extractPairs(it) }.any { it.korean == "사과" && it.english == "apple" })
        assertEquals(listOf("우유"), m.state.value.getValue(1).pairs.map { it.korean })
        m.undo(1, added)
        assertTrue(notes.chunks(doc).flatMap { com.dero.juststudykorean.korean.extractPairs(it) }.none { it.korean == "사과" })
        assertEquals(setOf("사과", "우유"), m.state.value.getValue(1).pairs.map { it.korean }.toSet())
    }

    /** Phase 7: reprocessing the PDF (same text from MuPDF) keeps the words you added from messy notes. */
    @Test
    fun reprocessingKeepsTheAddedPairs() = runBlocking {
        AppForeground.visible = true
        val m = messy(Fake())
        m.readReady()
        m.accept(1, m.state.value.getValue(1).pairs.take(1))
        val pdf = com.dero.juststudykorean.pdf.PageContent(1, page, emptyList(), "Vocabulary", emptyList(), 0, page.length)
        indexer.saveExtracted(1, com.dero.juststudykorean.pdf.ParsedDocument(1, listOf(pdf), page.length, 0, 0, emptyList()))
        indexer.index(1)
        val doc = db.documentsDao().all().single()
        val words = NotesSource(context).chunks(doc).flatMap { com.dero.juststudykorean.korean.extractPairs(it) }
        assertEquals(1, words.count { it.korean == "사과" && it.english == "apple" })
        indexer.saveExtracted(1, com.dero.juststudykorean.pdf.ParsedDocument(1, listOf(pdf), page.length, 0, 0, emptyList())) // twice: never doubled
        assertEquals(1, files.extracted(1).readText().split("사과 = apple").size - 1)
    }
}
