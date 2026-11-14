package com.dero.juststudykorean.korean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Round 1, point 6: a chunk is read once; the same text gives the same result without reading it again. */
class ExtractCacheTest {
    private val chunk = Chunk(1, "notes.pdf", 1, 0, "Words", "Words", "vocabulary", "ko-en", "1. 물 = Water\n2. 불 = Fire")

    @Test
    fun theSameChunkIsReadOnce() {
        val first = extractPairs(chunk)
        assertSame(first, extractPairs(chunk.copy()))
        assertSame(extractGrammar(chunk), extractGrammar(chunk.copy()))
        assertEquals(listOf("물", "불"), first.map { it.korean })
    }
}
