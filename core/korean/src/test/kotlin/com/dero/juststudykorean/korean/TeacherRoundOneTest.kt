package com.dero.juststudykorean.korean

import com.dero.juststudykorean.korean.teacher.MemoryStore
import com.dero.juststudykorean.korean.teacher.Teacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// QA round 1 (R1-01, R1-12): the app departs from the web here (docs/DECISIONS.md). Invented notes only.
class TeacherRoundOneTest {
    private fun chunk(i: Int, section: String, text: String, type: String = "vocabulary") = Chunk(9, "notes.pdf", 1, i, section, section, type, "mixed", text)

    private val teacher = Teacher(
        MemoryStore(
            listOf(
                chunk(0, "Words 1–25", "1. 근처 = Nearby\n도서관이 집 근처에 있어요. = The library is near my house.\n2. 우유 = Milk"),
                chunk(
                    1, "2. -는 것 vs -기",
                    "2. -는 것 vs -기\nMeaning: Nominalization = change to noun\nTense and structure:\n- -는 것: adjectives and verbs\n- -기: only verbs\n- +기 전에\n- +기 때문에\n" +
                        "Examples:\n책 읽는 것을 좋아해요\n책 읽기를 좋아해요\nExplanation:\nEither is fine.",
                    "grammar",
                ),
            ),
        ),
    )

    @Test
    fun anEnglishWordWithoutEntryIsFoundInTheTranslationOfASentence() {
        val a = teacher.ask("how do I say library")
        assertEquals("answered", a.status)
        assertTrue(a.answer, "도서관이 집 근처에 있어요." in a.answer && "[1]" in a.answer)
        assertTrue(a.sources.isNotEmpty())
    }

    @Test
    fun aKoreanWordWithAParticleIsFoundInASentence() {
        val a = teacher.ask("도서관을 어떻게 말해요?")
        assertEquals("answered", a.status)
        assertTrue(a.answer, "도서관이 집 근처에 있어요." in a.answer)
    }

    @Test
    fun aWordInNoSentenceIsLeftForTheModel() {
        assertNotEquals("answered", teacher.ask("how do I say submarine").status)
    }

    @Test
    fun aSentenceWithJeoneRelatesToGiJeoneOfYourNotes() {
        val a = teacher.ask("먹는 전에 맞아요?")
        assertEquals("check.v1", a.promptVersion)
        assertTrue(a.answer, "기 전에" in a.answer && "-는 것 vs -기" in a.answer)
    }
}
