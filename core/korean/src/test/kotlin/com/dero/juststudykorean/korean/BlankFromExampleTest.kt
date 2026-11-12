package com.dero.juststudykorean.korean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** R-017: Fill in the blank by rules, from the examples of your notes (docs/RECOMMENDATIONS.md). */
class BlankFromExampleTest {
    private fun chunk(text: String) = Chunk(1, "notes.pdf", 1, 0, "-아서/어서", "grammar", "grammar", "ko", text)
    private val others = listOf(GrammarPoint("pattern", "-지만", "but"), GrammarPoint("pattern", "-고", "and"), GrammarPoint("pattern", "-(으)면", "if"))
    private val text = "-아서/어서 = because\n밥을 먹어서 배불러요. = I ate, so I'm full.\n비가 오지만 가요.\n친구를 만나고 집에 가요.\n돈이 있으면 사요."

    @Test
    fun theFormAfterAStemBecomesTheGap() {
        val point = GrammarPoint("pattern", "-아서/어서", "because", examples = listOf("밥을 먹어서 배불러요." to "I ate, so I'm full."))
        val built = buildBlankFromExample(point, "밥을 먹어서 배불러요.", others, chunk(text), text, PyRandom(7))
        val c = built.candidate
        assertNotNull(built.reason, c)
        assertEquals("Fill in the blank: 밥을 먹___ 배불러요.", c!!.prompt)
        assertEquals("어서", c.answer)
        assertEquals(4, c.options!!.size)
        assertTrue(c.options!!.containsAll(listOf("어서", "지만", "고")))
    }

    @Test
    fun noFormNoQuestion() {
        val point = GrammarPoint("pattern", "-아서/어서", "because")
        assertEquals("form_not_found", buildBlankFromExample(point, "배고파요.", others, chunk(text), text, PyRandom(7)).reason)
        assertEquals("too_few_distractors", buildBlankFromExample(point, "밥을 먹어서 배불러요.", others.take(2), chunk(text), text, PyRandom(7)).reason)
    }

    /** Every blank built from the test PDFs: its sentence, filled in, is literal in the document (no model, no invented Korean). */
    @Test
    fun testPdfsGiveLiteralSentences() {
        var built = 0
        for (name in File("../../golden/chunks").listFiles { f -> f.extension == "json" }!!.map { it.nameWithoutExtension }.sorted()) {
            val chunks = ChunkerGoldenTest.chunks(name)
            val grounding = chunks.joinToString("\n") { it.text }
            val points = chunks.flatMap { c -> extractGrammar(c).map { c to it } }
            for ((c, p) in points) for ((ko, _) in p.examples) {
                val cand = buildBlankFromExample(p, ko, points.map { it.second }, c, grounding, PyRandom(7)).candidate ?: continue
                built++
                val filled = cand.prompt.removePrefix("Fill in the blank: ").replace("___", cand.answer)
                assertTrue(filled, norm(filled) in norm(c.text))
            }
        }
        println("R-017: $built blanks from the test PDFs")
        assertTrue(built > 0)
    }
}
