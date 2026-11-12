package com.dero.juststudykorean.korean.ai

import com.dero.juststudykorean.korean.Chunk
import com.dero.juststudykorean.korean.PyRandom
import com.dero.juststudykorean.korean.VocabPair
import com.dero.juststudykorean.korean.buildModelQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task 2's validators with fake model outputs: approve, reject, invented Korean. */
class QuizPromptsTest {
    @Test
    fun judgeOnlyApprovesEnglish() {
        assertTrue(judgeApproves("YES, both mean thankful.", "to be grateful"))
        assertFalse(judgeApproves("NO. Different meaning.", "to be grateful"))
        assertFalse(judgeApproves("Maybe", "to be grateful"))
        assertFalse(judgeApproves("YES", "감사하다")) // never Korean that differs from your notes
    }

    @Test
    fun mistakeQuotesOnlyItsInput() {
        val input = mistakeInput("Pick the particle: 책___ 있어요", "이", "가", "이/가 = subject; 이 after a consonant")
        assertNotNull(validMistake("You used 가, but 이 goes after a consonant like 책.", input))
        assertNull(validMistake("You used 가, but 은 is better here, like 학교.", input)) // invented Korean
        assertNull(validMistake("이 다음에 와요 자음 뒤에서는 이를 써요", input)) // not English
    }

    private val chunk = Chunk(1, "notes.pdf", 2, 0, "Food", "Food", "vocabulary", "ko", "1. 사과 = Apple\n2. 우유 = Milk\n3. 빵 = Bread\n4. 물 = Water")
    private val pairs = listOf(VocabPair("사과", "Apple"), VocabPair("우유", "Milk"), VocabPair("빵", "Bread"), VocabPair("물", "Water"))

    @Test
    fun modelQuestionsAreCheckedLikeTheWeb() {
        val mc = parseQuestion("QUESTION: What does 사과 mean?\nANSWER: Apple\nWRONG: Milk | Bread | Water\nWHY: The notes say 사과 = Apple.", "multiple_choice")!!
        val ok = buildModelQuestion(mc, chunk, listOf("multiple_choice"), pairs, emptyList(), PyRandom(7))
        assertNotNull(ok.reason, ok.candidate)
        assertEquals(4, ok.candidate!!.options!!.size)
        val invented = mc.copy(prompt = "What does 포도 mean?")
        assertEquals("invented_korean", buildModelQuestion(invented, chunk, listOf("multiple_choice"), pairs, emptyList(), PyRandom(7)).reason)
        val sa = parseQuestion("QUESTION: How do you say Milk in Korean?\nANSWER: 우유\nWHY: The notes list it.", "short_answer")!!
        assertEquals("우유", buildModelQuestion(sa, chunk, listOf("short_answer"), pairs, emptyList(), PyRandom(7)).candidate?.answer)
        assertEquals("type_not_requested", buildModelQuestion(sa, chunk, listOf("multiple_choice"), pairs, emptyList(), PyRandom(7)).reason)
        assertNull(parseQuestion("Sure! Here is a question about apples.", "short_answer"))
    }
}
