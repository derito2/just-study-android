package com.dero.juststudykorean.data.golden

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.flashcards.FlashcardRepository
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class FlashcardGoldenTest : GoldenDb() {
    private val repo by lazy { FlashcardRepository(db, clock, ProgressRepository(db, clock), NotesSource(context)) }

    @Test
    fun decks() = runBlocking {
        assertJson("decks", golden("flashcard_decks"), js(repo.decksNow().map { d ->
            obj(
                "document_id" to d.id, "custom" to d.custom, "merged" to d.merged, "members" to d.members, "name" to d.name,
                "total" to d.total, "new" to d.new, "learning" to d.learning, "due" to d.due, "available_now" to d.availableNow,
                "last_studied_at" to d.lastStudiedAt, "last_card" to d.lastCard?.let { obj("korean_word" to it.koreanWord, "translation" to it.translation) },
                "next_due_at" to d.nextDueAt,
            )
        }))
    }

    @Test
    fun stats() = runBlocking {
        val s = repo.statsNow()
        assertJson("stats", golden("flashcard_stats"), obj(
            "total" to s.total, "new" to s.new, "due_now" to s.dueNow, "learning" to s.learning, "learned" to s.learned, "next_due_at" to s.nextDueAt,
        ))
    }

    @Test
    fun dueCards() = runBlocking {
        assertJson("due_cards", golden("due_cards"), js(repo.dueCards(20).map { (card, new) -> obj("id" to card.id, "new" to new) }))
    }

    @Test
    fun history() = runBlocking {
        assertJson("history", golden("history"), js(repo.history(50).map { h ->
            obj("flashcard_id" to h.flashcardId, "document_id" to h.deckId, "korean_word" to h.koreanWord, "translation" to h.translation,
                "rating" to h.rating, "reviewed_at" to h.reviewedAt, "interval_hours" to h.intervalHours)
        }))
    }
}
