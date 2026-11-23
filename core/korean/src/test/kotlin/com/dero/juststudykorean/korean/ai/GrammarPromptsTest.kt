package com.dero.juststudykorean.korean.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Use 7 with fake outputs, and the coach's fact check. */
class GrammarPromptsTest {
    private val point = "-아서/어서 = because, so\n밥을 먹어서 배불러요. = I ate, so I'm full."
    private val notes = "먹다 = to eat\n배부르다 = to be full\n밥 = rice, meal\n가다 = to go\n학교 = school"

    @Test
    fun simplerQuotesOnlyThePoint() {
        assertNotNull(validSimpler("Use -어서 to say why something happens, like 먹어서 in your example.", point))
        assertNull(validSimpler("Use -어서 to say why, like 자서 or 학교에 가서.", point))
    }

    @Test
    fun practiceUsesOnlyYourWordsAndThePattern() {
        assertEquals("학교에 가서 밥을 먹어요.", validPractice("KOREAN: 학교에 가서 밥을 먹어요.\nENGLISH: I go to school and eat.", "-아서/어서", notes))
        assertNull(validPractice("KOREAN: 커피를 마셔서 좋아요.\nENGLISH: I drank coffee, so it's good.", "-아서/어서", notes)) // 커피, 마시다: not in your notes
        assertNull(validPractice("KOREAN: 학교에 가요.\nENGLISH: I go to school.", "-아서/어서", notes)) // no -어서
        assertNull(validPractice("학교에 가서 밥을 먹어요.", "-아서/어서", notes))
    }

    @Test
    fun validFromFactsKeepsOnlyTheFacts() { // the coach's check (the Dashboard's week is gone: AJ-10)
        val facts = "Words learned this week: 4\nQuiz answers this week: 10\nQuiz accuracy this week: 80%\nWeakest topic: Particles"
        assertNotNull(validFromFacts("You learned 4 words and got 80% right this week. Keep going with Particles.", facts, listOf("Particles", "Food")))
        assertNull(validFromFacts("You learned 6 words this week, great work on Food!", facts, listOf("Particles", "Food")))
    }
}
