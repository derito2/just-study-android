package com.dero.juststudykorean.korean.games

import com.dero.juststudykorean.korean.games.FactoriesTest.Companion.BANK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure checks of the games. Invented content. */
class EvaluatorsTest {
    private val apple = BANK.byKey.getValue("w:사과")

    @Test
    fun hangmanRevealsEveryPlaceOfAKey() {
        val spec = RoundSpec.Hangman(2, apple, true, listOf("ㅅ", "ㅏ", "ㄱ", "ㅘ"), listOf("ㅅ", "ㅏ", "ㄱ", "ㅘ"))
        assertEquals(listOf(1), Evaluators.hangmanReveals(spec, "ㅏ"))
        assertFalse(Evaluators.hangmanSolved(spec, setOf(0, 1)))
        assertTrue(Evaluators.hangmanSolved(spec, setOf(0, 1, 2, 3)))
    }

    @Test
    fun sentenceOrders() {
        val item = BANK.sentences.first()
        val spec = RoundSpec.SentenceBuilder(1, item, listOf("먹어요", "사과를"), listOf("사과를", "먹어요"), ".", emptyList())
        assertEquals(Outcome.WON, Evaluators.sentence(spec, listOf("사과를", "먹어요")))
        assertEquals(Outcome.LOST, Evaluators.sentence(spec, listOf("먹어요", "사과를")))
        val three = spec.copy(answer = listOf("저는", "사과를", "먹어요"))
        assertEquals(Outcome.NEUTRAL, Evaluators.sentence(three, listOf("사과를", "저는", "먹어요")))
    }

    @Test
    fun memoryAndTyping() {
        val spec = RoundSpec.MemoryFlip(1, listOf(apple), listOf(0 to false, 0 to true), 3, 1000)
        assertTrue(Evaluators.memoryPair(spec, 0, 1))
        assertFalse(Evaluators.memoryPair(spec, 0, 0))
        val most = BANK.words.single { it.korean == "가장 / 제일" }
        val typing = RoundSpec.HangulTyping(1, most, RoundSpec.TypingMode.COPY)
        assertTrue(Evaluators.typing(typing, "제일") && Evaluators.typing(typing, " 가 장 "))
        assertFalse(Evaluators.typing(typing, ""))
    }

    @Test
    fun aPathThatSpellsAnotherWordOfTheNotes() {
        val water = BANK.words.single { it.korean == "물" }
        val spec = RoundSpec.WordPath(1, apple, 3, listOf("사", "과", "물", "집", "책", "손", "발", "눈", "꽃"), listOf(0, 1))
        assertEquals(Evaluators.PathCheck.Target, Evaluators.path(spec, listOf(0, 1), BANK))
        assertEquals(Evaluators.PathCheck.OtherWord(water), Evaluators.path(spec, listOf(2), BANK))
        assertEquals(Evaluators.PathCheck.Wrong, Evaluators.path(spec, listOf(4, 5), BANK))
    }
}
