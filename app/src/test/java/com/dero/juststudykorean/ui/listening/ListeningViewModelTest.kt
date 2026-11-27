package com.dero.juststudykorean.ui.listening

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.audio.KoreanVoice
import com.dero.juststudykorean.data.audio.VoiceState
import com.dero.juststudykorean.data.documents.DocumentFiles
import com.dero.juststudykorean.data.games.GameContentProvider
import com.dero.juststudykorean.data.listening.ListeningRepository
import com.dero.juststudykorean.data.speech.SpeechEvent
import com.dero.juststudykorean.korean.listening.ListenMode
import com.dero.juststudykorean.ui.components.speech.SpeakPhase
import com.dero.juststudykorean.ui.testing.FakeKoreanSpeech
import com.dero.juststudykorean.ui.testing.FakeKoreanVoices
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import java.time.Duration

/** F4 (FASE_11_PLAN PR 7): Listening Lab with a fake voice and a fake recognizer on the golden base. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ListeningViewModelTest {
    /** Records what it said and at which rate; "speaks" for a moment. */
    private class RecordingVoice : KoreanVoice {
        override val state = MutableStateFlow(VoiceState.Ready)
        val said = mutableListOf<Pair<String, Float>>()
        override var onInterrupted: (() -> Unit)? = null
        override suspend fun speak(text: String, rate: Float) { said += text to rate; delay(5) }
        override fun stop() = Unit
        override fun release() = Unit
    }

    private val app = GoldenApp()
    private val voice = RecordingVoice()
    private val speech = FakeKoreanSpeech()
    private val repo = ListeningRepository(app.db, GameContentProvider(app.db, app.notes, DocumentFiles(app.context)), app.flashcards, app.clock)

    @After
    fun close() = app.close()

    private fun lab(mode: ListenMode): ListeningViewModel = ListeningViewModel(repo, app.prefs, FakeKoreanVoices(app.context, voice), speech).also {
        it.handsFreePauseMs = 10 // the 3 s and 2 s pauses, short in tests
        it.setMode(mode)
        it.open()
        idleUntil { !it.state.value.loading && it.state.value.current != null }
    }

    @Test
    fun withWordsTheDictationHasOnlyTheirSentences() {
        val any = lab(ListenMode.Dictation).state.value.items.first()
        val word = any.korean.split(" ").first()
        val vm = ListeningViewModel(repo, app.prefs, FakeKoreanVoices(app.context, voice), speech).also {
            it.setMode(ListenMode.Shadowing) // a list of words is always a dictation
            it.open(listOf(word))
            idleUntil { !it.state.value.loading }
        }
        assertEquals(ListenMode.Dictation, vm.state.value.mode)
        assertTrue(vm.state.value.items.isNotEmpty() && vm.state.value.items.all { word in it.korean })
        val none = ListeningViewModel(repo, app.prefs, FakeKoreanVoices(app.context, voice), speech).also { it.open(listOf("없는단어")); idleUntil { !it.state.value.loading } }
        assertEquals(NO_SENTENCE_FOR_WORDS, none.state.value.reason)
    }

    @Test
    fun replayUsesTheSpeedAndSlowerIsPointSeven() {
        val vm = lab(ListenMode.Dictation)
        vm.setRate(1.2f)
        idleUntil { vm.state.value.rate == 1.2f }
        voice.said.clear()
        vm.replay()
        idleUntil { voice.said.size == 1 }
        vm.slower()
        idleUntil { voice.said.size == 2 }
        assertEquals(listOf(1.2f, 0.7f), voice.said.map { it.second })
    }

    @Test
    fun aRightDictationIsSavedAndTheSentenceSoundsAgain() {
        val vm = lab(ListenMode.Dictation)
        val sentence = vm.state.value.current!!
        voice.said.clear()
        vm.type(sentence.korean)
        vm.check()
        idleUntil { vm.state.value.result != null && voice.said.isNotEmpty() }
        assertTrue(vm.state.value.result!!.correct)
        assertEquals(sentence.korean, voice.said.last().first)
        idleUntil { runBlocking { app.db.listeningAttemptsDao().all() }.size == 1 }
    }

    @Test
    fun shadowingSavesOnlyWhetherItMatched() {
        val vm = lab(ListenMode.Shadowing)
        val sentence = vm.state.value.current!!
        speech.script(SpeechEvent.Final(listOf(sentence.korean), null))
        vm.tapMic()
        idleUntil { vm.speaking.value.phase == SpeakPhase.Compared }
        assertTrue(vm.speaking.value.comparison!!.matches)
        idleUntil { runBlocking { app.db.listeningAttemptsDao().all() }.size == 1 }
        val saved = runBlocking { app.db.listeningAttemptsDao().all() }.single()
        assertEquals("shadowing" to true, saved.mode to saved.correct)
    }

    @Test
    fun handsFreeGoesOnByItselfNeverRatesAndAnInterruptionPauses() {
        val reviews = runBlocking { app.db.flashcardReviewsDao().count() }
        val vm = lab(ListenMode.HandsFree)
        vm.play()
        // The pauses are delays on the main looper: move its clock on.
        idleUntil { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20)); vm.state.value.index >= 2 }
        voice.onInterrupted!!.invoke() // a call, or another app takes the audio
        idleUntil { !vm.state.value.running }
        val at = vm.state.value.index
        repeat(10) { idleUntil { true }; Thread.sleep(5) }
        assertEquals(at, vm.state.value.index)
        assertFalse(vm.state.value.running)
        assertEquals(reviews, runBlocking { app.db.flashcardReviewsDao().count() })
    }
}
