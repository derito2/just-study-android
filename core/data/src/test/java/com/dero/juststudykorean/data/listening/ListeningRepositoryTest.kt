package com.dero.juststudykorean.data.listening

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.games.GameContentProvider
import com.dero.juststudykorean.data.golden.GoldenDb
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.korean.listening.ListenMode
import com.dero.juststudykorean.korean.listening.dictation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F4 (FASE_11_PLAN PR 7): Listening Lab's data on the golden base. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ListeningRepositoryTest : GoldenDb() {
    private val repo by lazy {
        val notes = NotesSource(context)
        ListeningRepository(db, GameContentProvider(db, notes, DocumentFiles(context)), FlashcardRepository(db, clock, ProgressRepository(db, clock), notes), clock)
    }

    @Test
    fun sentencesCardsAndAttempts() = runBlocking {
        assertTrue("the golden notes have dictation sentences", dictation(repo.sentences()).isNotEmpty())
        val (cards, due) = repo.cards()
        assertTrue(cards.isNotEmpty() && due >= 0)
        val reviews = db.flashcardReviewsDao().count()
        repo.save(ListenMode.Shadowing, "s:key", correct = true)
        val saved = db.listeningAttemptsDao().all().single()
        assertEquals("shadowing" to true, saved.mode to saved.correct)
        assertEquals(reviews, db.flashcardReviewsDao().count()) // hands-free never rates a card
    }
}
