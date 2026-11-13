package com.dero.juststudykorean.data.documents

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.backup.BackupFormat
import com.dero.juststudykorean.data.db.DocumentsEntity
import com.dero.juststudykorean.data.db.FlashcardReviewsEntity
import com.dero.juststudykorean.data.db.FlashcardsEntity
import com.dero.juststudykorean.data.db.JustStudyDatabase
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.time.SystemClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** QA round 1: R1-15 and R1-16 reach the cards when the document is reprocessed. Invented notes only. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class RefreshCardsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, JustStudyDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
    private val notes = NotesSource(context)
    private val indexer = SystemClock().let { DocumentIndexer(db, it, ProgressRepository(db, it), notes, DocumentFiles(context)) }
    private val words = "17. 놓다 = To put\n책을 여기 놓으세요\n-아/어 주세요 -> please do\n18. 사과 = Apple\n사과가 맛있어요"
    private val explanation = "1. 친구가 웃는 걸 봤어요 = I saw my friend laughing\n" +
        "Here, 웃는 걸 is a noun phrase that focuses on the action of laughing, not on the person who does it.\n" +
        "2. 웃는 친구를 봤어요 = I saw the laughing friend\n" +
        "In this case, 웃는 친구를 is a noun phrase that emphasizes the friend as the one who is laughing."
    private val doc = DocumentsEntity(1, "notes.pdf", "notes.pdf", "sha", 2, "ready", true, 0, 0, 0, "[]", null, 0, 0)

    init {
        val pages = org.json.JSONArray()
            .put(org.json.JSONObject().put("page_number", 1).put("section", "Words 1–25").put("text", words).put("tables", org.json.JSONArray()))
            .put(org.json.JSONObject().put("page_number", 2).put("section", "Same sentence different meaning").put("text", explanation).put("tables", org.json.JSONArray()))
        File(context.filesDir, "${BackupFormat.FILES_DIR}/${BackupFormat.extractedEntry("1")}").apply { parentFile!!.mkdirs() }
            .writeText(org.json.JSONObject().put("document_id", 1).put("pages", pages).toString())
        runBlocking { db.documentsDao().insert(doc) }
    }

    @After
    fun close() = db.close()

    private fun card(id: Long, word: String, chunk: String, example: String?, translation: String?, deck: Long? = null) = FlashcardsEntity(
        id = id, vocabularyId = null, front = word, back = "x", koreanWord = word, translation = "x", romanization = null, exampleSentence = example,
        topicId = null, difficulty = 1, ease = 2.5, intervalHours = 0, repetitions = 0, dueAt = 0, sourceChunkId = chunk,
        exampleTranslation = translation, createdAt = 0, customDeckId = deck,
    )

    @Test
    fun reprocessingFixesTheExamplesAndDropsGrammarSentences() = runBlocking {
        val chunks = notes.chunks(doc)
        val wordChunk = chunks.first { "놓다" in it.text }.chromaId
        val sentenceChunk = chunks.first { "웃는 친구를" in it.text }
        assertEquals("explanation", sentenceChunk.contentType)
        db.flashcardsDao().insert(card(1, "놓다", wordChunk, "-아/어 주세요", "please do"))
        db.flashcardsDao().insert(card(2, "사과", wordChunk, "사과가 맛있어요", null))
        db.flashcardsDao().insert(card(3, "웃는 친구를 봤어요", sentenceChunk.chromaId, null, null))
        db.flashcardsDao().insert(card(4, "놓다", wordChunk, "-아/어 주세요", "please do", deck = 9)) // a copy in a coach deck: yours
        db.flashcardsDao().insert(card(5, "사과", wordChunk, "빨간 사과", null)) // an example from another document: kept
        db.flashcardReviewsDao().insert(FlashcardReviewsEntity(1, 3, "good", 0, 24, 0))

        indexer.refreshCards(1)

        val cards = db.flashcardsDao().all().associateBy { it.id }
        assertEquals("책을 여기 놓으세요", cards.getValue(1).exampleSentence)
        assertNull(cards.getValue(1).exampleTranslation)
        assertEquals("사과가 맛있어요", cards.getValue(2).exampleSentence)
        assertNull(cards[3])
        assertEquals("-아/어 주세요", cards.getValue(4).exampleSentence)
        assertEquals("빨간 사과", cards.getValue(5).exampleSentence)
        assertEquals(1, db.flashcardReviewsDao().all().size) // your history stays
    }
}
