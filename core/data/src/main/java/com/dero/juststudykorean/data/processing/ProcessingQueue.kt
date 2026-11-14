package com.dero.juststudykorean.data.processing

import android.util.Log
import com.dero.juststudykorean.ai.GateGuard
import androidx.room3.withWriteTransaction
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.ProcessingJobsEntity
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.documents.DocumentIndexer
import com.dero.juststudykorean.data.documents.STAGE_CHUNKING
import com.dero.juststudykorean.data.documents.STAGE_EXTRACTING
import com.dero.juststudykorean.data.documents.STAGE_INDEXING
import com.dero.juststudykorean.data.documents.STAGE_PARSING
import com.dero.juststudykorean.data.documents.STAGE_PENDING
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.time.AppClock
import com.dero.juststudykorean.pdf.ParsedDocument
import com.dero.juststudykorean.pdf.PdfError
import com.dero.juststudykorean.pdf.PdfParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// Port of document_service.save_upload and _process_document, by stages: PENDING → EXTRACTING → PARSING → CHUNKING →
// INDEXING → ready | failed | unsupported_scanned. Each stage saves its result and the next stage in Room before going on,
// so a run killed half way (ColorOS) starts again at the first stage not finished, without duplicating anything.

/** Reads a PDF (MuPDF on the phone; a fake in the JVM tests). */
fun interface PdfReader {
    fun parse(file: File): ParsedDocument
}

sealed interface UploadResult {
    data class Queued(val documentId: Long) : UploadResult
    data class Rejected(val message: String) : UploadResult
}

private const val MAX_UPLOAD_BYTES = 25L * 1024 * 1024 // the web's max_upload_mb
private val CONTROL = Regex("[\\x00-\\x1f\\x7f]")
const val ALREADY_IN_LIBRARY = "This PDF is already in your Library."

/** safe_display_name: a name to show, never used for a path. */
internal fun safeDisplayName(filename: String?): String =
    CONTROL.replace((filename ?: "document.pdf").replace('\\', '/').substringAfterLast('/'), "").trim().ifEmpty { "document.pdf" }.take(255)

@Singleton
class ProcessingQueue @Inject constructor(
    private val db: JustStudyDatabase,
    private val clock: AppClock,
    private val progress: ProgressRepository,
    private val files: DocumentFiles,
    private val indexer: DocumentIndexer,
    private val reader: PdfReader,
) : GateGuard {
    private val running = Mutex()
    @Volatile private var current: Pair<Long, Job>? = null

    /** Copies the chosen PDF to private storage and queues it (save_upload). Nothing else is read. */
    suspend fun upload(input: InputStream, filename: String?): UploadResult = withContext(Dispatchers.IO) {
        val name = safeDisplayName(filename)
        if (!name.lowercase().endsWith(".pdf")) return@withContext UploadResult.Rejected("Only .pdf files are accepted.")
        val (tmp, sha) = files.receive(input)
        try {
            if (tmp.length() == 0L) return@withContext UploadResult.Rejected("The file is empty.")
            if (tmp.length() > MAX_UPLOAD_BYTES) return@withContext UploadResult.Rejected("File is larger than 25 MB.")
            val header = ByteArray(1024).let { b -> b.copyOf(tmp.inputStream().use { it.read(b).coerceAtLeast(0) }) } // the first KB (minSdk 31: no readNBytes)
            if ("%PDF-".toByteArray().let { h -> (0..header.size - h.size).none { i -> h.indices.all { header[i + it] == h[it] } } }) {
                return@withContext UploadResult.Rejected("The file is not a valid PDF.")
            }
            val queued: Long = db.withWriteTransaction<Long?> {
                if (db.documentsDao().all().any { it.sha256 == sha }) return@withWriteTransaction null
                val stored = "${UUID.randomUUID().toString().replace("-", "")}.pdf"
                val now = clock.now().toEpochMilli()
                val doc = DocumentsEntity(progress.nextId("documents"), name, stored, sha, 0, "uploaded", false, 0, 0, 0, null, null, now, null)
                db.documentsDao().insert(doc)
                db.processingJobsDao().upsert(ProcessingJobsEntity(doc.id, STAGE_PENDING, now))
                files.keepPdf(tmp, stored, sha)
                doc.id
            } ?: return@withContext UploadResult.Rejected(ALREADY_IN_LIBRARY)
            Log.i("JustStudy", "document_uploaded id=$queued") // never the name or the text
            UploadResult.Queued(queued)
        } finally {
            tmp.delete() // already moved when it was kept
        }
    }

    /** Processes every queued document, one at a time, starting each at its first unfinished stage. */
    suspend fun runAll(onStage: (documentId: Long, stage: String) -> Unit = { _, _ -> }) = running.withLock {
        while (true) {
            val job = db.processingJobsDao().all().firstOrNull() ?: break
            coroutineScope {
                val work = launch(Dispatchers.Default) { process(job, onStage) }
                current = job.documentId to work
                work.join()
                current = null
            }
        }
    }

    /** Stops [documentId] if it is the one being processed (Delete while it runs); its rows are then removed by the caller. */
    suspend fun cancel(documentId: Long) {
        current?.takeIf { it.first == documentId }?.second?.let { it.cancel(); it.join() }
        db.processingJobsDao().delete(documentId)
    }

    private suspend fun stage(documentId: Long, stage: String) =
        db.processingJobsDao().get(documentId)?.let { db.processingJobsDao().upsert(it.copy(stage = stage)) }

    private suspend fun process(job: ProcessingJobsEntity, onStage: (Long, String) -> Unit) {
        val id = job.documentId
        val start = System.nanoTime()
        val doc = db.documentsDao().all().firstOrNull { it.id == id } ?: return db.processingJobsDao().delete(id)
        db.documentsDao().update(doc.copy(status = "processing", errorMessage = null, warnings = null))
        var stage = if (job.stage == STAGE_PENDING) STAGE_EXTRACTING else job.stage
        try {
            while (true) {
                onStage(id, stage)
                Log.i("JustStudy", "document_stage id=$id stage=$stage") // ids and stage names only, never text
                when (stage) {
                    STAGE_EXTRACTING -> {
                        stage(id, STAGE_EXTRACTING)
                        indexer.saveExtracted(id, reader.parse(files.pdf(doc.storedName)))
                        stage = STAGE_PARSING
                    }
                    STAGE_PARSING -> { indexer.storeGrammar(id); stage = STAGE_CHUNKING }
                    STAGE_CHUNKING -> { indexer.index(id); stage = STAGE_INDEXING }
                    STAGE_INDEXING -> {
                        indexer.syncTopics(id)
                        indexer.refreshCards(id)
                        db.withWriteTransaction {
                            db.documentsDao().all().firstOrNull { it.id == id }?.let {
                                db.documentsDao().update(it.copy(status = "ready", processedAt = clock.now().toEpochMilli()))
                            }
                            db.processingJobsDao().delete(id)
                        }
                        val pages = db.documentsDao().all().firstOrNull { it.id == id }?.pageCount ?: 0
                        // Counts only (time, pages, memory), never names or text: the phase's goals are measured from these.
                        Log.i("JustStudy", "document_processed id=$id ms=${(System.nanoTime() - start) / 1_000_000} pages=$pages pss_kb=${android.os.Debug.getPss()}")
                        return
                    }
                    else -> error("unknown stage")
                }
                stage(id, stage)
            }
        } catch (e: CancellationException) {
            throw e // deleted while it ran: the delete removes its rows
        } catch (e: PdfError) {
            withContext(NonCancellable) { finish(id, if (e is PdfError.NoDigitalText) "unsupported_scanned" else "failed", e.message, discard = true) }
            Log.w("JustStudy", "document_failed id=$id error=${e.javaClass.simpleName}")
        } catch (e: Exception) {
            withContext(NonCancellable) { finish(id, "failed", "Unexpected error while processing the PDF.", discard = true) }
            Log.e("JustStudy", "document_failed id=$id error=${e.javaClass.simpleName}")
        }
    }

    /** A rejected PDF leaves no chunks, grammar or topics the app could still show (nothing half done in the database). */
    private suspend fun finish(id: Long, status: String, message: String?, discard: Boolean) {
        if (discard) indexer.discard(id)
        db.withWriteTransaction {
            db.documentsDao().all().firstOrNull { it.id == id }?.let {
                db.documentsDao().update(it.copy(status = status, errorMessage = message, hasDigitalText = status != "unsupported_scanned" && it.hasDigitalText))
            }
            db.processingJobsDao().delete(id)
        }
        db.notesFtsDao().deleteOrphanTopics()
    }

    /** True while there is something to process (the foreground service runs until this is false). */
    suspend fun hasWork() = db.processingJobsDao().all().isNotEmpty()

    override suspend fun pdfsQueued() = hasWork()
}

/** MuPDF, on the phone. */
class MuPdfReader @Inject constructor() : PdfReader {
    override fun parse(file: File) = PdfParser.parse(file)
}
