package com.dero.juststudykorean.korean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// QA round 1 (R1-15, R1-16, R1-21): the app departs from the web here (docs/DECISIONS.md). Invented notes only.
class ReaderRoundOneTest {
    private fun chunk(text: String, section: String? = "Words 1–25", type: String = "vocabulary") =
        Chunk(1, "notes.pdf", 1, 0, section, cleanTopic(section), type, "ko-en", text)

    private fun pair(text: String, korean: String) = extractPairs(chunk(text)).first { it.korean == korean }

    @Test
    fun anArrowLineBelowAnEntryIsANoteNotItsExample() {
        val p = pair("17. 놓다 = To put\n책을 여기 놓으세요\n-아/어 주세요 -> please do", "놓다")
        assertEquals("책을 여기 놓으세요", p.exampleKo)
        assertNull(p.exampleEn)
        assertTrue(p.examples.none { "주세요" in it.first })
        assertTrue(p.notes.any { "please do" in it })
    }

    @Test
    fun aUnicodeArrowLineBeforeTheSentenceIsANote() {
        val p = pair("22. 닫다 = To close\n열다 → to open\n문을 닫아 주세요", "닫다")
        assertEquals("문을 닫아 주세요", p.exampleKo)
        assertTrue(p.notes.any { it.startsWith("열다") })
    }

    @Test
    fun aDoubleEqualsLineIsANote() {
        val p = pair("5. 사이 = Between\n== 간격\n우리 사이가 좋아요", "사이")
        assertEquals("우리 사이가 좋아요", p.exampleKo)
        assertTrue(p.notes.any { it.startsWith("==") })
    }

    @Test
    fun aLineWithoutTheWordIsNeverTheExample() {
        assertNull(pair("8. 장소 = Place\n곳 - casual", "장소").exampleKo)
        assertNull(pair("9. 가운데 = Center\n중앙 = Middle of a place", "가운데").exampleKo)
    }

    @Test
    fun aSentenceEntryDoesNotTakeAnExampleFromItsExplanation() {
        val p = pair("12. 비가 오니까 집에 있어요 = Since it rains, I stay home.\nExample: 가다 (to go) → 가니까 (because going)", "비가 오니까 집에 있어요")
        assertNull(p.exampleKo)
    }

    @Test
    fun anArrowTranslationOfASentenceWithTheWordStaysTheExample() {
        val p = pair("7. 고르다 = To choose\n이 중에서 하나를 고르세요 → Choose one of these.", "고르다")
        assertEquals("이 중에서 하나를 고르세요", p.exampleKo)
        assertEquals("Choose one of these", p.exampleEn)
    }

    @Test
    fun aTranslatedSentenceWithTheWordIsPreferred() {
        val p = pair("3. 빵 = Bread\n빵을 먹어요\n빵이 맛있어요 = The bread is tasty", "빵")
        assertEquals("빵이 맛있어요", p.exampleKo)
    }

    @Test
    fun sentencesThatIllustrateGrammarAreNotCards() {
        val text = "1. 친구가 웃는 걸 봤어요 = I saw my friend laughing\nHere, 웃는 걸 is a noun phrase that focuses on the action of laughing.\n" +
            "2. 웃는 친구를 봤어요 = I saw the laughing friend\nIn this case, 웃는 친구를 is a noun phrase that emphasizes the friend."
        assertEquals(emptyList<VocabPair>(), extractPairs(chunk(text, "Same sentence different meaning", "explanation")))
    }

    @Test
    fun aWordEntryInAnExplanationIsStillACard() {
        val text = "40. 지치다 = To be exhausted\nThis word is used when you are tired after a long effort, and it is more common in speech."
        assertEquals(listOf("지치다"), extractPairs(chunk(text, type = "explanation")).map { it.korean })
    }

    @Test
    fun aGrammarExampleEndsWhereItsKoreanEnds() {
        val text = "Meaning: whether\nExamples:\n내가 할지 동생이 할지 아직 몰라요. = We don't know yet whether I'll do it or my\nbrother\nwill.\n" +
            "비가 올지 모르겠어요. = I don't know whether it'll rain."
        val point = extractGrammar(chunk(text, "1. -ㄹ지", "grammar")).single()
        assertEquals("내가 할지 동생이 할지 아직 몰라요.", point.examples[0].first)
        assertEquals("We don't know yet whether I'll do it or my brother will.", point.examples[0].second)
        assertEquals("비가 올지 모르겠어요.", point.examples[1].first)
    }
}
