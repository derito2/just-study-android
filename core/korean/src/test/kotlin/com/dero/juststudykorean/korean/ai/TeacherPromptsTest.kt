package com.dero.juststudykorean.korean.ai

import com.dero.juststudykorean.korean.Chunk
import com.dero.juststudykorean.korean.teacher.Hit
import com.dero.juststudykorean.korean.teacher.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Use 4's validator with fake outputs: valid citations, invented Korean, a citation that doesn't exist, no sources. */
class TeacherPromptsTest {
    private fun source(n: Int, text: String) = Source(n, Hit("1:1:$n", text, 0.1, Chunk(1, "notes.pdf", 1, n, null, "General", "vocabulary", "ko", text)))
    private val sources = listOf(source(1, "근처 = Nearby\n학교 근처에 살아요."), source(2, "도서관 = Library"))

    @Test
    fun citedAnswersQuoteOnlyTheirSources() {
        assertEquals(setOf(1), validTeacher("근처 means nearby, as in 학교 근처에 살아요 [1].", sources)?.cited)
        assertNull(validTeacher("근처 means nearby, as in 공원 근처 [1].", sources)) // 공원 is not in the cited source
        assertNull(validTeacher("도서관 means library [2], and 근처 means nearby.", sources)) // 근처 is in [1], which isn't cited
        assertNull(validTeacher("It means nearby [3].", sources)) // no source 3
        assertNull(validTeacher("It means nearby, near a place.", sources)) // no citation
        assertNull(validTeacher("NOT_FOUND", sources))
    }

    @Test
    fun withoutSourcesNoKoreanAtAll() {
        assertEquals("Honorifics show respect to the listener.", validNoSources("Honorifics show respect to the listener."))
        assertNull(validNoSources("Honorifics like 습니다 show respect."))
    }
}
