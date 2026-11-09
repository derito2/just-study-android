package com.dero.juststudykorean.data.processing

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.backup.BackupFormat
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.documents.DocumentIndexer
import com.dero.juststudykorean.data.documents.DocumentRepository
import com.dero.juststudykorean.data.documents.STAGE_CHUNKING
import com.dero.juststudykorean.data.documents.goldenParsed
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.time.FixedClock
import com.dero.juststudykorean.korean.referencePoints
import com.dero.juststudykorean.pdf.ParsedDocument
import com.dero.juststudykorean.pdf.PdfError
import com.dero.juststudykorean.pdf.SCANNED_MESSAGE
import com.dero.juststudykorean.pdf.imagesMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The queue's risks 1–4 of the phase plan, with MuPDF replaced by the golden pages (it is checked on the phone). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ProcessingQueueTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FixedClock(Instant.parse("2026-11-09T12:00:00Z"), ZoneId.of("America/Mexico_City"))
    private lateinit var db: JustStudyDatabase
    private lateinit var files: DocumentFiles
    private lateinit var notes: NotesSource
    private lateinit var indexer: DocumentIndexer
    private val parses = AtomicInteger()
    /** What the next parse gives: a document's golden pages, or an error. */
    private var reader: (File) -> ParsedDocument = { parses.incrementAndGet(); goldenParsed("Korean_Notes_Example") }
    private lateinit var queue: ProcessingQueue
    private lateinit var repo: DocumentRepository

    @Before
    fun open() {
        File(context.filesDir, BackupFormat.FILES_DIR).deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        files = DocumentFiles(context)
        notes = NotesSource(context)
        val progress = ProgressRepository(db, clock)
        indexer = DocumentIndexer(db, clock, progress, notes, files)
        queue = ProcessingQueue(db, clock, progress, files, indexer) { reader(it) }
        repo = DocumentRepository(db, clock, notes, files, indexer, FlashcardRepository(db, clock, progress, notes), queue)
    }

    @After
    fun close() = db.close()

    private fun pdf(tag: String) = "%PDF-1.4\n% $tag\n".toByteArray()

    private suspend fun upload(tag: String, name: String = "$tag.pdf") =
        (queue.upload(pdf(tag).inputStream(), name) as UploadResult.Queued).documentId

    /** Every row the document can have, by table (none left after a failure or a delete). */
    private suspend fun rows(id: Long) = mapOf(
        "grammar_points" to db.grammarPointsDao().all().count { it.documentId == id },
        "notes_fts" to db.notesFtsDao().forDocument(id).size,
        "document_topics" to db.documentTopicsDao().all().count { it.documentId == id },
        "processing_jobs" to listOfNotNull(db.processingJobsDao().get(id)).size,
    )

    @Test
    fun resumesFromLastStage() = runBlocking {
        val id = upload("notes")
        // ColorOS kills the app when CHUNKING starts: EXTRACTING and PARSING are saved, the rest is not.
        runCatching {
            coroutineScope { launch { queue.runAll { _, stage -> if (stage == STAGE_CHUNKING) this@coroutineScope.cancel() } } }
        }.exceptionOrNull()?.let { assertTrue(it is CancellationException) }
        assertEquals(STAGE_CHUNKING, db.processingJobsDao().get(id)!!.stage)
        assertEquals("processing", db.documentsDao().all().single().status)

        queue.runAll()
        val doc = db.documentsDao().all().single()
        assertEquals("ready", doc.status)
        assertEquals("MuPDF read it once", 1, parses.get())
        val chunks = notes.chunks(doc)
        assertEquals(mapOf("grammar_points" to chunks.sumOf { referencePoints(it).size }, "notes_fts" to chunks.size,
            "document_topics" to chunks.map { it.topic.lowercase() }.filter { it != "general" }.distinct().size, "processing_jobs" to 0), rows(id))
        // A stage run twice (killed after its work, before saving the next stage) writes the same rows again.
        indexer.storeGrammar(id); indexer.index(id); indexer.syncTopics(id)
        assertEquals(rows(id), rows(id).also { indexer.storeGrammar(id) })
        assertEquals(chunks.sumOf { referencePoints(it).size }, rows(id)["grammar_points"])
    }

    @Test
    fun samePdfTwiceIsRejected() = runBlocking {
        upload("same")
        val again = queue.upload(pdf("same").inputStream(), "renamed.pdf")
        assertEquals(UploadResult.Rejected(ALREADY_IN_LIBRARY), again)
        assertEquals(1, db.documentsDao().all().size)
        assertEquals(1, File(context.filesDir, "${BackupFormat.FILES_DIR}/pdfs").listFiles()!!.size)
        assertEquals(UploadResult.Rejected("The file is not a valid PDF."), queue.upload("hello".byteInputStream(), "fake.pdf"))
        assertEquals(UploadResult.Rejected("Only .pdf files are accepted."), queue.upload(pdf("x").inputStream(), "notes.docx"))
    }

    @Test
    fun deleteWhileProcessingCancelsCleanly() = runBlocking {
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = upload("first")
        val second = upload("second")
        reader = { file ->
            if (parses.incrementAndGet() == 1) { inside.countDown(); release.await(5, TimeUnit.SECONDS) }
            goldenParsed(if (file.name == db.run { runBlocking { documentsDao().all() } }.first { it.id == second }.storedName) "Vocab_cafe_y_comida" else "Korean_Notes_Example")
        }
        val run = async(Dispatchers.Default) { queue.runAll() }
        withContext(Dispatchers.IO) { inside.await(5, TimeUnit.SECONDS) }
        val delete = async(Dispatchers.Default) { repo.delete(first, withCards = false) }
        release.countDown() // MuPDF returns; the cancelled stage must not write anything
        delete.await()
        run.await()
        assertTrue(db.documentsDao().all().none { it.id == first })
        assertEquals(mapOf("grammar_points" to 0, "notes_fts" to 0, "document_topics" to 0, "processing_jobs" to 0), rows(first))
        assertFalse(files.extracted(first).exists())
        assertEquals(1, File(context.filesDir, "${BackupFormat.FILES_DIR}/pdfs").listFiles()!!.size)
        assertEquals("the other one goes on", "ready", db.documentsDao().all().single { it.id == second }.status)
        assertTrue(rows(second).getValue("notes_fts") > 0)
        // Deleting a ready document leaves no topic that nothing uses.
        repo.delete(second, withCards = false)
        assertEquals(0, db.topicsDao().all().size)
    }

    @Test
    fun failedPdfLeavesNoRows() = runBlocking {
        val id = upload("ok")
        queue.runAll()
        assertTrue(rows(id).getValue("notes_fts") > 0)
        // Reprocessed, and now it is rejected: the old index goes too (_discard_previous_index).
        reader = { throw PdfError.Corrupt("The PDF is password-protected and cannot be processed.") }
        repo.reprocess(id)
        queue.runAll()
        val failed = db.documentsDao().all().single()
        assertEquals("failed" to "The PDF is password-protected and cannot be processed.", failed.status to failed.errorMessage)
        assertEquals(mapOf("grammar_points" to 0, "notes_fts" to 0, "document_topics" to 0, "processing_jobs" to 0), rows(id))
        assertFalse(files.extracted(id).exists())
        assertEquals(0, db.topicsDao().all().size)

        // Scanned, or with large images (ImagesNotSupportedError is a NoDigitalTextError in the web): Not supported.
        for ((tag, error) in listOf("scanned" to PdfError.NoDigitalText(SCANNED_MESSAGE), "images" to PdfError.ImagesNotSupported(imagesMessage(listOf(1))))) {
            reader = { throw error }
            val other = upload(tag)
            queue.runAll()
            val doc = db.documentsDao().all().single { it.id == other }
            assertEquals(Triple("unsupported_scanned", error.message, false), Triple(doc.status, doc.errorMessage, doc.hasDigitalText))
        }
    }
}
