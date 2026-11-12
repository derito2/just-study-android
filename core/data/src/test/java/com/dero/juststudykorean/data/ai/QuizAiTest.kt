package com.dero.juststudykorean.data.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.ai.AppForeground
import com.dero.juststudykorean.ai.NanoGate
import com.dero.juststudykorean.ai.NanoModel
import com.dero.juststudykorean.ai.NanoState
import com.dero.juststudykorean.ai.NanoStatus
import com.dero.juststudykorean.data.golden.GoldenDb
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.quiz.QuizRepository
import com.dero.juststudykorean.data.quiz.QuizRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Phase 6, task 2, with a fake model on the golden database: each quiz use approves, rejects, or falls back. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class QuizAiTest : GoldenDb() {
    private class Fake : NanoModel {
        var judge = "YES, same meaning."
        var explain = "You wrote the other meaning; the notes give a different one."
        var invent = false
        var slowMs = 0L
        override suspend fun status() = NanoStatus.Available
        override fun download() = emptyFlow<NanoStatus>()
        override suspend fun generate(system: String, prompt: String, maxTokens: Int, temperature: Float?): String {
            if (slowMs > 0) delay(slowMs)
            return when {
                prompt.startsWith("A Korean learner was asked") -> judge
                prompt.startsWith("A Korean learner got") -> explain
                else -> question(prompt)
            }
        }

        /** A multiple choice question about the first "Korean = English" line of the SOURCE. */
        private fun question(prompt: String): String {
            val source = prompt.substringAfter("SOURCE:\n").substringAfter("\n")
            val line = source.lines().firstOrNull { Regex("^[\\s\\d.)]*[가-힣]+\\s*=\\s*[A-Za-z]").containsMatchIn(it) } ?: return "Nothing suitable."
            val (ko, en) = line.split("=", limit = 2).map { it.trim().trimStart('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', ')', ' ') }
            val asked = if (invent) "포도라는" else ko
            return "QUESTION: What does $asked mean?\nANSWER: $en\nWRONG: Banana | Tomorrow | Window\nWHY: The notes say $ko = $en."
        }
    }

    private fun setUp(model: Fake): Pair<QuizAi, QuizRepository> {
        AppForeground.visible = true
        val state = NanoState(model).also { it.refresh() }
        runBlocking { repeat(50) { if (state.status.value != NanoStatus.Available) delay(20) } }
        val notes = NotesSource(context)
        return QuizAi(NanoGate(model, state) { false }, db, notes) to QuizRepository(db, clock, ProgressRepository(db, clock), notes)
    }

    @Test
    fun judgeApprovesOnlyEnglishAndFallsBackWhenLate() = runBlocking {
        val fake = Fake()
        val (ai, _) = setUp(fake)
        assertTrue(ai.sameMeaning("What does '감사하다' mean?", "To be thankful", "to be grateful"))
        assertFalse(ai.sameMeaning("What does '감사하다' mean?", "To be thankful", "고맙다")) // never Korean
        fake.judge = "NO, different."
        assertFalse(ai.sameMeaning("What does '가다' mean?", "To go", "to come"))
        fake.judge = "YES"; fake.slowMs = 2_500 // over the judge's 2 s
        assertFalse(ai.sameMeaning("What does '오다' mean?", "To come", "to arrive"))
    }

    @Test
    fun mistakeExplanationNeverInventsKorean() = runBlocking {
        val fake = Fake()
        val (ai, _) = setUp(fake)
        assertEquals(fake.explain, ai.explainMistake("Pick the particle: 책___", "이", "가", "이/가 = subject marker"))
        fake.explain = "Use 은 like in 학교, not 가."
        assertNull(ai.explainMistake("Pick the particle: 물___", "이", "가", "이/가 = subject marker")) // another input (the first is cached)
        assertNull(ai.explainMistake("q", "a", "b", null)) // no grammar point: no model
    }

    /** The model's questions stay in memory: nothing of them reaches `questions` or `question_attempts` (backup tables). */
    @Test
    fun modelQuestionsAreNeverSaved() = runBlocking {
        val (ai, quiz) = setUp(Fake())
        val request = QuizRequest(count = 4, types = listOf("multiple_choice", "ko_to_en"), seed = 7)
        val written = ai.questions(request, listOf(1, 2), 7)
        assertTrue("the fake writes questions from the notes", written.isNotEmpty())
        val questionsBefore = db.questionsDao().all().size
        val attemptsBefore = db.questionAttemptsDao().all().size
        val run = quiz.generate(request, written)!!
        val mine = run.questions.filter { it.id < 0 }
        assertEquals(written.size, mine.size)
        quiz.answer(run.id, mine[0].id, mine[0].correctAnswer, 1_000)
        assertEquals(questionsBefore + run.questions.size - mine.size, db.questionsDao().all().size)
        assertEquals(attemptsBefore, db.questionAttemptsDao().all().size)
        assertTrue(db.questionsDao().all().none { it.model == "gemini-nano" })
        assertTrue(quiz.results("run-${run.id}").any { it.question.id == mine[0].id && it.correct })
    }

    @Test
    fun inventedKoreanQuestionsAreDropped() = runBlocking {
        val (ai, _) = setUp(Fake().apply { invent = true })
        assertTrue(ai.questions(QuizRequest(count = 3, types = listOf("multiple_choice"), seed = 7), listOf(1), 7).isEmpty())
    }

    /** Only a true/false is saved when the judge approves (the owner's choice), and the grade says the model accepted it. */
    @Test
    fun anApprovedAnswerCountsAsCorrect() = runBlocking {
        val (_, quiz) = setUp(Fake())
        val run = quiz.generate(QuizRequest(count = 3, types = listOf("ko_to_en"), seed = 7))!!
        val q = run.questions.first()
        val grade = quiz.answer(run.id, q.id, "something else entirely", 1_000, accepted = true)
        assertTrue(grade.correct && grade.aiAccepted)
        assertTrue(db.questionAttemptsDao().all().last().isCorrect)
    }
}
