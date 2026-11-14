package com.dero.juststudykorean.data.documents

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.backup.BackupFormat
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.processing.ProcessingQueue
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.time.SystemClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Phase 8, 0b: Library → Details shows every example of an entry long enough to be split in several chunks. Invented notes. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ExtractedWholeTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()

    @After
    fun close() = db.close()

    @Test
    fun aSplitEntryKeepsAllItsExamples() = runBlocking {
        val examples = (1..60).map { "물을 ${it}번 마셨어요. = I drank water $it times." }
        val text = (listOf("1. 물 = Water") + examples).joinToString("\n")
        File(context.filesDir, "${BackupFormat.FILES_DIR}/${BackupFormat.extractedEntry("1")}").apply { parentFile!!.mkdirs() }
            .writeText(org.json.JSONObject().put("document_id", 1).put("pages", org.json.JSONArray().put(
                org.json.JSONObject().put("page_number", 1).put("section", "Words").put("text", text).put("tables", org.json.JSONArray()),
            )).toString())
        db.documentsDao().insert(DocumentsEntity(1, "notes.pdf", "notes.pdf", "sha", 1, "ready", true, 0, 0, 0, "[]", null, 0, 0))
        val clock = SystemClock()
        val notes = NotesSource(context)
        val files = DocumentFiles(context)
        val progress = ProgressRepository(db, clock)
        val indexer = DocumentIndexer(db, clock, progress, notes, files)
        val repo = DocumentRepository(db, clock, notes, files, indexer, FlashcardRepository(db, clock, progress, notes), ProcessingQueue(db, clock, progress, files, indexer) { error("no PDFs here") })
        assertTrue("the entry is long enough to be split", notes.chunks(db.documentsDao().all().single()).size > 1)
        assertEquals(60, repo.extracted(1)!!.words.single { it.korean == "물" }.examples.size)
    }
}
