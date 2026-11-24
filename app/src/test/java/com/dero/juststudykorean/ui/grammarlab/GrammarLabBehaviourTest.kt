package com.dero.juststudykorean.ui.grammarlab

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.grammar.GrammarLabRepository
import com.dero.juststudykorean.data.grammar.GrammarRepository
import com.dero.juststudykorean.korean.grammar.Drill
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** design/beta.md 3 (FASE_11_PLAN PR 6): a Grammar Lab session on the golden base. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class GrammarLabBehaviourTest {
    private val app = GoldenApp()
    private val repo = GrammarLabRepository(app.db, GrammarRepository(app.db), app.notes, app.clock)
    private fun attempts() = runBlocking { app.db.grammarDrillAttemptsDao().count() }

    @After
    fun close() = app.close()

    private fun lab(level: Int = 2): GrammarLabViewModel = GrammarLabViewModel(repo, app.prefs).also {
        it.open(null, seed = 3)
        it.setLevel(level)
        idleUntil { !it.state.value.loading && it.state.value.drills.isNotEmpty() }
    }

    private fun answerOf(d: Drill) = when (d) {
        is Drill.Choose -> d.answer
        is Drill.Produce -> d.answers.first()
        is Drill.FillExample -> d.answers.first()
    }

    @Test
    fun aRightAnswerIsSavedAndAWrongOneIsExplained() {
        val vm = lab()
        val first = vm.state.value.drill!!
        vm.type(answerOf(first))
        vm.check()
        idleUntil { vm.state.value.result != null }
        assertTrue(vm.state.value.result!!.correct)
        idleUntil { attempts() == 1 } // saved off the main thread
        vm.next()
        vm.type("틀린 답")
        vm.check()
        idleUntil { vm.state.value.result != null }
        assertFalse(vm.state.value.result!!.correct)
        assertNotNull(vm.state.value.result!!.explanation) // by rules, no model
        idleUntil { attempts() == 2 }
        assertEquals(2, attempts())
    }

    @Test
    fun afterTheSessionTheSummaryAndThenTheMissedOnesAgain() {
        val vm = lab()
        val total = vm.state.value.drills.size
        var wrong = 0
        repeat(total) { i ->
            val d = vm.state.value.drill!!
            vm.type(if (i % 2 == 0) answerOf(d) else "틀린 답".also { wrong++ })
            vm.check()
            idleUntil { vm.state.value.result != null }
            vm.next()
        }
        assertTrue(vm.state.value.done)
        assertEquals(total - wrong, vm.state.value.answers.count { it.second })
        vm.practiceMissed()
        idleUntil { !vm.state.value.done }
        assertEquals(wrong, vm.state.value.drills.size)
    }

    @Test
    fun levelOneChooses() {
        val vm = lab(level = 1)
        assertTrue(vm.state.value.drills.all { it is Drill.Choose })
    }

    @Test
    fun withoutGrammarPointsItSaysWhatToAdd() {
        runBlocking { app.db.grammarPointsDao().all().forEach { app.db.grammarPointsDao().delete(it) } }
        val vm = GrammarLabViewModel(repo, app.prefs).also { it.open(null, seed = 3) }
        idleUntil { !vm.state.value.loading }
        assertTrue(vm.state.value.empty)
    }
}
