package com.dero.juststudykorean.korean

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shipped Notes template (tools/fixtures/Korean_Notes_Template.pdf) as the app's own reader (MuPDF, on the phone)
 * extracted it into tools/fixtures/Korean_Notes_Template.pages.json, read back with the app's rules.
 */
class KoreanNotesTemplateTest {
    private val pages = JSONObject(File("../../tools/fixtures/Korean_Notes_Template.pages.json").readText()).getJSONArray("pages").let { a ->
        (0 until a.length()).map { i ->
            val p = a.getJSONObject(i)
            ExtractedPage(p.getInt("page_number"), p.optString("section").ifEmpty { null }, p.getString("text"))
        }
    }
    private val chunks = chunkDocument(1, "Korean_Notes_Template.pdf", pages)
    private val pairs = chunks.flatMap(::extractPairs)
    private val grammar = chunks.flatMap(::extractGrammar)

    private fun word(ko: String) = pairs.single { it.korean == ko }
    private fun point(p: String) = grammar.single { it.pattern == p }

    @Test
    fun wordsKeepTheirExamplesAndNotes() {
        val nearby = word("근처")
        assertEquals("Nearby", nearby.english)
        assertEquals(2, nearby.examples.size)
        assertEquals(1, word("가장 / 제일").examples.size)
        val walk = word("걷다")
        assertEquals("geotda", walk.romanization)
        assertEquals(2, walk.examples.size)
        assertTrue(walk.examples[0].first.startsWith("매일 공원에서 걸어요"))
        assertTrue(walk.examples[1].first.startsWith("내일은 학교까지 걸을"))
        assertEquals("Tomorrow I will walk to school.", walk.examples[1].second)
        val repay = word("갚다")
        assertEquals(1, repay.examples.size)
        assertEquals(2, repay.notes.size)
        assertTrue(repay.notes[0].startsWith("-지 모르겠어요 ->"))
        assertEquals(1, word("얼른").examples.size)
        assertEquals(listOf("빨리 → fast (synonym: saved as a note)"), word("얼른").notes)
        assertEquals(1, word("챙기다").examples.size)
        assertEquals(1, word("챙기다").notes.size)
    }

    @Test
    fun exactlyTwoSentenceCards() {
        val sentences = chunks.filter { it.section == "Sentences" }.flatMap(::extractPairs)
        assertEquals(listOf("피곤하니까 집에 갈래요", "비가 오니까 우산을 챙기세요"), sentences.map { it.korean })
        assertTrue(sentences[1].notes.single().startsWith("Use 으니까"))
        assertEquals(6, pairs.size - sentences.size)
    }

    @Test
    fun grammarPointsKeepTheirParts() {
        val a = point("-아서/어서")
        assertEquals(3, a.forms.size)
        assertEquals(2, a.explanation!!.split(". ").size)
        assertEquals(2, a.examples.size)
        assertTrue(point("-ㄹ지/을지").explanation!!.startsWith("Use -ㄹ지 말지"))
        assertEquals(2, point("-ㄹ지/을지").examples.size)
        assertEquals(2, point("-네요").examples.size)
        // a grammar example is never a word
        assertFalse(pairs.any { it.korean.startsWith("비가 와서") })
    }

    @Test
    fun particlesAreThreeGrammarPointsAndNoWords() {
        val chunk = chunks.single { it.section == "Particles" }
        assertTrue(extractPairs(chunk).isEmpty())
        val particles = extractGrammar(chunk)
        assertTrue(particles.all { it.kind == "particle" })
        assertEquals(listOf("이/가", "을/를", "에"), particles.map { it.pattern })
        assertEquals(listOf("Subject", "Object", "Place or time"), particles.map { it.meaning })
        assertEquals("고양이가 집 뒤에 숨었어요." to "The cat hid behind the house.", particles[0].examples.single().let { it.first to it.second.trimEnd('.') + "." })
        assertTrue(particles.all { it.examples.size == 1 && it.examples[0].second.isNotEmpty() })
        assertTrue(particles[1].examples[0].first.startsWith("집 주위를 둘러봤어요"))
        assertTrue(particles[2].examples[0].first.startsWith("가방 안에 있어요"))
    }

    @Test
    fun theIntroductionIsNeitherWordNorNote() {
        val intro = chunks.filter { it.section == "My Korean notes" }
        assertTrue(intro.flatMap(::extractPairs).isEmpty())
        assertTrue(intro.flatMap(::extractGrammar).isEmpty())
        val introStarts = listOf("One rule", "Korean sentences", "Particles:", "Grammar:")
        assertTrue(pairs.none { p -> p.notes.any { n -> introStarts.any(n::startsWith) } })
        assertTrue(grammar.none { g -> g.explanation.orEmpty().let { e -> introStarts.any { it in e } } })
    }
}
