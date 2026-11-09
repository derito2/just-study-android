package com.dero.juststudykorean.data.documents

import android.content.Context
import android.net.Uri
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.backup.BackupExporter
import com.dero.juststudykorean.data.backup.BackupFormat
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.golden.assertJson
import com.dero.juststudykorean.data.golden.js
import com.dero.juststudykorean.data.golden.obj
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.time.FixedClock
import com.dero.juststudykorean.pdf.Box
import com.dero.juststudykorean.pdf.PageContent
import com.dero.juststudykorean.pdf.ParsedDocument
import com.dero.juststudykorean.pdf.Table
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.time.ZoneId
import java.util.zip.ZipFile

/**
 * The pipeline's stages on the test PDFs' pages (golden/parser: MuPDF itself is checked on the phone by ParserGoldenTest)
 * leave documents, topics, document_topics and grammar_points like golden/db/base (the web right after processing),
 * the chunks like golden/chunks, and Library's reads like the web's analyze_extracted, vocabulary and duplicates.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ProcessingGoldenTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val created = BackupFormat.parseUtc("2026-09-30T00:30:00Z") // tools/golden/export_db_golden.py CREATED
    private val clock = FixedClock(java.time.Instant.ofEpochMilli(created), ZoneId.of("America/Mexico_City"))
    private lateinit var db: JustStudyDatabase
    private lateinit var notes: NotesSource
    private lateinit var indexer: DocumentIndexer
    private lateinit var repo: DocumentRepository


    /** The test PDFs in the golden's upload order (their document ids in golden/chunks). */
    private val names = File("../../golden/chunks").listFiles()!!.map { it.nameWithoutExtension }
        .associateWith { golden("chunks/$it.json").jsonArray.firstOrNull()?.jsonObject?.get("document_id")?.jsonPrimitive?.long }
        .filterValues { it != null }.entries.sortedBy { it.value }.map { it.key }

    /** Upload + every stage, as the queue runs them. */
    private suspend fun process(id: Long, name: String, original: String = "$name.pdf") {
        db.documentsDao().insert(DocumentsEntity(id, original, original, MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).joinToString("") { "%02x".format(it) },
            0, "processing", false, 0, 0, 0, null, null, created, null))
        indexer.saveExtracted(id, goldenParsed(name))
        indexer.index(id)
        indexer.syncTopics(id)
        indexer.storeGrammar(id)
        val doc = db.documentsDao().all().first { it.id == id }
        db.documentsDao().update(doc.copy(status = "ready", processedAt = created))
    }

    @Before
    fun open() = runBlocking {
        File(context.filesDir, BackupFormat.FILES_DIR).deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        notes = NotesSource(context)
        val files = DocumentFiles(context)
        val progress = ProgressRepository(db, clock)
        indexer = DocumentIndexer(db, clock, progress, notes, files)
        repo = DocumentRepository(db, clock, notes, files, indexer, FlashcardRepository(db, clock, progress, notes), com.dero.juststudykorean.data.processing.ProcessingQueue(db, clock, progress, files, indexer) { error("no PDFs here") })
        names.forEachIndexed { i, name -> process(i + 1L, name) }
    }

    @After
    fun close() = db.close()

    @Test
    fun tablesLikeTheWebAfterProcessing() = runBlocking {
        val out = File(context.cacheDir, "processed.jsbackup.zip")
        BackupExporter(context, db).export(Uri.fromFile(out))
        ZipFile(File("../../golden/db/base.jsbackup.zip")).use { want ->
            ZipFile(out).use { got ->
                for (table in listOf("documents", "topics", "document_topics", "grammar_points")) {
                    fun rows(z: ZipFile) = String(z.getInputStream(z.getEntry("tables/$table.jsonl")).readBytes()).lines().filter(String::isNotEmpty)
                        .map { Json.parseToJsonElement(it).jsonObject }
                    val w = rows(want)
                    val g = rows(got)
                    assertEquals("$table rows", w.size, g.size)
                    w.zip(g).forEachIndexed { i, (a, b) ->
                        // The golden wrote its documents directly; the web's pipeline (and this one) stores warnings = [].
                        val skip = if (table == "documents") setOf("warnings") else emptySet()
                        assertJson("$table[$i]", JsonObject(a - skip), JsonObject(b - skip))
                        if (table == "documents") assertEquals("$table[$i].warnings", 0, b.getValue("warnings").jsonArray.size)
                    }
                }
            }
        }
    }

    @Test
    fun chunksAndIndexLikeTheWeb() = runBlocking {
        for ((i, name) in names.withIndex()) {
            val want = golden("chunks/$name.json").jsonArray.map { it.jsonObject }
            val doc = db.documentsDao().all().first { it.id == i + 1L }
            val got = notes.chunks(doc)
            assertEquals("$name chunks", want.size, got.size)
            want.zip(got).forEach { (w, c) ->
                assertEquals("$name ${c.chromaId}", w.getValue("chroma_id").jsonPrimitive.content, c.chromaId)
                assertEquals("$name ${c.chromaId} text", w.getValue("text").jsonPrimitive.content, c.text)
                assertEquals("$name ${c.chromaId} topic", w.getValue("topic").jsonPrimitive.content, c.topic)
            }
            assertEquals("$name index rows", want.size, db.notesFtsDao().forDocument(i + 1L).size) // no test PDF repeats a chunk
        }
    }

    @Test
    fun whatTheAppExtractedLikeTheWeb() = runBlocking {
        for ((i, name) in names.withIndex()) {
            val e = repo.extracted(i + 1L)!!
            val got = obj(
                "document_id" to (i + 1L),
                "words" to e.words.map { w ->
                    obj("korean" to w.korean, "english" to w.english, "romanization" to w.romanization, "example_ko" to w.exampleKo,
                        "example_en" to w.exampleEn, "examples" to w.examples.map { listOf(it.first, it.second) }, "notes" to w.notes, "page_number" to w.pageNumber)
                },
                "grammar" to e.grammar.map { g ->
                    obj("kind" to g.kind, "pattern" to g.pattern, "meaning" to g.meaning, "forms" to g.forms,
                        "examples" to g.examples.map { obj("korean" to it.first, "english" to it.second) }, "explanation" to g.explanation, "page_number" to g.pageNumber)
                },
                "fragments" to e.fragments.map { f ->
                    obj("n" to f.n, "page_number" to f.pageNumber, "section" to f.section, "topic" to f.topic, "content_type" to f.contentType, "text" to f.text)
                },
            )
            assertJson(name, golden("extracted/$name.json"), got)
        }
    }

    @Test
    fun vocabularyAndDuplicatesLikeTheWeb() = runBlocking {
        assertJson("vocabulary", golden("db/library_vocabulary.json"), js(repo.vocabulary().map { mapOf("korean" to it.korean, "english" to it.english) }))
        for (id in 1L..names.size) assertEquals("duplicates of $id", 0, repo.duplicates(id)!!.size)
        // The test PDFs don't repeat each other: a copy of two of them, uploaded again under another name.
        for ((name, case) in golden("db/library_duplicates_copy.json").jsonObject) {
            val id = case.jsonObject.getValue("document_id").jsonPrimitive.long
            process(id, name, original = "Copy_of_$name.pdf")
            val got = repo.duplicates(id)!!.map { d ->
                fun info(x: DuplicateInfo) = mapOf("meaning" to x.meaning, "example" to x.example, "example_translation" to x.exampleTranslation, "filename" to x.filename, "page_number" to x.pageNumber)
                buildMap {
                    put("kind", d.kind); put("text", d.text); put("current", d.current.map(::info)); put("new", info(d.new))
                    if (d.kind == "word") put("combined", d.combined)
                }
            }
            assertJson("duplicates of the copy of $name", case.jsonObject.getValue("duplicates"), js(got))
            repo.delete(id, withCards = false)
        }
    }
}
