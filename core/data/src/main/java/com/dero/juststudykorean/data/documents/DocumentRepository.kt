package com.dero.juststudykorean.data.documents

import androidx.room3.withWriteTransaction
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.ProcessingJobsEntity
import com.dero.juststudykorean.data.flashcards.FlashcardException
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.time.AppClock
import com.dero.juststudykorean.korean.extractGrammar
import com.dero.juststudykorean.korean.extractPairs
import com.dero.juststudykorean.korean.norm
import com.dero.juststudykorean.korean.referencePoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

// Library's data: the web's documents API (list, analysis, reprocess, delete) and reference_service's vocabulary and
// duplicates. Processing itself is DocumentIndexer's stages, run one PDF at a time by the processing queue.

/** A document as Library shows it; [stage] while it is in the pipeline. */
data class LibraryDocument(val document: DocumentsEntity, val warnings: List<String>, val stage: String?)

/** What the app extracted (analyze_extracted): words, grammar and fragments. */
data class Extracted(val words: List<ExtractedWord>, val grammar: List<ExtractedGrammar>, val fragments: List<Fragment>)

data class ExtractedWord(
    val korean: String, val english: String, val romanization: String?, val exampleKo: String?, val exampleEn: String?,
    val examples: List<Pair<String, String>>, val notes: List<String>, val pageNumber: Int,
)

data class ExtractedGrammar(
    val kind: String, val pattern: String, val meaning: String, val forms: List<String>,
    val examples: List<Pair<String, String>>, val explanation: String?, val pageNumber: Int,
)

data class Fragment(val n: Int, val pageNumber: Int, val section: String?, val topic: String, val contentType: String, val text: String)

/** One entry of All vocabulary: the Korean once, with all its meanings. */
data class VocabularyWord(val korean: String, val english: String)

/** The All vocabulary filter: [query] in the Korean or in the meaning; the order stays. */
fun filterVocabulary(words: List<VocabularyWord>, query: String): List<VocabularyWord> {
    val q = norm(query)
    return if (q.isEmpty()) words else words.filter { q in norm(it.korean) || q in norm(it.english) }
}

/** What a document repeats from the others (DuplicateDialog). [combined]: the word's meanings once joined. */
data class Duplicate(val kind: String, val text: String, val current: List<DuplicateInfo>, val new: DuplicateInfo, val combined: String?)

data class DuplicateInfo(val meaning: String, val example: String, val exampleTranslation: String, val filename: String, val pageNumber: Int)

class DocumentException(message: String) : Exception(message)

@Singleton
class DocumentRepository @Inject constructor(
    private val db: JustStudyDatabase,
    private val clock: AppClock,
    private val notes: NotesSource,
    private val files: DocumentFiles,
    private val indexer: DocumentIndexer,
    private val cards: FlashcardRepository,
    private val queue: com.dero.juststudykorean.data.processing.ProcessingQueue,
) {
    /** Your documents, newest first (the web's list order), with the pipeline stage of the ones being processed. */
    fun documents(): Flow<List<LibraryDocument>> =
        combine(db.invalidationTracker.createFlow("documents").map { db.documentsDao().all() }, db.processingJobsDao().observe()) { docs, jobs ->
            val stages = jobs.associate { it.documentId to it.stage }
            docs.sortedByDescending { it.id }.map { LibraryDocument(it, warnings(it.warnings), stages[it.id]) }
        }

    private fun warnings(json: String?): List<String> =
        json?.let { runCatching { Json.parseToJsonElement(it).jsonArray.map { e -> e.jsonPrimitive.content } }.getOrNull() }.orEmpty()

    private suspend fun document(id: Long) = db.documentsDao().all().firstOrNull { it.id == id } ?: throw DocumentException("Document not found.")

    /** What the app extracted from a ready document; null if its text is not there. */
    suspend fun extracted(documentId: Long): Extracted? = withContext(Dispatchers.Default) {
        val doc = document(documentId)
        if (notes.pages(doc.id) == null) return@withContext null
        val chunks = notes.chunks(doc)
        val words = LinkedHashMap<String, ExtractedWord>()
        for (chunk in chunks) for (p in extractPairs(chunk)) {
            words.getOrPut(norm(p.korean)) { ExtractedWord(p.korean, p.english, p.romanization, p.exampleKo, p.exampleEn, p.examples, p.notes, chunk.pageNumber) }
        }
        val grammar = chunks.flatMap { c ->
            extractGrammar(c).map { ExtractedGrammar(it.kind, it.pattern, it.meaning, it.forms, it.examples, it.explanation, c.pageNumber) }
        }
        val fragments = chunks.mapIndexed { i, c -> Fragment(i + 1, c.pageNumber, c.section, c.topic, c.contentType, c.text) }
        Extracted(words.values.toList(), grammar, fragments)
    }

    /** All vocabulary: every word once, in Korean dictionary order; [query] filters by Korean or meaning. */
    suspend fun vocabulary(query: String = ""): List<VocabularyWord> = withContext(Dispatchers.Default) {
        val ready = db.documentsDao().all().filter { it.status == "ready" }.sortedBy { it.id }
        val words = cards.mergedWords(ready).values.map { VocabularyWord(it.korean, cards.joinedMeaning(it)) }
            .sortedWith(compareBy({ com.dero.juststudykorean.data.grammar.sortKey(it.korean) }, { it.korean }))
        filterVocabulary(words, query)
    }

    /** The words and grammar of [documentId] that other ready documents already had; null if it isn't ready. */
    suspend fun duplicates(documentId: Long): List<Duplicate>? = withContext(Dispatchers.Default) {
        val doc = db.documentsDao().all().firstOrNull { it.id == documentId }?.takeIf { it.status == "ready" } ?: return@withContext null
        if (notes.pages(doc.id) == null) return@withContext emptyList()
        val newWords = LinkedHashMap<String, Pair<com.dero.juststudykorean.korean.VocabPair, Int>>()
        val newGrammar = LinkedHashMap<String, com.dero.juststudykorean.korean.ReferencePoint>()
        for (chunk in notes.chunks(doc)) {
            for (p in extractPairs(chunk)) newWords.getOrPut(norm(p.korean)) { p to chunk.pageNumber }
            for (point in referencePoints(chunk)) newGrammar.getOrPut(norm(point.pattern)) { point }
        }
        val ready = db.documentsDao().all().filter { it.status == "ready" }
        val oldWords = LinkedHashMap<String, MutableList<DuplicateInfo>>()
        for (other in ready.filter { it.id != documentId }) {
            if (notes.pages(other.id) == null) continue
            for (chunk in notes.chunks(other)) for (p in extractPairs(chunk)) {
                val key = norm(p.korean)
                if (key !in newWords) continue
                val info = DuplicateInfo(p.english, p.exampleKo ?: "", p.exampleEn ?: "", other.originalName, chunk.pageNumber)
                val list = oldWords.getOrPut(key) { mutableListOf() }
                if (info !in list) list += info
            }
        }
        val names = ready.associate { it.id to it.originalName }
        val oldGrammar = LinkedHashMap<String, MutableList<DuplicateInfo>>()
        for (gp in db.grammarPointsDao().all().filter { it.documentId != documentId && it.documentId in names }) {
            val key = norm(gp.pattern)
            if (key !in newGrammar) continue
            val first = Json.parseToJsonElement(gp.examples).jsonArray.firstOrNull()?.jsonArray
            oldGrammar.getOrPut(key) { mutableListOf() } += DuplicateInfo(
                gp.meaning, first?.get(0)?.jsonPrimitive?.content ?: "", first?.get(1)?.jsonPrimitive?.content ?: "", names.getValue(gp.documentId), gp.pageNumber.toInt(),
            )
        }
        val merged = cards.mergedWords(ready.sortedBy { it.id })
        val out = mutableListOf<Duplicate>()
        for ((key, current) in oldWords) {
            val (pair, page) = newWords.getValue(key)
            out += Duplicate(
                "word", pair.korean, current, DuplicateInfo(pair.english, pair.exampleKo ?: "", pair.exampleEn ?: "", doc.originalName, page),
                merged[key]?.let(cards::joinedMeaning),
            )
        }
        for ((key, current) in oldGrammar) {
            val point = newGrammar.getValue(key)
            val first = point.examples.firstOrNull() ?: listOf("", "")
            out += Duplicate("grammar", point.pattern, current, DuplicateInfo(point.meaning, first[0], first[1], doc.originalName, point.pageNumber), null)
        }
        out.sortedWith(compareBy({ it.kind != "grammar" }, { com.dero.juststudykorean.data.grammar.sortKey(it.text) }, { it.text }))
    }

    /** Reprocess: back to the queue (the stages start again from the PDF). */
    suspend fun reprocess(documentId: Long) = db.withWriteTransaction {
        val doc = document(documentId)
        if (db.processingJobsDao().get(documentId) != null) throw DocumentException("The document is already being processed.")
        db.documentsDao().update(doc.copy(status = "uploaded", errorMessage = null))
        db.processingJobsDao().upsert(ProcessingJobsEntity(documentId, STAGE_PENDING, clock.now().toEpochMilli()))
    }

    /** Deletes the document, its PDF, its text and its index; [withCards]: also its deck and the deck's history. */
    suspend fun delete(documentId: Long, withCards: Boolean) {
        val doc = document(documentId)
        queue.cancel(documentId) // deleting the one being processed stops it first; nothing of it is left behind
        if (withCards) try { cards.delete(documentId) } catch (e: FlashcardException) { /* it had no cards */ }
        indexer.discard(documentId)
        db.withWriteTransaction {
            db.processingJobsDao().delete(documentId)
            db.documentsDao().delete(doc)
        }
        files.deletePdf(doc.storedName)
        indexer.cleanupAfterDelete()
    }
}

const val STAGE_PENDING = "PENDING"
const val STAGE_EXTRACTING = "EXTRACTING"
const val STAGE_PARSING = "PARSING"
const val STAGE_CHUNKING = "CHUNKING"
const val STAGE_INDEXING = "INDEXING"
