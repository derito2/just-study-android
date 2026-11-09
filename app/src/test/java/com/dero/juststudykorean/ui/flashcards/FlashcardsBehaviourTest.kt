package com.dero.juststudykorean.ui.flashcards

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.korean.Rating
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Collections

/** Flashcards' risks, with the real repositories on the golden base (design/flashcards.md, plan task 4). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class FlashcardsBehaviourTest {
    private val app = GoldenApp()

    @After
    fun close() = app.close()

    private fun reviews() = runBlocking { app.db.flashcardReviewsDao().all().size }
    private fun cards() = runBlocking { app.db.flashcardsDao().all().size }
    private fun decks() = runBlocking { app.flashcards.decksNow() }

    @Test
    fun doubleTapRatesOnce() {
        val vm = StudyViewModel(app.flashcards, app.prefs)
        vm.open(null)
        idleUntil { !vm.state.value.loading }
        val first = vm.state.value.card!!.card.id
        vm.flip()
        val before = reviews()
        vm.rate(Rating.Good)
        vm.rate(Rating.Good) // the second tap of a double tap
        idleUntil { vm.state.value.reviewed == 1 }
        repeat(20) { idleUntil { true }; Thread.sleep(5) }
        assertEquals(before + 1, reviews())
        assertEquals(1, vm.state.value.reviewed)
        assert(vm.state.value.card?.card?.id != first)
    }

    @Test
    fun deleteThenUndoRestoresCards() {
        val vm = DecksViewModel(app.flashcards, app.prefs)
        val messages = Collections.synchronizedList(mutableListOf<DecksMessage>())
        val collector = CoroutineScope(Dispatchers.IO).launch { vm.events.collect { messages += it } }
        val deck = decks().first { it.total > 0 }
        val cardsBefore = cards()
        val reviewsBefore = reviews()
        vm.delete(deck)
        idleUntil { messages.isNotEmpty() }
        assertEquals(cardsBefore - deck.total, cards())
        assertEquals("Deleted ${deck.total} cards from ${deck.name}.", messages[0].text)
        vm.undo(messages[0].undo!!)
        idleUntil { cards() == cardsBefore }
        assertEquals(reviewsBefore, reviews())
        assertEquals(deck, decks().first { it.id == deck.id })
        collector.cancel()
    }

    @Test
    fun renameEmptyKeepsOldName() {
        val vm = DecksViewModel(app.flashcards, app.prefs)
        val deck = decks().first()
        vm.rename(deck, "   ")
        repeat(20) { idleUntil { true }; Thread.sleep(5) }
        assertEquals(deck.name, decks().first { it.id == deck.id }.name)
        vm.rename(deck, "A new name")
        idleUntil { decks().first { it.id == deck.id }.name == "A new name" }
    }

    @Test
    fun mergeNeedsName() {
        val vm = DecksViewModel(app.flashcards, app.prefs)
        val before = decks()
        assertFalse(vm.merge(before.take(2).map { it.id }, "  "))
        repeat(20) { idleUntil { true }; Thread.sleep(5) }
        assertEquals(before.map { it.id }, decks().map { it.id })
    }
}
