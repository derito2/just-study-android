package com.dero.juststudykorean.ui.quiz

import com.dero.juststudykorean.data.speech.SpeechAvailability
import com.dero.juststudykorean.data.speech.SpeechEvent
import com.dero.juststudykorean.data.speech.SpeechFailure
import com.dero.juststudykorean.ui.testing.FakeKoreanSpeech
import com.dero.juststudykorean.ui.testing.FakeKoreanSpeech.Companion.HOLD
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FASE_9_PLAN task 5: design/quiz-speaking.md C as a state machine, with the fake engine. Invented words. */
class SpeakingMachineTest {
    private val scope = TestScope()
    private val speech = FakeKoreanSpeech()
    private val machine = SpeakingMachine(speech, scope)
    private val phase get() = machine.state.value.phase

    private fun ready(availability: SpeechAvailability = SpeechAvailability.Ready, sentence: Boolean = false) =
        machine.start("학교에", sentence, availability, permissionDenied = false)

    @Test
    fun tryAgainIsUnlimitedAndKeepsTheComparison() {
        ready()
        repeat(6) { n ->
            speech.script(SpeechEvent.Listening, SpeechEvent.EndOfSpeech, SpeechEvent.Final(listOf("학교를"), null))
            machine.tap(); scope.advanceUntilIdle()
            assertEquals(SpeakPhase.Compared, phase)
            assertEquals(n + 1, machine.state.value.attempts)
            assertFalse(machine.state.value.comparison!!.matches)
            machine.tryAgain()
            assertEquals(SpeakPhase.Ready, phase)
            assertTrue(machine.state.value.comparison != null) // the box stays
        }
        speech.script(SpeechEvent.Final(listOf("학교 에"), null))
        machine.tap(); scope.advanceUntilIdle()
        assertTrue(machine.state.value.comparison!!.matches)
        assertTrue(machine.state.value.canGoOn)
    }

    @Test
    fun showAnswerLetsYouGoOnWithoutSpeaking() {
        ready()
        assertFalse(machine.state.value.canGoOn)
        machine.showAnswer()
        assertTrue(machine.state.value.canGoOn)
        assertEquals(0, speech.listens)
    }

    @Test
    fun cancelClosesTheMicrophone() {
        ready(); speech.script(SpeechEvent.Listening, HOLD)
        machine.tap(); scope.runCurrent()
        assertTrue(speech.open)
        machine.close() // Cancel, Next, Leave quiz, leaving the screen, ON_STOP: all close here
        scope.runCurrent()
        assertFalse(speech.open)
        assertEquals(SpeakPhase.Ready, phase)
    }

    @Test
    fun aNewQuestionClosesTheMicrophone() {
        ready(); speech.script(SpeechEvent.Listening, HOLD)
        machine.tap(); scope.runCurrent()
        assertTrue(speech.open)
        machine.start("우유", false, SpeechAvailability.Ready, false)
        scope.runCurrent()
        assertFalse(speech.open)
        assertNull(machine.state.value.comparison)
    }

    @Test
    fun losingTheAudioFocusClosesItAndGoesBackToReady() {
        ready(); speech.script(SpeechEvent.Listening, SpeechEvent.Level(0.5f)) // the engine ends without a result
        machine.tap(); scope.advanceUntilIdle()
        assertFalse(speech.open)
        assertEquals(SpeakPhase.Ready, phase)
    }

    @Test
    fun tappingAgainFinishesNowAndCheckingLastsFourSecondsAtMost() {
        ready(); speech.script(SpeechEvent.Listening, HOLD)
        machine.tap(); scope.runCurrent()
        machine.tap()
        assertEquals(1, speech.finished)
        assertEquals(SpeakPhase.Processing, phase)
        scope.advanceTimeBy(SpeakingMachine.PROCESSING_MS + 1)
        assertEquals(SpeakPhase.NoSpeech, phase)
        scope.runCurrent()
        assertFalse(speech.open)
    }

    @Test
    fun failuresNeverSayWrong() {
        ready(); speech.script(SpeechEvent.Failed(SpeechFailure.NoSpeech))
        machine.tap(); scope.advanceUntilIdle()
        assertEquals(SpeakPhase.NoSpeech, phase)
        speech.script(SpeechEvent.Failed(SpeechFailure.Engine))
        machine.tap(); scope.advanceUntilIdle()
        assertEquals(SpeakPhase.Unavailable, phase)
        assertTrue(machine.state.value.canGoOn) // nobody is left stuck
    }

    @Test
    fun withoutTheModelOrThePermissionYouCanGoOn() {
        ready(SpeechAvailability.NeedsDownload)
        assertEquals(SpeakPhase.NotDownloaded, phase)
        assertTrue(machine.state.value.canGoOn)
        ready()
        machine.permissionDenied()
        assertEquals(SpeakPhase.PermissionDenied, phase)
        machine.tap()
        assertEquals(0, speech.listens)
    }
}
