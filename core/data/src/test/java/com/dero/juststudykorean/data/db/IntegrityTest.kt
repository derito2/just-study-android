package com.dero.juststudykorean.data.db

import androidx.room3.useReaderConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.documents.DocumentIndexer
import com.dero.juststudykorean.data.documents.DocumentRepository
import com.dero.juststudykorean.data.documents.goldenParsed
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.golden.GoldenDb
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.processing.ProcessingQueue
import com.dero.juststudykorean.data.progress.ProgressRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * docs/AUDIT_2.md H-06: the schema has no foreign keys (it is the web's, 1:1, and the backup v1 shares it), so integrity is kept by
 * hand. This walks every relation and checks that deleting a document (with and without its cards) and reprocessing another one
 * leave no row pointing at something that is gone. The golden base itself may carry rows the web already left dangling: those
 * are the baseline, and only NEW dangling rows fail the test.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class IntegrityTest : GoldenDb() {
    /** child table, its column, the parent table (always its `id`, or `topic_id`-style key given as parentKey). */
    private data class Relation(val child: String, val column: String, val parent: String, val parentKey: String = "id")

    private val relations = listOf(
        Relation("flashcard_reviews", "flashcard_id", "flashcards"),
        Relation("flashcards", "custom_deck_id", "custom_decks"),
        Relation("flashcards", "topic_id", "topics"),
        Relation("question_attempts", "question_id", "questions"),
        Relation("question_attempts", "quiz_run_id", "quiz_runs"),
        Relation("question_attempts", "session_id", "study_sessions"),
        Relation("questions", "topic_id", "topics"),
        Relation("document_topics", "document_id", "documents"),
        Relation("document_topics", "topic_id", "topics"),
        Relation("grammar_points", "document_id", "documents"),
        Relation("user_topic_progress", "topic_id", "topics"),
        Relation("study_session_topics", "session_id", "study_sessions"),
        Relation("study_session_topics", "topic_id", "topics"),
        Relation("deck_places", "group_id", "deck_groups"),
        Relation("notes_fts", "document_id", "documents"),
        Relation("processing_jobs", "document_id", "documents"),
    )

    /** Rows whose column points at nothing, per relation. */
    private fun dangling(): Map<String, Int> = runBlocking {
        db.useReaderConnection { c ->
            relations.associate { r ->
                val sql = "SELECT COUNT(*) FROM ${r.child} c WHERE c.${r.column} IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ${r.parent} p WHERE p.${r.parentKey} = c.${r.column})"
                "${r.child}.${r.column} -> ${r.parent}" to c.usePrepared(sql) { it.step(); it.getLong(0).toInt() }
            }
        }
    }

    private val progress get() = ProgressRepository(db, clock)
    private val notes get() = NotesSource(context)
    private val files get() = DocumentFiles(context)
    private val indexer get() = DocumentIndexer(db, clock, progress, notes, files)
    private val queue by lazy { ProcessingQueue(db, clock, progress, files, indexer) { goldenParsed("Korean_Notes_Example") } }
    private val repo by lazy { DocumentRepository(db, clock, notes, files, indexer, FlashcardRepository(db, clock, progress, notes), queue) }

    private fun assertNoNewDangling(before: Map<String, Int>, step: String) {
        val after = dangling()
        val worse = after.filter { (k, v) -> v > before.getValue(k) }.map { (k, v) -> "$k: ${before.getValue(k)} -> $v" }
        assertTrue("$step left dangling rows:\n" + worse.joinToString("\n"), worse.isEmpty())
    }

    @Test
    fun deletingAndReprocessingDocumentsLeavesNothingDangling() = runBlocking {
        val before = dangling()
        val ready = db.documentsDao().all().filter { it.status == "ready" }.sortedBy { it.id }
        assertTrue("the golden base has at least three ready documents", ready.size >= 3)

        repo.delete(ready[0].id, withCards = false)
        assertNoNewDangling(before, "delete without cards")

        repo.delete(ready[1].id, withCards = true)
        assertNoNewDangling(before, "delete with cards")

        repo.reprocess(ready[2].id)
        queue.runAll()
        assertNoNewDangling(before, "reprocess")
        Unit
    }

    @Test
    fun theSchemasRelationsAreTheOnesTheWebDeclares() {
        // A table or column renamed by a migration would make the walk above silently check nothing: fail loudly instead.
        runBlocking {
            db.useReaderConnection { c ->
                for (r in relations) c.usePrepared("SELECT ${r.column} FROM ${r.child} LIMIT 1") { it.step() }
                for (r in relations) c.usePrepared("SELECT ${r.parentKey} FROM ${r.parent} LIMIT 1") { it.step() }
            }
        }
    }

    /** The walk has to be able to fail: a review of a card that does not exist is counted. */
    @Test
    fun theWalkSeesADanglingRow() = runBlocking {
        val key = "flashcard_reviews.flashcard_id -> flashcards"
        val before = dangling().getValue(key)
        db.flashcardReviewsDao().insert(FlashcardReviewsEntity(9_000_000, 9_999_999, "good", 0, 0, 0))
        assertTrue(dangling().getValue(key) == before + 1)
    }
}
