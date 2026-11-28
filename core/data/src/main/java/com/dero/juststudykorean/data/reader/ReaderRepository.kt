package com.dero.juststudykorean.data.reader

import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.flashcards.Deck
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.teacher.TeacherService
import com.dero.juststudykorean.korean.reader.Gloss
import com.dero.juststudykorean.korean.reader.ReaderNotes
import com.dero.juststudykorean.korean.reader.tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

// F8 (FASE_11_PLAN PR 8, design/beta.md 5): the Reader. Only the text already extracted: MuPDF never reads the PDF again.

data class ReaderLine(val text: String, val heading: Boolean)

data class ReaderPage(val page: Int, val lines: List<ReaderLine>)

/** A deck's name for "New deck" from the Reader (FlashcardRepository dates it when taken: "Reader · 24 Nov"). */
const val READER_DECK = "Reader"

@Singleton
class ReaderRepository @Inject constructor(
    private val db: JustStudyDatabase,
    private val files: DocumentFiles,
    private val cards: FlashcardRepository,
    private val teacher: TeacherService,
) {
    /** Your Ready documents, newest first (as Library). */
    suspend fun documents(): List<DocumentsEntity> = withContext(Dispatchers.IO) {
        db.documentsDao().all().filter { it.status == "ready" }.sortedByDescending { it.id }
    }

    /** The pages of extracted/<id>.json, text then tables (kept apart from the text), line by line; "## " marks a heading. */
    suspend fun pages(documentId: Long): List<ReaderPage> = withContext(Dispatchers.IO) {
        val file = files.extracted(documentId).takeIf { it.isFile } ?: return@withContext emptyList()
        Json.parseToJsonElement(file.readText()).jsonObject["pages"]?.jsonArray.orEmpty().map { p ->
            val o = p.jsonObject
            fun strings(key: String) = o[key]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            val headings = (strings("headings") + listOfNotNull(o["section"]?.jsonPrimitive?.contentOrNull?.trim())).toSet()
            val tables = o["tables"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            val text = (listOf(o["text"]?.jsonPrimitive?.contentOrNull.orEmpty()) + tables).flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }
            ReaderPage(o.getValue("page_number").jsonPrimitive.int, text.map { line ->
                val bare = line.trimStart('#').trim()
                ReaderLine(bare, bare != line || bare in headings)
            }.filter { it.text.isNotEmpty() })
        }.sortedBy { it.page }
    }

    /** Your words (all their meanings, 2 examples and the document they come from) and Teacher's index of your notes. */
    suspend fun notes(): ReaderNotes = withContext(Dispatchers.Default) {
        val ready = db.documentsDao().all().filter { it.status == "ready" }.sortedBy { it.id }
        val names = ready.associate { it.id to it.originalName }
        val words = cards.mergedWords(ready).values.associate { w ->
            w.korean to Gloss.Entry(w.korean, cards.joinedMeaning(w), names[w.firstDoc].orEmpty(), w.examples.take(2))
        }
        val index = teacher.index()
        ReaderNotes(
            words, index.verbs, index.points.map { it.second.pattern to it.second.meaning }, index.sentences.map { it.first },
            index.points.groupBy({ it.second.pattern }) { p -> p.second.examples.flatMap { ex -> tokens(ex.firstOrNull().orEmpty()).map { it.text } } }.mapValues { (_, v) -> v.flatten().toSet() },
        )
    }

    /** Your own decks, where a word can be added (a document's deck holds that document's words only). */
    suspend fun decks(): List<Deck> = cards.decksNow().filter { it.custom }

    /** Adds [word] to deck [deckId] (negative), or to a new deck when null; returns the deck. */
    suspend fun addTo(deckId: Long?, word: String): Long =
        if (deckId == null) cards.createCustomDeck(READER_DECK, listOf(word)) else deckId.also { cards.addWords(it, listOf(word)) }
}
