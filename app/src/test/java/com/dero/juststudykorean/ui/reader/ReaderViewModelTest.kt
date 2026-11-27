package com.dero.juststudykorean.ui.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.audio.KoreanVoice
import com.dero.juststudykorean.data.audio.VoiceState
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.reader.ReaderRepository
import com.dero.juststudykorean.data.teacher.TeacherService
import com.dero.juststudykorean.korean.reader.Gloss
import com.dero.juststudykorean.korean.reader.tokens
import com.dero.juststudykorean.ui.testing.FakeKoreanVoices
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F8 (FASE_11_PLAN PR 8): the Reader on the golden base: documents, pages, a tap, a long press and Add to deck. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ReaderViewModelTest {
    private class RecordingVoice : KoreanVoice {
        override val state = MutableStateFlow(VoiceState.Ready)
        val said = mutableListOf<String>()
        override var onInterrupted: (() -> Unit)? = null
        override suspend fun speak(text: String, rate: Float) { said += text }
        override fun stop() = Unit
        override fun release() = Unit
    }

    private val app = GoldenApp()
    private val voice = RecordingVoice()
    private val repo = ReaderRepository(app.db, DocumentFiles(app.context), app.flashcards, TeacherService(app.db))

    @After
    fun close() = app.close()

    private fun reader(documentId: Long? = null) = ReaderViewModel(repo, FakeKoreanVoices(app.context, voice), ai = null).also {
        it.open(documentId)
        idleUntil { !it.state.value.loading }
    }

    @Test
    fun choosingADocumentLoadsItsPages() {
        val vm = reader()
        val ui = vm.state.value
        assertTrue(ui.documents.isNotEmpty())
        assertNull(ui.document)
        vm.choose(ui.documents.first())
        idleUntil { vm.state.value.pages.isNotEmpty() }
        assertEquals(ui.documents.first(), vm.state.value.document)
        vm.back()
        assertNull(vm.state.value.document) // back to the list
    }

    @Test
    fun tappingAWordShowsWhatYourNotesSay() {
        val first = reader().state.value.documents.first()
        val vm = reader(first.id)
        val line = vm.state.value.pages.flatMap { it.lines }.first { l -> tokens(l.text).isNotEmpty() && !l.heading }
        val word = tokens(line.text).first().text
        vm.tap(word)
        idleUntil { vm.state.value.gloss != null }
        val gloss = vm.state.value.gloss!!
        assertTrue(gloss !is Gloss.NotFound || (gloss as Gloss.NotFound).surface == word)
        vm.speakWord()
        idleUntil { voice.said.isNotEmpty() }
        assertEquals(word, voice.said.last())
        vm.closeSheet()
        assertNull(vm.state.value.gloss)
    }

    @Test
    fun addToDeckFromTheSheet() {
        val vm = reader(reader().state.value.documents.first().id)
        val word = repo.let { kotlinx.coroutines.runBlocking { it.notes().words.keys.first() } }
        vm.tap(word)
        idleUntil { vm.state.value.gloss != null }
        vm.chooseDeck()
        idleUntil { vm.state.value.decks != null }
        vm.addTo(null) // New deck
        idleUntil { vm.state.value.added != null }
        assertTrue(vm.state.value.added!!.startsWith("Added to Reader"))
        assertNull(vm.state.value.decks)
    }

    @Test
    fun longPressWithoutTheModelListsTheWords() {
        val vm = reader(reader().state.value.documents.first().id)
        val line = vm.state.value.pages.flatMap { it.lines }.first { tokens(it.text).size >= 2 }.text
        vm.longPress(line)
        assertEquals(line, vm.state.value.sentence?.line)
        vm.explain()
        idleUntil { vm.state.value.sentence?.glosses != null }
        assertNull(vm.state.value.sentence!!.explanation) // no model: the glosses are the explanation
        assertEquals(tokens(line).map { it.text }.distinct(), vm.state.value.sentence!!.glosses!!.map { it.first })
        vm.hearSentence()
        idleUntil { voice.said.isNotEmpty() }
        assertNotNull(voice.said.single { it == line })
    }
}
