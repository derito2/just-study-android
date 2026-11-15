package com.dero.juststudykorean.data.processing

import android.content.Context
import android.net.Uri
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.backup.BackupExporter
import com.dero.juststudykorean.data.backup.BackupFormat
import com.dero.juststudykorean.data.backup.BackupImporter
import com.dero.juststudykorean.data.backup.BackupError
import com.dero.juststudykorean.data.backup.ExportResult
import com.dero.juststudykorean.data.backup.ImportResult
import com.dero.juststudykorean.data.backup.NO_SPACE
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.documents.DocumentIndexer
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.time.FixedClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId

/**
 * H-09 (docs/AUDIT_2.md): a full phone says so, in upload, import and export, and a failed export leaves no half file behind.
 * Nothing here reads or writes any note text.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class NoSpaceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FixedClock(Instant.parse("2026-11-15T12:00:00Z"), ZoneId.of("America/Mexico_City"))
    private lateinit var db: JustStudyDatabase
    private lateinit var queue: ProcessingQueue

    @Before
    fun open() {
        File(context.filesDir, BackupFormat.FILES_DIR).deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        val files = DocumentFiles(context)
        val notes = NotesSource(context)
        val progress = ProgressRepository(db, clock)
        queue = ProcessingQueue(db, clock, progress, files, DocumentIndexer(db, clock, progress, notes, files)) { error("not parsed here") }
    }

    @After
    fun close() = db.close()

    /** A PDF whose copy runs out of space after 10 bytes, as Linux reports it. */
    private class FullDisk : InputStream() {
        private var n = 0
        override fun read(): Int = if (n++ < 10) 'a'.code else throw IOException("write failed: ENOSPC (No space left on device)")
    }

    @Test
    fun uploadSaysNotEnoughSpaceAndKeepsNothing() = runBlocking {
        val result = queue.upload(FullDisk(), "notes.pdf")
        assertEquals(UploadResult.Rejected(NOT_ENOUGH_SPACE), result)
        assertTrue(db.documentsDao().all().isEmpty())
        val leftovers = File(context.filesDir, BackupFormat.FILES_DIR + "/pdfs").listFiles().orEmpty()
        assertTrue("incoming copy not deleted: ${leftovers.toList()}", leftovers.isEmpty())
    }

    @Test
    fun otherReadErrorsStayAnIOException() {
        val broken = object : InputStream() { override fun read(): Int = throw IOException("Stream closed") }
        val thrown = runCatching { runBlocking { queue.upload(broken, "notes.pdf") } }.exceptionOrNull()
        assertTrue("an unrelated IOException must not be reported as lack of space", thrown is IOException)
    }

    @Test
    fun exportThatFailsOnTable5DeletesItsTargetAndSaysWhy() = runBlocking {
        val uri = Uri.parse("content://juststudy.test/export.zip")
        var writes = 0
        // Fails once the zip has taken a few entries: a table in the middle of the export.
        val out = object : OutputStream() {
            override fun write(b: Int) { if (++writes > 5_000) throw IOException("No space left on device") }
            override fun write(b: ByteArray, off: Int, len: Int) { writes += len; if (writes > 5_000) throw IOException("No space left on device") }
        }
        shadowOf(context.contentResolver).registerOutputStream(uri, out)
        val deleted = mutableListOf<Uri>()
        val exporter = BackupExporter(context, db).apply { discard = { deleted += it } }
        seed()
        val result = exporter.export(uri)
        assertEquals(ExportResult.Failed(NO_SPACE), result)
        assertEquals("the half-written zip must be deleted", listOf(uri), deleted)
    }

    @Test
    fun exportThatWorksKeepsItsFile() = runBlocking {
        val uri = Uri.parse("content://juststudy.test/ok.zip")
        shadowOf(context.contentResolver).registerOutputStream(uri, java.io.ByteArrayOutputStream())
        val deleted = mutableListOf<Uri>()
        val exporter = BackupExporter(context, db).apply { discard = { deleted += it } }
        assertTrue(exporter.export(uri) is ExportResult.Ok)
        assertFalse(deleted.isNotEmpty())
    }

    @Test
    fun importNeedsTwiceTheBackupSize() = runBlocking {
        val uri = Uri.parse("content://juststudy.test/big.jsbackup.zip")
        val importer = BackupImporter(context, db).apply { sizeOf = { Long.MAX_VALUE / 4 } }
        assertEquals(ImportResult.Failed(BackupError.NoSpace), importer.import(uri))
    }

    /** Enough rows for the zip to be bigger than the failing stream's budget. */
    private suspend fun seed() {
        for (i in 1..400L) db.customDecksDao().insert(com.dero.juststudykorean.data.db.CustomDecksEntity(i, "deck-$i-" + "x".repeat(40), i))
    }
}
