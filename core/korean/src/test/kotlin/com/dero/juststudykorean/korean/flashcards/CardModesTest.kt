package com.dero.juststudykorean.korean.flashcards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F2 (FASE_11_PLAN PR 4): the same card shown another way; when it isn't sure, the card stays classic. */
class CardModesTest {
    private fun ex(vararg s: Pair<String, String>) = s.toList()

    @Test
    fun aNounWithBatchimLeavesItsParticleOutsideTheGap() {
        val c = clozeFor("책", ex("책을 읽어요." to "I read a book."), seed = 1)!!
        assertEquals(Cloze("", "을 읽어요.", "책", "I read a book."), c)
    }

    @Test
    fun aConjugatedVerbIsFoundByItsForms() {
        val c = clozeFor("먹다", ex("밥을 먹어요." to "I eat rice."), seed = 1)!!
        assertEquals("밥을 ", c.before)
        assertEquals("먹어", c.answer)
        assertEquals("요.", c.after)
        assertEquals("I eat rice.", c.translation)
        assertEquals("밥을 먹어요.", c.before + c.answer + c.after)
    }

    @Test
    fun aWordInsideAnotherWordIsNotAGap() {
        assertNull(clozeFor("가", ex("가방이 커요." to "The bag is big."), seed = 1)) // 가 is not 가방
        assertNull(clozeFor("가다", ex("가방이 커요." to "The bag is big."), seed = 1)) // nor is the verb 가다
    }

    @Test
    fun withoutAnExampleOrATranslationTheCardStaysClassic() {
        assertNull(clozeFor("학교", emptyList(), seed = 1))
        assertNull(clozeFor("학교", ex("학교에 가요." to ""), seed = 1)) // the front shows the translation: none, no gap
        assertEquals(CardMode.Classic, modeFor(7, gap = true, listen = false, hasCloze = false, voice = true))
    }

    @Test
    fun everyCardWithAGapGetsOneOfItsExamples() {
        val examples = ex("학교에 가요." to "I go to school.", "학교가 커요." to "The school is big.")
        val picked = (1L..20L).map { clozeFor("학교", examples, seed = it)!!.translation }.toSet()
        assertEquals(setOf("I go to school.", "The school is big."), picked)
    }

    @Test
    fun withoutTheVoiceListenNeverComesUp() {
        for (id in 1L..50L) {
            assertTrue(modeFor(id, gap = false, listen = true, hasCloze = true, voice = false) == CardMode.Classic)
            assertTrue(modeFor(id, gap = true, listen = true, hasCloze = true, voice = false) == CardMode.Cloze)
        }
    }

    @Test
    fun bothOnAlternateByCardAndBothOffIsToday() {
        val modes = (1L..10L).map { modeFor(it, gap = true, listen = true, hasCloze = true, voice = true) }.toSet()
        assertEquals(setOf(CardMode.Cloze, CardMode.Listen), modes)
        assertEquals(setOf(CardMode.Classic), (1L..10L).map { modeFor(it, gap = false, listen = false, hasCloze = true, voice = true) }.toSet())
        assertEquals(CardMode.Listen, modeFor(3, gap = true, listen = true, hasCloze = false, voice = true)) // only what this card admits
    }
}
