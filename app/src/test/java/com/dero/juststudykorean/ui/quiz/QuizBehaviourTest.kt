package com.dero.juststudykorean.ui.quiz

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.prefs.QuizForm
import com.dero.juststudykorean.data.quiz.options
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Quiz risks, with the real repositories on the golden base (design/quiz.md, plan task 5). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class QuizBehaviourTest {
    private val app = GoldenApp()

    @After
    fun close() = app.close()

    private fun attempts() = runBlocking { app.db.questionAttemptsDao().all().size }

    private fun started(form: QuizForm = QuizForm()): QuestionViewModel {
        runBlocking { app.prefs.setQuizForm(form) }
        return QuestionViewModel(app.quiz, app.prefs).also { vm -> vm.open(null); idleUntil { !vm.state.value.loading } }
    }

    @Test
    fun onlyModelTypesShowsNoQuestions() {
        val vm = started(QuizForm(types = setOf("short_answer")))
        assertTrue(vm.state.value.failed)
        assertEquals(null, vm.state.value.run)
    }

    @Test
    fun answerIsSavedImmediately() {
        val vm = started()
        val q = vm.state.value.question!!
        val before = attempts()
        vm.answer(if (answerMode(q.type) == "choice") q.options().first() else "something")
        idleUntil { q.id in vm.state.value.answers }
        assertEquals(before + 1, attempts()) // saved before Next, before the end of the quiz
        assertTrue(vm.lastGenerationMs >= 0)
    }

    @Test
    fun correctQuestionRestsSevenDays() {
        val vm = started(QuizForm(count = 10))
        val q = vm.state.value.question!!
        vm.answer(q.correctAnswer)
        idleUntil { q.id in vm.state.value.answers }
        assertTrue(vm.state.value.answers.getValue(q.id).grade.correct)
        val again = started(QuizForm(count = 10)).state.value.run!!
        assertFalse("a question you got right rests 7 days", again.questions.any { it.id == q.id })
        val saved = runBlocking { app.quiz.practiceSaved() }!!
        assertTrue("…and goes to Practice saved questions", saved.questions.any { it.id == q.id })
    }

    @Test
    fun formOptionsPersist() {
        val vm = QuizHomeViewModel(app.quiz, app.prefs, app.clock)
        val form = QuizForm(count = 7, documentIds = setOf(1), types = setOf("write_ko", "short_answer"), levels = setOf(3))
        vm.setForm(form)
        idleUntil { runBlocking { app.prefs.quizForm.first() } == form }
        val reopened = QuizHomeViewModel(app.quiz, app.prefs, app.clock)
        idleUntil { reopened.form.value != null }
        assertEquals(form, reopened.form.value)
    }
}
