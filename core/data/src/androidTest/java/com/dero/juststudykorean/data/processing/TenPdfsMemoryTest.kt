package com.dero.juststudykorean.data.processing

import android.os.Debug
import android.util.Log
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dero.juststudykorean.data.backup.BackupFormat
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.documents.DocumentIndexer
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.time.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase 7, task 2 (point 9 of the plan): 10 PDFs processed in a row stay under 300 MB. The repo's synthetic PDFs
 * (3 of them twice, one byte apart so they are new files), MuPDF on the phone, an in-memory database (never yours).
 * Logs only the peak PSS.
 */
@RunWith(AndroidJUnit4::class)
class TenPdfsMemoryTest {
    @Test
    fun tenPdfsUnder300Mb() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        File(context.filesDir, BackupFormat.FILES_DIR).deleteRecursively()
        val db = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(BundledSQLiteDriver()).build()
        try {
            val clock = SystemClock()
            val progress = ProgressRepository(db, clock)
            val files = DocumentFiles(context)
            val queue = ProcessingQueue(db, clock, progress, files, DocumentIndexer(db, clock, progress, NotesSource(context), files), MuPdfReader())
            val names = assets.list("pdfs")!!.filter { it.endsWith(".pdf") }.sorted() + "extra/Tablas_sinteticas.pdf".let { listOf(it) }
            val pdfs = (names + names.take(3)).take(10).mapIndexed { i, n -> n to assets.open(if (n.contains("/")) n else "pdfs/$n").use { it.readBytes() } + if (i >= names.size) "\n%copy$i\n".toByteArray() else ByteArray(0) }
            for ((i, p) in pdfs.withIndex()) assertTrue(queue.upload(p.second.inputStream(), "pdf$i.pdf") is UploadResult.Queued)
            var peakKb = Debug.getPss()
            val sampler = launch(Dispatchers.Default) { while (isActive) { peakKb = maxOf(peakKb, Debug.getPss()); delay(100) } }
            val start = System.nanoTime()
            queue.runAll()
            sampler.cancel()
            val ready = db.documentsDao().all().count { it.status == "ready" }
            Log.i("JustStudy", "ten_pdfs ready=$ready ms=${(System.nanoTime() - start) / 1_000_000} peak_pss_mb=${peakKb / 1024}")
            assertEquals(10, ready)
            assertTrue("peak ${peakKb / 1024} MB", peakKb / 1024 < 300)
        } finally {
            db.close()
            File(context.filesDir, BackupFormat.FILES_DIR).deleteRecursively()
        }
    }
}
