package com.dero.juststudykorean.korean.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B5 / F8 (FASE_11_PLAN PR 3): Explain this sentence, with good and bad outputs of the model. */
class ReaderPromptsTest {
    private val sentence = "배가 고파서 밥을 먹어요."

    @Test
    fun thePromptCarriesTheSentenceItsTranslationAndTheGlosses() {
        val p = sentenceExplainPrompt(sentence, "I'm hungry, so I eat.", listOf("밥 = rice, meal", "-아서/어서 = because, so"))
        assertTrue(p.startsWith("Explain in English the grammar and word order of this Korean sentence for a learner"))
        assertTrue(sentence in p && "I'm hungry, so I eat." in p && "밥 = rice, meal" in p)
        assertTrue("Translation" !in sentenceExplainPrompt(sentence, null, emptyList())) // no translation in your notes: none is sent
    }

    @Test
    fun aShortEnglishExplanationQuotingTheSentenceIsKept() {
        val out = "The sentence has two parts joined by 고파서, which means \"because I'm hungry\". 밥을 is the object, and 먹어요 is the polite verb at the end."
        assertEquals(out, validSentenceExplain(out, sentence))
    }

    @Test
    fun koreanThatIsNotInTheSentenceIsRejected() {
        assertNull(validSentenceExplain("It uses 고파서 because of hunger, like 배고프다 in the dictionary form of the verb.", sentence))
    }

    @Test
    fun moreThanThreeSentencesAreRejected() {
        assertNull(validSentenceExplain("First, 배가 is the subject. Then 고파서 gives the reason. Next 밥을 is the object. Last, 먹어요 is the verb.", sentence))
    }

    @Test
    fun anAnswerInKoreanOrTooShortOrTooLongIsRejected() {
        assertNull(validSentenceExplain("배가 고파서 밥을 먹어요.", sentence))
        assertNull(validSentenceExplain("Okay.", sentence))
        assertNull(validSentenceExplain("This sentence is about eating. ".repeat(2) + "x".repeat(600), sentence))
    }
}
