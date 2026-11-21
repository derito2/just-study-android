package com.dero.juststudykorean.ui.library

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.documents.DocumentIndexer
import com.dero.juststudykorean.data.documents.DocumentRepository
import com.dero.juststudykorean.data.processing.ALREADY_IN_LIBRARY
import com.dero.juststudykorean.data.processing.NOT_ENOUGH_SPACE
import com.dero.juststudykorean.data.processing.ProcessingQueue
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.InputStream
import java.util.Collections

/**
 * design/polish.md P-05 / docs/AUDIT_2.md H-10: the Library's upload on the real repositories over the golden base: a PDF queued,
 * a file that isn't one, a copy that runs out of space, and the same PDF twice. MuPDF is not involved: nothing here is parsed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class LibraryViewModelTest {
    private val app = GoldenApp()
    private val files = DocumentFiles(app.context)
    private val indexer = DocumentIndexer(app.db, app.clock, app.progress, app.notes, files)
    private val queue = ProcessingQueue(app.db, app.clock, app.progress, files, indexer) { error("not parsed in this test") }
    private val repo = DocumentRepository(app.db, app.clock, app.notes, files, indexer, app.flashcards, queue)
    private val vm = LibraryViewModel(app.context, repo, queue, files)
    private val messages = Collections.synchronizedList(mutableListOf<String>())
    private val collector = CoroutineScope(Dispatchers.IO).launch { vm.messages.collect { messages += it } }

    @After
    fun close() { collector.cancel(); app.close() }

    private fun documents() = runBlocking { app.db.documentsDao().all().size }
    private fun pick(name: String, stream: InputStream): Uri =
        Uri.parse("content://juststudy.test/$name").also { shadowOf(app.context.contentResolver).registerInputStream(it, stream) }

    private fun pdf(tag: String) = "%PDF-1.4\n% $tag\n".toByteArray()

    private fun upload(vararg uris: Uri) { vm.upload(uris.toList()); idleUntil { !vm.uploading.value && (messages.isNotEmpty() || queued()) } }
    private fun queued() = runBlocking { queue.hasWork() }

    @Test
    fun aPdfIsCopiedAndQueued() {
        val before = documents()
        upload(pick("a.pdf", pdf("one").inputStream()))
        assertEquals(before + 1, documents())
        assertTrue(queued())
        assertTrue("a good upload says nothing", messages.isEmpty())
    }

    @Test
    fun aFileThatIsNotAPdfIsRejectedAndKeepsNothing() {
        val before = documents()
        upload(pick("note.pdf", "just some text, long enough to read as the first kilobyte".byteInputStream()))
        assertEquals(listOf("The file is not a valid PDF."), messages.toList())
        assertEquals(before, documents())
        assertFalse(queued())
    }

    @Test
    fun runningOutOfSpaceSaysSoAndKeepsNothing() {
        val before = documents()
        val full = object : InputStream() {
            private var n = 0
            override fun read(): Int = if (n++ < 10) 'a'.code else throw IOException("write failed: ENOSPC (No space left on device)")
        }
        upload(pick("big.pdf", full))
        assertEquals(listOf(NOT_ENOUGH_SPACE), messages.toList())
        assertEquals(before, documents())
    }

    @Test
    fun theSamePdfTwiceIsOnlyAddedOnce() {
        val before = documents()
        upload(pick("one.pdf", pdf("same").inputStream()))
        upload(pick("two.pdf", pdf("same").inputStream()))
        idleUntil { messages.isNotEmpty() }
        assertEquals(listOf(ALREADY_IN_LIBRARY), messages.toList())
        assertEquals(before + 1, documents())
    }
}
