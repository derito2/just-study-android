package com.dero.juststudykorean.ui.flashcards

import com.dero.juststudykorean.ui.components.displayName
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.korean.Rating
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.games.GameContentProvider
import com.dero.juststudykorean.korean.flashcards.CardMode
import com.dero.juststudykorean.ui.testing.FakeKoreanVoice
import com.dero.juststudykorean.ui.testing.FakeKoreanVoices
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private val voice = FakeKoreanVoice()
    private fun study() = StudyViewModel(
        app.flashcards, app.prefs, FakeKoreanVoices(app.context, voice), GameContentProvider(app.db, app.notes, DocumentFiles(app.context)),
    )

    /** The mode each card of the queue gets now (as StudyContent asks for it). */
    private fun modes(vm: StudyViewModel) = vm.state.value.queue.map { vm.modeOf(it) }

    @Test
    fun withBothSwitchesOffEveryCardIsAsToday() {
        val vm = study()
        vm.open(null)
        idleUntil { !vm.state.value.loading && vm.state.value.clozes != null }
        assertEquals(setOf(CardMode.Classic), modes(vm).toSet())
    }

    @Test
    fun aFillTheGapCardIsRatedOnTheSameFlashcard() {
        val cardsBefore = cards()
        runBlocking { app.prefs.setGapCards(true) }
        val vm = study()
        vm.open(null)
        idleUntil { !vm.state.value.loading && vm.state.value.clozes != null && vm.gapCards.value }
        assertTrue("some golden card has a gap in its examples", CardMode.Cloze in modes(vm))
        var rated = 0
        while (vm.modeOf(vm.state.value.card!!) != CardMode.Cloze) { // skip to the first gap card, rating the others
            vm.flip(); vm.rate(Rating.Good); rated++
            idleUntil { vm.state.value.reviewed == rated }
        }
        val card = vm.state.value.card!!.card.id
        vm.flip()
        vm.rate(Rating.Good)
        idleUntil { vm.state.value.reviewed == rated + 1 }
        val last = runBlocking { app.db.flashcardReviewsDao().all().maxBy { it.id } }
        assertEquals(card, last.flashcardId)
        assertEquals(cardsBefore, cards()) // no new card was made for the mode
    }

    @Test
    fun theSpeakerSaysTheKoreanAndAsksForVolumeWhenMuted() {
        val vm = study()
        vm.open(null)
        idleUntil { !vm.state.value.loading }
        vm.speak("가방")
        idleUntil { voice.said.isNotEmpty() }
        assertEquals(listOf("가방"), voice.said)
        voice.muted = true
        vm.speak("학교")
        idleUntil { vm.state.value.volumeHint }
        assertEquals(listOf("가방"), voice.said) // nothing sounds at volume 0: the hint shows instead
    }

    @Test
    fun doubleTapRatesOnce() {
        val vm = study()
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
        val vm = DecksViewModel(app.flashcards, app.prefs, app.words)
        val messages = Collections.synchronizedList(mutableListOf<DecksMessage>())
        val collector = CoroutineScope(Dispatchers.IO).launch { vm.events.collect { messages += it } }
        val deck = decks().first { it.total > 0 }
        val cardsBefore = cards()
        val reviewsBefore = reviews()
        vm.delete(deck)
        idleUntil { messages.isNotEmpty() }
        assertEquals(cardsBefore - deck.total, cards())
        assertEquals("Deleted ${deck.total} cards from ${displayName(deck.name, 40)}.", messages[0].text) // the snackbar shows the name without .pdf
        vm.undo(messages[0].undo!!)
        idleUntil { cards() == cardsBefore }
        assertEquals(reviewsBefore, reviews())
        assertEquals(deck, decks().first { it.id == deck.id })
        collector.cancel()
    }

    @Test
    fun renameEmptyKeepsOldName() {
        val vm = DecksViewModel(app.flashcards, app.prefs, app.words)
        val deck = decks().first()
        vm.rename(deck, "   ")
        repeat(20) { idleUntil { true }; Thread.sleep(5) }
        assertEquals(deck.name, decks().first { it.id == deck.id }.name)
        vm.rename(deck, "A new name")
        idleUntil { decks().first { it.id == deck.id }.name == "A new name" }
    }

    @Test
    fun mergeNeedsName() {
        val vm = DecksViewModel(app.flashcards, app.prefs, app.words)
        val before = decks()
        assertFalse(vm.merge(before.take(2).map { it.id }, "  "))
        repeat(20) { idleUntil { true }; Thread.sleep(5) }
        assertEquals(before.map { it.id }, decks().map { it.id })
    }
}
