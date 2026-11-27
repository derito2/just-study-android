package com.dero.juststudykorean.data.mistakes

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dero.juststudykorean.data.db.GameRoundsEntity
import com.dero.juststudykorean.data.db.GrammarDrillAttemptsEntity
import com.dero.juststudykorean.data.db.ListeningAttemptsEntity
import com.dero.juststudykorean.data.golden.GoldenDb
import com.dero.juststudykorean.korean.grammar.Ending
import com.dero.juststudykorean.korean.mistakes.PatternKind
import com.dero.juststudykorean.korean.mistakes.classify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** F5 (FASE_11_PLAN PR 9): every source's mistakes on the golden base plus synthetic Games, Grammar Lab and dictation rows. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MistakesRepositoryTest : GoldenDb() {
    private val repo by lazy { MistakesRepository(db) }
    private val now = NOW.toEpochMilli()

    @Test
    fun everySourceAndOnlySinceThePeriod() = runBlocking {
        val quiz = repo.mistakes(0).filter { it.source == "Quiz" }
        assertEquals(db.questionAttemptsDao().all().count { !it.isCorrect }, quiz.size)
        assertTrue(quiz.any { it.expected == "거울" && it.given == "구름" && classify(it) == PatternKind.WrongWord })

        db.gameRoundsDao().insert(GameRoundsEntity(1, 1, 0, "WordMatch", 1, "lost", 2, 900, "[\"w:학교\",\"g:아서어서\"]", now))
        db.gameRoundsDao().insert(GameRoundsEntity(2, 1, 1, "WordMatch", 1, "won", 0, 900, "[\"w:물\"]", now))
        db.grammarDrillAttemptsDao().insert(GrammarDrillAttemptsEntity(1, "아서어서", "먹다", Ending.SoBecause.label, "Produce", "먹아서", false, now))
        db.grammarDrillAttemptsDao().insert(GrammarDrillAttemptsEntity(2, "아서어서", "가다", Ending.SoBecause.label, "Produce", "가서", true, now))
        db.listeningAttemptsDao().insert(ListeningAttemptsEntity(1, "dictation", "s:비가 와요", false, now))
        db.listeningAttemptsDao().insert(ListeningAttemptsEntity(2, "shadowing", "s:눈이 와요", false, now)) // shadowing is never graded

        val recent = repo.mistakes(now)
        assertEquals(listOf("Games", "Games", "Grammar Lab", "Listening"), recent.map { it.source }.filter { it != "Quiz" && it != "Flashcards" })
        val games = recent.filter { it.source == "Games" }
        assertEquals("학교" to null, games[0].wordKey to games[0].pointKey)
        assertEquals("아서어서", games[1].pointKey)
        val drill = recent.single { it.source == "Grammar Lab" }
        assertEquals(Triple("먹어서", "먹아서", "아서어서"), Triple(drill.expected, drill.given, drill.pointKey))
        assertEquals(PatternKind.VowelHarmony, classify(drill))
        assertEquals("비가 와요", recent.single { it.source == "Listening" }.expected)
    }

    /** 비다 is a verb or an adjective: the form closest to what you typed is the one you meant (phone test, PR 9). */
    @Test
    fun anAmbiguousStemKeepsTheFormClosestToYours() = runBlocking {
        db.grammarDrillAttemptsDao().insert(GrammarDrillAttemptsEntity(1, "구나", "비다", Ending.Realize.label, "Choose", "비어구나", false, now))
        val drill = repo.mistakes(now).single { it.source == "Grammar Lab" }
        assertEquals("비구나", drill.expected)
        assertEquals(PatternKind.Contraction, classify(drill))
    }
}
