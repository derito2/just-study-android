package com.dero.juststudykorean.korean.games

import com.dero.juststudykorean.korean.Chunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FASE_8_PLAN 4.1 and task 0b. Invented notes only. */
class GameBankBuilderTest {
    private fun chunk(doc: Long, page: Int, i: Int, text: String, section: String = "Words", type: String = "vocabulary") =
        Chunk(doc, "doc$doc.pdf", page, i, section, section, type, "ko-en", text)

    private val chunks = listOf(
        chunk(1, 1, 0, "1. 사과 = Apple\n사과를 먹어요. = I eat an apple.\n사과가 달아요. = The apple is sweet.\nNote: red or green."),
        chunk(1, 2, 0, "1. 사과 = Apple\n사과를 사요. = I buy apples.\n2. 가장 / 제일 = The most\n3. 커피숍 = Coffee shop\n4. 아이스크림가게 = Ice cream shop\n5. 이거 -> this\n6. 비가 와요 = It rains"),
        chunk(2, 1, 0, "1. 사과 = Apology\n사과를 받았어요. = I got an apology.\nNote: another meaning.\n2. 물 = Water"),
        chunk(2, 2, 0, "1. -아서/어서\nMeaning: so, because\nTense and structure:\n- A/V + 아서\nExplanation:\nA reason.\nExamples:\n배가 고파서 밥을 먹어요. = I am hungry, so I eat.\n피곤해서 자요. = I am tired, so I sleep.", "1. -아서/어서", "grammar"),
    )
    private val bank = buildGameBank(chunks)

    @Test
    fun aWordKeepsEveryExampleAndNoteOfEveryChunkAndDocument() {
        val apple = bank.words.single { it.korean == "사과" }
        assertEquals("w:사과", apple.key)
        assertEquals("Apple", apple.meaning) // the first document wins
        assertEquals(1L, apple.documentId)
        assertEquals(4, apple.examples.size)
        assertEquals(2, apple.notes.size)
        assertEquals(listOf("사", "과"), apple.blocks)
    }

    @Test
    fun wordsAreHangulOnlyFromOneToSixBlocks() {
        assertEquals(listOf("가장", "제일"), bank.words.single { it.korean == "가장 / 제일" }.variants)
        assertTrue(bank.words.any { it.korean == "커피숍" })
        assertNull(bank.words.firstOrNull { it.korean == "아이스크림가게" }) // 7 blocks
        assertTrue(bank.words.any { it.korean == "물" })
    }

    @Test
    fun sentencesComeFromPairsAndFromEveryTranslatedExample() {
        val sentences = bank.sentences.map { it.korean }.toSet()
        assertTrue("비가 와요" in sentences)
        assertTrue(listOf("사과를 먹어요.", "사과가 달아요.", "사과를 사요.", "사과를 받았어요.").all { it in sentences })
        assertTrue("배가 고파서 밥을 먹어요." in sentences && "피곤해서 자요." in sentences)
        assertEquals("I eat an apple", bank.sentences.single { it.korean == "사과를 먹어요." }.meaning)
        assertTrue(bank.sentences.all { it.key.startsWith("s:") && it.kind == ItemKind.SENTENCE })
    }

    @Test
    fun aGrammarPointIsWhole() {
        val point = bank.points.single { it.pattern == "-아서/어서" }
        assertEquals("g:" + com.dero.juststudykorean.korean.norm("-아서/어서"), point.key)
        assertEquals(1, point.structure.size)
        assertEquals(2, point.examples.size)
        assertEquals("A reason.", point.explanation)
    }
}
