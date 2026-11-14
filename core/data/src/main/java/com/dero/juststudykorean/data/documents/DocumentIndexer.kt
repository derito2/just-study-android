package com.dero.juststudykorean.data.documents

import androidx.room3.withWriteTransaction
import com.dero.juststudykorean.data.backup.BackupFormat
import com.dero.juststudykorean.data.db.DocumentTopicsEntity
import com.dero.juststudykorean.data.db.GrammarPointsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.db.NotesFtsEntity
import com.dero.juststudykorean.data.db.TopicsEntity
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.time.AppClock
import com.dero.juststudykorean.korean.GENERAL_TOPIC
import com.dero.juststudykorean.korean.extractPairs
import com.dero.juststudykorean.korean.illustratesGrammar
import com.dero.juststudykorean.korean.norm
import com.dero.juststudykorean.korean.referencePoints
import com.dero.juststudykorean.pdf.ParsedDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

// The pipeline's stages after MuPDF, ported from document_service._process_document, indexing_service.index and
// _sync_topics, and reference_service.store_document. Each stage replaces what it wrote before, so running one twice
// (the process killed half way) leaves the same rows.

private fun strings(list: List<String>) = JsonArray(list.map(::JsonPrimitive))

/** sha256 of the chunk's text, as Chunk.text_hash: the same chunk in an earlier document is not indexed again. */
internal fun textHash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

private const val GRAMMAR_CARD = 2L // FlashcardRepository.GRAMMAR_DIFFICULTY

private val PAIR_LINE = Regex("^[^=\\n]*[가-힣][^=\\n]* = [^=\\n]+$")

@Singleton
class DocumentIndexer @Inject constructor(
    private val db: JustStudyDatabase,
    private val clock: AppClock,
    private val progress: ProgressRepository,
    private val notes: NotesSource,
    private val files: DocumentFiles,
) {
    /** EXTRACTING: the parsed pages to extracted/<id>.json (the web's asdict(page)) and the document's counts. */
    /**
     * Reprocessing (phase 7): the words added from messy notes (MessyNotes.accept) are "Korean = English" lines of the
     * extracted text, not of the PDF. Per page, the ones of the old text that the new one doesn't have; MuPDF gives the
     * same text for the same PDF, so these are only the added ones.
     */
    private fun addedLines(old: String, parsed: ParsedDocument): Map<Int, List<String>> =
        Json.parseToJsonElement(old).jsonObject["pages"]?.jsonArray.orEmpty().associate { p ->
            val o = p.jsonObject
            val n = o.getValue("page_number").jsonPrimitive.int
            val now = parsed.pages.firstOrNull { it.pageNumber == n }?.text.orEmpty().lines().toSet()
            n to (o["text"]?.jsonPrimitive?.contentOrNull ?: "").lines().filter { PAIR_LINE.matches(it) && it !in now }
        }

    suspend fun saveExtracted(documentId: Long, parsed: ParsedDocument) {
        val kept = files.extracted(documentId).takeIf { it.isFile }?.let { runCatching { addedLines(it.readText(), parsed) }.getOrNull() }.orEmpty()
        val pages = JsonArray(parsed.pages.map { p ->
            buildJsonObject {
                put("page_number", p.pageNumber)
                put("text", p.text + kept[p.pageNumber].orEmpty().joinToString("") { "\n$it" })
                put("headings", strings(p.headings))
                put("section", p.section?.let(::JsonPrimitive) ?: JsonNull)
                put("tables", JsonArray(p.tables.map { t ->
                    buildJsonObject {
                        put("rows", JsonArray(t.rows.map(::strings)))
                        put("text", t.text)
                        put("bbox", JsonArray(t.bbox.toList().map(::JsonPrimitive)))
                    }
                }))
                put("image_count", p.imageCount)
                put("char_count", p.charCount)
            }
        })
        currentCoroutineContext().ensureActive() // cancelled (deleted) while MuPDF read it: write nothing
        files.writeExtracted(documentId, buildJsonObject { put("document_id", documentId); put("pages", pages) }.toString())
        db.withWriteTransaction {
            val doc = db.documentsDao().all().first { it.id == documentId }
            db.documentsDao().update(
                doc.copy(
                    pageCount = parsed.pageCount.toLong(), hasDigitalText = true, imageCount = parsed.imageCount.toLong(),
                    tableCount = parsed.tableCount.toLong(), charCount = parsed.charCount.toLong(),
                    warnings = BackupFormat.pyJson(strings(parsed.warnings)),
                ),
            )
        }
    }

    /**
     * QA round 1 (R1-15, R1-16): when a document is processed again, its cards follow the reader. A card whose example
     * is now a note of its word takes the word's example; a grammar sentence that was a card goes (its reviews stay:
     * your history). Coach decks and examples from other documents are not touched.
     */
    suspend fun refreshCards(documentId: Long) = db.withWriteTransaction {
        val doc = db.documentsDao().all().firstOrNull { it.id == documentId } ?: return@withWriteTransaction
        val chunks = notes.chunks(doc).associateBy { it.chromaId }
        val pairs = HashMap<String, List<com.dero.juststudykorean.korean.VocabPair>>()
        for (card in db.flashcardsDao().all()) {
            if (card.customDeckId != null || card.difficulty == GRAMMAR_CARD) continue
            val chunk = chunks[card.sourceChunkId] ?: continue
            val pair = pairs.getOrPut(chunk.chromaId) { extractPairs(chunk) }.firstOrNull { norm(it.korean) == norm(card.koreanWord) }
            if (pair == null) {
                if (illustratesGrammar(chunk, card.koreanWord)) db.flashcardsDao().delete(card)
                continue
            }
            val example = card.exampleSentence ?: continue
            if (pair.examples.none { it.first == example } && pair.notes.any { example in it }) {
                db.flashcardsDao().update(card.copy(exampleSentence = pair.exampleKo, exampleTranslation = pair.exampleEn))
            }
        }
    }

    /** PARSING: the document's grammar points, without those already in an earlier ready document (the first stays). */
    suspend fun storeGrammar(documentId: Long) = db.withWriteTransaction {
        val doc = db.documentsDao().all().first { it.id == documentId }
        db.grammarPointsDao().all().filter { it.documentId == documentId }.forEach { db.grammarPointsDao().delete(it) }
        val earlierDocs = db.documentsDao().all().filter { it.status == "ready" && it.id < documentId }.map { it.id }.toSet()
        val known = db.grammarPointsDao().all().filter { it.documentId in earlierDocs }.map { norm(it.pattern) }.toSet()
        var position = 0L
        for (chunk in notes.chunks(doc)) {
            for (point in referencePoints(chunk)) {
                if (norm(point.pattern) in known) continue
                db.grammarPointsDao().insert(
                    GrammarPointsEntity(
                        id = progress.nextId("grammar_points"), documentId = documentId, pageNumber = point.pageNumber.toLong(),
                        position = position++, kind = point.kind, category = point.category, pattern = point.pattern.cut(120),
                        meaning = point.meaning, structure = BackupFormat.pyJson(strings(point.structure)),
                        examples = BackupFormat.pyJson(JsonArray(point.examples.map(::strings))),
                        explanation = BackupFormat.pyJson(strings(point.explanation)), notes = BackupFormat.pyJson(strings(point.notes)),
                    ),
                )
            }
        }
    }

    /** CHUNKING: the notes index, without chunks identical to an earlier document's (the tutor would cite them twice). */
    suspend fun index(documentId: Long): Int = db.withWriteTransaction {
        val doc = db.documentsDao().all().first { it.id == documentId }
        db.notesFtsDao().deleteDocument(documentId)
        val known = db.notesFtsDao().hashesBefore(documentId).toSet()
        val rows = notes.chunks(doc).map { c ->
            NotesFtsEntity(
                documentId = documentId, chromaId = c.chromaId, page = c.pageNumber.toLong(), section = c.section?.cut(255)?.ifEmpty { null },
                topic = c.topic, contentType = c.contentType, textHash = textHash(c.text), text = c.text,
            )
        }.filter { it.textHash !in known }
        db.notesFtsDao().insertAll(rows)
        rows.size
    }

    /** INDEXING: a topic per section name (found case-insensitively or created) and its pages in this document. */
    suspend fun syncTopics(documentId: Long) = db.withWriteTransaction {
        db.documentTopicsDao().all().filter { it.documentId == documentId }.forEach { db.documentTopicsDao().delete(it) }
        val groups = LinkedHashMap<String, MutableList<NotesFtsEntity>>()
        for (row in db.notesFtsDao().forDocument(documentId).sortedBy { it.rowId }) {
            if (row.topic != GENERAL_TOPIC) groups.getOrPut(row.topic.lowercase()) { mutableListOf() } += row
        }
        for (group in groups.values) {
            val name = group[0].topic
            val topic = db.topicsDao().all().firstOrNull { it.name.lowercase() == name.lowercase() } ?: run {
                val counts = LinkedHashMap<String, Int>()
                group.forEach { counts[it.contentType] = (counts[it.contentType] ?: 0) + 1 }
                val dominant = counts.entries.fold(counts.entries.first()) { best, e -> if (e.value > best.value) e else best }.key
                TopicsEntity(progress.nextId("topics"), name, if (dominant in setOf("grammar", "vocabulary")) dominant else "other", null)
                    .also { db.topicsDao().insert(it) }
            }
            val pages = group.map { it.page }
            db.documentTopicsDao().insert(DocumentTopicsEntity(documentId, topic.id, pages.min(), pages.max()))
        }
        db.notesFtsDao().deleteOrphanTopics()
    }

    /** A rejected or deleted document leaves nothing the app could still show or cite. */
    suspend fun discard(documentId: Long) {
        db.withWriteTransaction {
            db.grammarPointsDao().all().filter { it.documentId == documentId }.forEach { db.grammarPointsDao().delete(it) }
            db.notesFtsDao().deleteDocument(documentId)
            db.documentTopicsDao().all().filter { it.documentId == documentId }.forEach { db.documentTopicsDao().delete(it) }
        }
        files.deleteExtracted(documentId)
    }

    /** After deleting a document: grammar it hid in later documents comes back (rebuild_all), orphan topics go. */
    suspend fun cleanupAfterDelete() {
        db.withWriteTransaction { db.grammarPointsDao().all().forEach { db.grammarPointsDao().delete(it) } }
        for (doc in db.documentsDao().all().filter { it.status == "ready" }.sortedBy { it.id }) storeGrammar(doc.id)
        db.notesFtsDao().deleteOrphanTopics()
    }

    /** After the v1 → v2 migration the index is empty: it is built once from extracted/ (nothing else changes). */
    suspend fun ensureIndex() {
        if (db.notesFtsDao().count() == 0 && db.documentsDao().all().any { it.status == "ready" }) rebuildIndex()
    }

    /** After an import the index is rebuilt from extracted/ (it is not in the backup). */
    suspend fun rebuildIndex() {
        db.notesFtsDao().deleteAll()
        for (doc in db.documentsDao().all().filter { it.status == "ready" }.sortedBy { it.id }) index(doc.id)
    }

    internal fun now() = clock.now().toEpochMilli()
}

/** s[:n] by code points. */
private fun String.cut(n: Int): String = if (codePointCount(0, length) <= n) this else substring(0, offsetByCodePoints(0, n))
