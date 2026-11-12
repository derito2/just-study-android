package com.dero.juststudykorean.korean.ai

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Use 5's validator: nothing that the rules didn't compute. */
class CoachPromptsTest {
    private val facts = "Today: practise Particles (high priority). 12 flashcards are due. Your words: 근처, 도서관."
    private val topics = listOf("Particles", "Food", "Verbs")

    @Test
    fun onlyTheFacts() {
        assertNotNull(validFromFacts("Start with Particles, it's your weakest topic. Then review your 12 due flashcards, like 근처.", facts, topics))
        assertNull(validFromFacts("Review your 15 due flashcards today, then rest a bit.", facts, topics)) // a new number
        assertNull(validFromFacts("Practise Particles, then words like 학교 and 근처 today.", facts, topics)) // new Korean
        assertNull(validFromFacts("Practise Particles first, then Food words for a change.", facts, topics)) // a topic not in the facts
        assertNull(validFromFacts("오늘은 조사를 공부하세요", facts, topics)) // not English
    }
}
