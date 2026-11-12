package com.dero.juststudykorean.ui.quiz

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ai.AppForeground
import com.dero.juststudykorean.ai.NanoGate
import com.dero.juststudykorean.ai.NanoModel
import com.dero.juststudykorean.ai.NanoState
import com.dero.juststudykorean.ai.NanoStatus
import com.dero.juststudykorean.data.ai.QuizAi
import com.dero.juststudykorean.data.prefs.QuizForm
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.data.ai.CoachAi
import com.dero.juststudykorean.korean.coach.CoachAnswer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** design/quiz.md A.3: the quiz starts with the rules' questions; the model's arrive at the end, or drop out. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class QuizAiStreamTest {
    private val app = GoldenApp()

    /** Like idleUntil, but moves the main looper's clock too: the gate's 2 s gap and the fake's delay run on Main. */
    private fun idleUntil(timeoutMs: Long = 30_000, done: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            if (done()) return
            Thread.sleep(5)
        }
        error("timed out")
    }

    @After
    fun close() { app.close(); AppForeground.visible = false }

    /** A multiple choice about the first "Korean = English" line of the SOURCE; [fail]: nothing usable. */
    private class Fake(val slowMs: Long = 0, val fail: Boolean = false, val hang: CompletableDeferred<Unit>? = null) : NanoModel {
        override suspend fun status() = NanoStatus.Available
        override fun download() = emptyFlow<NanoStatus>()
        override suspend fun generate(system: String, prompt: String, maxTokens: Int, temperature: Float?): String {
            delay(slowMs)
            hang?.let { withContext(NonCancellable) { it.await() } } // never answers and ignores cancellation (ML Kit, phase 7)
            if (fail) return "Sorry."
            val source = prompt.substringAfter("SOURCE:\n").substringAfter("\n")
            val m = Regex("([가-힣]+)\\s*=\\s*([A-Za-z][A-Za-z ]*[A-Za-z])").find(source) ?: return "Nothing."
            val (ko, en) = m.destructured
            return "QUESTION: What does $ko mean?\nANSWER: $en\nWRONG: Banana | Tomorrow | Window\nWHY: The notes say $ko = $en."
        }
    }

    private fun gate(model: Fake): NanoGate {
        AppForeground.visible = true
        val state = NanoState(model).also { it.refresh() }
        runBlocking { repeat(50) { if (state.status.value != NanoStatus.Available) delay(20) } }
        return NanoGate(model, state) { false }
    }

    private fun started(model: Fake, form: QuizForm, gate: NanoGate = gate(model)): QuestionViewModel {
        runBlocking { app.prefs.setQuizForm(form) }
        return QuestionViewModel(app.quiz, app.prefs, QuizAi(gate, app.db, app.notes)).also { it.open(null) }
    }

    /** Phase 7: ML Kit's call hung on the OPPO and ignored the deadline; the quiz, the Coach and the judge go on without it. */
    @Test
    fun aHungModelNeverBlocksTheQuizNorTheOthers() {
        val model = Fake(hang = CompletableDeferred())
        val gate = gate(model)
        val vm = started(model, QuizForm(count = 5, types = setOf("multiple_choice", "ko_to_en")), gate)
        idleUntil(30_000) { vm.state.value.run != null && vm.state.value.pendingAi == 0 }
        val s = vm.state.value
        assertTrue(s.run!!.questions.isNotEmpty() && s.run!!.questions.all { it.id > 0 }) // the rules' questions
        assertTrue(app.quiz.aiFailed(s.run!!.id) > 0) // "{k} AI questions couldn't be written this time"
        val rules = CoachAnswer("Review your flashcards today.", emptyList(), null, null, fallback = false)
        val after = CoroutineScope(Dispatchers.Main).async {
            CoachAi(gate, app.db).write(rules) to QuizAi(gate, app.db, app.notes).sameMeaning("What does 커피 mean?", "coffee", "a coffee")
        }
        idleUntil(30_000) { after.isCompleted }
        assertEquals(rules to false, runBlocking { after.await() }) // they answered, with the rules' version
    }

    @Test
    fun startsWithTheRulesAndAddsTheModelsAtTheEnd() {
        val vm = started(Fake(slowMs = 300), QuizForm(count = 5, types = setOf("multiple_choice", "ko_to_en")))
        idleUntil { vm.state.value.run != null && !vm.state.value.loading }
        val first = vm.state.value
        assertTrue("the counter counts the model's", first.pendingAi > 0 && first.run!!.questions.size + first.pendingAi == 5)
        assertTrue(first.run!!.questions.all { it.id > 0 }) // the rules' ones first
        idleUntil(30_000) { vm.state.value.pendingAi == 0 }
        val run = vm.state.value.run!!
        assertTrue(run.questions.takeLastWhile { it.id < 0 }.isNotEmpty())
    }

    @Test
    fun aFailingModelDropsItsQuestions() {
        val vm = started(Fake(fail = true), QuizForm(count = 5, types = setOf("multiple_choice", "ko_to_en")))
        idleUntil(30_000) { vm.state.value.run != null && vm.state.value.pendingAi == 0 }
        val s = vm.state.value
        assertTrue(s.run!!.questions.all { it.id > 0 })
        assertTrue(app.quiz.aiFailed(s.run!!.id) > 0) // the results say how many couldn't be written
        assertTrue(!s.failed)
    }

    @Test
    fun allFromTheModelWaitsForTheFirst() {
        val vm = started(Fake(slowMs = 200), QuizForm(count = 3, types = setOf("multiple_choice")))
        assertTrue("state B while the first one is written", vm.state.value.loading || vm.state.value.run?.questions?.isNotEmpty() == true)
        idleUntil(30_000) { !vm.state.value.loading }
        val s = vm.state.value
        // Either the model's first question opened the quiz, or none could be written and the quiz says so.
        if (s.run?.questions?.isNotEmpty() == true) assertTrue(s.run!!.questions.first().id < 0) else assertTrue(s.failed)
    }
}
