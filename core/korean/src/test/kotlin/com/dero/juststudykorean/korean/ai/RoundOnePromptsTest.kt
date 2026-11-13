package com.dero.juststudykorean.korean.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA round 1 (R1-17 to R1-22) with fake model outputs. */
class RoundOnePromptsTest {
    @Test
    fun aFreeHowDoISayAnswerMustBringHangul() {
        val q = "how do I say submarine"
        assertTrue(teacherNoSourcesPrompt(q).contains("Hangul"))
        assertNotNull(validNoSources("You can say 잠수함 (jamsuham). It is the usual word for a submarine.", q))
        assertNull(validNoSources("You can say 'submaring' in Korean, pronounced 'soh-mah-ring'.", q)) // romanization only
    }

    @Test
    fun otherFreeAnswersStayWithoutKorean() {
        val q = "why is Korean grammar hard"
        assertNull(validNoSources("Because 문법 has many endings and you need practice.", q))
        assertNotNull(validNoSources("Because it has many endings, and they change with politeness.", q))
    }

    @Test
    fun thePracticeSentenceHasNoTranslation() {
        assertFalse(practicePrompt("-아서/어서", listOf("가다")).contains("ENGLISH"))
        val notes = "가다 = to go\n밥 = rice, meal\n먹다 = to eat\n학교 = school"
        assertEquals("학교에 가서 밥을 먹어요.", validPractice("KOREAN: 학교에 가서 밥을 먹어요.", "-아서/어서", notes))
        assertEquals("학교에 가서 밥을 먹어요.", validPractice("KOREAN: 학교에 가서 밥을 먹어요.\nENGLISH: I know it.", "-아서/어서", notes))
    }

    @Test
    fun mistakeAndSimplerKeepTheRuleAndAllItsUses() {
        val mistake = mistakePrompt(mistakeInput("What particle marks the subject?", "이/가", "coffee", "이/가 = subject; 이 after a consonant, 가 after a vowel"))
        assertTrue(mistake, "rule" in mistake && "every use" in mistake)
        val simpler = simplerPrompt("-거든(요) = because (giving a reason); also objecting to what someone said")
        assertTrue(simpler, "every use" in simpler)
    }

    @Test
    fun theCoachAsksForNaturalEnglish() {
        assertTrue(coachPrompt("Quiz: 10 questions, Your mistakes").contains("natural"))
    }
}
