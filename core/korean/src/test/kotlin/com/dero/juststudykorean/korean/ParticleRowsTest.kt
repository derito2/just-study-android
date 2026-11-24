package com.dero.juststudykorean.korean

import org.junit.Assert.assertEquals
import org.junit.Test

/** What the reader does today with particle rows; the rules for writing them are in docs/NOTES_TEMPLATE.md §3 (Partículas). */
class ParticleRowsTest {
    private fun particles(vararg rows: List<String>) =
        chunkDocument(1, "t.pdf", listOf(ExtractedPage(1, null, "## Particles\n" + rows.joinToString("\n") { it.joinToString("\n") })))
            .flatMap(::extractGrammar).filter { it.kind == "particle" }

    private val also = listOf("도", "Also, too (replaces 은/는, 이/가, 을/를)", "저도 커피를 좋아해요. — I like coffee too.")
    private val only = listOf("만", "Only", "커피만 마셔요. — I only drink coffee.")
    private val of = listOf("의", "Possessive", "제 책이에요. — It is my book.")
    private val subject = listOf("이/가", "Subject", "고양이가 집 뒤에 숨었어요. — The cat hid behind the house.")

    @Test
    fun hangulInTheFunctionCutsItThere() {
        // The function line goes in English only (NOTES_TEMPLATE.md §3): the reader cuts it at the first Hangul
        // and the rest goes to the front of the example.
        val p = particles(also, only, of).single { it.pattern == "도" }
        assertEquals("Also, too (replaces", p.meaning)
        assertEquals("은/는, 이/가, 을/를) 저도 커피를 좋아해요." to "I like coffee too.", p.examples.single())
    }

    @Test
    fun aParticlesGroupIsReadWholeUpTo1500Characters() {
        // NOTES_TEMPLATE.md §3: at most 1500 characters under one Particles title (25 rows like these); past it a cut can split a row.
        assertEquals(25, particles(*Array(25) { subject }).size)
        assertEquals(25, particles(*Array(26) { subject }).size)
    }
}
