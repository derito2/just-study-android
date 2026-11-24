package com.dero.juststudykorean.data.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ai.AppForeground
import com.dero.juststudykorean.ai.NanoGate
import com.dero.juststudykorean.ai.NanoModel
import com.dero.juststudykorean.ai.NanoState
import com.dero.juststudykorean.ai.NanoStatus
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.ai.ReaderAi
import com.dero.juststudykorean.data.flashcards.FlashcardException
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.golden.GoldenDb
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.teacher.TeacherService
import com.dero.juststudykorean.korean.reader.Gloss
import com.dero.juststudykorean.korean.reader.lookup
import com.dero.juststudykorean.korean.reader.tokens
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F8 (FASE_11_PLAN PR 8): the Reader's data on the golden base: the extracted text, your notes and Add to deck. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ReaderRepositoryTest : GoldenDb() {
    private val cards by lazy { FlashcardRepository(db, clock, ProgressRepository(db, clock), NotesSource(context)) }
    private val repo by lazy { ReaderRepository(db, DocumentFiles(context), cards, TeacherService(db)) }

    @Test
    fun pagesComeFromTheExtractedTextWithTheirHeadings() = runBlocking {
        val docs = repo.documents()
        assertTrue(docs.isNotEmpty() && docs.all { it.status == "ready" })
        val pages = repo.pages(docs.first().id)
        assertTrue(pages.isNotEmpty())
        assertEquals(pages.map { it.page }, pages.map { it.page }.sorted())
        assertTrue("no blank lines", pages.flatMap { it.lines }.none { it.text.isBlank() })
        assertTrue("some line is a heading", docs.any { d -> repo.pages(d.id).flatMap { it.lines }.any { it.heading } })
        assertTrue(repo.pages(-1).isEmpty()) // no extracted text: nothing to read
    }

    @Test
    fun everyWordOfYourNotesIsFoundOnItsPage() = runBlocking {
        val notes = repo.notes()
        assertTrue(notes.words.isNotEmpty())
        val sample = notes.words.values.first { ' ' !in it.korean && tokens(it.korean).size == 1 }
        assertEquals(sample, lookup(sample.korean, notes))
        assertTrue(sample.source.isNotEmpty())
        assertTrue(lookup(sample.korean + "에서", notes) is Gloss.Inflected)
    }

    @Test
    fun addToDeckCopiesTheWordOnce() = runBlocking {
        val word = repo.notes().words.keys.first()
        val reviews = db.flashcardReviewsDao().count()
        val deck = repo.addTo(null, word) // New deck
        assertTrue(repo.decks().any { it.id == deck })
        assertEquals(deck, repo.addTo(deck, word)) // already there: nothing more
        val other = repo.notes().words.keys.first { it != word }
        repo.addTo(deck, other)
        assertEquals(2, db.flashcardsDao().all().count { it.customDeckId == -deck })
        assertEquals(reviews, db.flashcardReviewsDao().count())
        assertTrue(runCatching { repo.addTo(deck, "없는단어") }.exceptionOrNull() is FlashcardException)
    }

    private class Fake(val reply: (String) -> String) : NanoModel {
        override suspend fun status() = NanoStatus.Available
        override fun download() = emptyFlow<NanoStatus>()
        override suspend fun generate(system: String, prompt: String, maxTokens: Int, temperature: Float?) = reply(prompt)
    }

    private fun ai(model: Fake): ReaderAi {
        AppForeground.visible = true
        val state = NanoState(model).also { it.refresh() }
        runTest { state.status.first { it == NanoStatus.Available } }
        return ReaderAi(NanoGate(model, state) { false })
    }

    @Test
    fun explainQuotesOnlyTheSentence() = runBlocking {
        val sentence = "저는 학교에서 공부해요."
        assertNotNull(ai(Fake { "The subject 저는 comes first, then the place 학교에서, and the verb 공부해요 ends the sentence." }).explain(sentence, null, listOf("학교 = School")))
        assertNull(ai(Fake { "It is like 집에서 쉬어요, where the place comes before the verb at the end." }).explain(sentence, null, emptyList()))
    }
}
