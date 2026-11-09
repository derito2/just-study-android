package com.dero.juststudykorean.korean

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Normalizer

class CompareAnswerGoldenTest {
    @Test
    fun everyCaseMatches() {
        val cases = Golden.arr("compare_answer.json").objects()
        assertEquals(2011, cases.size)
        cases.forEachIndexed { i, c ->
            val typed = c.getString("typed").let { if (c.optString("transform") == "nfd") Normalizer.normalize(it, Normalizer.Form.NFD) else it }
            val got = compareAnswer(typed, c.getString("answer"), c.getBoolean("korean"))
            val want = c.getJSONObject("out")
            val where = "#$i ${c.getString("kind")}"
            assertEquals("$where verdict", want.getString("verdict"), got.verdict.name.lowercase())
            assertEquals("$where closest", want.getString("closest"), got.closest)
            assertEquals("$where off", want.getInt("off"), got.off)
            assertEquals("$where letters", want.getJSONArray("letters").objects().map { it.getString("char") to it.getString("mark") },
                got.letters.map { it.char to it.mark.name.lowercase() })
        }
    }
}
