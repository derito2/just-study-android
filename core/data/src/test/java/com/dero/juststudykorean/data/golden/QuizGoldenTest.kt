package com.dero.juststudykorean.data.golden

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.notes.NotesSource
import com.dero.juststudykorean.data.progress.ProgressRepository
import com.dero.juststudykorean.data.quiz.QuizRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class QuizGoldenTest : GoldenDb() {
    private val repo by lazy { QuizRepository(db, clock, ProgressRepository(db, clock), NotesSource(context)) }

    @Test
    fun mistakes() = runBlocking {
        assertJson("mistakes", golden("quiz_mistakes"), js(repo.mistakesNow(5).map { obj("question_id" to it.question.id, "last_at" to pyNaive(it.lastAt), "times" to it.times) }))
    }

    @Test
    fun history() = runBlocking {
        assertJson("history", golden("quiz_history"), js(repo.historyNow(20).map { c ->
            obj("key" to c.key, "title" to c.title, "started_at" to pyNaive(c.startedAt), "items" to c.items.map { listOf(it.questionId, it.prompt, it.result) })
        }))
    }

    @Test
    fun savedQuestions() = runBlocking {
        assertJson("saved", golden("saved_questions"), js(repo.savedQuestions(null).map { it.id }))
    }
}
