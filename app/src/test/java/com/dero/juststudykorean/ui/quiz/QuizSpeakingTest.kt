package com.dero.juststudykorean.ui.quiz

import com.dero.juststudykorean.ui.components.speech.SpeakPhase
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.prefs.QuizForm
import com.dero.juststudykorean.data.speech.SpeechEvent
import com.dero.juststudykorean.ui.testing.FakeKoreanSpeech
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** FASE_9_PLAN task 5: spoken questions on the golden base never save an answer and are not scored. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class QuizSpeakingTest {
    private val app = GoldenApp()
    private val speech = FakeKoreanSpeech()

    @After
    fun close() = app.close()

    private fun attempts() = runBlocking { app.db.questionAttemptsDao().all().size }

    private fun started(types: Set<String>, saved: SavedStateHandle = SavedStateHandle()): QuestionViewModel {
        runBlocking { app.prefs.setQuizForm(QuizForm(count = 10, types = types)) }
        return QuestionViewModel(app.quiz, app.prefs, null, saved, speech).also { vm -> vm.open(null); idleUntil { !vm.state.value.loading } }
    }

    @Test
    fun theGeneratorGetsWriteInKorean() {
        assertEquals(listOf("ko_to_en", "write_ko"), requestTypes(listOf("ko_to_en", SPEAK)))
        assertEquals(listOf("write_ko"), requestTypes(listOf("write_ko", SPEAK)))
    }

    @Test
    fun spokenQuestionsAreNeverSavedNorScored() {
        val before = attempts()
        val vm = started(setOf(SPEAK))
        val s = vm.state.value
        assertTrue("the golden notes give sayable questions", s.spoken.isNotEmpty())
        assertTrue(s.run!!.questions.filter { it.id in s.spoken }.all(::speakable))
        assertEquals("only Say it in Korean: every question is spoken", s.run!!.questions.map { it.id }.toSet(), s.spoken)
        var spokenSeen = 0
        while (!vm.state.value.finished) {
            val now = vm.state.value
            if (now.isSpoken) {
                spokenSeen++
                speech.script(SpeechEvent.Final(listOf("다른 말"), null))
                vm.tapMic(); idleUntil { vm.speaking.value.phase == SpeakPhase.Compared }
                vm.answer(now.question!!.correctAnswer) // ignored: spoken answers never go to answer()
            } else {
                vm.answer(now.question!!.correctAnswer); idleUntil { now.question!!.id in vm.state.value.answers }
            }
            vm.next()
        }
        val normal = vm.state.value.run!!.questions.size - spokenSeen
        assertEquals(before + normal, attempts())
        val runId = vm.state.value.run!!.id
        assertEquals(spokenSeen, app.quiz.spoken(runId).size)
        assertTrue(app.quiz.spoken(runId).all { it.heard == "다른 말" })
        assertEquals(normal, runBlocking { app.quiz.results("run-$runId") }.size) // the score counts the normal ones only
    }

    @Test
    fun bothTypesAlternateStartingSpoken() {
        val vm = started(setOf("write_ko", SPEAK))
        val sayable = vm.state.value.run!!.questions.filter(::speakable)
        assertEquals(sayable.filterIndexed { i, _ -> i % 2 == 0 }.map { it.id }.toSet(), vm.state.value.spoken)
    }

    @Test
    fun afterTheProcessIsKilledSpokenQuestionsAreSkipped() {
        val saved = SavedStateHandle()
        val first = started(setOf(SPEAK), saved)
        val spoken = first.state.value.spoken
        val reopened = QuestionViewModel(app.quiz, app.prefs, null, saved, speech).also { it.open(null); idleUntil { !it.state.value.loading } }
        val q = reopened.state.value.question
        assertTrue(reopened.state.value.finished || q!!.id !in spoken)
        if (reopened.state.value.finished) { // only spoken ones: the results show the practice, not "0 / 0 correct"
            val practice = app.quiz.spoken(reopened.state.value.run!!.id)
            assertEquals(spoken, practice.map { it.question.id }.toSet())
            assertTrue(practice.all { it.heard == null })
        }
    }
}
