package com.dero.juststudykorean.korean.ai

import com.dero.juststudykorean.korean.Chunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Use 6 with fake model lines: pairs from messy notes are kept only if both sides are close together in the chunk. */
class PairsPromptsTest {
    // Messy notes: no "Korean = English" lines the rules understand.
    private val messy = Chunk(1, "messy.pdf", 3, 0, "Food", "Food", "vocabulary", "ko", "사과\napple, the fruit\n\n\n\n우유\n(drink) milk\n\nfar away: 바나나")

    @Test
    fun messyNotesNeedTheModel() {
        assertTrue(needsLlmPairs(messy))
        assertFalse(needsLlmPairs(messy.copy(text = "1. 사과 = Apple\n2. 우유 = Milk")))
    }

    @Test
    fun onlyPairsThatAreInTheNotes() {
        val out = "사과 = apple\n우유 = milk\n포도 = grape\n바나나 = banana\nnot a pair"
        assertEquals(listOf("사과" to "apple", "우유" to "milk"), parsePairs(out, messy).map { it.korean to it.english })
        assertTrue(parsePairs(out, messy).all { it.source == "llm" })
    }
}
