package com.dero.juststudykorean.ui.mistakes

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.GrammarDrillAttemptsEntity
import com.dero.juststudykorean.data.mistakes.MistakesRepository
import com.dero.juststudykorean.korean.grammar.Ending
import com.dero.juststudykorean.korean.mistakes.PatternKind
import com.dero.juststudykorean.ui.testing.GoldenApp
import com.dero.juststudykorean.ui.testing.idleUntil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F5 (FASE_11_PLAN PR 9): My Mistakes on the golden base: the period, Practice this and Make a deck. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MistakesViewModelTest {
    private val app = GoldenApp()

    @After
    fun close() = app.close()

    private fun vm(period: Int) = MistakesViewModel(MistakesRepository(app.db), app.quiz, app.flashcards, app.clock).also {
        it.setPeriod(period)
        idleUntil { !it.state.value.loading }
    }

    @Test
    fun theGoldenQuizMistakesAreWordsAndMeanings() {
        val all = vm(2).state.value.patterns
        assertTrue(all.map { it.kind }.containsAll(listOf(PatternKind.WrongWord, PatternKind.Meaning)))
        assertEquals(all.sortedByDescending { it.count }.map { it.count }, all.map { it.count })
        assertTrue(vm(0).state.value.patterns.sumOf { it.count } <= all.sumOf { it.count })
    }

    @Test
    fun aGrammarPatternOpensGrammarLabWithItsPoint() {
        runBlocking { app.db.grammarDrillAttemptsDao().insert(GrammarDrillAttemptsEntity(1, "아서어서", "먹다", Ending.SoBecause.label, "Produce", "먹아서", false, app.clock.now().toEpochMilli())) }
        val m = vm(0)
        val p = m.state.value.patterns.single { it.kind == PatternKind.VowelHarmony }
        assertEquals(Practice.GrammarLab, practiceOf(p.kind))
        assertEquals("아서어서", m.pointOf(p))
    }

    @Test
    fun aSoundPatternSendsItsWordsToTheDictation() {
        runBlocking { app.db.grammarDrillAttemptsDao().insert(GrammarDrillAttemptsEntity(1, "아서어서", "먹다", Ending.SoBecause.label, "Produce", "먹엇서", false, app.clock.now().toEpochMilli())) }
        val m = vm(0)
        val p = m.state.value.patterns.single { it.kind == PatternKind.Batchim }
        assertEquals(Practice.Dictation, practiceOf(p.kind))
        assertEquals(listOf("먹다"), m.dictationWords(p))
    }

    @Test
    fun soundsOpenTheDictationAndWordsMakeADeck() {
        assertEquals(Practice.Dictation, practiceOf(PatternKind.Batchim))
        val m = vm(2)
        val words = m.state.value.patterns.first { it.kind == PatternKind.WrongWord }
        m.makeDeck(words)
        idleUntil { m.state.value.message != null }
        assertTrue(m.state.value.message!!, m.state.value.message!!.startsWith("Deck created: Mistakes · "))
        assertNotNull(runBlocking { app.db.customDecksDao().all().firstOrNull { it.name.startsWith("Mistakes") } })
    }
}
